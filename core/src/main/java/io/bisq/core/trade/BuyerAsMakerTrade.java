/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade;

import io.bisq.common.app.Version;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.Offer;
import io.bisq.core.trade.messages.TradeMessage;
import io.bisq.core.trade.protocol.BuyerAsMakerProtocol;
import io.bisq.core.trade.protocol.MakerProtocol;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;

@Slf4j
public final class BuyerAsMakerTrade extends BuyerTrade implements MakerTrade {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsMakerTrade(Offer offer,
                             Coin txFee,
                             Coin takeOfferFee,
                             boolean isCurrencyForTakerFeeBtc,
                             Storage<? extends TradableList> storage,
                             BtcWalletService btcWalletService) {
        super(offer, txFee, takeOfferFee, isCurrencyForTakerFeeBtc,
                storage, btcWalletService);
    }

    @Override
    protected void createTradeProtocol() {
        tradeProtocol = new BuyerAsMakerProtocol(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleTakeOfferRequest(TradeMessage message, NodeAddress taker) {
        ((MakerProtocol) tradeProtocol).handleTakeOfferRequest(message, taker);
    }

    @Override
    public PB.Tradable toProtoMessage() {
        return PB.Tradable.newBuilder()
                .setBuyerAsMakerTrade(PB.BuyerAsMakerTrade.newBuilder().setTrade((PB.Trade) super.toProtoMessage())).build();
    }

    public static Tradable fromProto(PB.BuyerAsMakerTrade proto, Storage<? extends TradableList> storage,
                                     BtcWalletService btcWalletService) {
        return new BuyerAsMakerTrade(Offer.fromProto(proto.getTrade().getOffer()),
                Coin.valueOf(proto.getTrade().getTxFeeAsLong()),
                Coin.valueOf(proto.getTrade().getTakerFeeAsLong()),
                proto.getTrade().getIsCurrencyForTakerFeeBtc(), storage, btcWalletService);
    }
}
