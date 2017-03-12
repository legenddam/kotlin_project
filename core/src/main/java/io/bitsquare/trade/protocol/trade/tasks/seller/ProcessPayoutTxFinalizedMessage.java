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

import io.bitsquare.btc.wallet.BtcWalletService;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.messages.trade.protocol.trade.messages.PayoutTxFinalizedMessage;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.messages.util.Validator.checkTradeId;

public class ProcessPayoutTxFinalizedMessage extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(ProcessPayoutTxFinalizedMessage.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public ProcessPayoutTxFinalizedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("current trade state " + trade.getState());
            PayoutTxFinalizedMessage message = (PayoutTxFinalizedMessage) processModel.getTradeMessage();
            checkTradeId(processModel.getId(), message);
            checkNotNull(message);
            checkArgument(message.payoutTx != null);
            Transaction walletTx = processModel.getTradeWalletService().addTransactionToWallet(message.payoutTx);
            trade.setPayoutTx(walletTx);
            BtcWalletService.printTx("payoutTx received from peer", walletTx);


            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            removeMailboxMessageAfterProcessing();

            trade.setState(Trade.State.SELLER_RECEIVED_AND_COMMITTED_PAYOUT_TX);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}