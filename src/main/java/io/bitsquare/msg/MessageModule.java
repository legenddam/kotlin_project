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

package io.bitsquare.msg;

import io.bitsquare.BitsquareModule;

import com.google.inject.Injector;
import com.google.inject.name.Names;

import java.util.Properties;

public abstract class MessageModule extends BitsquareModule {

    public static final String BOOTSTRAP_NODE_ID_KEY = "id";
    public static final String BOOTSTRAP_NODE_IP_KEY = "ip";
    public static final String BOOTSTRAP_NODE_PORT_KEY = "port";
    public static final String NETWORK_INTERFACE_KEY = "networkInterface";

    protected MessageModule(Properties properties) {
        super(properties);
    }

    @Override
    protected final void configure() {
        bind(MessageFacade.class).to(messageFacade()).asEagerSingleton();

        bind(String.class)
                .annotatedWith(Names.named("networkInterface"))
                .toInstance(properties.getProperty(NETWORK_INTERFACE_KEY, ""));

        doConfigure();
    }

    protected void doConfigure() {
    }

    protected abstract Class<? extends MessageFacade> messageFacade();

    @Override
    protected void doClose(Injector injector) {
        injector.getInstance(MessageFacade.class).shutDown();
    }
}
