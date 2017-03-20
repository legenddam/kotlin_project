package io.bisq.wire.message.p2p.peers.keepalive;

import io.bisq.common.app.Version;
import io.bisq.wire.message.ToProtoBuffer;
import io.bisq.wire.proto.Messages;

public final class Pong extends KeepAliveMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final int requestNonce;

    public Pong(int requestNonce) {
        this.requestNonce = requestNonce;
    }

    @Override
    public String toString() {
        return "Pong{" +
                "requestNonce=" + requestNonce +
                "} " + super.toString();
    }

    @Override
    public Messages.Envelope toProtoBuf() {
        Messages.Envelope.Builder baseEnvelope = ToProtoBuffer.getBaseEnvelope();
        return baseEnvelope.setPong(Messages.Pong.newBuilder().setRequestNonce(requestNonce)).build();
    }
}
