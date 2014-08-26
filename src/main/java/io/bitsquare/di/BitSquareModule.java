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

package io.bitsquare.di;


import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.params.RegTestParams;
import com.google.bitcoin.params.TestNet3Params;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.msg.BootstrappedPeerFactory;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.P2PNode;
import io.bitsquare.msg.SeedNodeAddress;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Persistence;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.orderbook.OrderBook;
import io.bitsquare.user.User;

import javax.inject.Inject;

public class BitSquareModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(User.class).asEagerSingleton();
        bind(OrderBook.class).asEagerSingleton();
        bind(Persistence.class).asEagerSingleton();
        bind(Settings.class).asEagerSingleton();

        bind(CryptoFacade.class).asEagerSingleton();
        bind(WalletFacade.class).asEagerSingleton();
        bind(FeePolicy.class).asEagerSingleton();

        bind(BlockChainFacade.class).asEagerSingleton();
        bind(MessageFacade.class).asEagerSingleton();
        bind(P2PNode.class).asEagerSingleton();
        bind(BootstrappedPeerFactory.class).asEagerSingleton();

        bind(TradeManager.class).asEagerSingleton();
        bind(BitSquareFormatter.class).asEagerSingleton();


        //bind(String.class).annotatedWith(Names.named("networkType")).toInstance(WalletFacade.MAIN_NET);
        // how to use reg test see description in the readme file
        bind(String.class).annotatedWith(Names.named("networkType")).toInstance(WalletFacade.REG_TEST_NET);
        //test net not working yet: http://sourceforge.net/p/bitcoin/mailman/message/32349208/
        //bind(String.class).annotatedWith(Names.named("networkType")).toInstance(WalletFacade.TEST_NET);
        bind(NetworkParameters.class).toProvider(NetworkParametersProvider.class).asEagerSingleton();

        // bind(Boolean.class).annotatedWith(Names.named("useDiskStorage")).toInstance(new Boolean(true));
        bind(Boolean.class).annotatedWith(Names.named("useDiskStorage")).toInstance(new Boolean(false));
        bind(SeedNodeAddress.StaticSeedNodeAddresses.class).annotatedWith(
                Names.named("defaultSeedNode")).toInstance(SeedNodeAddress.StaticSeedNodeAddresses.LOCALHOST);
        // bind(SeedNodeAddress.StaticSeedNodeAddresses.class).annotatedWith(Names.named("defaultSeedNode"))
        // .toInstance(SeedNodeAddress.StaticSeedNodeAddresses.DIGITAL_OCEAN);
    }
}

class NetworkParametersProvider implements Provider<NetworkParameters> {
    private final String networkType;

    @Inject
    public NetworkParametersProvider(@Named("networkType") String networkType) {
        this.networkType = networkType;
    }


    public NetworkParameters get() {
        NetworkParameters result = null;

        switch (networkType) {
            case WalletFacade.MAIN_NET:
                result = MainNetParams.get();
                break;
            case WalletFacade.TEST_NET:
                result = TestNet3Params.get();
                break;
            case WalletFacade.REG_TEST_NET:
                result = RegTestParams.get();
                break;
        }
        return result;
    }
}
