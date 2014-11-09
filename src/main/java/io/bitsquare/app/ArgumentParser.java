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

package io.bitsquare.app;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import static io.bitsquare.msg.MessageModule.*;

public class ArgumentParser {

    public static final String NAME_FLAG = "name";

    private final net.sourceforge.argparse4j.inf.ArgumentParser parser;

    public ArgumentParser() {
        parser = ArgumentParsers.newArgumentParser("Bitsquare")
                .defaultHelp(true)
                .description("Bitsquare - The decentralized bitcoin exchange");

        // Args for seed node config
        parser.addArgument("-d", "--" + BOOTSTRAP_NODE_ID_KEY)
                .help("Seed node ID");
        parser.addArgument("-s", "--" + BOOTSTRAP_NODE_IP_KEY)
                .help("Seed node IP");
        parser.addArgument("-p", "--" + BOOTSTRAP_NODE_PORT_KEY)
                .help("Seed node port");

        // Args for app config
        parser.addArgument("-n", "--" + NAME_FLAG)
                .help("Name to append to default application name");
    }

    public Namespace parseArgs(String... args) {
        try {
            return parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
            return null;
        }
    }
}
