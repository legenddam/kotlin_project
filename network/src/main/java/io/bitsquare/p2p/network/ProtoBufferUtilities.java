package io.bitsquare.p2p.network;

import com.google.protobuf.ByteString;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.common.crypto.SealedAndSigned;
import io.bitsquare.common.wire.proto.Messages;
import io.bitsquare.messages.alert.Alert;
import io.bitsquare.messages.alert.PrivateNotification;
import io.bitsquare.messages.alert.PrivateNotificationMessage;
import io.bitsquare.messages.arbitration.*;
import io.bitsquare.messages.availability.AvailabilityResult;
import io.bitsquare.messages.availability.OfferAvailabilityResponse;
import io.bitsquare.messages.btc.data.RawTransactionInput;
import io.bitsquare.messages.dao.compensation.payload.CompensationRequestPayload;
import io.bitsquare.messages.filter.payload.Filter;
import io.bitsquare.messages.filter.payload.PaymentAccountFilter;
import io.bitsquare.messages.payment.payload.*;
import io.bitsquare.messages.provider.price.PriceFeedService;
import io.bitsquare.messages.trade.offer.payload.Offer;
import io.bitsquare.messages.trade.payload.Contract;
import io.bitsquare.messages.trade.protocol.trade.messages.*;
import io.bitsquare.messages.trade.statistics.payload.TradeStatistics;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.messaging.PrefixedSealedAndSignedMessage;
import io.bitsquare.p2p.network.messages.CloseConnectionMessage;
import io.bitsquare.p2p.peers.getdata.messages.GetDataResponse;
import io.bitsquare.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import io.bitsquare.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import io.bitsquare.p2p.peers.keepalive.messages.Ping;
import io.bitsquare.p2p.peers.keepalive.messages.Pong;
import io.bitsquare.p2p.peers.peerexchange.Peer;
import io.bitsquare.p2p.peers.peerexchange.messages.GetPeersRequest;
import io.bitsquare.p2p.peers.peerexchange.messages.GetPeersResponse;
import io.bitsquare.p2p.storage.messages.AddDataMessage;
import io.bitsquare.p2p.storage.messages.RefreshTTLMessage;
import io.bitsquare.p2p.storage.messages.RemoveDataMessage;
import io.bitsquare.p2p.storage.messages.RemoveMailboxDataMessage;
import io.bitsquare.p2p.storage.payload.MailboxStoragePayload;
import io.bitsquare.p2p.storage.payload.StoragePayload;
import io.bitsquare.p2p.storage.storageentry.ProtectedMailboxStorageEntry;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * If the Messages class is giving errors in IntelliJ, you should change the IntelliJ IDEA Platform Properties file,
 * idea.properties, to something bigger like 12500:
 * <p>
 * #---------------------------------------------------------------------
 * # Maximum file size (kilobytes) IDE should provide code assistance for.
 * # The larger file is the slower its editor works and higher overall system memory requirements are
 * # if code assistance is enabled. Remove this property or set to very large number if you need
 * # code assistance for any files available regardless their size.
 * #---------------------------------------------------------------------
 * idea.max.intellisense.filesize=2500
 */
@Slf4j
public class ProtoBufferUtilities {


