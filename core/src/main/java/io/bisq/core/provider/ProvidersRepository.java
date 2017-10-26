/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */
package io.bisq.core.provider;

import com.google.inject.Inject;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.network.NetworkOptionKeys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
public class ProvidersRepository {
    private final String providersFromProgramArgs;
    private final boolean useLocalhostForP2P;

    private List<String> providerList;
    private String baseUrl = "";

    // added in v0.6
    @Nullable
    private List<String> bannedNodes;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProvidersRepository(@Named(AppOptionKeys.PROVIDERS) String providers,
                               @Named(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P) boolean useLocalhostForP2P) {

        this.providersFromProgramArgs = providers;
        this.useLocalhostForP2P = useLocalhostForP2P;
    }

    public void setBannedNodes(@Nullable List<String> bannedNodes) {
        this.bannedNodes = bannedNodes;
    }

    public void onAllServicesInitialized() {
        fillProviderList();
        setBaseUrl();
    }

    public void fillProviderList() {
        String providerAsString;
        if (providersFromProgramArgs == null || providersFromProgramArgs.isEmpty()) {
            if (useLocalhostForP2P) {
                // If we run in localhost mode we don't have the tor node running, so we need a clearnet host
                // Use localhost for using a locally running provider
                // providerAsString = "http://localhost:8080/";
                // providerAsString = "http://localhost:8080/, http://37.139.14.34:8080/";
                providerAsString = "http://37.139.14.34:8080/";
            } else {
                providerAsString = "http://44mgyoe2b6oqiytt.onion/, http://5bmpx76qllutpcyp.onion/";
            }
        } else {
            providerAsString = providersFromProgramArgs;
        }
        if (bannedNodes != null)
            log.info("banned provider nodes: " + bannedNodes);
        providerList = Arrays.asList(StringUtils.deleteWhitespace(providerAsString).split(","))
            .stream()
            .filter(e -> bannedNodes == null || !bannedNodes.contains(e.replace("http://", "").replace("/", "").replace(".onion", "")))
            .collect(Collectors.toList());
        log.info("providerList={}", providerList);
    }

    private void setBaseUrl() {
        if (!providerList.isEmpty())
            baseUrl = providerList.get(new Random().nextInt(providerList.size()));

        log.info("selected baseUrl={}", baseUrl);
    }

    public void setNewRandomBaseUrl() {
        int counter = 0;
        String newBaseUrl = "";
        do {
            if (!providerList.isEmpty())
                newBaseUrl = providerList.get(new Random().nextInt(providerList.size()));
            counter++;
        }
        while (counter < 100 && baseUrl.equals(newBaseUrl));
        baseUrl = newBaseUrl;
        log.info("Use new provider baseUrl: " + baseUrl);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean hasMoreProviders() {
        return !providerList.isEmpty();
    }

}
