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

package io.bisq.core.app;

import io.bisq.common.crypto.KeyRing;
import io.bisq.core.trade.statistics.TradeStatisticsManager;
import io.bisq.network.crypto.EncryptionService;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.P2PServiceListener;
import io.bisq.network.p2p.network.CloseConnectionReason;
import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.network.ConnectionListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

@Slf4j
public class AppSetupWithP2P extends AppSetup {
    private final P2PService p2PService;
    private BooleanProperty p2pNetWorkReady;

    @Inject
    public AppSetupWithP2P(BisqEnvironment bisqEnvironment,
                           EncryptionService encryptionService,
                           KeyRing keyRing,
                           P2PService p2PService,
                           TradeStatisticsManager tradeStatisticsManager) {
        super(bisqEnvironment,
                encryptionService,
                keyRing,
                tradeStatisticsManager);
        this.p2PService = p2PService;
    }

    @Override
    protected void startBasicServices() {
        p2pNetWorkReady = initP2PNetwork();

        p2pNetWorkReady.addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                onBasicServicesInitialized();
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BooleanProperty initP2PNetwork() {
        log.info("initP2PNetwork");
        p2PService.getNetworkNode().addConnectionListener(new ConnectionListener() {
            @Override
            public void onConnection(Connection connection) {
            }

            @Override
            public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
                // We only check at seed nodes as they are running the latest version
                // Other disconnects might be caused by peers running an older version
                if (connection.getPeerType() == Connection.PeerType.SEED_NODE &&
                        closeConnectionReason == CloseConnectionReason.RULE_VIOLATION) {
                    log.warn("RULE_VIOLATION onDisconnect closeConnectionReason=" + closeConnectionReason);
                    log.warn("RULE_VIOLATION onDisconnect connection=" + connection);
                }
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });

        final BooleanProperty p2pNetworkInitialized = new SimpleBooleanProperty();
        p2PService.start(new P2PServiceListener() {
            @Override
            public void onTorNodeReady() {
            }

            @Override
            public void onHiddenServicePublished() {
                log.info("onHiddenServicePublished");
            }

            @Override
            public void onRequestingDataCompleted() {
                log.info("onRequestingDataCompleted");
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onNoSeedNodeAvailable() {
                log.info("onNoSeedNodeAvailable");
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onNoPeersAvailable() {
                log.info("onNoPeersAvailable");
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onBootstrapComplete() {
                log.info("onBootstrapComplete");
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
                log.error(throwable.toString());
            }
        });

        return p2pNetworkInitialized;
    }

    private void onBasicServicesInitialized() {
        log.info("onBasicServicesInitialized");
        p2PService.onAllServicesInitialized();
    }
}