    public static Optional<Message> fromProtoBuf(Messages.Envelope envelope) {
        log.info("Convert protobuffer envelope: {},{}", envelope.getMessageCase(), envelope.toString());
        Message result = null;
        switch (envelope.getMessageCase()) {
            case PING:
                result = getPing(envelope);
                break;
            case PONG:
                result = getPong(envelope);
                break;
            case REFRESH_TTL_MESSAGE:
                result = getRefreshTTLMessage(envelope);
                break;
            case CLOSE_CONNECTION_MESSAGE:
                result = getCloseConnectionMessage(envelope);
                break;
            case PRELIMINARY_GET_DATA_REQUEST:
                result = getPreliminaryGetDataRequest(envelope);
                break;
            case GET_UPDATED_DATA_REQUEST:
                result = getGetUpdatedDataRequest(envelope);
                break;
            case GET_PEERS_REQUEST:
                result = getGetPeersRequest(envelope);
                break;
            case GET_PEERS_RESPONSE:
                result = getGetPeersResponse(envelope);
                break;
            case GET_DATA_RESPONSE:
                // TODO protectedstorageentry is NULL
                result = getGetDataResponse(envelope);
                break;
            case PREFIXED_SEALED_AND_SIGNED_MESSAGE:
                result = getPrefixedSealedAndSignedMessage(envelope);
                break;
            case OFFER_AVAILABILITY_RESPONSE:
                result = getOfferAvailabilityResponse(envelope);
                break;
            case REMOVE_DATA_MESSAGE:
                result = getRemoveDataMessage(envelope);
                break;
            case ADD_DATA_MESSAGE:
                result = getAddDataMessage(envelope);
                break;
            case REMOVE_MAILBOX_DATA_MESSAGE:
                result = getRemoveMailBoxDataMessage(envelope.getRemoveMailboxDataMessage());
                break;
            case DEPOSIT_TX_PUBLISHED_MESSAGE:
                result = getDepositTxPublishedMessage(envelope.getDepositTxPublishedMessage());
                break;
            case FINALIZE_PAYOUT_TX_REQUEST:
                result = getFinalizePayoutTxRequest(envelope.getFinalizePayoutTxRequest());
                break;
            case DISPUTE_COMMUNICATION_MESSAGE:
                result = getDisputeCommunicationMessage(envelope.getDisputeCommunicationMessage());
                break;
            case OPEN_NEW_DISPUTE_MESSAGE:
                result = getOpenNewDisputeMessage(envelope.getOpenNewDisputeMessage());
                break;
            case PEER_OPENED_DISPUTE_MESSAGE:
                result = getPeerOpenedDisputeMessage(envelope.getPeerOpenedDisputeMessage());
                break;
            case DISPUTE_RESULT_MESSAGE:
                result = getDisputeResultMessage(envelope.getDisputeResultMessage());
                break;
            case PEER_PUBLISHED_PAYOUT_TX_MESSAGE:
                result = getPeerPublishedPayoutTxMessage(envelope.getPeerPublishedPayoutTxMessage());
                break;
            case PAY_DEPOSIT_REQUEST:
                result = getPayDepositRequest(envelope.getPayDepositRequest());
                break;
            case PUBLISH_DEPOSIT_TX_REQUEST:
                result = getPublishDepositTxRequest(envelope.getPublishDepositTxRequest());
                break;
            case FIAT_TRANSFER_STARTED_MESSAGE:
                result = getFiatTransferStartedMessage(envelope.getFiatTransferStartedMessage());
                break;
            case PAYOUT_TX_FINALIZED_MESSAGE:
                result = getPayoutTxFinalizedMessage(envelope.getPayoutTxFinalizedMessage());
                break;
            case PRIVATE_NOTIFICATION_MESSAGE:
                result = getPrivateNotificationMessage(envelope.getPrivateNotificationMessage());
                break;
            default:
                log.warn("Unknown message case:{}:{}", envelope.getMessageCase());
        }
        return Optional.ofNullable(result);
    }

    private static Message getPrivateNotificationMessage(Messages.PrivateNotificationMessage privateNotificationMessage) {
        return new PrivateNotificationMessage(getPrivateNotification(privateNotificationMessage.getPrivateNotification()),
                getNodeAddress(privateNotificationMessage.getMyNodeAddress()));
    }

    private static PrivateNotification getPrivateNotification(Messages.PrivateNotification privateNotification) {
        return new PrivateNotification(privateNotification.getMessage());
    }

    private static Message getPayoutTxFinalizedMessage(Messages.PayoutTxFinalizedMessage payoutTxFinalizedMessage) {
        return new PayoutTxFinalizedMessage(payoutTxFinalizedMessage.getTradeId(), payoutTxFinalizedMessage.getPayoutTx().toByteArray(),
                getNodeAddress(payoutTxFinalizedMessage.getSenderNodeAddress()));
    }

    private static Message getFiatTransferStartedMessage(Messages.FiatTransferStartedMessage fiatTransferStartedMessage) {
        return new FiatTransferStartedMessage(fiatTransferStartedMessage.getTradeId(), fiatTransferStartedMessage.getBuyerPayoutAddress(),
                getNodeAddress(fiatTransferStartedMessage.getSenderNodeAddress()));
    }

    private static Message getPublishDepositTxRequest(Messages.PublishDepositTxRequest publishDepositTxRequest) {
        List<RawTransactionInput> rawTransactionInputs = publishDepositTxRequest.getOffererInputsList().stream()
                .map(rawTransactionInput -> new RawTransactionInput(rawTransactionInput.getIndex(),
                        rawTransactionInput.getParentTransaction().toByteArray(), rawTransactionInput.getValue()))
                .collect(Collectors.toList());

        return new PublishDepositTxRequest(publishDepositTxRequest.getTradeId(),
                getPaymentAccountContractData(publishDepositTxRequest.getOffererPaymentAccountContractData()),
                publishDepositTxRequest.getOffererAccountId(),
                publishDepositTxRequest.getOffererMultiSigPubKey().toByteArray(),
                publishDepositTxRequest.getOffererContractAsJson(),
                publishDepositTxRequest.getOffererContractSignature(),
                publishDepositTxRequest.getOffererPayoutAddressstring(),
                publishDepositTxRequest.getPreparedDepositTx().toByteArray(), rawTransactionInputs);
    }

