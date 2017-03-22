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

package io.bisq.core.payment;

import io.bisq.common.app.Version;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.core.user.Preferences;
import io.bisq.protobuffer.payload.payment.OKPayAccountPayload;
import io.bisq.protobuffer.payload.payment.PaymentAccountPayload;
import io.bisq.protobuffer.payload.payment.PaymentMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO missing support for selected trade currency
public final class OKPayAccount extends PaymentAccount {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(OKPayAccount.class);

    public OKPayAccount() {
        super(PaymentMethod.OK_PAY);
        tradeCurrencies.addAll(CurrencyUtil.getAllOKPayCurrencies(Preferences.getDefaultLocale()));
    }

    @Override
    protected PaymentAccountPayload setPayload() {
        return new OKPayAccountPayload(paymentMethod.getId(), id, paymentMethod.getMaxTradePeriod());
    }

    public void setAccountNr(String accountNr) {
        ((OKPayAccountPayload) paymentAccountPayload).setAccountNr(accountNr);
    }

    public String getAccountNr() {
        return ((OKPayAccountPayload) paymentAccountPayload).getAccountNr();
    }
}
