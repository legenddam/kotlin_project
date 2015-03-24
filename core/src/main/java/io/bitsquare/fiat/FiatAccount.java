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

package io.bitsquare.fiat;

import io.bitsquare.locale.Country;

import java.io.Serializable;

import java.util.Currency;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

@Immutable
public class FiatAccount implements Serializable {
    private static final long serialVersionUID = 1792577576443221268L;

    private final FiatAccountType fiatAccountType;
    private final String accountPrimaryID;  // like IBAN
    private final String accountSecondaryID; // like BIC
    private final String accountHolderName;
    private final Country country;     // where bank is registered
    private final String nameOfBank;

    // The main currency if account support multiple currencies.
    // The user can create multiple bank accounts with same bank account but other currency if his bank account
    // support that.
    private final Currency currency;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public FiatAccount(FiatAccountType fiatAccountType, Currency currency, Country country, String nameOfBank,
                       String accountHolderName, String accountPrimaryID, String accountSecondaryID) {
        this.fiatAccountType = fiatAccountType;
        this.currency = currency;
        this.country = country;
        this.nameOfBank = nameOfBank;
        this.accountHolderName = accountHolderName;
        this.accountPrimaryID = accountPrimaryID;
        this.accountSecondaryID = accountSecondaryID;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters/Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getAccountPrimaryID() {
        return accountPrimaryID;
    }

    public String getAccountSecondaryID() {
        return accountSecondaryID;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public FiatAccountType getFiatAccountType() {
        return fiatAccountType;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Country getCountry() {
        return country;
    }

    // we use the accountTitle as unique id
    public String getId() {
        return nameOfBank;
    }

    public String getNameOfBank() {
        return nameOfBank;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public int hashCode() {
        return Objects.hashCode(nameOfBank);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FiatAccount)) return false;
        if (obj == this) return true;

        final FiatAccount other = (FiatAccount) obj;
        return nameOfBank.equals(other.getId());
    }

    @Override
    public String toString() {
        return "BankAccount{" +
                "bankAccountType=" + fiatAccountType +
                ", accountPrimaryID='" + accountPrimaryID + '\'' +
                ", accountSecondaryID='" + accountSecondaryID + '\'' +
                ", accountHolderName='" + accountHolderName + '\'' +
                ", country=" + country +
                ", currency=" + currency +
                ", accountTitle='" + nameOfBank + '\'' +
                '}';
    }
}
