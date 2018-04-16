/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.portfolio.editoffer;


import bisq.desktop.main.offer.EditableOfferDataModel;
import bisq.desktop.util.BSFormatter;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.filter.FilterManager;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;

import com.google.inject.Inject;

public class EditOpenOfferDataModel extends EditableOfferDataModel {

    @Inject
    EditOpenOfferDataModel(OpenOfferManager openOfferManager, BtcWalletService btcWalletService, BsqWalletService bsqWalletService, Preferences preferences, User user, KeyRing keyRing, P2PService p2PService, PriceFeedService priceFeedService, FilterManager filterManager, AccountAgeWitnessService accountAgeWitnessService, TradeWalletService tradeWalletService, FeeService feeService, BSFormatter formatter) {
        super(openOfferManager, btcWalletService, bsqWalletService, preferences, user, keyRing, p2PService, priceFeedService, filterManager, accountAgeWitnessService, tradeWalletService, feeService, formatter);
    }
}
