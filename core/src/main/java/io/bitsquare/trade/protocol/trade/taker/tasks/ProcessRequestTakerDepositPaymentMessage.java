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

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.TakerTrade;
import io.bitsquare.trade.protocol.trade.messages.RequestTakerDepositPaymentMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;
import static io.bitsquare.util.Validator.*;

public class ProcessRequestTakerDepositPaymentMessage extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(ProcessRequestTakerDepositPaymentMessage.class);

    public ProcessRequestTakerDepositPaymentMessage(TaskRunner taskHandler, TakerTrade model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            RequestTakerDepositPaymentMessage message = (RequestTakerDepositPaymentMessage) takerTradeProcessModel.getTradeMessage();
            checkTradeId(takerTradeProcessModel.getId(), message);
            checkNotNull(message);

            takerTradeProcessModel.offerer.setConnectedOutputsForAllInputs(checkNotNull(message.offererConnectedOutputsForAllInputs));
            checkArgument(message.offererConnectedOutputsForAllInputs.size() > 0);
            takerTradeProcessModel.offerer.setOutputs(checkNotNull(message.offererOutputs));
            takerTradeProcessModel.offerer.setTradeWalletPubKey(checkNotNull(message.offererTradeWalletPubKey));
            takerTradeProcessModel.offerer.setP2pSigPublicKey(checkNotNull(message.offererP2PSigPublicKey));
            takerTradeProcessModel.offerer.setP2pEncryptPubKey(checkNotNull(message.offererP2PEncryptPublicKey));
            takerTradeProcessModel.offerer.setFiatAccount(checkNotNull(message.offererFiatAccount));
            takerTradeProcessModel.offerer.setAccountId(nonEmptyStringOf(message.offererAccountId));

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            takerTrade.setThrowable(t);
            failed(t);
        }
    }
}