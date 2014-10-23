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

package io.bitsquare.util;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class BitsquareArgumentParser {

    public static String SEED_FLAG = "seed";
    public static String PORT_FLAG = "port";
    public static Integer PORT_DEFAULT = 5000;
    public static String INFHINT_FLAG = "interface";
    public static String NAME_FLAG = "name";

    private final ArgumentParser parser;

    public BitsquareArgumentParser() {
        parser = ArgumentParsers.newArgumentParser("BitSquare")
                .defaultHelp(true)
                .description("BitSquare decentralized bitcoin exchange.");
        parser.addArgument("-s", "--" + SEED_FLAG)
                .action(Arguments.storeTrue())
                .help("Start in DHT seed mode, no UI.");
        parser.addArgument("-p", "--"+PORT_FLAG)
                .setDefault(PORT_DEFAULT)
                .help("IP port to listen on.");
        parser.addArgument("-i", "--"+INFHINT_FLAG)
                .help("interface to listen on.");
        parser.addArgument("-n", "--"+NAME_FLAG)
                .help("Append name to application name.");
    }

    public Namespace parseArgs(String... args) throws ArgumentParserException {
        return parser.parseArgs(args);
    }

    public void handleError(ArgumentParserException e) {
        parser.handleError(e);
    }
}
