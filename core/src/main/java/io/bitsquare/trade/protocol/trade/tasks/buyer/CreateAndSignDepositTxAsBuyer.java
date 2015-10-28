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

package io.bitsquare.trade.protocol.trade.tasks.buyer;

import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.data.PreparedDepositTxAndOffererInputs;
import io.bitsquare.common.crypto.CryptoUtil;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class CreateAndSignDepositTxAsBuyer extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(CreateAndSignDepositTxAsBuyer.class);

    public CreateAndSignDepositTxAsBuyer(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not be null");
            Coin buyerInputAmount = FeePolicy.SECURITY_DEPOSIT.add(FeePolicy.TX_FEE);
            Coin msOutputAmount = buyerInputAmount.add(FeePolicy.SECURITY_DEPOSIT).add(trade.getTradeAmount());

            log.debug("getContractAsJson");
            log.debug("----------");
            log.debug(trade.getContractAsJson());
            log.debug("----------");

            byte[] contractHash = CryptoUtil.getHash(trade.getContractAsJson());
            trade.setContractHash(contractHash);
            PreparedDepositTxAndOffererInputs result = processModel.getTradeWalletService().offererCreatesAndSignsDepositTx(
                    true,
                    contractHash,
                    buyerInputAmount,
                    msOutputAmount,
                    processModel.tradingPeer.getRawInputs(),
                    processModel.tradingPeer.getChangeOutputValue(),
                    processModel.tradingPeer.getChangeOutputAddress(),
                    processModel.getAddressEntry(),
                    processModel.getTradeWalletPubKey(),
                    processModel.tradingPeer.getTradeWalletPubKey(),
                    processModel.getArbitratorPubKey(trade.getArbitratorAddress()));

            processModel.setPreparedDepositTx(result.depositTransaction);
            processModel.setRawInputs(result.rawOffererInputs);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
