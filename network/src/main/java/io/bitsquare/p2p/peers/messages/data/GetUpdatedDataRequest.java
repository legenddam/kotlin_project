package io.bitsquare.p2p.peers.messages.data;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.messages.SendersNodeAddressMessage;

public final class GetUpdatedDataRequest implements SendersNodeAddressMessage, GetDataRequest {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final int messageVersion = Version.getP2PMessageVersion();
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
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String toString() {
        return "GetUpdatedDataRequest{" +
                "messageVersion=" + messageVersion +
                ", senderNodeAddress=" + senderNodeAddress +
                ", nonce=" + nonce +
                '}';
    }
}
