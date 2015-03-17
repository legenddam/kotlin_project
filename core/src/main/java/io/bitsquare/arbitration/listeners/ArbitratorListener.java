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

package io.bitsquare.arbitration.listeners;

import io.bitsquare.arbitration.Arbitrator;

import java.util.List;

// Arbitration is not much developed yet
public interface ArbitratorListener {
    void onArbitratorAdded(Arbitrator arbitrator);

    void onArbitratorsReceived(List<Arbitrator> arbitrators);

    void onArbitratorRemoved(Arbitrator arbitrator);
}