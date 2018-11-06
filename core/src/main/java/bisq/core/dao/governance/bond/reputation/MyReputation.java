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

package bisq.core.dao.governance.bond.reputation;

import bisq.core.dao.governance.bond.BondedAsset;

import bisq.common.crypto.Hash;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

@Immutable
@Value
@Slf4j
public final class MyReputation implements PersistablePayload, NetworkPayload, BondedAsset {
    private final String uid;
    private final byte[] salt;
    private final transient byte[] hash; // not persisted as it is derived from salt. Stored for caching purpose only.

    public MyReputation(byte[] salt) {
        this(UUID.randomUUID().toString(), salt);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private MyReputation(String uid, byte[] salt) {
        this.uid = uid;
        this.salt = salt;
        this.hash = Hash.getSha256Ripemd160hash(salt);
    }

    @Override
    public PB.MyReputation toProtoMessage() {
        return PB.MyReputation.newBuilder()
                .setUid(uid)
                .setSalt(ByteString.copyFrom(salt))
                .build();
    }

    public static MyReputation fromProto(PB.MyReputation proto) {
        return new MyReputation(proto.getUid(), proto.getSalt().toByteArray());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BondedAsset implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public byte[] getHash() {
        return hash;
    }

    @Override
    public String getDisplayString() {
        return Utilities.bytesAsHexString(hash);
    }

    @Override
    public String getUid() {
        return Utilities.bytesAsHexString(hash);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MyReputation)) return false;
        if (!super.equals(o)) return false;
        MyReputation that = (MyReputation) o;
        return Objects.equals(uid, that.uid) &&
                Arrays.equals(salt, that.salt);
    }

    @Override
    public int hashCode() {

        int result = Objects.hash(super.hashCode(), uid);
        result = 31 * result + Arrays.hashCode(salt);
        return result;
    }
///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        return "MyReputation{" +
                "\n     uid=" + uid +
                "\n     salt=" + Utilities.bytesAsHexString(salt) +
                "\n     hash=" + Utilities.bytesAsHexString(hash) +
                "\n}";
    }
}
