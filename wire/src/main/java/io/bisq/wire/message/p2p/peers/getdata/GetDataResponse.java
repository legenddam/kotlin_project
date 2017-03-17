package io.bisq.wire.message.p2p.peers.getdata;

import io.bisq.common.app.Capabilities;
import io.bisq.common.app.Version;
import io.bisq.common.wire.proto.Messages;
import io.bisq.wire.message.p2p.SupportedCapabilitiesMessage;
import io.bisq.wire.payload.p2p.storage.ProtectedMailboxStorageEntry;
import io.bisq.wire.payload.p2p.storage.ProtectedStorageEntry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;

public final class GetDataResponse implements SupportedCapabilitiesMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private final int messageVersion = Version.getP2PMessageVersion();

    public final HashSet<ProtectedStorageEntry> dataSet;
    public final int requestNonce;
    public final boolean isGetUpdatedDataResponse;

    @Nullable
    private final ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public GetDataResponse(HashSet<ProtectedStorageEntry> dataSet, int requestNonce, boolean isGetUpdatedDataResponse) {
        this.dataSet = dataSet;
        this.requestNonce = requestNonce;
        this.isGetUpdatedDataResponse = isGetUpdatedDataResponse;
    }

    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public Messages.Envelope toProtoBuf() {
        Messages.GetDataResponse.Builder builder = Messages.GetDataResponse.newBuilder();
        builder.addAllDataSet(
                dataSet.stream()
                        .map(protectedStorageEntry -> {
                            Messages.ProtectedStorageEntryOrProtectedMailboxStorageEntry.Builder builder1 =
                                    Messages.ProtectedStorageEntryOrProtectedMailboxStorageEntry.newBuilder();
                            if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry) {
                                builder1.setProtectedMailboxStorageEntry((Messages.ProtectedMailboxStorageEntry) protectedStorageEntry.toProtoBuf());
                            } else {
                                builder1.setProtectedStorageEntry((Messages.ProtectedStorageEntry) protectedStorageEntry.toProtoBuf());
                            }
                            return builder1.build();
                        })
                        .collect(Collectors.toList()))
                .setRequestNonce(requestNonce)
                .setIsGetUpdatedDataResponse(isGetUpdatedDataResponse);
        return Messages.Envelope.newBuilder().setGetDataResponse(builder).build();
    }


    @Override
    public String toString() {
        return "GetDataResponse{" +
                "dataSet.size()=" + dataSet.size() +
                ", isGetUpdatedDataResponse=" + isGetUpdatedDataResponse +
                ", requestNonce=" + requestNonce +
                ", supportedCapabilities=" + supportedCapabilities +
                ", messageVersion=" + messageVersion +
                '}';
    }
}
