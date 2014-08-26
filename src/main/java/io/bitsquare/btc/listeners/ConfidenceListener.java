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

package io.bitsquare.btc.listeners;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.TransactionConfidence;

public class ConfidenceListener {
    private final Address address;

    public ConfidenceListener(Address address) {
        this.address = address;
    }

    public Address getAddress() {
        return address;
    }

    public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
    }
}