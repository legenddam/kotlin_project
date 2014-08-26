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

package io.bitsquare.btc;

import com.google.bitcoin.core.*;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeePolicy {
    public static final Coin TX_FEE = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;
    public static final Coin ACCOUNT_REGISTRATION_FEE = Coin.CENT; // 0.01
    public static final Coin CREATE_OFFER_FEE = Coin.MILLICOIN; // 0.001
    public static final Coin TAKE_OFFER_FEE = CREATE_OFFER_FEE;
    private static final Logger log = LoggerFactory.getLogger(FeePolicy.class);
    private static final String registrationFeeAddress = "mvkDXt4QmN4Nq9dRUsRigBCaovde9nLkZR";
    private static final String createOfferFeeAddress = "n2upbsaKAe4PD3cc4JfS7UCqPC5oNd7Ckg";
    private static final String takeOfferFeeAddress = "n2upbsaKAe4PD3cc4JfS7UCqPC5oNd7Ckg";

    private final NetworkParameters params;

    @Inject
    public FeePolicy(NetworkParameters params) {
        this.params = params;
    }

    //TODO other users or dev address? use donation option list?
    // (dev, other users, wikileaks, tor, sub projects (bitcoinj, tomp2p,...)...)

    public Address getAddressForRegistrationFee() {
        try {
            return new Address(params, registrationFeeAddress);
        } catch (AddressFormatException e) {
            e.printStackTrace();
            return null;
        }
    }

    //TODO get address form arbitrator list

    public Address getAddressForCreateOfferFee() {
        try {
            return new Address(params, createOfferFeeAddress);
        } catch (AddressFormatException e) {
            e.printStackTrace();
            return null;
        }
    }

    //TODO get address form the intersection of  both traders arbitrator lists

    public Address getAddressForTakeOfferFee() {
        try {
            return new Address(params, takeOfferFeeAddress);
        } catch (AddressFormatException e) {
            e.printStackTrace();
            return null;
        }
    }
}
