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

package io.bisq.core.arbitration;

import com.google.protobuf.Message;
import io.bisq.common.persistable.PersistableEnvelope;
import io.bisq.common.storage.Storage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@ToString
/**
 * Holds a List of Dispute objects.
 *
 * Calls to the List are delegated because this class intercepts the add/remove calls so changes
 * can be saved to disc.
 */
public final class DisputeList implements PersistableEnvelope {
    final transient private Storage<DisputeList> storage;
    final private List<Dispute> disputeList = new ArrayList<>();

    public DisputeList(Storage<DisputeList> storage) {
        this.storage = storage;

        DisputeList persisted = storage.initAndGetPersisted(this);
        if (persisted != null) {
            disputeList.addAll(persisted.stream().collect(Collectors.toList()));
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    public ObservableList<Dispute> getObservableList() {
        return FXCollections.observableArrayList(disputeList);
    }

    public boolean add(Dispute dispute) {
        if (!disputeList.contains(dispute)) {
            boolean changed = disputeList.add(dispute);
            if (changed)
                storage.queueUpForSave();
            return changed;
        } else {
            return false;
        }
    }

    public boolean remove(Object dispute) {
        boolean changed = disputeList.remove(dispute);
        if (changed)
            storage.queueUpForSave();
        return changed;
    }

    //// Delegate methods to List implementation /////

    public int size() {
        return disputeList.size();
    }

    public boolean isEmpty() {
        return disputeList.isEmpty();
    }

    public boolean contains(Object o) {
        return disputeList.contains(o);
    }

    public Stream<Dispute> stream() {
        return disputeList.stream();
    }

    // TODO not impl yet
    @Override
    public Message toProtoMessage() {
        return null;
    }
}
