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

package io.bitsquare.dao.vote;

import io.bitsquare.app.Version;
import io.bitsquare.common.persistance.Persistable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

//TODO if sent over wire make final
public class VoteItem implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(VoteItem.class);
    //  public final String version;
    public final VotingType votingType;
    @Nullable
    public final String name;
    public final long defaultValue;
    protected boolean hasVoted;

    public byte getValue() {
        return value;
    }

    private byte value;

    public VoteItem(VotingType votingType, @Nullable String name, byte value, @Nullable VotingDefaultValues votingDefaultValues) {
        this.votingType = votingType;
        this.name = name;
        this.value = value;
        this.defaultValue = votingDefaultValues != null ? votingDefaultValues.getValueByVotingType(votingType) : 0;
    }

    public VoteItem(VotingType votingType, String name, VotingDefaultValues votingDefaultValues) {
        this(votingType, name, (byte) 0x00, votingDefaultValues);
    }

    public long getAdjustedValue(long originalValue, int change) {
        checkArgument(change < 255 && change > -1,
                "Range for change can be 0 to 254. 255 is not supported as we want a 0 value in the middle");
        double fact = (change - 127) / 127d;
        return (long) (originalValue * Math.pow(10, fact));
    }

    // We return the change parameter (0-254)
    public int getChange(long originalValue, long newValue) {
        return (int) Math.round(Math.log10((double) newValue / (double) originalValue) * 127 + 127);
    }


    public void setValue(byte value) {
        this.value = value;
        this.hasVoted = true;
    }

    public boolean hasVoted() {
        return hasVoted;
    }

    @Override
    public String toString() {
        return "VoteItem{" +
                "code=" + votingType +
                ", name='" + name + '\'' +
                ", value=" + value +
                '}';
    }
}