    private static Message getPayDepositRequest(Messages.PayDepositRequest payDepositRequest) {
        List<RawTransactionInput> rawTransactionInputs = payDepositRequest.getRawTransactionInputsList().stream()
                .map(rawTransactionInput -> new RawTransactionInput(rawTransactionInput.getIndex(),
                        rawTransactionInput.getParentTransaction().toByteArray(), rawTransactionInput.getValue()))
                .collect(Collectors.toList());
        List<NodeAddress> nodeAddresses = payDepositRequest.getAcceptedArbitratorNodeAddressesList().stream()
                .map(ProtoBufferUtilities::getNodeAddress).collect(Collectors.toList());
        return new PayDepositRequest(getNodeAddress(payDepositRequest.getSenderNodeAddress()),
                payDepositRequest.getTradeId(), payDepositRequest.getTradeAmount(), payDepositRequest.getTradePrice(),
                Coin.valueOf(payDepositRequest.getTxFee().getValue()), Coin.valueOf(payDepositRequest.getTakeOfferFee().getValue()),
                rawTransactionInputs, payDepositRequest.getChangeOutputValue(), payDepositRequest.getChangeOutputAddress(),
                payDepositRequest.getTakerMultiSigPubKey().toByteArray(), payDepositRequest.getTakerPayoutAddressString(),
                getPubKeyRing(payDepositRequest.getTakerPubKeyRing()), getPaymentAccountContractData(payDepositRequest.getTakerPaymentAccountContractData()),
                payDepositRequest.getTakerAccountId(), payDepositRequest.getTakeOfferFeeTxId(), nodeAddresses, getNodeAddress(payDepositRequest.getArbitratorNodeAddress()));
    }

    private static Message getPeerPublishedPayoutTxMessage(Messages.PeerPublishedPayoutTxMessage peerPublishedPayoutTxMessage) {
        return new PeerPublishedPayoutTxMessage(peerPublishedPayoutTxMessage.getTransaction().toByteArray(),
                peerPublishedPayoutTxMessage.getTradeId(), getNodeAddress(peerPublishedPayoutTxMessage.getMyNodeAddress()));
    }

    private static Message getDisputeResultMessage(Messages.DisputeResultMessage disputeResultMessage) {
        DisputeResult disputeResult = new DisputeResult(disputeResultMessage.getDisputeResult().getTradeId(),
                disputeResultMessage.getDisputeResult().getTraderId());
        return new DisputeResultMessage(disputeResult, getNodeAddress(disputeResultMessage.getMyNodeAddress()));
    }

    private static Message getPeerOpenedDisputeMessage(Messages.PeerOpenedDisputeMessage peerOpenedDisputeMessage) {
        return new PeerOpenedDisputeMessage(getDispute(peerOpenedDisputeMessage.getDispute()),
                getNodeAddress(peerOpenedDisputeMessage.getMyNodeAddress()), peerOpenedDisputeMessage.getUid());
    }

    private static Message getOpenNewDisputeMessage(Messages.OpenNewDisputeMessage openNewDisputeMessage) {
        return new OpenNewDisputeMessage(getDispute(openNewDisputeMessage.getDispute()),
                getNodeAddress(openNewDisputeMessage.getMyNodeAddress()), openNewDisputeMessage.getUid());
    }

    private static Dispute getDispute(Messages.Dispute dispute) {
        return new Dispute(dispute.getTradeId(), dispute.getTraderId(),
                dispute.getDisputeOpenerIsBuyer(), dispute.getDisputeOpenerIsOfferer(),
                getPubKeyRing(dispute.getTraderPubKeyRing()), new Date(dispute.getTradeDate()), getContract(dispute.getContract()),
                dispute.getContractHash().toByteArray(), dispute.getDepositTxSerialized().toByteArray(), dispute.getPayoutTxSerialized().toByteArray(),
                dispute.getDepositTxId(), dispute.getPayoutTxId(), dispute.getContractAsJson(), dispute.getOffererContractSignature(),
                dispute.getTakerContractSignature(), getPubKeyRing(dispute.getArbitratorPubKeyRing()), dispute.getIsSupportTicket());

    }

    private static Contract getContract(Messages.Contract contract) {
        return new Contract(getOffer(contract.getOffer()), Coin.valueOf(contract.getTradeAmount()), getFiat(contract.getOffer().getCurrencyCode(), contract.getTradePrice()),
                contract.getTakeOfferFeeTxId(), getNodeAddress(contract.getBuyerNodeAddress()), getNodeAddress(contract.getSellerNodeAddress()),
                getNodeAddress(contract.getArbitratorNodeAddress()), contract.getIsBuyerOffererAndSellerTaker(), contract.getOffererAccountId(),
                contract.getTakerAccountId(), getPaymentAccountContractData(contract.getOffererPaymentAccountContractData()),
                getPaymentAccountContractData(contract.getTakerPaymentAccountContractData()), getPubKeyRing(contract.getOffererPubKeyRing()),
                getPubKeyRing(contract.getTakerPubKeyRing()), contract.getOffererPayoutAddressstring(), contract.getTakerPayoutAddressstring(),
                contract.getOffererBtcPubKey().toByteArray(), contract.getTakerBtcPubKey().toByteArray());
    }

