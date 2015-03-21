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

package io.bitsquare.trade.protocol.trade.taker.tasks;

import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.taker.models.TakerAsSellerModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.*;

public class ProcessFiatTransferStartedMessage extends Task<TakerAsSellerModel> {
    private static final Logger log = LoggerFactory.getLogger(ProcessFiatTransferStartedMessage.class);

    public ProcessFiatTransferStartedMessage(TaskRunner taskHandler, TakerAsSellerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            checkTradeId(model.id, model.getTradeMessage());
            FiatTransferStartedMessage message = (FiatTransferStartedMessage) model.getTradeMessage();

            model.offerer.signature = checkNotNull(message.offererSignature);
            model.offerer.payoutAmount = positiveCoinOf(nonZeroCoinOf(message.offererPayoutAmount));
            model.taker.payoutAmount = positiveCoinOf(nonZeroCoinOf(message.takerPayoutAmount));
            model.offerer.payoutAddressString = nonEmptyStringOf(message.offererPayoutAddress);
            model.trade.setState(Trade.State.FIAT_PAYMENT_STARTED);

            complete();
        } catch (Throwable t) {
            model.trade.setState(Trade.State.FAULT);
            failed(t);
        }
    }
}