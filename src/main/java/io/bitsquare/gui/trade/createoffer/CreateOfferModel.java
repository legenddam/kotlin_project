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

package io.bitsquare.gui.trade.createoffer;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.locale.Country;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.utils.Fiat;

import com.google.inject.Inject;

import java.util.Locale;
import java.util.UUID;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Data model:
 * Does not know the Presenter and View (CodeBehind)
 * Use Guice for DI to get domain objects
 * <p>
 * - Holds domain data
 * - Apply business logic (no view related, that is done in presenter)
 */
class CreateOfferModel {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferModel.class);

    private final TradeManager tradeManager;
    private final WalletFacade walletFacade;
    private final Settings settings;
    private final User user;

    private final String offerId;
    private Direction direction = null;

    final Coin totalFeesAsCoin;
    Coin amountAsCoin;
    Coin minAmountAsCoin;
    Coin collateralAsCoin;
    Fiat priceAsFiat;
    Fiat volumeAsFiat;
    AddressEntry addressEntry;

    final StringProperty requestPlaceOfferErrorMessage = new SimpleStringProperty();
    final StringProperty transactionId = new SimpleStringProperty();
    final StringProperty bankAccountCurrency = new SimpleStringProperty();
    final StringProperty bankAccountCounty = new SimpleStringProperty();
    final StringProperty bankAccountType = new SimpleStringProperty();

    final LongProperty collateralAsLong = new SimpleLongProperty();
   
    final BooleanProperty requestPlaceOfferSuccess = new SimpleBooleanProperty();
    final BooleanProperty requestPlaceOfferFailed = new SimpleBooleanProperty();
   
    final ObjectProperty<Coin> totalToPayAsCoin = new SimpleObjectProperty<>();

    ObservableList<Country> acceptedCountries = FXCollections.observableArrayList();
    ObservableList<Locale> acceptedLanguages = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    CreateOfferModel(TradeManager tradeManager, WalletFacade walletFacade, Settings settings, User user) {
        this.tradeManager = tradeManager;
        this.walletFacade = walletFacade;
        this.settings = settings;
        this.user = user;

        // static data
        offerId = UUID.randomUUID().toString();
        totalFeesAsCoin = FeePolicy.CREATE_OFFER_FEE.add(FeePolicy.TX_FEE);


        //TODO just for unit testing, use mockito?
        if (walletFacade != null && walletFacade.getWallet() != null)
            addressEntry = walletFacade.getAddressInfoByTradeID(offerId);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    void activate() {
        // dynamic data, might be changing when switching screen and returning (edit settings)
        collateralAsLong.set(settings.getCollateral());

        BankAccount bankAccount = user.getCurrentBankAccount();
        if (bankAccount != null) {
            bankAccountType.set(bankAccount.getBankAccountType().toString());
            bankAccountCurrency.set(bankAccount.getCurrency().getCurrencyCode());
            bankAccountCounty.set(bankAccount.getCountry().getName());
        }
        acceptedCountries.setAll(settings.getAcceptedCountries());
        acceptedLanguages.setAll(settings.getAcceptedLanguageLocales());
    }

    void deactivate() {
    }

    void placeOffer() {
        tradeManager.requestPlaceOffer(offerId,
                direction,
                priceAsFiat,
                amountAsCoin,
                minAmountAsCoin,
                (transaction) -> {
                    requestPlaceOfferSuccess.set(true);
                    transactionId.set(transaction.getHashAsString());
                },
                (errorMessage) -> {
                    requestPlaceOfferFailed.set(true);
                    requestPlaceOfferErrorMessage.set(errorMessage);
                }
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter/Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    Direction getDirection() {
        return direction;
    }

    void setDirection(Direction direction) {
        // direction can not be changed once it is initially set
        checkArgument(this.direction == null);
        this.direction = direction;
    }

    public WalletFacade getWalletFacade() {
        return walletFacade;
    }

    String getOfferId() {
        return offerId;
    }

}
