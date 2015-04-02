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

package io.bitsquare.trade.protocol.trade.buyer.taker.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.trade.BuyerAsTakerTrade;
import io.bitsquare.trade.SellerAsTakerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.TradeTask;
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;
import io.bitsquare.trade.states.TakerState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerSendsFiatTransferStartedMessage extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(TakerSendsFiatTransferStartedMessage.class);

    public TakerSendsFiatTransferStartedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void doRun() {
        try {
            FiatTransferStartedMessage tradeMessage = new FiatTransferStartedMessage(processModel.getId(),
                    processModel.getPayoutTxSignature(),
                    processModel.getPayoutAmount(),
                    processModel.tradingPeer.getPayoutAmount(),
                    processModel.getAddressEntry().getAddressString());

            processModel.getMessageService().sendMessage(trade.getTradingPeer(), tradeMessage,
                    processModel.getP2pSigPubKey(),
                    processModel.getP2pEncryptPubKey(),
                    new SendMessageListener() {
                        @Override
                        public void handleResult() {
                            log.trace("Sending FiatTransferStartedMessage succeeded.");

                            if (trade instanceof BuyerAsTakerTrade) {
                                trade.setProcessState(TakerState.ProcessState.FIAT_PAYMENT_STARTED);
                            }
                            else if (trade instanceof SellerAsTakerTrade) {
                                trade.setProcessState(TakerState.ProcessState.FIAT_PAYMENT_STARTED);
                            }

                            complete();
                        }

                        @Override
                        public void handleFault() {
                            appendToErrorMessage("Sending FiatTransferStartedMessage failed");
                            trade.setErrorMessage(errorMessage);

                            if (trade instanceof BuyerAsTakerTrade) {
                                ((BuyerAsTakerTrade) trade).setProcessState(TakerState.ProcessState.MESSAGE_SENDING_FAILED);
                            }
                            else if (trade instanceof SellerAsTakerTrade) {
                                trade.setProcessState(TakerState.ProcessState.MESSAGE_SENDING_FAILED);
                            }

                            failed();
                        }
                    });
        } catch (Throwable t) {
            t.printStackTrace();
            trade.setThrowable(t);
            failed(t);
        }
    }
}
