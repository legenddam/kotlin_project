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

package io.bisq.core.trade.protocol;

import com.google.protobuf.Message;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.persistable.PersistablePayload;
import io.bisq.core.btc.data.RawTransactionInput;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.generated.protobuffer.PB;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.List;

@Slf4j
@Getter
@Setter
public final class TradingPeer implements PersistablePayload {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private String accountId;
    private PaymentAccountPayload paymentAccountPayload;
    // private Coin payoutAmount;
    private String payoutAddressString;
    // private byte[] signature;
    private String contractAsJson;
    private String contractSignature;
    private byte[] signature;
    private PubKeyRing pubKeyRing;
    private byte[] multiSigPubKey;
    private List<RawTransactionInput> rawTransactionInputs;
    private long changeOutputValue;
    @Nullable
    private String changeOutputAddress;

    public TradingPeer() {
    }

    @Override
    public Message toProtoMessage() {
        // TODO
        // nullable
        // changeOutputAddress
        //  .setRawTransactionInputs(rawTransactionInputs)
        // .setPaymentAccountPayload(paymentAccountPayload.toProto())
        // .setSignature(signature)
        // .setMultiSigPubKey(multiSigPubKey)
        return PB.TradingPeer.newBuilder()
                .setAccountId(accountId)
               /* .setPaymentAccountPayload(paymentAccountPayload.toProto())*/
                .setPayoutAddressString(payoutAddressString)
                .setContractAsJson(contractAsJson)
                .setContractSignature(contractSignature)
              /*  .setSignature(signature)*/
                .setPubKeyRing(pubKeyRing.toProtoMessage())
              /*  .setMultiSigPubKey(multiSigPubKey)*/
                /*.setRawTransactionInputs(rawTransactionInputs)*/
                .setChangeOutputValue(changeOutputValue)
               /* .setChangeOutputAddress(changeOutputAddress)*/
                .build();
    }
}
