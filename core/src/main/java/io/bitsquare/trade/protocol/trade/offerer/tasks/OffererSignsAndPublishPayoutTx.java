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

package io.bitsquare.trade.protocol.trade.offerer.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.OffererAsBuyerTrade;
import io.bitsquare.trade.OffererAsSellerTrade;
import io.bitsquare.trade.OffererTrade;

import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererSignsAndPublishPayoutTx extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererSignsAndPublishPayoutTx.class);

    public OffererSignsAndPublishPayoutTx(TaskRunner taskHandler, OffererTrade offererTrade) {
        super(taskHandler, offererTrade);
    }

    @Override
    protected void doRun() {
        try {
            offererTradeProcessModel.getTradeWalletService().signAndPublishPayoutTx(
                    offererTrade.getDepositTx(),
                    offererTradeProcessModel.taker.getSignature(),
                    offererTradeProcessModel.taker.getPayoutAmount(),
                    offererTradeProcessModel.offerer.getPayoutAmount(),
                    offererTradeProcessModel.taker.getPayoutAddressString(),
                    offererTradeProcessModel.offerer.getAddressEntry(),
                    offererTradeProcessModel.taker.getTradeWalletPubKey(),
                    offererTradeProcessModel.offerer.getTradeWalletPubKey(),
                    offererTradeProcessModel.getArbitratorPubKey(),
                    new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            offererTradeProcessModel.setPayoutTx(transaction);

                            if (offererTrade instanceof OffererAsBuyerTrade)
                                offererTrade.setProcessState(OffererAsBuyerTrade.ProcessState.PAYOUT_PUBLISHED);
                            else if (offererTrade instanceof OffererAsSellerTrade)
                                offererTrade.setProcessState(OffererAsSellerTrade.ProcessState.PAYOUT_PUBLISHED);

                            complete();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            t.printStackTrace();
                            offererTrade.setThrowable(t);
                            failed(t);
                        }
                    });
        } catch (Throwable t) {
            t.printStackTrace();
            offererTrade.setThrowable(t);
            failed(t);
        }
    }
}
