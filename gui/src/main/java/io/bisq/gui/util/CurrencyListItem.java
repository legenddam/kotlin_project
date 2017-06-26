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

package io.bisq.gui.util;

import io.bisq.common.locale.TradeCurrency;

public class CurrencyListItem {
    public final TradeCurrency tradeCurrency;
    public final int numTrades;

    public CurrencyListItem(TradeCurrency tradeCurrency, int numTrades) {
        this.tradeCurrency = tradeCurrency;
        this.numTrades = numTrades;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CurrencyListItem that = (CurrencyListItem) o;

        //noinspection SimplifiableIfStatement
        if (numTrades != that.numTrades) return false;
        return !(tradeCurrency != null ? !tradeCurrency.equals(that.tradeCurrency) : that.tradeCurrency != null);

    }

    @Override
    public int hashCode() {
        int result = tradeCurrency != null ? tradeCurrency.hashCode() : 0;
        result = 31 * result + numTrades;
        return result;
    }

    @Override
    public String toString() {
        return "CurrencyListItem{" +
                "tradeCurrency=" + tradeCurrency +
                ", numTrades=" + numTrades +
                '}';
    }
}
