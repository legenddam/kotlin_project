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

import ch.qos.logback.classic.Level;
import io.bisq.common.CommonOptionKeys;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.KeyStorage;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.btc.BaseCryptoNetwork;
import io.bisq.core.btc.BtcOptionKeys;
import io.bisq.core.btc.UserAgent;
import io.bisq.core.dao.DaoOptionKeys;
import io.bisq.core.exceptions.BisqException;
import io.bisq.network.NetworkOptionKeys;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.*;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;

public class BisqEnvironment extends StandardEnvironment {
    private static final Logger log = LoggerFactory.getLogger(BisqEnvironment.class);

    private static final String BITCOIN_NETWORK_PROP = "baseCryptoNetwork.properties";

    public static void setDefaultAppName(String defaultAppName) {
        DEFAULT_APP_NAME = defaultAppName;
    }

    public static String DEFAULT_APP_NAME = "bisq";

    public static final String DEFAULT_USER_DATA_DIR = defaultUserDataDir();
    public static final String DEFAULT_APP_DATA_DIR = appDataDir(DEFAULT_USER_DATA_DIR, DEFAULT_APP_NAME);

    public static final String LOG_LEVEL_DEFAULT = Level.INFO.levelStr;

    public static final String BISQ_COMMANDLINE_PROPERTY_SOURCE_NAME = "bisqCommandLineProperties";
    public static final String BISQ_APP_DIR_PROPERTY_SOURCE_NAME = "bisqAppDirProperties";
    public static final String BISQ_HOME_DIR_PROPERTY_SOURCE_NAME = "bisqHomeDirProperties";
    public static final String BISQ_CLASSPATH_PROPERTY_SOURCE_NAME = "bisqClasspathProperties";
    public static final String BISQ_DEFAULT_PROPERTY_SOURCE_NAME = "bisqDefaultProperties";

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    private final String appName;
    private final String userDataDir;
    private final String appDataDir;
    private final String btcNetworkDir;
    private final String logLevel, providers;
    private BaseCryptoNetwork baseCryptoNetwork;
    private final String btcNodes, seedNodes, ignoreDevMsg, useTorForBtc, rpcUser, rpcPassword,
            rpcPort, rpcBlockNotificationPort, dumpBlockchainData, fullDaoNode,
            myAddress, banList, dumpStatistics, maxMemory, socks5ProxyBtcAddress,
            socks5ProxyHttpAddress;

    public BaseCryptoNetwork getBaseCryptoNetwork() {
        return baseCryptoNetwork;
    }

    public void saveBaseCryptoNetwork(BaseCryptoNetwork baseCryptoNetwork) {
        try {
            Resource resource = getAppDirPropertiesResource();
            File file = resource.getFile();
            Properties properties = new Properties();
            if (file.exists()) {
                Object propertiesObject = getAppDirProperties().getSource();
                if (propertiesObject instanceof Properties) {
                    properties = (Properties) propertiesObject;
                } else {
                    log.warn("propertiesObject not instance of Properties");
                }
            }
            properties.setProperty(BtcOptionKeys.BASE_CRYPTO_NETWORK, baseCryptoNetwork.name());

            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                properties.store(fileOutputStream, null);
            } catch (IOException e1) {
                log.error(e1.getMessage());
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }

    public String getAppDataDir() {
        return appDataDir;
    }

    public BisqEnvironment(OptionSet options) {
        this(new JOptCommandLinePropertySource(BISQ_COMMANDLINE_PROPERTY_SOURCE_NAME, checkNotNull(
                options)));
    }

    public BisqEnvironment(PropertySource commandLineProperties) {
        //CommonOptionKeys
        logLevel = commandLineProperties.containsProperty(CommonOptionKeys.LOG_LEVEL_KEY) ?
                (String) commandLineProperties.getProperty(CommonOptionKeys.LOG_LEVEL_KEY) :
                LOG_LEVEL_DEFAULT;

        //AppOptionKeys
        userDataDir = commandLineProperties.containsProperty(AppOptionKeys.USER_DATA_DIR_KEY) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.USER_DATA_DIR_KEY) :
                DEFAULT_USER_DATA_DIR;

        appName = commandLineProperties.containsProperty(AppOptionKeys.APP_NAME_KEY) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.APP_NAME_KEY) :
                DEFAULT_APP_NAME;

