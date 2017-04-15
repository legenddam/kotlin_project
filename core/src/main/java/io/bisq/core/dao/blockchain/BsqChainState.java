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

package io.bisq.core.dao.blockchain;

import io.bisq.common.persistence.Persistable;
import io.bisq.common.util.Utilities;
import io.bisq.core.dao.blockchain.vo.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.google.common.base.Preconditions.checkArgument;

// Represents mutable state of BSQ chain data
// We get accesses the data from non-UserThread context, so we need to handle threading here.
@Slf4j
public class BsqChainState implements Persistable {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Statics
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static BsqChainState getClonedMap(BsqChainState bsqChainState) {
        return Utilities.<BsqChainState>deserialize(Utilities.serialize(bsqChainState));
    }

    // Outside only used in Json exporter atm
    public HashMap<TxIdIndexTuple, TxOutput> getTxOutputMap() {
        HashMap<TxIdIndexTuple, TxOutput> txOutputMap = new HashMap<>();
        txMap.values().stream()
                .flatMap(tx -> tx.getOutputs().stream())
                .forEach(txOutput -> txOutputMap.put(txOutput.getTxIdIndexTuple(), txOutput));
        return txOutputMap;
    }

    private final Map<String, Tx> txMap;
    private final List<BsqBlock> blocks;
    private final Map<TxIdIndexTuple, SpendInfo> spendInfoMap = new HashMap<>();
    private final Map<TxIdIndexTuple, TxOutput> verifiedTxOutputMap = new HashMap<>();
    private final Map<String, Long> burnedFeeMap = new HashMap<>();
    private Tx genesisTx;

    @Getter
    @Setter
    private int chainTip;
   

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqChainState() {
        txMap = new ConcurrentHashMap<>();
        blocks = new CopyOnWriteArrayList<>();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addBlock(BsqBlock block) {
        checkArgument(chainTip <= block.getHeight(), "chainTip must not be lager than block.getHeight(). chainTip=" +
                chainTip + ": block.getHeight()=" + chainTip);
        checkArgument(!blocks.contains(block), "blocks must not contain block");
        blocks.add(block);
        printSize();
    }

    public void addTxToBlock(BsqBlock block, Tx tx) {
        checkArgument(blocks.contains(block), "blocks must contain block");
        checkArgument(!block.getTxs().contains(tx), "block must not contain tx");
        checkArgument(!txMap.containsValue(tx), "txMap must not contain tx");

        block.getTxs().add(tx);
        txMap.put(tx.getId(), tx);
        chainTip = block.getHeight();
    }

    public void addTx(Tx tx) {
        txMap.put(tx.getId(), tx);
    }

    // SpendInfo
    public void setSpendInfo(TxOutput txOutput, SpendInfo spendInfo) {
        spendInfoMap.put(txOutput.getTxIdIndexTuple(), spendInfo);
    }

    public boolean isTxOutputUnSpend(TxIdIndexTuple txIdIndexTuple) {
        return !spendInfoMap.containsKey(txIdIndexTuple);
    }

    public boolean isTxOutputUnSpend(TxOutput txOutput) {
        return isTxOutputUnSpend(txOutput.getTxIdIndexTuple());
    }

    public boolean isTxOutputUnSpent(String txId, int index) {
        return isTxOutputUnSpend(new TxIdIndexTuple(txId, index));
    }

    // Genesis
    public void setGenesisTx(Tx tx) {
        genesisTx = tx;
    }

    // Verified
    public void addVerifiedTxOutput(TxOutput txOutput) {
        verifiedTxOutputMap.put(txOutput.getTxIdIndexTuple(), txOutput);
    }

    public boolean isVerifiedTxOutput(TxOutput txOutput) {
        return verifiedTxOutputMap.containsKey(txOutput.getTxIdIndexTuple());
    }

    // BurnedFee
    public void addTxIdBurnedFeeMap(String txId, long burnedFee) {
        burnedFeeMap.put(txId, burnedFee);
    }

    public boolean hasTxBurnedFee(String txId) {
        return burnedFeeMap.containsKey(txId) ? burnedFeeMap.get(txId) > 0 : false;
    }

    public Optional<Tx> getTx(String txId) {
        return txMap.get(txId) != null ? Optional.of(txMap.get(txId)) : Optional.<Tx>empty();
    }

    public boolean containsTx(String txId) {
        return getTx(txId).isPresent();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<TxOutput> getTxOutput(String txId, int index) {
        return getTx(txId).flatMap(e -> e.getTxOutput(index));
    }

    // only used in test...
    public Collection<TxOutput> values() {
        return getTxOutputMap().values();
    }

    public int size() {
        return txMap.size();
    }

    @Override
    public String toString() {
        return "txMap " + txMap.toString();
    }

    private void printSize() {
        log.info("Nr of entries={}; Size in kb={}, chainTip={}", size(), Utilities.serialize(this).length / 1000d, chainTip);
    }


  /*  private Set<TxOutput> getUnspentTxOutputs() {
        return getTxOutputMap().values().stream().filter(TxOutput::isUnSpend).collect(Collectors.toSet());
    }*/

    /*private List<TxOutput> getSortedUnspentTxOutputs() {
        List<TxOutput> list = getUnspentTxOutputs().stream().collect(Collectors.toList());
        Collections.sort(list, (o1, o2) -> o1.getBlockHeightWithTxoId().compareTo(o2.getBlockHeightWithTxoId()));
        return list;
    }*/

   /* private void printUnspentTxOutputs(String prefix) {
        final String txoIds = getBlocHeightSortedTxoIds();
        log.info(prefix + " utxo: size={}, blockHeight={}, hashCode={}, txoids={}",
                getSortedUnspentTxOutputs().size(),
                chainTip,
                getBlockHeightSortedTxoIdsHashCode(),
                txoIds);
    }*/

  /*  private int getBlockHeightSortedTxoIdsHashCode() {
        return getBlocHeightSortedTxoIds().hashCode();
    }*/

    /*private String getBlocHeightSortedTxoIds() {
        return getSortedUnspentTxOutputs().stream()
                .map(e -> e.getBlockHeight() + "/" + e.getTxoId())
                .collect(Collectors.joining("\n"));
    }*/


}

