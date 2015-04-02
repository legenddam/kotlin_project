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

package io.bitsquare.trade.protocol.trade.seller.taker.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.TakerAsBuyerTrade;
import io.bitsquare.trade.TakerAsSellerTrade;
import io.bitsquare.trade.TakerState;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerTradeTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.*;

public class TakerProcessFiatTransferStartedMessage extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(TakerProcessFiatTransferStartedMessage.class);

    public TakerProcessFiatTransferStartedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void doRun() {
        try {
            FiatTransferStartedMessage message = (FiatTransferStartedMessage) processModel.getTradeMessage();
            checkTradeId(processModel.getId(), message);
            checkNotNull(message);

            processModel.tradingPeer.setSignature(checkNotNull(message.buyerSignature));
            processModel.tradingPeer.setPayoutAmount(positiveCoinOf(nonZeroCoinOf(message.buyerPayoutAmount)));
            processModel.setPayoutAmount(positiveCoinOf(nonZeroCoinOf(message.sellerPayoutAmount)));
            processModel.tradingPeer.setPayoutAddressString(nonEmptyStringOf(message.buyerPayoutAddress));

            if (trade instanceof TakerAsBuyerTrade)
                trade.setProcessState(TakerState.ProcessState.FIAT_PAYMENT_STARTED);
            else if (trade instanceof TakerAsSellerTrade)
                trade.setProcessState(TakerState.ProcessState.FIAT_PAYMENT_STARTED);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            trade.setThrowable(t);
            failed(t);
        }
    }
}