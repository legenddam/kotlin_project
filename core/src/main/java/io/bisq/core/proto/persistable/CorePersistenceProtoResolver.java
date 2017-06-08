package io.bisq.core.proto.persistable;

import com.google.inject.Provider;
import io.bisq.common.proto.ProtobufferException;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.common.proto.persistable.NavigationPath;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.core.arbitration.DisputeList;
import io.bisq.core.btc.AddressEntryList;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.dao.blockchain.parse.BsqChainState;
import io.bisq.core.dao.compensation.CompensationRequestPayload;
import io.bisq.core.dao.vote.VoteItemsList;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.proto.CoreProtoResolver;
import io.bisq.core.trade.*;
import io.bisq.core.trade.statistics.TradeStatisticsList;
import io.bisq.core.user.PreferencesPayload;
import io.bisq.core.user.UserPayload;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.peers.peerexchange.PeerList;
import io.bisq.network.p2p.storage.PersistedEntryMap;
import io.bisq.network.p2p.storage.SequenceNumberMap;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;

@Slf4j
public class CorePersistenceProtoResolver extends CoreProtoResolver implements PersistenceProtoResolver {
    private final Provider<BtcWalletService> btcWalletService;
    private final NetworkProtoResolver networkProtoResolver;
    private final File storageDir;

    @Inject
    public CorePersistenceProtoResolver(Provider<BtcWalletService> btcWalletService,
                                        NetworkProtoResolver networkProtoResolver,
                                        @Named(Storage.STORAGE_DIR) File storageDir) {
        this.btcWalletService = btcWalletService;
        this.networkProtoResolver = networkProtoResolver;
        this.storageDir = storageDir;

    }

    @Override
    public PersistableEnvelope fromProto(PB.PersistableEnvelope proto) {
        if (proto != null) {
            switch (proto.getMessageCase()) {
                case SEQUENCE_NUMBER_MAP:
                    return SequenceNumberMap.fromProto(proto.getSequenceNumberMap());
                case PERSISTED_ENTRY_MAP:
                    return PersistedEntryMap.fromProto(proto.getPersistedEntryMap().getPersistedEntryMapMap(),
                            networkProtoResolver);
                case PEER_LIST:
                    return PeerList.fromProto(proto.getPeerList());
                case ADDRESS_ENTRY_LIST:
                    return AddressEntryList.fromProto(proto.getAddressEntryList());
                case TRADABLE_LIST:
                    return TradableList.fromProto(proto.getTradableList(),
                            this,
                            new Storage<>(storageDir, this),
                            btcWalletService.get());
                case TRADE_STATISTICS_LIST:
                    return TradeStatisticsList.fromProto(proto.getTradeStatisticsList());
                case DISPUTE_LIST:
                    return DisputeList.fromProto(proto.getDisputeList(),
                            this,
                            new Storage<>(storageDir, this));
                case PREFERENCES_PAYLOAD:
                    return PreferencesPayload.fromProto(proto.getPreferencesPayload(), this);
                case USER_PAYLOAD:
                    return UserPayload.fromProto(proto.getUserPayload(), this);
                case NAVIGATION_PATH:
                    return NavigationPath.fromProto(proto.getNavigationPath());
                case COMPENSATION_REQUEST_PAYLOAD:
                    return CompensationRequestPayload.fromProto(proto.getCompensationRequestPayload());
                case VOTE_ITEMS_LIST:
                    return VoteItemsList.fromProto(proto.getVoteItemsList());
                case BSQ_CHAIN_STATE:
                    return BsqChainState.fromProto(proto.getBsqChainState());
                default:
                    throw new ProtobufferException("Unknown proto message case(PB.PersistableEnvelope). messageCase=" + proto.getMessageCase());
            }
        } else {
            log.error("PersistableEnvelope.fromProto: PB.PersistableEnvelope is null");
            throw new ProtobufferException("PB.PersistableEnvelope is null");
        }
    }

    public Tradable fromProto(PB.Tradable proto, Storage<TradableList<SellerAsMakerTrade>> storage) {
        if (proto != null) {
            switch (proto.getMessageCase()) {
                case OPEN_OFFER:
                    return OpenOffer.fromProto(proto.getOpenOffer());
                case BUYER_AS_MAKER_TRADE:
                    return BuyerAsMakerTrade.fromProto(proto.getBuyerAsMakerTrade(), storage, btcWalletService.get(), this);
                case BUYER_AS_TAKER_TRADE:
                    return BuyerAsTakerTrade.fromProto(proto.getBuyerAsTakerTrade(), storage, btcWalletService.get(), this);
                case SELLER_AS_MAKER_TRADE:
                    return SellerAsMakerTrade.fromProto(proto.getSellerAsMakerTrade(), storage, btcWalletService.get(), this);
                case SELLER_AS_TAKER_TRADE:
                    return SellerAsTakerTrade.fromProto(proto.getSellerAsTakerTrade(), storage, btcWalletService.get(), this);
                default:
                    throw new ProtobufferException("Unknown proto message case(PB.Tradable). messageCase=" + proto.getMessageCase());
            }
        } else {
            log.error("PersistableEnvelope.fromProto: PB.Tradable is null");
            throw new ProtobufferException("PB.Tradable is null");
        }
    }
}
