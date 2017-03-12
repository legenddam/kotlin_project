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

package io.bitsquare.messages.payment.payload;

import io.bitsquare.app.Version;
import io.bitsquare.common.wire.proto.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NationalBankAccountContractData extends BankAccountContractData {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private static final Logger log = LoggerFactory.getLogger(NationalBankAccountContractData.class);

    public NationalBankAccountContractData(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    @Override
    public String getPaymentDetails() {
        return "National Bank transfer - " + getPaymentDetailsForTradePopup().replace("\n", ", ");
    }

    @Override
    public Messages.PaymentAccountContractData toProtoBuf() {
        Messages.NationalBankAccountContractData.Builder thisClass =
                Messages.NationalBankAccountContractData.newBuilder();
        Messages.BankAccountContractData.Builder bankAccountContractData =
                Messages.BankAccountContractData.newBuilder()
                        .setHolderName(holderName)
                        .setBankName(bankName)
                        .setBankId(bankId)
                        .setBranchId(branchId)
                        .setAccountNr(accountNr)
                        .setAccountType(accountType)
                        .setHolderTaxId(holderTaxId)
                        .setNationalBankAccountContractData(thisClass);
        Messages.CountryBasedPaymentAccountContractData.Builder countryBasedPaymentAccountContractData =
                Messages.CountryBasedPaymentAccountContractData.newBuilder()
                        .setCountryCode(countryCode)
                        .setBankAccountContractData(bankAccountContractData);
        Messages.PaymentAccountContractData.Builder paymentAccountContractData =
                Messages.PaymentAccountContractData.newBuilder()
                        .setId(id)
                        .setPaymentMethodName(paymentMethodName)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setCountryBasedPaymentAccountContractData(countryBasedPaymentAccountContractData);
        return paymentAccountContractData.build();
    }
}
