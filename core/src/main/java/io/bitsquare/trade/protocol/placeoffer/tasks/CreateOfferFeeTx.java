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

package io.bitsquare.trade.protocol.placeoffer.tasks;

import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.BitcoinWalletService;
import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferModel;
import io.bitsquare.trade.protocol.trade.ArbitrationSelectionRule;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class CreateOfferFeeTx extends Task<PlaceOfferModel> {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferFeeTx.class);

    public CreateOfferFeeTx(TaskRunner taskHandler, PlaceOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        Offer offer = model.offer;
        try {
            runInterceptHook();

            NodeAddress selectedArbitratorNodeAddress = ArbitrationSelectionRule.select(model.user.getAcceptedArbitratorAddresses(), model.offer);
            log.debug("selectedArbitratorAddress " + selectedArbitratorNodeAddress);
            Arbitrator selectedArbitrator = model.user.getAcceptedArbitratorByAddress(selectedArbitratorNodeAddress);
            checkNotNull(selectedArbitrator, "selectedArbitrator must not be null at CreateOfferFeeTx");
            BitcoinWalletService walletService = model.walletService;
            String id = offer.getId();
            Transaction transaction = model.tradeWalletService.createTradingFeeTx(
                    walletService.getOrCreateAddressEntry(id, AddressEntry.Context.OFFER_FUNDING).getAddress(),
                    walletService.getOrCreateAddressEntry(id, AddressEntry.Context.RESERVED_FOR_TRADE).getAddress(),
                    walletService.getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE).getAddress(),
                    model.reservedFundsForOffer,
                    model.useSavingsWallet,
                    offer.getCreateOfferFee(),
                    offer.getTxFee(),
                    selectedArbitrator.getBtcAddress());

            // We assume there will be no tx malleability. We add a check later in case the published offer has a different hash.
            // As the txId is part of the offer and therefore change the hash data we need to be sure to have no
            // tx malleability
            offer.setOfferFeePaymentTxID(transaction.getHashAsString());
            model.setTransaction(transaction);

            complete();
        } catch (Throwable t) {
            offer.setErrorMessage("An error occurred.\n" +
                    "Error message:\n"
                    + t.getMessage());
            failed(t);
        }
    }
}
