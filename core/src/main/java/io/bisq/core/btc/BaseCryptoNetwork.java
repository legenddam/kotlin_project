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

package io.bisq.core.btc;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.libdohj.params.LitecoinMainNetParams;
import org.libdohj.params.LitecoinRegTestParams;
import org.libdohj.params.LitecoinTestNet3Params;

public enum BaseCryptoNetwork {
    BTC_MAINNET(MainNetParams.get(), "BTC", "MAINNET"),
    BTC_TESTNET(TestNet3Params.get(), "BTC", "TESTNET"),
    BTC_REGTEST(RegTestParams.get(), "BTC", "REGTEST"),
    LTC_MAINNET(LitecoinMainNetParams.get(), "LTC", "MAINNET"),
    LTC_TESTNET(LitecoinTestNet3Params.get(), "LTC", "TESTNET"),
    LTC_REGTEST(LitecoinRegTestParams.get(), "LTC", "REGTEST");

    public static final BaseCryptoNetwork DEFAULT = BTC_MAINNET;

    private final NetworkParameters parameters;
    private String cryptoName;
    private String networkTranslation;

    BaseCryptoNetwork(NetworkParameters parameters, String cryptoName, String networkTranslation) {
        this.parameters = parameters;
        this.cryptoName = cryptoName;
        this.networkTranslation = networkTranslation;
    }

    public NetworkParameters getParameters() {
        return parameters;
    }

}
