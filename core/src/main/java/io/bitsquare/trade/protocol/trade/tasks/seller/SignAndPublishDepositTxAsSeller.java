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

package io.bitsquare.trade.protocol.trade.tasks.seller;

import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.common.crypto.Hash;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignAndPublishDepositTxAsSeller extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SignAndPublishDepositTxAsSeller.class);

    public SignAndPublishDepositTxAsSeller(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.info("\n\n------------------------------------------------------------\n"
                    + "Contract as json\n"
                    + trade.getContractAsJson()
                    + "\n------------------------------------------------------------\n");

            byte[] contractHash = Hash.getHash(trade.getContractAsJson());
            trade.setContractHash(contractHash);
            processModel.getTradeWalletService().takerSignsAndPublishesDepositTx(
                    true,
                    contractHash,
                    processModel.getPreparedDepositTx(),
                    processModel.tradingPeer.getRawInputs(),
                    processModel.getRawInputs(),
                    processModel.tradingPeer.getTradeWalletPubKey(),
                    processModel.getTradeWalletPubKey(),
                    processModel.getArbitratorPubKey(trade.getArbitratorNodeAddress()),
                    new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            log.trace("takerSignAndPublishTx succeeded " + transaction);

                            trade.setDepositTx(transaction);
                            //trade.setTakeOfferDate(new Date());
                            trade.setTakeOfferDateAsBlockHeight(processModel.getTradeWalletService().getBestChainHeight());
                            trade.setState(Trade.State.DEPOSIT_PUBLISHED);

                            complete();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            failed(t);
                        }
                    });
        } catch (Throwable t) {
            failed(t);
        }
    }
}
