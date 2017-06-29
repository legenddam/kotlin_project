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

package io.bisq.core.payment;

import com.google.protobuf.Message;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistableList;
import io.bisq.core.proto.CoreProtoResolver;
import io.bisq.generated.protobuffer.PB;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PaymentAccountList extends PersistableList<PaymentAccount> {

    public PaymentAccountList(List<PaymentAccount> list) {
        super(list);
    }

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder()
                .setPaymentAccountList(PB.PaymentAccountList.newBuilder()
                        .addAllPaymentAccount(getList().stream().map(PaymentAccount::toProtoMessage).collect(Collectors.toList())))
                .build();
    }

    public static PersistableEnvelope fromProto(PB.PaymentAccountList proto, CoreProtoResolver coreProtoResolver) {
        return new PaymentAccountList(new ArrayList<>(proto.getPaymentAccountList().stream()
                .map(e -> PaymentAccount.fromProto(e, coreProtoResolver))
                .collect(Collectors.toList())));
    }
}
