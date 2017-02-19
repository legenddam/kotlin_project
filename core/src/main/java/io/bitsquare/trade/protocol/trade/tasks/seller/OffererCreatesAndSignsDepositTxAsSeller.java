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

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.data.PreparedDepositTxAndOffererInputs;
import io.bitsquare.common.crypto.Hash;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class OffererCreatesAndSignsDepositTxAsSeller extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererCreatesAndSignsDepositTxAsSeller.class);

    public OffererCreatesAndSignsDepositTxAsSeller(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not be null");
            Offer offer = trade.getOffer();
            Coin sellerInputAmount = FeePolicy.getSecurityDeposit(offer).add(FeePolicy.getFixedTxFeeForTrades(offer)).add(trade.getTradeAmount());
            Coin msOutputAmount = sellerInputAmount.add(FeePolicy.getSecurityDeposit(offer));

            log.debug("\n\n------------------------------------------------------------\n"
                    + "Contract as json\n"
                    + trade.getContractAsJson()
                    + "\n------------------------------------------------------------\n");

            byte[] contractHash = Hash.getHash(trade.getContractAsJson());
            trade.setContractHash(contractHash);
            WalletService walletService = processModel.getWalletService();
            String id = processModel.getOffer().getId();
            AddressEntry offererAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.RESERVED_FOR_TRADE);
            AddressEntry sellerMultiSigAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            sellerMultiSigAddressEntry.setCoinLockedInMultiSig(sellerInputAmount.subtract(FeePolicy.getFixedTxFeeForTrades(offer)));
            Address changeAddress = walletService.getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE).getAddress();
            PreparedDepositTxAndOffererInputs result = processModel.getTradeWalletService().offererCreatesAndSignsDepositTx(
                    false,
                    contractHash,
                    sellerInputAmount,
                    msOutputAmount,
                    processModel.tradingPeer.getRawTransactionInputs(),
                    processModel.tradingPeer.getChangeOutputValue(),
                    processModel.tradingPeer.getChangeOutputAddress(),
                    offererAddressEntry.getAddress(),
                    changeAddress,
                    processModel.tradingPeer.getMultiSigPubKey(),
                    sellerMultiSigAddressEntry.getPubKey(),
                    trade.getArbitratorPubKey());

            processModel.setPreparedDepositTx(result.depositTransaction);
            processModel.setRawTransactionInputs(result.rawOffererInputs);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
