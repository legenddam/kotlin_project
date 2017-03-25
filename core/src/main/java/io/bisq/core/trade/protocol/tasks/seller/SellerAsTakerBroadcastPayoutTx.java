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

package io.bisq.core.trade.protocol.tasks.seller;

import com.google.common.util.concurrent.FutureCallback;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class SellerAsTakerBroadcastPayoutTx extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SellerAsTakerBroadcastPayoutTx.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public SellerAsTakerBroadcastPayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            Transaction payoutTx = trade.getPayoutTx();
            checkNotNull(payoutTx, "payoutTx must not be null");

            TransactionConfidence.ConfidenceType confidenceType = payoutTx.getConfidence().getConfidenceType();
            log.debug("payoutTx confidenceType:" + confidenceType);
            if (confidenceType.equals(TransactionConfidence.ConfidenceType.BUILDING) ||
                    confidenceType.equals(TransactionConfidence.ConfidenceType.PENDING)) {
                log.debug("payoutTx was already published. confidenceType:" + confidenceType);
                trade.setState(Trade.State.PAYOUT_BROAD_CASTED);
                complete();
            } else {
                processModel.getTradeWalletService().broadcastTx(payoutTx, new FutureCallback<Transaction>() {
                    @Override
                    public void onSuccess(Transaction transaction) {
                        log.debug("BroadcastTx succeeded. Transaction:" + transaction);
                        trade.setState(Trade.State.PAYOUT_BROAD_CASTED);
                        complete();
                    }

                    @Override
                    public void onFailure(@NotNull Throwable t) {
                        log.error("BroadcastTx failed. Error:" + t.getMessage());
                        failed(t);
                    }
                });
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
