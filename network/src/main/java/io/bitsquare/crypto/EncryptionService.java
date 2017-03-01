/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.crypto;

import com.google.protobuf.InvalidProtocolBufferException;
import io.bitsquare.common.crypto.*;
import io.bitsquare.common.wire.proto.Messages;
import io.bitsquare.messages.Message;
import io.bitsquare.p2p.network.ProtoBufferUtilities;

import javax.crypto.SecretKey;
import javax.inject.Inject;
import java.security.PrivateKey;

import static io.bitsquare.common.crypto.Encryption.decryptPayloadWithHmac;
import static io.bitsquare.common.crypto.Encryption.decryptSecretKey;

public class EncryptionService {
    private final KeyRing keyRing;

    @Inject
    public EncryptionService(KeyRing keyRing) {
        this.keyRing = keyRing;
    }

    public SealedAndSigned encryptAndSign(PubKeyRing pubKeyRing, Message message) throws CryptoException {
        return Encryption.encryptHybridWithSignature(message, keyRing.getSignatureKeyPair(), pubKeyRing.getEncryptionPubKey());
    }

    /**
     * @param sealedAndSigned The sealedAndSigned object.
     * @param privateKey      The private key for decryption
     * @return A DecryptedPayloadWithPubKey object.
     * @throws CryptoException
     */
    public static DecryptedDataTuple decryptHybridWithSignature(SealedAndSigned sealedAndSigned, PrivateKey privateKey) throws CryptoException {
        SecretKey secretKey = decryptSecretKey(sealedAndSigned.encryptedSecretKey, privateKey);
        boolean isValid = Sig.verify(sealedAndSigned.sigPublicKey,
                Hash.getHash(sealedAndSigned.encryptedPayloadWithHmac),
                sealedAndSigned.signature);
        if (!isValid)
            throw new CryptoException("Signature verification failed.");

        Message decryptedPayload = null;
        try {
            decryptedPayload = ProtoBufferUtilities
                    .fromProtoBuf(Messages.Envelope.parseFrom(decryptPayloadWithHmac(sealedAndSigned.encryptedPayloadWithHmac, secretKey))).get();
        } catch (InvalidProtocolBufferException e) {
            throw new CryptoException("Unable to parse protobuffer message.", e);
        }
        return new DecryptedDataTuple(decryptedPayload, sealedAndSigned.sigPublicKey);
    }

    public DecryptedMsgWithPubKey decryptAndVerify(SealedAndSigned sealedAndSigned) throws CryptoException {
        DecryptedDataTuple decryptedDataTuple = decryptHybridWithSignature(sealedAndSigned,
                keyRing.getEncryptionKeyPair().getPrivate());
        if (decryptedDataTuple.payload instanceof Message) {
            return new DecryptedMsgWithPubKey((Message) decryptedDataTuple.payload,
                    decryptedDataTuple.sigPublicKey);
        } else {
            throw new CryptoException("decryptedPayloadWithPubKey.payload is not instance of Message");
        }
    }
}

