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

package io.bitsquare.trade.protocol.trade.tasks.taker;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.wallet.BtcWalletService;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.messages.trade.protocol.trade.messages.PayDepositRequest;
import io.bitsquare.p2p.messaging.SendMailboxMessageListener;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class SendPayDepositRequest extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SendPayDepositRequest.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public SendPayDepositRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            checkNotNull(trade.getTradeAmount(), "TradeAmount must not be null");
            checkNotNull(trade.getTakeOfferFeeTxId(), "TakeOfferFeeTxId must not be null");

            BtcWalletService walletService = processModel.getWalletService();
            String id = processModel.getOffer().getId();
            AddressEntry takerPayoutAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT);
            checkArgument(!walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG).isPresent(), "addressEntry must not be set here.");
            AddressEntry addressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            byte[] takerMultiSigPubKey = addressEntry.getPubKey();
            String takerPayoutAddressString = takerPayoutAddressEntry.getAddressString();
            PayDepositRequest payDepositRequest = new PayDepositRequest(
                    processModel.getMyNodeAddress(),
                    processModel.getId(),
                    trade.getTradeAmount().value,
                    trade.getTradePrice().value,
                    trade.getTxFee(),
                    trade.getTakeOfferFee(),
                    processModel.getRawTransactionInputs(),
                    processModel.getChangeOutputValue(),
                    processModel.getChangeOutputAddress(),
                    takerMultiSigPubKey,
                    takerPayoutAddressString,
                    processModel.getPubKeyRing(),
                    processModel.getPaymentAccountContractData(trade),
                    processModel.getAccountId(),
                    trade.getTakeOfferFeeTxId(),
                    new ArrayList<>(processModel.getUser().getAcceptedArbitratorAddresses()),
                    trade.getArbitratorNodeAddress()
            );
            processModel.setMyMultiSigPubKey(takerMultiSigPubKey);

            processModel.getP2PService().sendEncryptedMailboxMessage(
                    trade.getTradingPeerNodeAddress(),
                    processModel.tradingPeer.getPubKeyRing(),
                    payDepositRequest,
                    new SendMailboxMessageListener() {
                        @Override
                        public void onArrived() {
                            log.trace("Message arrived at peer.");
                            complete();
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.trace("Message stored in mailbox.");
                            complete();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            appendToErrorMessage("PayDepositRequest sending failed");
                            failed();
                        }
                    }
            );
        } catch (Throwable t) {
            failed(t);
        }
    }
}
