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

package io.bisq.protocol.availability.tasks;

import io.bisq.common.taskrunner.Task;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.network_messages.availability.OfferAvailabilityRequest;
import io.bisq.network_messages.p2p.messaging.SendDirectMessageListener;
import io.bisq.network_messages.trade.offer.payload.OfferPayload;
import io.bisq.p2p.protocol.availability.OfferAvailabilityModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendOfferAvailabilityRequest extends Task<OfferAvailabilityModel> {
    private static final Logger log = LoggerFactory.getLogger(SendOfferAvailabilityRequest.class);

    public SendOfferAvailabilityRequest(TaskRunner taskHandler, OfferAvailabilityModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            model.p2PService.sendEncryptedDirectMessage(model.getPeerNodeAddress(),
                    model.offer.getPubKeyRing(),
                    new OfferAvailabilityRequest(model.offer.getId(), model.pubKeyRing, model.getTakersTradePrice()),
                    new SendDirectMessageListener() {
                        @Override
                        public void onArrived() {
                            complete();
                        }

                        @Override
                        public void onFault() {
                            model.offer.setState(OfferPayload.State.OFFERER_OFFLINE);
                        }
                    }
            );
        } catch (Throwable t) {
            model.offer.setErrorMessage("An error occurred.\n" +
                    "Error message:\n"
                    + t.getMessage());

            failed(t);
        }
    }
}

