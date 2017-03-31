package io.bisq.network.p2p.peers.keepalive.messages;

import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.common.persistance.Msg;

public final class Pong extends KeepAliveMsg {
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
    public PB.Envelope toProto() {
        PB.Envelope.Builder baseEnvelope = Msg.getBaseEnvelope();
        return baseEnvelope.setPong(PB.Pong.newBuilder().setRequestNonce(requestNonce)).build();
    }
}
