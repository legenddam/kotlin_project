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

package io.bisq.core.btc.wallet;

import com.google.common.collect.Sets;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * We use a specialized version of the CoinSelector based on the DefaultCoinSelector implementation.
 * We lookup for spendable outputs which matches any of our addresses.
 */
class BtcCoinSelector extends BisqDefaultCoinSelector {
    private static final Logger log = LoggerFactory.getLogger(BtcCoinSelector.class);

    private final Set<Address> addresses;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    BtcCoinSelector(Set<Address> addresses, boolean permitForeignPendingTx) {
        super(permitForeignPendingTx);
        this.addresses = addresses;
    }

    BtcCoinSelector(Set<Address> addresses) {
        this(addresses, true);
    }

    BtcCoinSelector(Address address, boolean permitForeignPendingTx) {
        this(Sets.newHashSet(address), permitForeignPendingTx);
    }

    BtcCoinSelector(Address address) {
        this(Sets.newHashSet(address), true);
    }

    @Override
    protected boolean isTxOutputSpendable(TransactionOutput output) {
        if (WalletUtils.isOutputScriptConvertableToAddress(output)) {
            Address address = WalletUtils.getAddressFromOutput(output);
            boolean containsAddress = addresses.contains(address);
            if (!containsAddress)
                log.trace("addresses not containing address " + addresses + " / " + address);
            return containsAddress;
        } else {
            log.warn("transactionOutput.getScriptPubKey() not isSentToAddress or isPayToScriptHash");
            return false;
        }
    }
}
