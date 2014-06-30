package io.bitsquare.msg;

import com.google.inject.Inject;
import io.bitsquare.BitSquare;
import io.bitsquare.msg.listeners.*;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.payment.offerer.OffererPaymentProtocol;
import io.bitsquare.trade.payment.taker.TakerPaymentProtocol;
import io.bitsquare.user.Arbitrator;
import io.bitsquare.util.DSAKeyUtil;
import io.bitsquare.util.FileUtil;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import net.tomp2p.connection.Bindings;
import net.tomp2p.connection.PeerConnection;
import net.tomp2p.futures.*;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerMaker;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.ObjectDataReply;
import net.tomp2p.storage.Data;
import net.tomp2p.storage.StorageDisk;
import net.tomp2p.utils.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * That facade delivers messaging functionality from the TomP2P library
 * The TomP2P library codebase shall not be used outside that facade.
 * That way a change of the library will only affect that class.
 */
@SuppressWarnings({"EmptyMethod", "ConstantConditions"})
public class MessageFacade
{
    private static final String PING = "ping";
    private static final String PONG = "pong";
    private static final Logger log = LoggerFactory.getLogger(MessageFacade.class);
    private static final int MASTER_PEER_PORT = 5000;
    @NotNull
    private static String MASTER_PEER_IP = "192.168.1.33";
    private final List<OrderBookListener> orderBookListeners = new ArrayList<>();
    private final List<TakeOfferRequestListener> takeOfferRequestListeners = new ArrayList<>();
    private final List<ArbitratorListener> arbitratorListeners = new ArrayList<>();
    // //TODO change to map (key: offerID) instead of list (offererPaymentProtocols, takerPaymentProtocols)
    private final List<TakerPaymentProtocol> takerPaymentProtocols = new ArrayList<>();
    private final List<OffererPaymentProtocol> offererPaymentProtocols = new ArrayList<>();
    private final List<PingPeerListener> pingPeerListeners = new ArrayList<>();
    private final BooleanProperty isDirty = new SimpleBooleanProperty(false);
    private Peer myPeer;
    @Nullable
    private KeyPair keyPair;
    private Peer masterPeer;
    private Long lastTimeStamp = -3L;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MessageFacade()
    {
       /* try
        {
            masterPeer = BootstrapMasterPeer.INSTANCE(MASTER_PEER_PORT);
        } catch (Exception e)
        {
            if (masterPeer != null)
                masterPeer.shutdown();
            System.err.println("masterPeer already instantiated by another app. " + e.getMessage());
        }  */
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void init()
    {
        int port = Bindings.MAX_PORT - Math.abs(new Random().nextInt()) % (Bindings.MAX_PORT - Bindings.MIN_DYN_PORT);
        if ("taker".equals(BitSquare.ID))
            port = 4501;
        else if ("offerer".equals(BitSquare.ID))
            port = 4500;

        try
        {
            createMyPeerInstance(port);
            // setupStorage();
            //TODO save periodically or get informed if network address changes
            saveMyAddressToDHT();
            setupReplyHandler();
        } catch (IOException e)
        {
            shutDown();
            log.error("Error at setup myPeerInstance" + e.getMessage());
        }
    }

    public void shutDown()
    {
        if (myPeer != null)
            myPeer.shutdown();

        if (masterPeer != null)
            masterPeer.shutdown();
    }


    public void setupReputationRoot() throws IOException
    {
        String pubKeyAsHex = DSAKeyUtil.getHexStringFromPublicKey(getPubKey());  // out message ID
        final Number160 locationKey = Number160.createHash("REPUTATION_" + pubKeyAsHex); // out reputation root storage location
        final Number160 contentKey = Utils.makeSHAHash(getPubKey().getEncoded());  // my pubKey -> i may only put in 1 reputation
        final Data reputationData = new Data(Number160.ZERO).setProtectedEntry().setPublicKey(getPubKey()); // at registration time we add a null value as data
        // we use a pubkey where we provable cannot own the private key.
        // the domain key must be verifiable by peers to be sure the reputation root was net deleted by the owner.
        // so we use the locationKey as it already meets our requirements (verifiable and impossible to create a private key out of it)
        myPeer.put(locationKey).setData(contentKey, reputationData).setDomainKey(locationKey).setProtectDomain().start();
    }

    public void addReputation(String pubKeyAsHex) throws IOException
    {
        final Number160 locationKey = Number160.createHash("REPUTATION_" + pubKeyAsHex);  // reputation root storage location ot the peer
        final Number160 contentKey = Utils.makeSHAHash(getPubKey().getEncoded());  // my pubKey -> i may only put in 1 reputation, I may update it later. eg. counter for 5 trades...
        final Data reputationData = new Data("TODO: some reputation data..., content signed and sig attached").setProtectedEntry().setPublicKey(getPubKey());
        myPeer.put(locationKey).setData(contentKey, reputationData).start();
    }

    // At any offer or take offer fee payment the trader add the tx id and the pubKey and the signature of that tx to that entry.
    // That way he can prove with the signature that he is the payer of the offer fee.
    // It does not assure that the trade was really executed, but we can protect the traders privacy that way.
    // If we use the trade, we would link all trades together and would reveal the whole trading history.
    @SuppressWarnings("UnusedParameters")
    public void addOfferFeePaymentToReputation(String txId, String pubKeyOfFeePayment) throws IOException
    {
        String pubKeyAsHex = DSAKeyUtil.getHexStringFromPublicKey(getPubKey());  // out message ID
        final Number160 locationKey = Number160.createHash("REPUTATION_" + pubKeyAsHex);  // reputation root storage location ot the peer
        final Number160 contentKey = Utils.makeSHAHash(getPubKey().getEncoded());  // my pubKey -> i may only put in 1 reputation, I may update it later. eg. counter for 5 trades...
        final Data reputationData = new Data("TODO: tx, btc_pubKey, sig(tx), content signed and sig attached").setProtectedEntry().setPublicKey(getPubKey());
        myPeer.put(locationKey).setData(contentKey, reputationData).start();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Arbitrators
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addArbitrator(@NotNull Arbitrator arbitrator) throws IOException
    {
        Number160 locationKey = Number160.createHash("Arbitrators");
        final Number160 contentKey = Number160.createHash(arbitrator.getId());
        @NotNull final Data arbitratorData = new Data(arbitrator);
        //offerData.setTTLSeconds(5);
        final FutureDHT addFuture = myPeer.put(locationKey).setData(contentKey, arbitratorData).start();
        //final FutureDHT addFuture = myPeer.add(locationKey).setData(offerData).start();
        addFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(@NotNull BaseFuture future) throws Exception
            {
                Platform.runLater(() -> onArbitratorAdded(arbitratorData, future.isSuccess(), locationKey));
            }
        });
    }

