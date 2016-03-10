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

package io.bitsquare.trade.offer;

import io.bitsquare.app.Version;
import io.bitsquare.btc.Restrictions;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.common.util.JsonExclude;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.storage.payload.RequiresOwnerIsOnlinePayload;
import io.bitsquare.p2p.storage.payload.StoragePayload;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.trade.protocol.availability.OfferAvailabilityModel;
import io.bitsquare.trade.protocol.availability.OfferAvailabilityProtocol;
import javafx.beans.property.*;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class Offer implements StoragePayload, RequiresOwnerIsOnlinePayload {
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    // That object is sent over the wire, so we need to take care of version compatibility.
    @JsonExclude
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    @JsonExclude
    private static final Logger log = LoggerFactory.getLogger(Offer.class);
    public static final long TTL = TimeUnit.SECONDS.toMillis(4 * 60);
    public final static String TAC_OFFERER = "When placing that offer I accept that anyone who fulfills my conditions can " +
            "take that offer.";
    public static final String TAC_TAKER = "With taking the offer I commit to the trade conditions as defined.";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enums
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum Direction {BUY, SELL}

    public enum State {
        UNDEFINED,
        OFFER_FEE_PAID,
        AVAILABLE,
        NOT_AVAILABLE,
        REMOVED,
        OFFERER_OFFLINE
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////


    // Fields for filtering offers
    private final Direction direction;
    private final String currencyCode;
    // payment method
    private final String paymentMethodName;
    @Nullable
    private final String countryCode;
    @Nullable
    private final ArrayList<String> acceptedCountryCodes;

    @Nullable
    private final String bankId;
    @Nullable
    private final ArrayList<String> acceptedBankIds;

    private final ArrayList<NodeAddress> arbitratorNodeAddresses;


    private final String id;
    private final long date;
    private final long protocolVersion;
    private final long fiatPrice;
    private final long amount;
    private final long minAmount;
    private final NodeAddress offererNodeAddress;
    @JsonExclude
    private final PubKeyRing pubKeyRing;
    private final String offererPaymentAccountId;

    // Mutable property. Has to be set before offer is save in P2P network as it changes the objects hash!
    private String offerFeePaymentTxID;

    @JsonExclude
    transient private State state = State.UNDEFINED;
    // Those state properties are transient and only used at runtime! 
    // don't access directly as it might be null; use getStateProperty() which creates an object if not instantiated
    @JsonExclude
    transient private ObjectProperty<State> stateProperty = new SimpleObjectProperty<>(state);
    @JsonExclude
    @Nullable
    transient private OfferAvailabilityProtocol availabilityProtocol;
    @JsonExclude
    transient private StringProperty errorMessageProperty = new SimpleStringProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer(String id,
                 NodeAddress offererNodeAddress,
                 PubKeyRing pubKeyRing,
                 Direction direction,
                 long fiatPrice,
                 long amount,
                 long minAmount,
                 String currencyCode,
                 ArrayList<NodeAddress> arbitratorNodeAddresses,
                 String paymentMethodName,
                 String offererPaymentAccountId,
                 @Nullable String countryCode,
                 @Nullable ArrayList<String> acceptedCountryCodes,
                 @Nullable String bankId,
                 @Nullable ArrayList<String> acceptedBankIds) {
        this.id = id;
        this.offererNodeAddress = offererNodeAddress;
        this.pubKeyRing = pubKeyRing;
        this.direction = direction;
        this.fiatPrice = fiatPrice;
        this.amount = amount;
        this.minAmount = minAmount;
        this.currencyCode = currencyCode;
        this.arbitratorNodeAddresses = arbitratorNodeAddresses;
        this.paymentMethodName = paymentMethodName;
        this.offererPaymentAccountId = offererPaymentAccountId;
        this.countryCode = countryCode;
        this.acceptedCountryCodes = acceptedCountryCodes;
        this.bankId = bankId;
        this.acceptedBankIds = acceptedBankIds;

        protocolVersion = Version.TRADE_PROTOCOL_VERSION;

        date = new Date().getTime();
        setState(State.UNDEFINED);
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            stateProperty = new SimpleObjectProperty<>(State.UNDEFINED);

            // we don't need to fill it as the error message is only relevant locally, so we don't store it in the transmitted object
            errorMessageProperty = new SimpleStringProperty();
        } catch (Throwable t) {
            log.error("Cannot be deserialized." + t.getMessage());
        }
    }

    @Override
    public NodeAddress getOwnerNodeAddress() {
        return offererNodeAddress;
    }

    public void validate() {
        checkNotNull(getAmount(), "Amount is null");
        checkNotNull(getArbitratorNodeAddresses(), "Arbitrator is null");
        checkNotNull(getDate(), "CreationDate is null");
        checkNotNull(getCurrencyCode(), "Currency is null");
        checkNotNull(getDirection(), "Direction is null");
        checkNotNull(getId(), "Id is null");
        checkNotNull(getPubKeyRing(), "pubKeyRing is null");
        checkNotNull(getMinAmount(), "MinAmount is null");
        checkNotNull(getPrice(), "Price is null");

        checkArgument(getMinAmount().compareTo(Restrictions.MIN_TRADE_AMOUNT) >= 0, "MinAmount is less then "
                + Restrictions.MIN_TRADE_AMOUNT.toFriendlyString());
        checkArgument(getAmount().compareTo(getPaymentMethod().getMaxTradeLimitInBitcoin()) <= 0, "Amount is larger then "
                + getPaymentMethod().getMaxTradeLimitInBitcoin().toFriendlyString());
        checkArgument(getAmount().compareTo(getMinAmount()) >= 0, "MinAmount is larger then Amount");

        checkArgument(getPrice().isPositive(), "Price is not a positive value");
        // TODO check upper and lower bounds for fiat
    }

    public void resetState() {
        setState(State.UNDEFINED);
    }

    public boolean isMyOffer(KeyRing keyRing) {
        return getPubKeyRing().equals(keyRing.getPubKeyRing());
    }

    public Fiat getVolumeByAmount(Coin amount) {
        if (fiatPrice != 0 && amount != null && !amount.isZero())
            return new ExchangeRate(Fiat.valueOf(currencyCode, fiatPrice)).coinToFiat(amount);
        else
            return null;
    }

    public Fiat getOfferVolume() {
        return getVolumeByAmount(getAmount());
    }

    public Fiat getMinOfferVolume() {
        return getVolumeByAmount(getMinAmount());
    }

    public String getReferenceText() {
        return id.substring(0, Math.min(8, id.length()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Availability
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void checkOfferAvailability(OfferAvailabilityModel model, ResultHandler resultHandler) {
        availabilityProtocol = new OfferAvailabilityProtocol(model,
                () -> {
                    cancelAvailabilityRequest();
                    resultHandler.handleResult();
                },
                (errorMessage) -> {
                    availabilityProtocol.cancel();
                    log.error(errorMessage);
                });
        availabilityProtocol.sendOfferAvailabilityRequest();
    }


    public void cancelAvailabilityRequest() {
        if (availabilityProtocol != null)
            availabilityProtocol.cancel();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setState(State state) {
        this.state = state;
        stateProperty().set(state);
    }

    public void setOfferFeePaymentTxID(String offerFeePaymentTxID) {
        this.offerFeePaymentTxID = offerFeePaymentTxID;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessageProperty.set(errorMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return pubKeyRing.getSignaturePubKey();
    }

    public long getProtocolVersion() {
        return protocolVersion;
    }

    public String getId() {
        return id;
    }

    public String getShortId() {
        return id.substring(0, Math.min(8, id.length()));
    }

    public NodeAddress getOffererNodeAddress() {
        return offererNodeAddress;
    }

    public PubKeyRing getPubKeyRing() {
        return pubKeyRing;
    }

    public Fiat getPrice() {
        return Fiat.valueOf(currencyCode, fiatPrice);
    }

    public Coin getAmount() {
        return Coin.valueOf(amount);
    }

    public Coin getMinAmount() {
        return Coin.valueOf(minAmount);
    }

    public Direction getDirection() {
        return direction;
    }

    public Direction getMirroredDirection() {
        return direction == Direction.BUY ? Direction.SELL : Direction.BUY;
    }

    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.getPaymentMethodById(paymentMethodName);
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    @Nullable
    public String getCountryCode() {
        return countryCode;
    }

    @Nullable
    public List<String> getAcceptedCountryCodes() {
        return acceptedCountryCodes;
    }

    @Nullable
    public List<String> getAcceptedBankIds() {
        return acceptedBankIds;
    }

    @Nullable
    public String getBankId() {
        return bankId;
    }

    public String getOfferFeePaymentTxID() {
        return offerFeePaymentTxID;
    }

    public List<NodeAddress> getArbitratorNodeAddresses() {
        return arbitratorNodeAddresses;
    }

    public Date getDate() {
        return new Date(date);
    }

    public State getState() {
        return state;
    }

    public ObjectProperty<State> stateProperty() {
        return stateProperty;
    }

    public String getOffererPaymentAccountId() {
        return offererPaymentAccountId;
    }

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessageProperty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Offer)) return false;

        Offer offer = (Offer) o;

        if (date != offer.date) return false;
        if (fiatPrice != offer.fiatPrice) return false;
        if (amount != offer.amount) return false;
        if (minAmount != offer.minAmount) return false;
        if (id != null ? !id.equals(offer.id) : offer.id != null) return false;
        if (direction != offer.direction) return false;
        if (currencyCode != null ? !currencyCode.equals(offer.currencyCode) : offer.currencyCode != null) return false;
        if (offererNodeAddress != null ? !offererNodeAddress.equals(offer.offererNodeAddress) : offer.offererNodeAddress != null)
            return false;
        if (pubKeyRing != null ? !pubKeyRing.equals(offer.pubKeyRing) : offer.pubKeyRing != null) return false;
        if (paymentMethodName != null ? !paymentMethodName.equals(offer.paymentMethodName) : offer.paymentMethodName != null)
            return false;
        if (countryCode != null ? !countryCode.equals(offer.countryCode) : offer.countryCode != null)
            return false;
        if (offererPaymentAccountId != null ? !offererPaymentAccountId.equals(offer.offererPaymentAccountId) : offer.offererPaymentAccountId != null)
            return false;
        if (acceptedCountryCodes != null ? !acceptedCountryCodes.equals(offer.acceptedCountryCodes) : offer.acceptedCountryCodes != null)
            return false;
        if (bankId != null ? !bankId.equals(offer.bankId) : offer.bankId != null) return false;
        if (acceptedBankIds != null ? !acceptedBankIds.equals(offer.acceptedBankIds) : offer.acceptedBankIds != null)
            return false;
        if (arbitratorNodeAddresses != null ? !arbitratorNodeAddresses.equals(offer.arbitratorNodeAddresses) : offer.arbitratorNodeAddresses != null)
            return false;
        return !(offerFeePaymentTxID != null ? !offerFeePaymentTxID.equals(offer.offerFeePaymentTxID) : offer.offerFeePaymentTxID != null);

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (direction != null ? direction.hashCode() : 0);
        result = 31 * result + (currencyCode != null ? currencyCode.hashCode() : 0);
        result = 31 * result + (int) (date ^ (date >>> 32));
        result = 31 * result + (int) (fiatPrice ^ (fiatPrice >>> 32));
        result = 31 * result + (int) (amount ^ (amount >>> 32));
        result = 31 * result + (int) (minAmount ^ (minAmount >>> 32));
        result = 31 * result + (offererNodeAddress != null ? offererNodeAddress.hashCode() : 0);
        result = 31 * result + (pubKeyRing != null ? pubKeyRing.hashCode() : 0);
        result = 31 * result + (paymentMethodName != null ? paymentMethodName.hashCode() : 0);
        result = 31 * result + (countryCode != null ? countryCode.hashCode() : 0);
        result = 31 * result + (bankId != null ? bankId.hashCode() : 0);
        result = 31 * result + (offererPaymentAccountId != null ? offererPaymentAccountId.hashCode() : 0);
        result = 31 * result + (acceptedCountryCodes != null ? acceptedCountryCodes.hashCode() : 0);
        result = 31 * result + (acceptedBankIds != null ? acceptedBankIds.hashCode() : 0);
        result = 31 * result + (arbitratorNodeAddresses != null ? arbitratorNodeAddresses.hashCode() : 0);
        result = 31 * result + (offerFeePaymentTxID != null ? offerFeePaymentTxID.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Offer{" +
                "\n\tid='" + id + '\'' +
                "\n\tdirection=" + direction +
                "\n\tcurrencyCode='" + currencyCode + '\'' +
                "\n\tdate=" + date +
                "\n\tfiatPrice=" + fiatPrice +
                "\n\tamount=" + amount +
                "\n\tminAmount=" + minAmount +
                "\n\toffererAddress=" + offererNodeAddress +
                "\n\tpubKeyRing=" + pubKeyRing +
                "\n\tpaymentMethodName='" + paymentMethodName + '\'' +
                "\n\tpaymentMethodCountryCode='" + countryCode + '\'' +
                "\n\toffererPaymentAccountId='" + offererPaymentAccountId + '\'' +
                "\n\tacceptedCountryCodes=" + acceptedCountryCodes +
                "\n\tbankId=" + bankId +
                "\n\tacceptedBanks=" + acceptedBankIds +
                "\n\tarbitratorAddresses=" + arbitratorNodeAddresses +
                "\n\tofferFeePaymentTxID='" + offerFeePaymentTxID + '\'' +
                "\n\tstate=" + state +
                "\n\tstateProperty=" + stateProperty +
                "\n\tavailabilityProtocol=" + availabilityProtocol +
                "\n\terrorMessageProperty=" + errorMessageProperty +
                "\n\tTAC_OFFERER=" + TAC_OFFERER +
                "\n\tTAC_TAKER=" + TAC_TAKER +
                '}';
    }

}
