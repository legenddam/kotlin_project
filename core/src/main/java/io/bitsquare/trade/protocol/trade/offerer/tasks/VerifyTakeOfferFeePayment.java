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

import io.bitsquare.trade.protocol.trade.offerer.BuyerAsOffererModel;
import io.bitsquare.util.taskrunner.Task;
import io.bitsquare.util.taskrunner.TaskRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyTakeOfferFeePayment extends Task<BuyerAsOffererModel> {
    private static final Logger log = LoggerFactory.getLogger(VerifyTakeOfferFeePayment.class);

    public VerifyTakeOfferFeePayment(TaskRunner taskHandler, BuyerAsOffererModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        //TODO mocked yet, need a confidence listeners
        int numOfPeersSeenTx = model.getWalletService().getNumOfPeersSeenTx(model.getTakeOfferFeeTxId());
       /* if (numOfPeersSeenTx > 2) {
            resultHandler.handleResult();
        }*/
        
        complete();
    }
    @Override
    protected void rollBackOnFault() {
    }
}
