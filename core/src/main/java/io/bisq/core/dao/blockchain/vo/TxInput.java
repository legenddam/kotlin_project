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

package io.bisq.core.dao.blockchain.vo;

import io.bisq.common.app.Version;
import io.bisq.common.persistable.PersistablePayload;
import lombok.Data;
import lombok.experimental.Delegate;

@Data
public class TxInput implements PersistablePayload {
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    @Delegate
    private final TxInputVo txInputVo;

    private TxOutput connectedTxOutput;

    public TxInput(TxInputVo txInputVo) {
        this.txInputVo = txInputVo;
    }

    public void reset() {
        connectedTxOutput = null;
    }


    @Override
    public String toString() {
        return "TxInput{" +
                "\n     txId=" + getTxId() +
                ",\n     txOutputIndex=" + getTxOutputIndex() +
                ",\n     txOutput='" + connectedTxOutput + '\'' +
                "\n}";
    }
}
