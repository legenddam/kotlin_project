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

package io.bisq.core.payment.payload;

import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@ToString
@Setter
@Getter
@Slf4j
public final class FasterPaymentsAccountPayload extends PaymentAccountPayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private String sortCode;
    private String accountNr;

    public FasterPaymentsAccountPayload(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    public FasterPaymentsAccountPayload(String paymentMethod, String id, long maxTradePeriod,
                                        String sortCode, String accountNr) {
        super(paymentMethod, id, maxTradePeriod);
        this.sortCode = sortCode;
        this.accountNr = accountNr;
    }

    @Override
    public String getPaymentDetails() {
        return "FasterPayments - UK Sort code: " + sortCode + ", Account number: " + accountNr;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return "UK Sort code: " + sortCode + "\n" +
                "Account number: " + accountNr;
    }

    @Override
    public PB.PaymentAccountPayload toProtoMessage() {
        PB.FasterPaymentsAccountPayload.Builder thisClass =
                PB.FasterPaymentsAccountPayload.newBuilder()
                        .setSortCode(sortCode)
                        .setAccountNr(accountNr);
        PB.PaymentAccountPayload.Builder paymentAccountPayload =
                PB.PaymentAccountPayload.newBuilder()
                        .setId(id)
                        .setPaymentMethodId(paymentMethodId)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setFasterPaymentsAccountPayload(thisClass);
        return paymentAccountPayload.build();
    }
}
