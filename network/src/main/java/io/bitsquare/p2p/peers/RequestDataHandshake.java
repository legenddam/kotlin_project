package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.CloseConnectionReason;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.messages.data.GetDataRequest;
import io.bitsquare.p2p.peers.messages.data.GetDataResponse;
import io.bitsquare.p2p.peers.messages.data.GetUpdatedDataRequest;
import io.bitsquare.p2p.peers.messages.data.PreliminaryGetDataRequest;
import io.bitsquare.p2p.storage.P2PDataStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Random;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

public class RequestDataHandshake implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(RequestDataHandshake.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onComplete();

        void onFault(String errorMessage, @Nullable Connection connection);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final P2PDataStorage dataStorage;
    private final PeerManager peerManager;
    private final Listener listener;
    private Timer timeoutTimer;
    private final long nonce = new Random().nextLong();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public RequestDataHandshake(NetworkNode networkNode, P2PDataStorage dataStorage, PeerManager peerManager,
                                Listener listener) {
        this.networkNode = networkNode;
        this.dataStorage = dataStorage;
        this.peerManager = peerManager;
        this.listener = listener;

        networkNode.addMessageListener(this);
    }

    public void shutDown() {
        Log.traceCall();
        networkNode.removeMessageListener(this);
        stopTimeoutTimer();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestData(NodeAddress nodeAddress) {
        Log.traceCall("nodeAddress=" + nodeAddress);
        GetDataRequest getDataRequest;
        if (networkNode.getNodeAddress() == null)
            getDataRequest = new PreliminaryGetDataRequest(nonce);
        else
            getDataRequest = new GetUpdatedDataRequest(networkNode.getNodeAddress(), nonce);

        log.info("We send a {} to peer {}. ", getDataRequest.getClass().getSimpleName(), nodeAddress);

        SettableFuture<Connection> future = networkNode.sendMessage(nodeAddress, getDataRequest);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                log.trace("Send " + getDataRequest + " to " + nodeAddress + " succeeded.");
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                String errorMessage = "Sending getDataRequest to " + nodeAddress +
                        " failed. That is expected if the peer is offline.\n\t" +
                        "getDataRequest=" + getDataRequest + "." +
                        "\n\tException=" + throwable.getMessage();
                log.info(errorMessage);
                peerManager.shutDownConnection(nodeAddress, CloseConnectionReason.SEND_MSG_FAILURE);
                shutDown();
                listener.onFault(errorMessage, null);
            }
        });

        checkArgument(timeoutTimer == null, "requestData must not be called twice.");
        timeoutTimer = UserThread.runAfter(() -> {
                    String errorMessage = "A timeout occurred at sending getDataRequest:" + getDataRequest +
                            " on nodeAddress:" + nodeAddress;
                    log.info(errorMessage + " / RequestDataHandshake=" +
                            RequestDataHandshake.this);
                    peerManager.shutDownConnection(nodeAddress, CloseConnectionReason.SEND_MSG_TIMEOUT);
                    shutDown();
                    listener.onFault(errorMessage, null);
                },
                10, TimeUnit.SECONDS);
    }

    public void onDataRequest(Message message, final Connection connection) {
        Log.traceCall(message.toString() + "\n\tconnection=" + connection);

        GetDataResponse getDataResponse = new GetDataResponse(new HashSet<>(dataStorage.getMap().values()),
                ((GetDataRequest) message).getNonce());
        SettableFuture<Connection> future = networkNode.sendMessage(connection, getDataResponse);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                log.trace("Send DataResponse to {} succeeded. getDataResponse={}",
                        connection.getPeersNodeAddressOptional(), getDataResponse);
                shutDown();
                listener.onComplete();
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                String errorMessage = "Sending getDataRequest to " + connection +
                        " failed. That is expected if the peer is offline. getDataResponse=" + getDataResponse + "." +
                        "Exception: " + throwable.getMessage();
                log.info(errorMessage);

                peerManager.shutDownConnection(connection, CloseConnectionReason.SEND_MSG_FAILURE);
                shutDown();
                listener.onFault(errorMessage, connection);
            }
        });

        checkArgument(timeoutTimer == null, "requestData must not be called twice.");
        timeoutTimer = UserThread.runAfter(() -> {
                    String errorMessage = "A timeout occurred for getDataResponse:" + getDataResponse +
                            " on connection:" + connection;
                    log.info(errorMessage + " / RequestDataHandshake=" +
                            RequestDataHandshake.this);
                    peerManager.shutDownConnection(connection, CloseConnectionReason.SEND_MSG_TIMEOUT);
                    shutDown();
                    listener.onFault(errorMessage, connection);
                },
                10, TimeUnit.SECONDS);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof GetDataResponse) {
            Log.traceCall(message.toString() + "\n\tconnection=" + connection);
            GetDataResponse getDataResponse = (GetDataResponse) message;
            if (getDataResponse.requestNonce == nonce) {
                stopTimeoutTimer();
                checkArgument(connection.getPeersNodeAddressOptional().isPresent(),
                        "RequestDataHandshake.onMessage: connection.getPeersNodeAddressOptional() must be present " +
                                "at that moment");
                ((GetDataResponse) message).dataSet.stream()
                        .forEach(protectedData -> dataStorage.add(protectedData,
                                connection.getPeersNodeAddressOptional().get()));

                shutDown();
                listener.onComplete();
            } else {
                log.debug("Nonce not matching. That can happen rarely if we get a response after a canceled " +
                                "handshake (timeout causes connection close but peer might have sent a msg before " +
                                "connection was closed).\n\t" +
                                "We drop that message. nonce={} / requestNonce={}",
                        nonce, getDataResponse.requestNonce);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }
}
