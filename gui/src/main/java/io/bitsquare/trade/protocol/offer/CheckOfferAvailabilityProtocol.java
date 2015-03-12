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

package io.bitsquare.trade.protocol.offer;

import io.bitsquare.network.Message;
import io.bitsquare.network.Peer;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.protocol.offer.messages.ReportOfferAvailabilityMessage;
import io.bitsquare.trade.protocol.offer.tasks.GetPeerAddress;
import io.bitsquare.trade.protocol.offer.tasks.RequestIsOfferAvailable;
import io.bitsquare.util.tasks.TaskRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.nonEmptyStringOf;

public class CheckOfferAvailabilityProtocol {
    private static final Logger log = LoggerFactory.getLogger(CheckOfferAvailabilityProtocol.class);

    private CheckOfferAvailabilityModel model;
    private boolean isCanceled;
    private TaskRunner<CheckOfferAvailabilityModel> sequence;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public CheckOfferAvailabilityProtocol(CheckOfferAvailabilityModel model) {
        this.model = model;
    }

    public void cleanup() {
        model.getTradeMessageService().removeMessageHandler(this::handleMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onCheckOfferAvailability() {
        model.getTradeMessageService().addMessageHandler(this::handleMessage);

        sequence = new TaskRunner<>(model,
                () -> {
                    log.debug("sequence at onCheckOfferAvailability completed");
                },
                (message, throwable) -> {
                    log.error(message);
                }
        );
        sequence.addTasks(
                GetPeerAddress.class,
                RequestIsOfferAvailable.class
        );
        sequence.run();
    }

    public void cancel() {
        isCanceled = true;
        sequence.cancel();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleMessage(Message message, Peer sender) {
        if (!isCanceled) {
            if (message instanceof ReportOfferAvailabilityMessage) {
                ReportOfferAvailabilityMessage reportOfferAvailabilityMessage = (ReportOfferAvailabilityMessage) message;
                nonEmptyStringOf(reportOfferAvailabilityMessage.getOfferId());

                if (model.getOffer().getState() != Offer.State.OFFER_REMOVED) {
                    if (reportOfferAvailabilityMessage.isOfferOpen())
                        model.getOffer().setState(Offer.State.OFFER_AVAILABLE);
                    else
                        model.getOffer().setState(Offer.State.OFFER_NOT_AVAILABLE);
                }
            }
        }

        model.getResultHandler().handleResult();
    }
}