    private static PaymentAccountContractData getPaymentAccountContractData(Messages.PaymentAccountContractData protoEntry) {
        PaymentAccountContractData result = null;
        switch (protoEntry.getMessageCase()) {
            case ALI_PAY_ACCOUNT_CONTRACT_DATA:
                result = new AliPayAccountContractData(protoEntry.getPaymentMethodName(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getAliPayAccountContractData().getAccountNr());
                break;
            case CHASE_QUICK_PAY_ACCOUNT_CONTRACT_DATA:
                result = new ChaseQuickPayAccountContractData(protoEntry.getPaymentMethodName(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getChaseQuickPayAccountContractData().getEmail(),
                        protoEntry.getChaseQuickPayAccountContractData().getHolderName());
                break;
            case CLEAR_XCHANGE_ACCOUNT_CONTRACT_DATA:
                result = new ClearXchangeAccountContractData(protoEntry.getPaymentMethodName(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getClearXchangeAccountContractData().getHolderName(),
                        protoEntry.getClearXchangeAccountContractDataOrBuilder().getEmailOrMobileNr());
                break;
            case COUNTRY_BASED_PAYMENT_ACCOUNT_CONTRACT_DATA:
                switch (protoEntry.getCountryBasedPaymentAccountContractData().getMessageCase()) {
                    case BANK_ACCOUNT_CONTRACT_DATA:
                        switch (protoEntry.getCountryBasedPaymentAccountContractData().getBankAccountContractData().getMessageCase()) {
                            case NATIONAL_BANK_ACCOUNT_CONTRACT_DATA:
                                NationalBankAccountContractData nationalBankAccountContractData = new NationalBankAccountContractData(protoEntry.getPaymentMethodName(), protoEntry.getId(),
                                        protoEntry.getMaxTradePeriod());
                                fillInBankAccountContractData(protoEntry, nationalBankAccountContractData);
                                fillInCountryBasedPaymentAccountContractData(protoEntry, nationalBankAccountContractData);
                                result = nationalBankAccountContractData;
                                break;
                            case SAME_BANK_ACCONT_CONTRACT_DATA:
                                SameBankAccountContractData sameBankAccountContractData = new SameBankAccountContractData(protoEntry.getPaymentMethodName(), protoEntry.getId(),
                                        protoEntry.getMaxTradePeriod());
                                fillInBankAccountContractData(protoEntry, sameBankAccountContractData);
                                fillInCountryBasedPaymentAccountContractData(protoEntry, sameBankAccountContractData);
                                result = sameBankAccountContractData;
                                break;
                            case SPECIFIC_BANKS_ACCOUNT_CONTRACT_DATA:
                                SpecificBanksAccountContractData specificBanksAccountContractData = new SpecificBanksAccountContractData(protoEntry.getPaymentMethodName(), protoEntry.getId(),
                                        protoEntry.getMaxTradePeriod());
                                fillInBankAccountContractData(protoEntry, specificBanksAccountContractData);
                                fillInCountryBasedPaymentAccountContractData(protoEntry, specificBanksAccountContractData);
                                result = specificBanksAccountContractData;
                                break;
                        }
                        break;
                    case CASH_DEPOSIT_ACCOUNT_CONTRACT_DATA:
                        CashDepositAccountContractData cashDepositAccountContractData = new CashDepositAccountContractData(protoEntry.getPaymentMethodName(), protoEntry.getId(),
                                protoEntry.getMaxTradePeriod());
                        fillInCountryBasedPaymentAccountContractData(protoEntry, cashDepositAccountContractData);
                        result = cashDepositAccountContractData;
                        break;
                    case SEPA_ACCOUNT_CONTRACT_DATA:
                        SepaAccountContractData sepaAccountContractData = new SepaAccountContractData(protoEntry.getPaymentMethodName(), protoEntry.getId(),
                                protoEntry.getMaxTradePeriod());
                        fillInCountryBasedPaymentAccountContractData(protoEntry, sepaAccountContractData);
                        result = sepaAccountContractData;
                        break;
                }
                break;
            case CRYPTO_CURRENCY_ACCOUNT_CONTRACT_DATA:
                result = new CryptoCurrencyAccountContractData(protoEntry.getPaymentMethodName(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getCryptoCurrencyAccountContractData().getAddress());
                break;
            case FASTER_PAYMENTS_ACCOUNT_CONTRACT_DATA:
                result = new FasterPaymentsAccountContractData(protoEntry.getPaymentMethodName(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getFasterPaymentsAccountContractData().getSortCode(),
                        protoEntry.getFasterPaymentsAccountContractData().getAccountNr());
                break;
            case INTERAC_E_TRANSFER_ACCOUNT_CONTRACT_DATA:
                Messages.InteracETransferAccountContractData interacETransferAccountContractData =
                        protoEntry.getInteracETransferAccountContractData();
                result = new InteracETransferAccountContractData(protoEntry.getPaymentMethodName(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), interacETransferAccountContractData.getEmail(),
                        interacETransferAccountContractData.getHolderName(),
                        interacETransferAccountContractData.getQuestion(),
                        interacETransferAccountContractData.getAnswer());
                break;
            case O_K_PAY_ACCOUNT_CONTRACT_DATA:
                result = new OKPayAccountContractData(protoEntry.getPaymentMethodName(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getOKPayAccountContractData().getAccountNr());
                break;
            case PERFECT_MONEY_ACCOUNT_CONTRACT_DATA:
                result = new PerfectMoneyAccountContractData(protoEntry.getPaymentMethodName(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getPerfectMoneyAccountContractData().getAccountNr());
                break;
            case SWISH_ACCOUNT_CONTRACT_DATA:
                result = new SwishAccountContractData(protoEntry.getPaymentMethodName(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getSwishAccountContractData().getMobileNr(),
                        protoEntry.getSwishAccountContractData().getHolderName());
                break;
            case U_S_POSTAL_MONEY_ORDER_ACCOUNT_CONTRACT_DATA:
                result = new USPostalMoneyOrderAccountContractData(protoEntry.getPaymentMethodName(), protoEntry.getId(),
                        protoEntry.getMaxTradePeriod(), protoEntry.getUSPostalMoneyOrderAccountContractData().getPostalAddress(),
                        protoEntry.getUSPostalMoneyOrderAccountContractData().getHolderName());
                break;
            default:
                log.error("Unknown paymentaccountcontractdata:{}", protoEntry.getMessageCase());
        }
        return result;
    }

    private static void fillInBankAccountContractData(Messages.PaymentAccountContractData protoEntry, BankAccountContractData bankAccountContractData) {
        Messages.BankAccountContractData bankProto = protoEntry.getCountryBasedPaymentAccountContractData().getBankAccountContractData();
        bankAccountContractData.setHolderName(bankProto.getHolderName());
        bankAccountContractData.setBankName(bankProto.getBankName());
        bankAccountContractData.setBankId(bankProto.getBankId());
        bankAccountContractData.setBranchId(bankProto.getBranchId());
        bankAccountContractData.setAccountNr(bankProto.getAccountNr());
        bankAccountContractData.setAccountType(bankProto.getAccountType());
    }

    private static void fillInCountryBasedPaymentAccountContractData(Messages.PaymentAccountContractData protoEntry,
                                                      CountryBasedPaymentAccountContractData countryBasedPaymentAccountContractData) {
        countryBasedPaymentAccountContractData.setCountryCode(protoEntry.getCountryBasedPaymentAccountContractData().getCountryCode());
    }

    private static Fiat getFiat(String currencyCode, long tradePrice) {
        return Fiat.valueOf(currencyCode, tradePrice);
    }

    private static Offer getOffer(Messages.Offer offer) {
        List<NodeAddress> arbitratorNodeAddresses = offer.getArbitratorNodeAddressesList().stream().map(nodeAddress -> getNodeAddress(nodeAddress)).collect(Collectors.toList());
        PriceFeedService priceFeedService = null; // TODO refactor Offer, this should not be passed in the constructor, or we need to inject it in ProtoBufferUtilities
        return new Offer(offer.getId(), getNodeAddress(offer.getOffererNodeAddress()), getPubKeyRing(offer.getPubKeyRing()), getDirection(offer.getDirection()),
                offer.getFiatPrice(), offer.getMarketPriceMargin(), offer.getUseMarketBasedPrice(), offer.getAmount(), offer.getMinAmount(), offer.getCurrencyCode(), arbitratorNodeAddresses,
                offer.getPaymentMethodName(), offer.getOffererPaymentAccountId(), offer.getCountryCode(), offer.getAcceptedCountryCodesList(), offer.getBankId(), offer.getAcceptedCountryCodesList(), priceFeedService,
                offer.getVersionNr(), offer.getBlockHeightAtOfferCreation(), offer.getTxFee(), offer.getCreateOfferFee(), offer.getSecurityDeposit(), offer.getMaxTradeLimit(), offer.getMaxTradePeriod(), offer.getUseAutoClose(),
                offer.getUseReOpenAfterAutoClose(), offer.getLowerClosePrice(), offer.getUpperClosePrice(), offer.getIsPrivateOffer(), offer.getHashOfChallenge(), offer.getExtraDataMapMap());
    }

    private static Message getDisputeCommunicationMessage(Messages.DisputeCommunicationMessage disputeCommunicationMessage) {
        return new DisputeCommunicationMessage(disputeCommunicationMessage.getTradeId(), disputeCommunicationMessage.getTraderId(),
                disputeCommunicationMessage.getSenderIsTrader(), disputeCommunicationMessage.getMessage(),
                getNodeAddress(disputeCommunicationMessage.getMyNodeAddress()), disputeCommunicationMessage.getDate(),
                disputeCommunicationMessage.getArrived(), disputeCommunicationMessage.getStoredInMailbox());
    }

    private static Message getFinalizePayoutTxRequest(Messages.FinalizePayoutTxRequest finalizePayoutTxRequest) {
        return new FinalizePayoutTxRequest(finalizePayoutTxRequest.getTradeId(), finalizePayoutTxRequest.getSellerSignature().toByteArray(),
                finalizePayoutTxRequest.getSellerPayoutAddress(), finalizePayoutTxRequest.getLockTimeAsBlockHeight(),
                getNodeAddress(finalizePayoutTxRequest.getSenderNodeAddress()));
    }

    private static Message getDepositTxPublishedMessage(Messages.DepositTxPublishedMessage depositTxPublishedMessage) {
        return new DepositTxPublishedMessage(depositTxPublishedMessage.getTradeId(),
                depositTxPublishedMessage.getDepositTx().toByteArray(),
                getNodeAddress(depositTxPublishedMessage.getSenderNodeAddress()), depositTxPublishedMessage.getUid());
    }

    private static Message getRemoveMailBoxDataMessage(Messages.RemoveMailboxDataMessage msg) {
        return new RemoveMailboxDataMessage(getProtectedMailBoxStorageEntry(msg.getProtectedStorageEntry()));
    }

    private static Message getAddDataMessage(Messages.Envelope envelope) {
        return new AddDataMessage(getProtectedStorageEntry(envelope.getAddDataMessage().getProtectedStorageEntry()));
    }

    private static Message getRemoveDataMessage(Messages.Envelope envelope) {
        return new RemoveDataMessage(getProtectedStorageEntry(envelope.getRemoveDataMessage().getProtectedStorageEntry()));
    }

    private static ProtectedStorageEntry getProtectedStorageEntry(Messages.ProtectedStorageEntry protoEntry) {
        StoragePayload storagePayload = getStoragePayload(protoEntry.getStoragePayload());
        ProtectedStorageEntry storageEntry = new ProtectedStorageEntry(storagePayload,
                protoEntry.getOwnerPubKeyBytes().toByteArray(), protoEntry.getSequenceNumber(),
                protoEntry.getSignature().toByteArray());
        return storageEntry;
    }

    private static ProtectedMailboxStorageEntry getProtectedMailBoxStorageEntry(Messages.ProtectedMailboxStorageEntry protoEntry) {
        ProtectedStorageEntry entry = getProtectedStorageEntry(protoEntry.getEntry());

        if (!(entry.getStoragePayload() instanceof MailboxStoragePayload)) {
            log.error("Trying to extract MailboxStoragePayload from a ProtectedMailboxStorageEntry," +
                    " but it's the wrong type {}", entry.getStoragePayload().toString());
            return null;
        }

        ProtectedMailboxStorageEntry storageEntry = new ProtectedMailboxStorageEntry(
                (MailboxStoragePayload) entry.getStoragePayload(),
                entry.getStoragePayload().getOwnerPubKey(), entry.sequenceNumber,
                entry.signature, protoEntry.getReceiversPubKeyBytes().toByteArray());
        return storageEntry;
    }

    @Nullable
    private static StoragePayload getStoragePayload(Messages.StoragePayload protoEntry) {
        StoragePayload storagePayload = null;
        switch (protoEntry.getMessageCase()) {
            case ALERT:
                Messages.Alert protoAlert = protoEntry.getAlert();
                storagePayload = new Alert(protoAlert.getMessage(), protoAlert.getIsUpdateInfo(),
                        protoAlert.getVersion());
                break;
            case ARBITRATOR:
                Messages.Arbitrator arbitrator = protoEntry.getArbitrator();
                NodeAddress nodeAddress = new NodeAddress(arbitrator.getArbitratorNodeAddress().getHostName(),
                        arbitrator.getArbitratorNodeAddress().getPort());
                List<String> strings = arbitrator.getLanguageCodesList().stream().collect(Collectors.toList());
                Date date = new Date(arbitrator.getRegistrationDate());
                storagePayload = new Arbitrator(getNodeAddress(arbitrator.getArbitratorNodeAddress()),
                        arbitrator.getBtcPubKey().toByteArray(),
                        arbitrator.getBtcAddress(), getPubKeyRing(arbitrator.getPubKeyRing()), strings, date,
                        arbitrator.getRegistrationPubKey().toByteArray(), arbitrator.getRegistrationSignature());
                break;
            case FILTER:
                Messages.Filter filter = protoEntry.getFilter();
                List<PaymentAccountFilter> paymentAccountFilters = filter.getBannedPaymentAccountsList()
                        .stream().map(accountFilter -> getPaymentAccountFilter(accountFilter)).collect(Collectors.toList());
                storagePayload = new Filter(filter.getBannedOfferIdsList().stream().collect(Collectors.toList()),
                        filter.getBannedNodeAddressList().stream().collect(Collectors.toList()), paymentAccountFilters);
                break;
            case COMPENSATION_REQUEST_PAYLOAD:
                Messages.CompensationRequestPayload compensationRequestPayload = protoEntry.getCompensationRequestPayload();
                storagePayload = new CompensationRequestPayload(compensationRequestPayload.getUid(),
                        compensationRequestPayload.getName(), compensationRequestPayload.getTitle(),
                        compensationRequestPayload.getCategory(), compensationRequestPayload.getDescription(),
                        compensationRequestPayload.getLink(), new Date(compensationRequestPayload.getStartDate()),
                        new Date(compensationRequestPayload.getEndDate()),
                        Coin.valueOf(compensationRequestPayload.getRequestedBtc()), compensationRequestPayload.getBtcAddress(),
                        new NodeAddress(compensationRequestPayload.getNodeAddress()),
                        compensationRequestPayload.getP2PStorageSignaturePubKeyBytes().toByteArray());
                break;
            case TRADE_STATISTICS:
                Messages.TradeStatistics protoTrade = protoEntry.getTradeStatistics();
                storagePayload = new TradeStatistics(getDirection(protoTrade.getDirection()), protoTrade.getCurrency(), protoTrade.getPaymentMethod(),
                        protoTrade.getOfferDate(), protoTrade.getUseMarketBasedPrice(), protoTrade.getMarketPriceMargin(),
                        protoTrade.getOfferAmount(), protoTrade.getOfferMinAmount(), protoTrade.getOfferId(),
                        protoTrade.getTradePrice(), protoTrade.getTradeAmount(), protoTrade.getTradeDate(),
                        protoTrade.getDepositTxId(), new PubKeyRing(protoTrade.getPubKeyRing().getSignaturePubKeyBytes().toByteArray(),
                        protoTrade.getPubKeyRing().getEncryptionPubKeyBytes().toByteArray()));
                break;
            case MAILBOX_STORAGE_PAYLOAD:
                Messages.MailboxStoragePayload mbox = protoEntry.getMailboxStoragePayload();
                storagePayload = new MailboxStoragePayload(
                        getPrefixedSealedAndSignedMessage(mbox.getPrefixedSealedAndSignedMessage()),
                        mbox.getSenderPubKeyForAddOperationBytes().toByteArray(),
                        mbox.getReceiverPubKeyForRemoveOperationBytes().toByteArray());
                break;
            case OFFER:
                Messages.Offer offer = protoEntry.getOffer();
                List<NodeAddress> arbitratorNodeAddresses = offer.getArbitratorNodeAddressesList().stream().map(nodeAddress1 -> getNodeAddress(nodeAddress1)).collect(Collectors.toList());
                // TODO PriceFeedService not yet passed in due to not used in the real code.
                storagePayload = new Offer(offer.getId(), getNodeAddress(offer.getOffererNodeAddress()),
                        getPubKeyRing(offer.getPubKeyRing()), getDirection(offer.getDirection()), offer.getFiatPrice(),
                        offer.getMarketPriceMargin(), offer.getUseMarketBasedPrice(), offer.getAmount(), offer.getMinAmount(),
                        offer.getCurrencyCode(), arbitratorNodeAddresses, offer.getPaymentMethodName(), offer.getOffererPaymentAccountId(),
                        offer.getCountryCode(), offer.getAcceptedCountryCodesList(), offer.getBankId(), offer.getAcceptedBankIdsList(),
                        null, offer.getVersionNr(), offer.getBlockHeightAtOfferCreation(), offer.getTxFee(), offer.getCreateOfferFee(),
                        offer.getSecurityDeposit(), offer.getMaxTradeLimit(), offer.getMaxTradePeriod(), offer.getUseAutoClose(),
                        offer.getUseReOpenAfterAutoClose(), offer.getLowerClosePrice(), offer.getUpperClosePrice(), offer.getIsPrivateOffer(),
                        offer.getHashOfChallenge(), offer.getExtraDataMapMap());
                break;
            default:
                log.error("Unknown storagepayload:{}", protoEntry.getMessageCase());
        }
        return storagePayload;
    }

    // TODO UNIT TEST THIS !!!
    @NotNull
    private static Offer.Direction getDirection(Messages.Offer.Direction direction) {
        return Offer.Direction.valueOf(direction.name());
    }

    @NotNull
    private static PubKeyRing getPubKeyRing(Messages.PubKeyRing pubKeyRing) {
        return new PubKeyRing(pubKeyRing.getSignaturePubKeyBytes().toByteArray(),
                pubKeyRing.getEncryptionPubKeyBytes().toByteArray());
    }

    private static NodeAddress getNodeAddress(Messages.NodeAddress protoNode) {
        return new NodeAddress(protoNode.getHostName(), protoNode.getPort());
    }

    private static PaymentAccountFilter getPaymentAccountFilter(Messages.PaymentAccountFilter accountFilter) {
        return new PaymentAccountFilter(accountFilter.getPaymentMethodId(), accountFilter.getGetMethodName(),
                accountFilter.getValue());
    }

    private static Message getOfferAvailabilityResponse(Messages.Envelope envelope) {
        Messages.OfferAvailabilityResponse msg = envelope.getOfferAvailabilityResponse();
        return new OfferAvailabilityResponse(msg.getOfferId(),
                AvailabilityResult.valueOf(
                        Messages.AvailabilityResult.forNumber(msg.getAvailabilityResult().getNumber()).name()));
    }


    @NotNull
    private static Message getPrefixedSealedAndSignedMessage(Messages.Envelope envelope) {
        return getPrefixedSealedAndSignedMessage(envelope.getPrefixedSealedAndSignedMessage());
    }

    @NotNull
    private static PrefixedSealedAndSignedMessage getPrefixedSealedAndSignedMessage(Messages.PrefixedSealedAndSignedMessage msg) {
        NodeAddress nodeAddress;
        nodeAddress = new NodeAddress(msg.getNodeAddress().getHostName(), msg.getNodeAddress().getPort());
        SealedAndSigned sealedAndSigned = new SealedAndSigned(msg.getSealedAndSigned().getEncryptedSecretKey().toByteArray(),
                msg.getSealedAndSigned().getEncryptedPayloadWithHmac().toByteArray(),
                msg.getSealedAndSigned().getSignature().toByteArray(), msg.getSealedAndSigned().getSigPublicKeyBytes().toByteArray());
        return new PrefixedSealedAndSignedMessage(nodeAddress, sealedAndSigned, msg.getAddressPrefixHash().toByteArray());
    }

    @NotNull
    private static Message getGetDataResponse(Messages.Envelope envelope) {
        // TODO protectedstorageentry is NULL
        HashSet<ProtectedStorageEntry> set = new HashSet<ProtectedStorageEntry>(
                envelope.getGetDataResponse().getDataSetList()
                        .stream()
                        .map(protectedStorageEntry ->
                                new ProtectedStorageEntry(null, new byte[]{}, 0, null)).collect(Collectors.toList()));
        return new GetDataResponse(set, envelope.getGetDataResponse().getRequestNonce(), envelope.getGetDataResponse().getIsGetUpdatedDataResponse());
    }

    @NotNull
    private static Message getGetPeersResponse(Messages.Envelope envelope) {
        Message result;
        Messages.GetPeersResponse msg = envelope.getGetPeersResponse();
        HashSet<Peer> set = new HashSet<>(
                msg.getReportedPeersList()
                        .stream()
                        .map(peer ->
                                new Peer(new NodeAddress(peer.getNodeAddress().getHostName(),
                                        peer.getNodeAddress().getPort()))).collect(Collectors.toList()));
        result = new GetPeersResponse(msg.getRequestNonce(), set);
        return result;
    }

    @NotNull
    private static Message getGetPeersRequest(Messages.Envelope envelope) {
        NodeAddress nodeAddress;
        Message result;
        Messages.GetPeersRequest msg = envelope.getGetPeersRequest();
        nodeAddress = new NodeAddress(msg.getSenderNodeAddress().getHostName(), msg.getSenderNodeAddress().getPort());
        HashSet<Peer> set = new HashSet<>(
                msg.getReportedPeersList()
                        .stream()
                        .map(peer ->
                                new Peer(new NodeAddress(peer.getNodeAddress().getHostName(),
                                        peer.getNodeAddress().getPort()))).collect(Collectors.toList()));
        result = new GetPeersRequest(nodeAddress, msg.getNonce(), set);
        return result;
    }

    @NotNull
    private static Message getGetUpdatedDataRequest(Messages.Envelope envelope) {
        NodeAddress nodeAddress;
        Message result;
        Messages.GetUpdatedDataRequest msg = envelope.getGetUpdatedDataRequest();
        nodeAddress = new NodeAddress(msg.getSenderNodeAddress().getHostName(), msg.getSenderNodeAddress().getPort());
        Set<byte[]> updatedDataRequestSet = getByteSet(msg.getExcludedKeysList());
        result = new GetUpdatedDataRequest(nodeAddress, msg.getNonce(), updatedDataRequestSet);
        return result;
    }

    @NotNull
    private static Message getPreliminaryGetDataRequest(Messages.Envelope envelope) {
        Message result;
        result = new PreliminaryGetDataRequest(envelope.getPreliminaryGetDataRequest().getNonce(),
                getByteSet(envelope.getPreliminaryGetDataRequest().getExcludedKeysList()));
        return result;
    }

    @NotNull
    private static Message getCloseConnectionMessage(Messages.Envelope envelope) {
        Message result;
        result = new CloseConnectionMessage(envelope.getCloseConnectionMessage().getReason());
        return result;
    }

    @NotNull
    private static Message getRefreshTTLMessage(Messages.Envelope envelope) {
        Message result;
        Messages.RefreshTTLMessage msg = envelope.getRefreshTtlMessage();
        result = new RefreshTTLMessage(msg.getHashOfDataAndSeqNr().toByteArray(),
                msg.getSignature().toByteArray(), msg.getHashOfPayload().toByteArray(), msg.getSequenceNumber());
        return result;
    }

    @NotNull
    private static Message getPong(Messages.Envelope envelope) {
        Message result;
        result = new Pong(envelope.getPong().getRequestNonce());
        return result;
    }

    @NotNull
    private static Message getPing(Messages.Envelope envelope) {
        Message result;
        result = new Ping(envelope.getPing().getNonce(), envelope.getPing().getLastRoundTripTime());
        return result;
    }

    private static Set<byte[]> getByteSet(List<ByteString> byteStringList) {
        return new HashSet<>(
                byteStringList
                        .stream()
                        .map(ByteString::toByteArray).collect(Collectors.toList()));
    }
}
