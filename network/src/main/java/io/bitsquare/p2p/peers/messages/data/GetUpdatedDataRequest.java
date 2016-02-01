package io.bitsquare.p2p.peers.messages.data;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.messages.SendersNodeAddressMessage;

public final class GetUpdatedDataRequest implements SendersNodeAddressMessage, GetDataRequest {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    private final int networkId = Version.getNetworkId();
    private final NodeAddress senderNodeAddress;
    private final long nonce;

    public GetUpdatedDataRequest(NodeAddress senderNodeAddress, long nonce) {
        this.senderNodeAddress = senderNodeAddress;
        this.nonce = nonce;
    }

    @Override
    public long getNonce() {
        return nonce;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return senderNodeAddress;
    }

    @Override
    public int networkId() {
        return networkId;
    }

    @Override
    public String toString() {
        return "GetUpdatedDataRequest{" +
                "networkId=" + networkId +
                ", senderNodeAddress=" + senderNodeAddress +
                ", nonce=" + nonce +
                '}';
    }
}
