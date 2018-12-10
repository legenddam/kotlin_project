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

package bisq.monitor.metric;

import java.util.Properties;

/**
 * A dummy metric for development purposes.
 * 
 * @author Florian Reimair
 */
public class Dummy extends Metric {

    public Dummy() {
        super();
    }

    @Override
    public void configure(Properties properties) {
        super.configure(properties);

        System.out.println(this.configuration.toString());
        // TODO check if we need to restart this Metric
    }

    @Override
    protected void execute() {
        System.out.println(this.getName() + " running");
    }

}
