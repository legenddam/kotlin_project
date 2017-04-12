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

package io.bisq.core.dao.blockchain;

import io.bisq.common.app.Version;
import io.bisq.common.util.JsonExclude;
import lombok.Value;

import java.io.Serializable;

@Value
public class SpendInfo implements Serializable {
    @JsonExclude
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
  
    private final long blockHeight;
    private final String txId;
    private final int inputIndex;
}