        appDataDir = commandLineProperties.containsProperty(AppOptionKeys.APP_DATA_DIR_KEY) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.APP_DATA_DIR_KEY) :
                appDataDir(userDataDir, appName);
        ignoreDevMsg = commandLineProperties.containsProperty(AppOptionKeys.IGNORE_DEV_MSG_KEY) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.IGNORE_DEV_MSG_KEY) :
                "";
        dumpStatistics = commandLineProperties.containsProperty(AppOptionKeys.DUMP_STATISTICS) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.DUMP_STATISTICS) :
                "";
        maxMemory = commandLineProperties.containsProperty(AppOptionKeys.MAX_MEMORY) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.MAX_MEMORY) :
                "";
        providers = commandLineProperties.containsProperty(AppOptionKeys.PROVIDERS) ?
                (String) commandLineProperties.getProperty(AppOptionKeys.PROVIDERS) :
                "";

        //NetworkOptionKeys
        seedNodes = commandLineProperties.containsProperty(NetworkOptionKeys.SEED_NODES_KEY) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.SEED_NODES_KEY) :
                "";

        myAddress = commandLineProperties.containsProperty(NetworkOptionKeys.MY_ADDRESS) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.MY_ADDRESS) :
                "";
        banList = commandLineProperties.containsProperty(NetworkOptionKeys.BAN_LIST) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.BAN_LIST) :
                "";
        socks5ProxyBtcAddress = commandLineProperties.containsProperty(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS) :
                "";
        socks5ProxyHttpAddress = commandLineProperties.containsProperty(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS) ?
                (String) commandLineProperties.getProperty(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS) :
                "";

        //RpcOptionKeys
        rpcUser = commandLineProperties.containsProperty(DaoOptionKeys.RPC_USER) ?
                (String) commandLineProperties.getProperty(DaoOptionKeys.RPC_USER) :
                "";
        rpcPassword = commandLineProperties.containsProperty(DaoOptionKeys.RPC_PASSWORD) ?
                (String) commandLineProperties.getProperty(DaoOptionKeys.RPC_PASSWORD) :
                "";
        rpcPort = commandLineProperties.containsProperty(DaoOptionKeys.RPC_PORT) ?
                (String) commandLineProperties.getProperty(DaoOptionKeys.RPC_PORT) :
                "";
        rpcBlockNotificationPort = commandLineProperties.containsProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT) ?
                (String) commandLineProperties.getProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT) :
                "";
        dumpBlockchainData = commandLineProperties.containsProperty(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA) ?
                (String) commandLineProperties.getProperty(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA) :
                "";
        fullDaoNode = commandLineProperties.containsProperty(DaoOptionKeys.FULL_DAO_NODE) ?
                (String) commandLineProperties.getProperty(DaoOptionKeys.FULL_DAO_NODE) :
                "";

        btcNodes = commandLineProperties.containsProperty(BtcOptionKeys.BTC_NODES) ?
                (String) commandLineProperties.getProperty(BtcOptionKeys.BTC_NODES) :
                "";

        useTorForBtc = commandLineProperties.containsProperty(BtcOptionKeys.USE_TOR_FOR_BTC) ?
                (String) commandLineProperties.getProperty(BtcOptionKeys.USE_TOR_FOR_BTC) :
                "";

        MutablePropertySources propertySources = this.getPropertySources();
        propertySources.addFirst(commandLineProperties);
        try {
            propertySources.addLast(getAppDirProperties());
            baseCryptoNetwork = BaseCryptoNetwork.valueOf(getProperty(BtcOptionKeys.BASE_CRYPTO_NETWORK,
                    BaseCryptoNetwork.DEFAULT.name()).toUpperCase());
            btcNetworkDir = Paths.get(appDataDir, baseCryptoNetwork.name().toLowerCase()).toString();
            File btcNetworkDirFile = new File(btcNetworkDir);
            if (!btcNetworkDirFile.exists())
                btcNetworkDirFile.mkdir();

            // btcNetworkDir used in defaultProperties
            propertySources.addLast(defaultProperties());
        } catch (Exception ex) {
            throw new BisqException(ex);
        }
    }

    private Resource getAppDirPropertiesResource() {
        String location = String.format("file:%s/bisq.properties", appDataDir);
        return resourceLoader.getResource(location);
    }

    PropertySource<?> getAppDirProperties() throws Exception {
        Resource resource = getAppDirPropertiesResource();

        if (!resource.exists())
            return new PropertySource.StubPropertySource(BISQ_APP_DIR_PROPERTY_SOURCE_NAME);

        return new ResourcePropertySource(BISQ_APP_DIR_PROPERTY_SOURCE_NAME, resource);
    }

    private PropertySource<?> homeDirProperties() throws Exception {
        return new PropertySource.StubPropertySource(BISQ_HOME_DIR_PROPERTY_SOURCE_NAME);
    }

    private PropertySource<?> classpathProperties() throws Exception {
        return new PropertySource.StubPropertySource(BISQ_CLASSPATH_PROPERTY_SOURCE_NAME);
    }

    private PropertySource<?> defaultProperties() {
        return new PropertiesPropertySource(BISQ_DEFAULT_PROPERTY_SOURCE_NAME, new Properties() {
            {
                setProperty(CommonOptionKeys.LOG_LEVEL_KEY, logLevel);

                setProperty(NetworkOptionKeys.SEED_NODES_KEY, seedNodes);
                setProperty(NetworkOptionKeys.MY_ADDRESS, myAddress);
                setProperty(NetworkOptionKeys.BAN_LIST, banList);
                setProperty(NetworkOptionKeys.TOR_DIR, Paths.get(btcNetworkDir, "tor").toString());
                setProperty(NetworkOptionKeys.NETWORK_ID, String.valueOf(baseCryptoNetwork.ordinal()));
                setProperty(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS, socks5ProxyBtcAddress);
                setProperty(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS, socks5ProxyHttpAddress);

                setProperty(AppOptionKeys.APP_DATA_DIR_KEY, appDataDir);
                setProperty(AppOptionKeys.IGNORE_DEV_MSG_KEY, ignoreDevMsg);
                setProperty(AppOptionKeys.DUMP_STATISTICS, dumpStatistics);
                setProperty(AppOptionKeys.APP_NAME_KEY, appName);
                setProperty(AppOptionKeys.MAX_MEMORY, maxMemory);
                setProperty(AppOptionKeys.USER_DATA_DIR_KEY, userDataDir);
                setProperty(AppOptionKeys.PROVIDERS, providers);

                setProperty(DaoOptionKeys.RPC_USER, rpcUser);
                setProperty(DaoOptionKeys.RPC_PASSWORD, rpcPassword);
                setProperty(DaoOptionKeys.RPC_PORT, rpcPort);
                setProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT, rpcBlockNotificationPort);
                setProperty(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA, dumpBlockchainData);
                setProperty(DaoOptionKeys.FULL_DAO_NODE, fullDaoNode);

                setProperty(BtcOptionKeys.BTC_NODES, btcNodes);
                setProperty(BtcOptionKeys.USE_TOR_FOR_BTC, useTorForBtc);
                setProperty(BtcOptionKeys.WALLET_DIR, btcNetworkDir);

                setProperty(UserAgent.NAME_KEY, appName);
                setProperty(UserAgent.VERSION_KEY, Version.VERSION);

                setProperty(Storage.STORAGE_DIR, Paths.get(btcNetworkDir, "db").toString());
                setProperty(KeyStorage.KEY_STORAGE_DIR, Paths.get(btcNetworkDir, "keys").toString());
            }
        });
    }

    public static String defaultUserDataDir() {
        if (Utilities.isWindows())
            return System.getenv("APPDATA");
        else if (Utilities.isOSX())
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support").toString();
        else // *nix
            return Paths.get(System.getProperty("user.home"), ".local", "share").toString();
    }

    private static String appDataDir(String userDataDir, String appName) {
        return Paths.get(userDataDir, appName).toString();
    }
}
