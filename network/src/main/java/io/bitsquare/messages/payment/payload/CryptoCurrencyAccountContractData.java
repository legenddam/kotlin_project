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

package io.bitsquare.messages.payment.payload;

import io.bitsquare.app.Version;
import io.bitsquare.common.wire.proto.Messages;

public final class CryptoCurrencyAccountContractData extends PaymentAccountContractData {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private String address;

    public CryptoCurrencyAccountContractData(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    public CryptoCurrencyAccountContractData(String paymentMethod, String id, long maxTradePeriod, String address) {
        super(paymentMethod, id, maxTradePeriod);
        this.address = address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public String getPaymentDetails() {
        return "Receivers altcoin address: " + address;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return getPaymentDetails();
    }

    @Override
    public Messages.PaymentAccountContractData toProtoBuf() {
        Messages.CryptoCurrencyAccountContractData.Builder cryptoCurrencyAccountContractData =
                Messages.CryptoCurrencyAccountContractData.newBuilder().setAddress(address);
        Messages.PaymentAccountContractData.Builder paymentAccountContractData =
                Messages.PaymentAccountContractData.newBuilder()
                        .setId(id)
                        .setPaymentMethodName(paymentMethodName)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setCryptoCurrencyAccountContractData(cryptoCurrencyAccountContractData);
        return paymentAccountContractData.build();
    }
}
