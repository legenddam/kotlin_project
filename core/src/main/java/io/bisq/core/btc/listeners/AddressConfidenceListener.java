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

package io.bisq.core.btc.listeners;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.TransactionConfidence;

public class AddressConfidenceListener {
    private final Address address;

    public AddressConfidenceListener(Address address) {
        this.address = address;
    }

    public Address getAddress() {
        return address;
    }

    @SuppressWarnings("UnusedParameters")
    public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
    }
}