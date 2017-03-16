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

package io.bisq.trade.protocol.trade.tasks.offerer;

import com.google.common.base.Preconditions;
import io.bisq.btc.AddressEntry;
import io.bisq.btc.wallet.BtcWalletService;
import io.bisq.common.crypto.Sig;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.common.util.Utilities;
import io.bisq.messages.payment.payload.PaymentAccountContractData;
import io.bisq.messages.trade.payload.Contract;
import io.bisq.messages.NodeAddress;
import io.bisq.trade.BuyerAsOffererTrade;
import io.bisq.trade.Trade;
import io.bisq.trade.protocol.trade.TradingPeer;
import io.bisq.trade.protocol.trade.tasks.TradeTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class CreateAndSignContract extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(CreateAndSignContract.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public CreateAndSignContract(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            Preconditions.checkNotNull(trade.getTakeOfferFeeTxId(), "trade.getTakeOfferFeeTxId() must not be null");

            TradingPeer taker = processModel.tradingPeer;
            PaymentAccountContractData offererPaymentAccountContractData = processModel.getPaymentAccountContractData(trade);
            checkNotNull(offererPaymentAccountContractData, "offererPaymentAccountContractData must not be null");
            PaymentAccountContractData takerPaymentAccountContractData = taker.getPaymentAccountContractData();
            boolean isBuyerOffererAndSellerTaker = trade instanceof BuyerAsOffererTrade;

            NodeAddress buyerNodeAddress = isBuyerOffererAndSellerTaker ? processModel.getMyNodeAddress() : processModel.getTempTradingPeerNodeAddress();
            NodeAddress sellerNodeAddress = isBuyerOffererAndSellerTaker ? processModel.getTempTradingPeerNodeAddress() : processModel.getMyNodeAddress();
            BtcWalletService walletService = processModel.getWalletService();
            String id = processModel.getOffer().getId();
            AddressEntry takerAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT);
            checkArgument(!walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG).isPresent(), "addressEntry must not be set here.");
            AddressEntry offererAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            byte[] offererMultiSigPubKey = offererAddressEntry.getPubKey();
            Contract contract = new Contract(
                    processModel.getOffer().getOfferPayload(),
                    trade.getTradeAmount(),
                    trade.getTradePrice(),
                    trade.getTakeOfferFeeTxId(),
                    buyerNodeAddress,
                    sellerNodeAddress,
                    trade.getArbitratorNodeAddress(),
                    isBuyerOffererAndSellerTaker,
                    processModel.getAccountId(),
                    taker.getAccountId(),
                    offererPaymentAccountContractData,
                    takerPaymentAccountContractData,
                    processModel.getPubKeyRing(),
                    taker.getPubKeyRing(),
                    takerAddressEntry.getAddressString(),
                    taker.getPayoutAddressString(),
                    offererMultiSigPubKey,
                    taker.getMultiSigPubKey()
            );
            String contractAsJson = Utilities.objectToJson(contract);
            String signature = Sig.sign(processModel.getKeyRing().getSignatureKeyPair().getPrivate(), contractAsJson);

            trade.setContract(contract);
            trade.setContractAsJson(contractAsJson);
            trade.setOffererContractSignature(signature);
            processModel.setMyMultiSigPubKey(offererMultiSigPubKey);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
