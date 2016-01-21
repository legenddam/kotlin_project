/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.protocol.trade.BuyerProtocol;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class BuyerTrade extends Trade implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(BuyerAsOffererTrade.class);

    BuyerTrade(Offer offer, Coin tradeAmount, NodeAddress tradingPeerNodeAddress, Storage<? extends TradableList> storage) {
        super(offer, tradeAmount, tradingPeerNodeAddress, storage);
    }

    BuyerTrade(Offer offer, Storage<? extends TradableList> storage) {
        super(offer, storage);
    }

    @Override
    protected void initStates() {
        if (state == null)
            state = State.PREPARATION;
    }

    public void onFiatPaymentStarted() {
        checkArgument(tradeProtocol instanceof BuyerProtocol, "tradeProtocol NOT instanceof BuyerProtocol");
        ((BuyerProtocol) tradeProtocol).onFiatPaymentStarted();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setState(State state) {
        super.setState(state);

        if (state == State.WITHDRAW_COMPLETED)
            tradeProtocol.completed();
    }

}