    @SuppressWarnings("UnusedParameters")
    private void onArbitratorAdded(Data arbitratorData, boolean success, Number160 locationKey)
    {
    }


    @SuppressWarnings("UnusedParameters")
    public void getArbitrators(Locale languageLocale)
    {
        final Number160 locationKey = Number160.createHash("Arbitrators");
        final FutureDHT getArbitratorsFuture = myPeer.get(locationKey).setAll().start();
        getArbitratorsFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(@NotNull BaseFuture future) throws Exception
            {
                final Map<Number160, Data> dataMap = getArbitratorsFuture.getDataMap();
                Platform.runLater(() -> onArbitratorsReceived(dataMap, future.isSuccess()));
            }
        });
    }

    private void onArbitratorsReceived(Map<Number160, Data> dataMap, boolean success)
    {
        for (@NotNull ArbitratorListener arbitratorListener : arbitratorListeners)
            arbitratorListener.onArbitratorsReceived(dataMap, success);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Publish offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO use Offer and do proper serialisation here
    public void addOffer(@NotNull Offer offer) throws IOException
    {
        Number160 locationKey = Number160.createHash(offer.getCurrency().getCurrencyCode());
        final Number160 contentKey = Number160.createHash(offer.getId());
        @NotNull final Data offerData = new Data(offer);
        //offerData.setTTLSeconds(5);
        final FutureDHT addFuture = myPeer.put(locationKey).setData(contentKey, offerData).start();
        //final FutureDHT addFuture = myPeer.add(locationKey).setData(offerData).start();
        addFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(@NotNull BaseFuture future) throws Exception
            {
                Platform.runLater(() -> onOfferAdded(offerData, future.isSuccess(), locationKey));
            }
        });
    }

    private void onOfferAdded(Data offerData, boolean success, @NotNull Number160 locationKey)
    {
        setDirty(locationKey);

        for (@NotNull OrderBookListener orderBookListener : orderBookListeners)
            orderBookListener.onOfferAdded(offerData, success);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Get offers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void getOffers(String currency)
    {
        final Number160 locationKey = Number160.createHash(currency);
        final FutureDHT getOffersFuture = myPeer.get(locationKey).setAll().start();
        getOffersFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(@NotNull BaseFuture future) throws Exception
            {
                final Map<Number160, Data> dataMap = getOffersFuture.getDataMap();
                Platform.runLater(() -> onOffersReceived(dataMap, future.isSuccess()));
            }
        });
    }

    private void onOffersReceived(Map<Number160, Data> dataMap, boolean success)
    {
        for (@NotNull OrderBookListener orderBookListener : orderBookListeners)
            orderBookListener.onOffersReceived(dataMap, success);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Remove offer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void removeOffer(@NotNull Offer offer)
    {
        Number160 locationKey = Number160.createHash(offer.getCurrency().getCurrencyCode());
        Number160 contentKey = Number160.createHash(offer.getId());
        log.debug("removeOffer");
        FutureDHT removeFuture = myPeer.remove(locationKey).setReturnResults().setContentKey(contentKey).start();
        removeFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(@NotNull BaseFuture future) throws Exception
            {
                Data data = removeFuture.getData();
                Platform.runLater(() -> onOfferRemoved(data, future.isSuccess(), locationKey));
            }
        });
    }

    private void onOfferRemoved(Data data, boolean success, @NotNull Number160 locationKey)
    {
        log.debug("onOfferRemoved");
        setDirty(locationKey);

        for (@NotNull OrderBookListener orderBookListener : orderBookListeners)
            orderBookListener.onOfferRemoved(data, success);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Check dirty flag for a location key
    ///////////////////////////////////////////////////////////////////////////////////////////

    @NotNull
    public BooleanProperty getIsDirtyProperty()
    {
        return isDirty;
    }

    public void getDirtyFlag(@NotNull Currency currency)
    {
        Number160 locationKey = Number160.createHash(currency.getCurrencyCode());
        FutureDHT getFuture = myPeer.get(getDirtyLocationKey(locationKey)).start();
        getFuture.addListener(new BaseFutureListener<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                Data data = getFuture.getData();
                if (data != null)
                {
                    Object object = data.getObject();
                    if (object instanceof Long)
                    {
                        final long lastTimeStamp = (Long) object;
                        //System.out.println("getDirtyFlag " + lastTimeStamp);
                        Platform.runLater(() -> onGetDirtyFlag(lastTimeStamp));
                    }
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception
            {
                System.out.println("getFuture exceptionCaught " + System.currentTimeMillis());
            }
        });
    }

    private void onGetDirtyFlag(long timeStamp)
    {
        // TODO don't get updates at first run....
        if (lastTimeStamp != timeStamp)
        {
            isDirty.setValue(!isDirty.get());
        }
        if (lastTimeStamp > 0)
            lastTimeStamp = timeStamp;
        else
            lastTimeStamp++;
    }

    private Number160 getDirtyLocationKey(@NotNull Number160 locationKey)
    {
        return Number160.createHash(locationKey + "Dirty");
    }

    private void setDirty(@NotNull Number160 locationKey)
    {
        // we don't want to get an update from dirty for own changes, so update the lastTimeStamp to omit a change trigger
        lastTimeStamp = System.currentTimeMillis();
        try
        {
            FutureDHT putFuture = myPeer.put(getDirtyLocationKey(locationKey)).setData(new Data(lastTimeStamp)).start();
            putFuture.addListener(new BaseFutureListener<BaseFuture>()
            {
                @Override
                public void operationComplete(BaseFuture future) throws Exception
                {
                    //System.out.println("operationComplete");
                }

                @Override
                public void exceptionCaught(Throwable t) throws Exception
                {
                    System.err.println("exceptionCaught");
                }
            });
        } catch (IOException e)
        {
            log.warn("Error at writing dirty flag (timeStamp) " + e.getMessage());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Send message
    ///////////////////////////////////////////////////////////////////////////////////////////

   /* public boolean sendMessage(Object message)
    {
        boolean result = false;
        if (otherPeerAddress != null)
        {
            if (peerConnection != null)
                peerConnection.close();

            peerConnection = myPeer.createPeerConnection(otherPeerAddress, 20);
            if (!peerConnection.isClosed())
            {
                FutureResponse sendFuture = myPeer.sendDirect(peerConnection).setObject(message).start();
                sendFuture.addListener(new BaseFutureAdapter<BaseFuture>()
                {
                    @Override
                    public void operationComplete(BaseFuture baseFuture) throws Exception
                    {
                        if (sendFuture.isSuccess())
                        {
                            final Object object = sendFuture.getObject();
                            Platform.runLater(() -> onResponseFromSend(object));
                        }
                        else
                        {
                            Platform.runLater(() -> onSendFailed());
                        }
                    }
                }
                );
                result = true;
            }
        }
        return result;
    } */
      /*
    private void onResponseFromSend(Object response)
    {
        for (MessageListener messageListener : messageListeners)
            messageListener.onResponseFromSend(response);
    }

    private void onSendFailed()
    {
        for (MessageListener messageListener : messageListeners)
            messageListener.onSendFailed();
    }
      */

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Find peer address
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void getPeerAddress(final String pubKeyAsHex, @NotNull AddressLookupListener listener)
    {
        final Number160 location = Number160.createHash(pubKeyAsHex);
        final FutureDHT getPeerAddressFuture = myPeer.get(location).start();
        getPeerAddressFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(@NotNull BaseFuture baseFuture) throws Exception
            {
                if (baseFuture.isSuccess() && getPeerAddressFuture.getData() != null)
                {
                    @NotNull final PeerAddress peerAddress = (PeerAddress) getPeerAddressFuture.getData().getObject();
                    Platform.runLater(() -> onAddressFound(peerAddress, listener));
                }
                else
                {
                    Platform.runLater(() -> onGetPeerAddressFailed(listener));
                }
            }
        });
    }

    private void onAddressFound(final PeerAddress peerAddress, @NotNull AddressLookupListener listener)
    {
        listener.onResult(peerAddress);
    }

    private void onGetPeerAddressFailed(@NotNull AddressLookupListener listener)
    {
        listener.onFailed();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade process
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendTradeMessage(final PeerAddress peerAddress, final TradeMessage tradeMessage, @NotNull TradeMessageListener listener)
    {
        final PeerConnection peerConnection = myPeer.createPeerConnection(peerAddress, 10);
        final FutureResponse sendFuture = myPeer.sendDirect(peerConnection).setObject(tradeMessage).start();
        sendFuture.addListener(new BaseFutureAdapter<BaseFuture>()
                               {
                                   @Override
                                   public void operationComplete(BaseFuture baseFuture) throws Exception
                                   {
                                       if (sendFuture.isSuccess())
                                       {
                                           Platform.runLater(() -> onSendTradingMessageResult(listener));
                                       }
                                       else
                                       {
                                           Platform.runLater(() -> onSendTradingMessageFailed(listener));
                                       }
                                   }
                               }
        );
    }

    private void onSendTradingMessageResult(@NotNull TradeMessageListener listener)
    {
        listener.onResult();
    }

    private void onSendTradingMessageFailed(@NotNull TradeMessageListener listener)
    {
        listener.onFailed();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Process incoming tradingMessage
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void processTradingMessage(@NotNull TradeMessage tradeMessage, PeerAddress sender)
    {
        //TODO change to map (key: offerID) instead of list (offererPaymentProtocols, takerPaymentProtocols)
        log.info("processTradingMessage " + tradeMessage.getType());
        switch (tradeMessage.getType())
        {
            case REQUEST_TAKE_OFFER:
                // That is used to initiate the OffererPaymentProtocol and to show incoming requests in the view
                for (@NotNull TakeOfferRequestListener takeOfferRequestListener : takeOfferRequestListeners)
                    takeOfferRequestListener.onTakeOfferRequested(tradeMessage, sender);
                break;
            case ACCEPT_TAKE_OFFER_REQUEST:
                for (@NotNull TakerPaymentProtocol takeOfferTradeListener : takerPaymentProtocols)
                    takeOfferTradeListener.onTakeOfferRequestAccepted();
                break;
            case REJECT_TAKE_OFFER_REQUEST:
                for (@NotNull TakerPaymentProtocol takeOfferTradeListener : takerPaymentProtocols)
                    takeOfferTradeListener.onTakeOfferRequestRejected();
                break;
            case TAKE_OFFER_FEE_PAYED:
                for (@NotNull OffererPaymentProtocol offererPaymentProtocol : offererPaymentProtocols)
                    offererPaymentProtocol.onTakeOfferFeePayed(tradeMessage);
                break;
            case REQUEST_TAKER_DEPOSIT_PAYMENT:
                for (@NotNull TakerPaymentProtocol takeOfferTradeListener : takerPaymentProtocols)
                    takeOfferTradeListener.onTakerDepositPaymentRequested(tradeMessage);
                break;
            case REQUEST_OFFERER_DEPOSIT_PUBLICATION:
                for (@NotNull OffererPaymentProtocol offererPaymentProtocol : offererPaymentProtocols)
                    offererPaymentProtocol.onDepositTxReadyForPublication(tradeMessage);
                break;
            case DEPOSIT_TX_PUBLISHED:
                for (@NotNull TakerPaymentProtocol takeOfferTradeListener : takerPaymentProtocols)
                    takeOfferTradeListener.onDepositTxPublished(tradeMessage);
                break;
            case BANK_TX_INITED:
                for (@NotNull TakerPaymentProtocol takeOfferTradeListener : takerPaymentProtocols)
                    takeOfferTradeListener.onBankTransferInited(tradeMessage);
                break;
            case PAYOUT_TX_PUBLISHED:
                for (@NotNull OffererPaymentProtocol offererPaymentProtocol : offererPaymentProtocols)
                    offererPaymentProtocol.onPayoutTxPublished(tradeMessage);
                break;

            default:
                log.info("default");
                break;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Ping peer
    ///////////////////////////////////////////////////////////////////////////////////////////
    //TODO not working anymore...
    public void pingPeer(String publicKeyAsHex)
    {
        Number160 location = Number160.createHash(publicKeyAsHex);
        final FutureDHT getPeerAddressFuture = myPeer.get(location).start();
        getPeerAddressFuture.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture baseFuture) throws Exception
            {
                final Data data = getPeerAddressFuture.getData();
                if (data != null && data.getObject() instanceof PeerAddress)
                {
                    @NotNull final PeerAddress peerAddress = (PeerAddress) data.getObject();
                    Platform.runLater(() -> onAddressFoundPingPeer(peerAddress));
                }
            }
        });
    }


    private void onAddressFoundPingPeer(PeerAddress peerAddress)
    {
        try
        {
            final PeerConnection peerConnection = myPeer.createPeerConnection(peerAddress, 10);
            if (!peerConnection.isClosed())
            {
                FutureResponse sendFuture = myPeer.sendDirect(peerConnection).setObject(PING).start();
                sendFuture.addListener(new BaseFutureAdapter<BaseFuture>()
                                       {
                                           @Override
                                           public void operationComplete(BaseFuture baseFuture) throws Exception
                                           {
                                               if (sendFuture.isSuccess())
                                               {
                                                   @NotNull final String pong = (String) sendFuture.getObject();
                                                   Platform.runLater(() -> onResponseFromPing(pong.equals(PONG)));
                                               }
                                               else
                                               {
                                                   peerConnection.close();
                                                   Platform.runLater(() -> onResponseFromPing(false));
                                               }
                                           }
                                       }
                );
            }
        } catch (Exception e)
        {
            //  ClosedChannelException can happen, check out if there is a better way to ping a myPeerInstance for online status
        }
    }

    private void onResponseFromPing(boolean success)
    {
        for (@NotNull PingPeerListener pingPeerListener : pingPeerListeners)
            pingPeerListener.onPingPeerResult(success);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Misc
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Nullable
    public PublicKey getPubKey()
    {
        return keyPair.getPublic();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Event Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addMessageListener(OrderBookListener listener)
    {
        orderBookListeners.add(listener);
    }

    public void removeMessageListener(OrderBookListener listener)
    {
        orderBookListeners.remove(listener);
    }

    public void addTakeOfferRequestListener(TakeOfferRequestListener listener)
    {
        takeOfferRequestListeners.add(listener);
    }

    public void removeTakeOfferRequestListener(TakeOfferRequestListener listener)
    {
        takeOfferRequestListeners.remove(listener);
    }

    public void addTakerPaymentProtocol(TakerPaymentProtocol listener)
    {
        takerPaymentProtocols.add(listener);
    }

    public void removeTakerPaymentProtocol(TakerPaymentProtocol listener)
    {
        takerPaymentProtocols.remove(listener);
    }

    public void addOffererPaymentProtocol(OffererPaymentProtocol listener)
    {
        offererPaymentProtocols.add(listener);
    }

    public void removeOffererPaymentProtocol(OffererPaymentProtocol listener)
    {
        offererPaymentProtocols.remove(listener);
    }

    public void addPingPeerListener(PingPeerListener listener)
    {
        pingPeerListeners.add(listener);
    }

    public void removePingPeerListener(PingPeerListener listener)
    {
        pingPeerListeners.remove(listener);
    }

    public void addArbitratorListener(ArbitratorListener listener)
    {
        arbitratorListeners.add(listener);
    }

    public void removeArbitratorListener(ArbitratorListener listener)
    {
        arbitratorListeners.remove(listener);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createMyPeerInstance(int port) throws IOException
    {
        keyPair = DSAKeyUtil.getKeyPair();
        myPeer = new PeerMaker(keyPair).setPorts(port).makeAndListen();
        final FutureBootstrap futureBootstrap = myPeer.bootstrap().setBroadcast().setPorts(MASTER_PEER_PORT).start();
        // futureBootstrap.awaitUninterruptibly();
        futureBootstrap.addListener(new BaseFutureAdapter<BaseFuture>()
        {
            @Override
            public void operationComplete(BaseFuture future) throws Exception
            {
                if (futureBootstrap.getBootstrapTo() != null)
                {
                    PeerAddress masterPeerAddress = futureBootstrap.getBootstrapTo().iterator().next();
                    final FutureDiscover futureDiscover = myPeer.discover().setPeerAddress(masterPeerAddress).start();
                    //futureDiscover.awaitUninterruptibly();
                    futureDiscover.addListener(new BaseFutureListener<BaseFuture>()
                    {
                        @Override
                        public void operationComplete(BaseFuture future) throws Exception
                        {
                            //System.out.println("operationComplete");
                        }

                        @Override
                        public void exceptionCaught(Throwable t) throws Exception
                        {
                            System.err.println("exceptionCaught");
                        }
                    });
                }
            }
        });
    }

    private void setupStorage()
    {
        myPeer.getPeerBean().setStorage(new StorageDisk(FileUtil.getDirectory(BitSquare.ID + "_tomP2P").getAbsolutePath()));
    }

    private void saveMyAddressToDHT() throws IOException
    {
        Number160 location = Number160.createHash(DSAKeyUtil.getHexStringFromPublicKey(getPubKey()));
        //log.debug("saveMyAddressToDHT location "+location.toString());
        myPeer.put(location).setData(new Data(myPeer.getPeerAddress())).start();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupReplyHandler()
    {
        myPeer.setObjectDataReply((sender, request) -> {
            if (!sender.equals(myPeer.getPeerAddress()))
            {
                Platform.runLater(() -> onMessage(request, sender));
            }
            //noinspection ReturnOfNull
            return null;
        });

        //noinspection Convert2Lambda
        myPeer.setObjectDataReply(new ObjectDataReply()
        {
            @Nullable
            @Override
            public Object reply(PeerAddress peerAddress, Object o) throws Exception
            {
                return null;
            }
        });
    }

    private void onMessage(Object request, PeerAddress sender)
    {
        if (request instanceof TradeMessage)
        {
            processTradingMessage((TradeMessage) request, sender);
        }
       /* else
        {
            for (OrderBookListener orderBookListener : orderBookListeners)
                orderBookListener.onMessage(request);
        }  */
    }


}
