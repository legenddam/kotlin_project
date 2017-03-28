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

package io.bisq.core.trade.messages;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.core.btc.data.RawTransactionInput;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.Msg;
import io.bisq.network.p2p.NodeAddress;
import lombok.EqualsAndHashCode;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@EqualsAndHashCode(callSuper = true)
@Immutable
public final class PayDepositRequest extends TradeMsg {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final long tradeAmount;
    public final long tradePrice;
    public final byte[] takerMultiSigPubKey;
    public final long txFee;
    public final long takeOfferFee;
    public final List<RawTransactionInput> rawTransactionInputs;
    public final long changeOutputValue;
    @Nullable
    public final String changeOutputAddress;
    public final String takerPayoutAddressString;
    public final PubKeyRing takerPubKeyRing;
    public final PaymentAccountPayload takerPaymentAccountPayload;
    public final String takerAccountId;
    public final String takeOfferFeeTxId;
    public final List<NodeAddress> acceptedArbitratorNodeAddresses;
    public final List<NodeAddress> acceptedMediatorNodeAddresses;
    public final NodeAddress arbitratorNodeAddress;
    public final NodeAddress mediatorNodeAddress;
    private final NodeAddress senderNodeAddress;

    public PayDepositRequest(NodeAddress senderNodeAddress,
                             String tradeId,
                             long tradeAmount,
                             long tradePrice,
                             long txFee,
                             long takeOfferFee,
                             List<RawTransactionInput> rawTransactionInputs,
                             long changeOutputValue,
                             String changeOutputAddress,
                             byte[] takerMultiSigPubKey,
                             String takerPayoutAddressString,
                             PubKeyRing takerPubKeyRing,
                             PaymentAccountPayload takerPaymentAccountPayload,
                             String takerAccountId,
                             String takeOfferFeeTxId,
                             List<NodeAddress> acceptedArbitratorNodeAddresses,
                             List<NodeAddress> acceptedMediatorNodeAddresses,
                             NodeAddress arbitratorNodeAddress,
                             NodeAddress mediatorNodeAddress) {
        super(tradeId);
        this.senderNodeAddress = senderNodeAddress;
        this.tradeAmount = tradeAmount;
        this.tradePrice = tradePrice;
        this.txFee = txFee;
        this.takeOfferFee = takeOfferFee;
        this.rawTransactionInputs = rawTransactionInputs;
        this.changeOutputValue = changeOutputValue;
        this.changeOutputAddress = changeOutputAddress;
        this.takerPayoutAddressString = takerPayoutAddressString;
        this.takerPubKeyRing = takerPubKeyRing;
        this.takerMultiSigPubKey = takerMultiSigPubKey;
        this.takerPaymentAccountPayload = takerPaymentAccountPayload;
        this.takerAccountId = takerAccountId;
        this.takeOfferFeeTxId = takeOfferFeeTxId;
        this.acceptedArbitratorNodeAddresses = acceptedArbitratorNodeAddresses;
        this.acceptedMediatorNodeAddresses = acceptedMediatorNodeAddresses;
        this.arbitratorNodeAddress = arbitratorNodeAddress;
        this.mediatorNodeAddress = mediatorNodeAddress;
    }

    @Override
    public PB.Envelope toProto() {
        PB.Envelope.Builder baseEnvelope = Msg.getBaseEnvelope();
        PB.PayDepositRequest.Builder builderForValue = PB.PayDepositRequest.newBuilder()
                .setTradeId(tradeId)
                .setTradeAmount(tradeAmount)
                .setTradePrice(tradePrice)
                .setTakerMultiSigPubKey(ByteString.copyFrom(takerMultiSigPubKey))
                .setTxFee(txFee)
                .setTakeOfferFee(takeOfferFee)
                .addAllRawTransactionInputs(rawTransactionInputs.stream()
                        .map(rawTransactionInput -> rawTransactionInput.toProto()).collect(Collectors.toList()))
                .setChangeOutputValue(changeOutputValue)
                .setTakerPayoutAddressString(takerPayoutAddressString)
                .setTakerPubKeyRing(takerPubKeyRing.toProto())
                .setTakerPaymentAccountPayload((PB.PaymentAccountPayload) takerPaymentAccountPayload.toProto())
                .setTakerAccountId(takerAccountId)
                .setTakeOfferFeeTxId(takeOfferFeeTxId)
                .addAllAcceptedArbitratorNodeAddresses(acceptedArbitratorNodeAddresses.stream()
                        .map(nodeAddress -> nodeAddress.toProto()).collect(Collectors.toList()))
                .addAllAcceptedMediatorNodeAddresses(acceptedMediatorNodeAddresses.stream()
                        .map(nodeAddress -> nodeAddress.toProto()).collect(Collectors.toList()))
                .setArbitratorNodeAddress(arbitratorNodeAddress.toProto())
                .setMediatorNodeAddress(mediatorNodeAddress.toProto())
                .setSenderNodeAddress(senderNodeAddress.toProto());
        Optional.ofNullable(changeOutputAddress).ifPresent(builderForValue::setChangeOutputAddress);
        return baseEnvelope.setPayDepositRequest(builderForValue).build();
    }

    // Use Hex for bytes
    @Override
    public String toString() {
        return "PayDepositRequest{" +
                "tradeAmount=" + tradeAmount +
                ", tradePrice=" + tradePrice +
                ", takerMultiSigPubKey=" + Hex.toHexString(takerMultiSigPubKey) +
                ", txFee=" + txFee +
                ", takeOfferFee=" + takeOfferFee +
                ", rawTransactionInputs=" + rawTransactionInputs +
                ", changeOutputValue=" + changeOutputValue +
                ", changeOutputAddress='" + changeOutputAddress + '\'' +
                ", takerPayoutAddressString='" + takerPayoutAddressString + '\'' +
                ", takerPubKeyRing=" + takerPubKeyRing +
                ", takerPaymentAccountPayload=" + takerPaymentAccountPayload +
                ", takerAccountId='" + takerAccountId + '\'' +
                ", takeOfferFeeTxId='" + takeOfferFeeTxId + '\'' +
                ", acceptedArbitratorNodeAddresses=" + acceptedArbitratorNodeAddresses +
                ", acceptedMediatorNodeAddresses=" + acceptedMediatorNodeAddresses +
                ", arbitratorNodeAddress=" + arbitratorNodeAddress +
                ", mediatorNodeAddress=" + mediatorNodeAddress +
                ", senderNodeAddress=" + senderNodeAddress +
                "} " + super.toString();
    }
}
