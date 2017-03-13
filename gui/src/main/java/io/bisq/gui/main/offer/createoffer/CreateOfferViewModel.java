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

package io.bisq.gui.main.offer.createoffer;

import io.bisq.app.DevEnv;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.util.MathUtils;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.model.ActivatableWithDataModel;
import io.bisq.gui.common.model.ViewModel;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.funds.FundsView;
import io.bisq.gui.main.funds.deposit.DepositView;
import io.bisq.gui.main.offer.createoffer.monetary.Altcoin;
import io.bisq.gui.main.offer.createoffer.monetary.Price;
import io.bisq.gui.main.offer.createoffer.monetary.Volume;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.settings.SettingsView;
import io.bisq.gui.main.settings.preferences.PreferencesView;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.gui.util.validation.BtcValidator;
import io.bisq.gui.util.validation.FiatValidator;
import io.bisq.gui.util.validation.InputValidator;
import io.bisq.gui.util.validation.SecurityDepositValidator;
import io.bisq.locale.Res;
import io.bisq.messages.btc.Restrictions;
import io.bisq.messages.locale.CurrencyUtil;
import io.bisq.messages.locale.TradeCurrency;
import io.bisq.messages.provider.price.MarketPrice;
import io.bisq.messages.provider.price.PriceFeedService;
import io.bisq.messages.trade.offer.payload.Offer;
import io.bisq.messages.user.Preferences;
import io.bisq.p2p.P2PService;
import io.bisq.payment.PaymentAccount;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static javafx.beans.binding.Bindings.createStringBinding;

class CreateOfferViewModel extends ActivatableWithDataModel<CreateOfferDataModel> implements ViewModel {
    private final BtcValidator btcValidator;
    private final SecurityDepositValidator securityDepositValidator;
    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final Preferences preferences;
    private final Navigation navigation;
    private final BSFormatter formatter;
    private final FiatValidator fiatValidator;

    private String amountDescription;
    private String directionLabel;
    private String addressAsString;
    private final String paymentLabel;
    private boolean createOfferRequested;

    final StringProperty amount = new SimpleStringProperty();
    final StringProperty minAmount = new SimpleStringProperty();
    final StringProperty securityDeposit = new SimpleStringProperty();

    // Price in the viewModel is always dependent on fiat/altcoin: Fiat Fiat/BTC, for altcoins we use inverted price.
    // The domain (dataModel) uses always the same price model (otherCurrencyBTC)
    // If we would change the price representation in the domain we would not be backward compatible
    final StringProperty price = new SimpleStringProperty();

    // Positive % value means always a better price form the offerer's perspective: 
    // Buyer (with fiat): lower price as market
    // Buyer (with altcoin): higher (display) price as market (display price is inverted)
    final StringProperty marketPriceMargin = new SimpleStringProperty();
    final StringProperty volume = new SimpleStringProperty();
    final StringProperty volumeDescriptionLabel = new SimpleStringProperty();
    final StringProperty volumePromptLabel = new SimpleStringProperty();
    final StringProperty tradeAmount = new SimpleStringProperty();
    final StringProperty totalToPay = new SimpleStringProperty();
    final StringProperty errorMessage = new SimpleStringProperty();
    final StringProperty btcCode = new SimpleStringProperty();
    final StringProperty tradeCurrencyCode = new SimpleStringProperty();
    final StringProperty waitingForFundsText = new SimpleStringProperty("");

    final BooleanProperty isPlaceOfferButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty cancelButtonDisabled = new SimpleBooleanProperty();
    final BooleanProperty isNextButtonDisabled = new SimpleBooleanProperty(true);
    final BooleanProperty placeOfferCompleted = new SimpleBooleanProperty();
    final BooleanProperty showPayFundsScreenDisplayed = new SimpleBooleanProperty();
    private final BooleanProperty showTransactionPublishedScreen = new SimpleBooleanProperty();
    final BooleanProperty isWaitingForFunds = new SimpleBooleanProperty();

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> minAmountValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> priceValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> volumeValidationResult = new SimpleObjectProperty<>();
    final ObjectProperty<InputValidator.ValidationResult> securityDepositValidationResult = new SimpleObjectProperty<>();

    // Those are needed for the addressTextField
    private final ObjectProperty<Address> address = new SimpleObjectProperty<>();

    private ChangeListener<String> amountStringListener;
    private ChangeListener<String> minAmountStringListener;
    private ChangeListener<String> priceStringListener, marketPriceMarginStringListener;
    private ChangeListener<String> volumeStringListener;
    private ChangeListener<String> securityDepositStringListener;

    private ChangeListener<Coin> amountAsCoinListener;
    private ChangeListener<Coin> minAmountAsCoinListener;
    private ChangeListener<Price> priceListener;
    private ChangeListener<Volume> volumeListener;
    private ChangeListener<Coin> securityDepositAsCoinListener;

    private ChangeListener<Boolean> isWalletFundedListener;
    //private ChangeListener<Coin> feeFromFundingTxListener;
    private ChangeListener<String> errorMessageListener;
    private Offer offer;
    private Timer timeoutTimer;
    private boolean inputIsMarketBasedPrice;
    private ChangeListener<Boolean> useMarketBasedPriceListener;
    private boolean ignorePriceStringListener, ignoreVolumeStringListener, ignoreAmountStringListener, ignoreSecurityDepositStringListener;
    private MarketPrice marketPrice;
    final IntegerProperty marketPriceAvailableProperty = new SimpleIntegerProperty(-1);
    private ChangeListener<Number> currenciesUpdateListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CreateOfferViewModel(CreateOfferDataModel dataModel, FiatValidator fiatValidator, BtcValidator btcValidator,
                                SecurityDepositValidator securityDepositValidator,
                                P2PService p2PService, PriceFeedService priceFeedService,
                                Preferences preferences, Navigation navigation,
                                BSFormatter formatter) {
        super(dataModel);

        this.fiatValidator = fiatValidator;
        this.btcValidator = btcValidator;
        this.securityDepositValidator = securityDepositValidator;
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.preferences = preferences;
        this.navigation = navigation;
        this.formatter = formatter;

        paymentLabel = Res.get("createOffer.fundsBox.paymentLabel", dataModel.shortOfferId);

        if (dataModel.getAddressEntry() != null) {
            addressAsString = dataModel.getAddressEntry().getAddressString();
            address.set(dataModel.getAddressEntry().getAddress());
        }
        createListeners();
    }

    @Override
    protected void activate() {
        if (DevEnv.DEV_MODE) {
            UserThread.runAfter(() -> {
                amount.set("1");
                minAmount.set(amount.get());
                UserThread.runAfter(() -> {
                    price.set("1000");
                    onFocusOutPriceAsPercentageTextField(true, false);
                }, 1);

                setAmountToModel();
                setMinAmountToModel();
                setPriceToModel();
                dataModel.calculateVolume();

                dataModel.calculateTotalToPay();
                updateButtonDisableState();
                updateSpinnerInfo();
            }, 10, TimeUnit.MILLISECONDS);
        }

        addBindings();
        addListeners();

        updateButtonDisableState();

        if (dataModel.getDirection() == Offer.Direction.BUY) {
            directionLabel = Res.get("shared.buyBitcoin");
            amountDescription = Res.get("createOffer.amountPriceBox.amountDescription", Res.get("shared.buy"));
        } else {
            directionLabel = Res.get("shared.sellBitcoin");
            amountDescription = Res.get("createOffer.amountPriceBox.amountDescription", Res.get("shared.sell"));
        }

        securityDeposit.set(formatter.formatCoin(dataModel.securityDeposit.get()));

        updateMarketPriceAvailable();
    }

    @Override
    protected void deactivate() {
        removeBindings();
        removeListeners();
        stopTimeoutTimer();
    }

    private void addBindings() {
        if (dataModel.getDirection() == Offer.Direction.BUY) {
            volumeDescriptionLabel.bind(createStringBinding(
                    () -> Res.get("createOffer.amountPriceBox.buy.volumeDescription", dataModel.tradeCurrencyCode.get()),
                    dataModel.tradeCurrencyCode));
        } else {
            volumeDescriptionLabel.bind(createStringBinding(
                    () -> Res.get("createOffer.amountPriceBox.sell.volumeDescription", dataModel.tradeCurrencyCode.get()),
                    dataModel.tradeCurrencyCode));
        }
        volumePromptLabel.bind(createStringBinding(
                () -> Res.get("createOffer.volume.prompt", dataModel.tradeCurrencyCode.get()),
                dataModel.tradeCurrencyCode));

        totalToPay.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.totalToPayAsCoin.get()),
                dataModel.totalToPayAsCoin));


        tradeAmount.bind(createStringBinding(() -> formatter.formatCoinWithCode(dataModel.amount.get()),
                dataModel.amount));


        btcCode.bind(dataModel.btcCode);
        tradeCurrencyCode.bind(dataModel.tradeCurrencyCode);
    }

    private void removeBindings() {
        totalToPay.unbind();
        tradeAmount.unbind();
        btcCode.unbind();
        tradeCurrencyCode.unbind();
        volumeDescriptionLabel.unbind();
        volumePromptLabel.unbind();
    }

    private void createListeners() {
        amountStringListener = (ov, oldValue, newValue) -> {
            if (!ignoreAmountStringListener) {
                if (isBtcInputValid(newValue).isValid) {
                    setAmountToModel();
                    dataModel.calculateVolume();
                    dataModel.calculateTotalToPay();
                }
                updateButtonDisableState();
            }
        };
        minAmountStringListener = (ov, oldValue, newValue) -> {
            if (isBtcInputValid(newValue).isValid)
                setMinAmountToModel();
            updateButtonDisableState();
        };
        priceStringListener = (ov, oldValue, newValue) -> {
            updateMarketPriceAvailable();
            final String currencyCode = dataModel.tradeCurrencyCode.get();
            if (!ignorePriceStringListener) {
                if (isPriceInputValid(newValue).isValid) {
                    setPriceToModel();
                    dataModel.calculateVolume();
                    dataModel.calculateTotalToPay();

                    if (!inputIsMarketBasedPrice) {
                        if (marketPrice != null) {
                            double marketPriceAsDouble = marketPrice.getPrice(getPriceFeedType());
                            try {
                                double priceAsDouble = formatter.parseNumberStringToDouble(price.get());
                                double relation = priceAsDouble / marketPriceAsDouble;
                                double percentage;
                                if (CurrencyUtil.isCryptoCurrency(currencyCode))
                                    percentage = dataModel.getDirection() == Offer.Direction.SELL ? 1 - relation : relation - 1;
                                else
                                    percentage = dataModel.getDirection() == Offer.Direction.BUY ? 1 - relation : relation - 1;

                                percentage = MathUtils.roundDouble(percentage, 4);
                                dataModel.setMarketPriceMargin(percentage);
                                dataModel.updateTradeFee();
                                marketPriceMargin.set(formatter.formatToPercent(percentage));
                            } catch (NumberFormatException t) {
                                marketPriceMargin.set("");
                                new Popup().warning(Res.get("validation.NaN")).show();
                            }
                        } else {
                            log.debug("We don't have a market price. We use the static price instead.");
                        }
                    }
                }
            }
            updateButtonDisableState();
        };
        marketPriceMarginStringListener = (ov, oldValue, newValue) -> {
            if (inputIsMarketBasedPrice) {
                try {
                    if (!newValue.isEmpty() && !newValue.equals("-")) {
                        double percentage = formatter.parsePercentStringToDouble(newValue);
                        if (percentage >= 1 || percentage <= -1) {
                            new Popup().warning(Res.get("popup.warning.tooLargePercentageValue") + "\n" + Res.get("popup.warning.examplePercentageValue"))
                                    .show();
                        } else {
                            final String currencyCode = dataModel.tradeCurrencyCode.get();
                            MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
                            if (marketPrice != null) {
                                percentage = MathUtils.roundDouble(percentage, 4);
                                dataModel.setMarketPriceMargin(percentage);
                                dataModel.updateTradeFee();

                                double marketPriceAsDouble = marketPrice.getPrice(getPriceFeedType());
                                double factor;
                                if (CurrencyUtil.isCryptoCurrency(currencyCode))
                                    factor = dataModel.getDirection() == Offer.Direction.SELL ? 1 - percentage : 1 + percentage;
                                else
                                    factor = dataModel.getDirection() == Offer.Direction.BUY ? 1 - percentage : 1 + percentage;
                                double targetPrice = marketPriceAsDouble * factor;
                                int precision = CurrencyUtil.isCryptoCurrency(currencyCode) ? Altcoin.SMALLEST_UNIT_EXPONENT : 2;
                                ignorePriceStringListener = true;
                                price.set(formatter.formatRoundedDoubleWithPrecision(targetPrice, precision));
                                ignorePriceStringListener = false;
                                setPriceToModel();
                                dataModel.calculateVolume();
                                dataModel.calculateTotalToPay();
                                updateButtonDisableState();
                            } else {
                                new Popup().warning(Res.get("popup.warning.noPriceFeedAvailable")).show();
                                marketPriceMargin.set("");
                            }
                        }
                    }
                } catch (Throwable t) {
                    new Popup().warning(Res.get("validation.inputError", t.toString())).show();
                }
            }
        };
        useMarketBasedPriceListener = (observable, oldValue, newValue) -> {
            if (newValue)
                priceValidationResult.set(new InputValidator.ValidationResult(true));
        };

        volumeStringListener = (ov, oldValue, newValue) -> {
            if (!ignoreVolumeStringListener) {
                if (isVolumeInputValid(newValue).isValid) {
                    setVolumeToModel();
                    setPriceToModel();
                    dataModel.calculateAmount();
                    dataModel.calculateTotalToPay();
                }
                updateButtonDisableState();
            }
        };
        securityDepositStringListener = (ov, oldValue, newValue) -> {
            if (!ignoreSecurityDepositStringListener) {
                if (securityDepositValidator.validate(newValue).isValid) {
                    setSecurityDepositToModel();
                    dataModel.calculateTotalToPay();
                }
                updateButtonDisableState();
            }
        };


        amountAsCoinListener = (ov, oldValue, newValue) -> {
            if (newValue != null)
                amount.set(formatter.formatCoin(newValue));
            else
                amount.set("");
        };
        minAmountAsCoinListener = (ov, oldValue, newValue) -> {
            if (newValue != null)
                minAmount.set(formatter.formatCoin(newValue));
            else
                minAmount.set("");
        };
        priceListener = (ov, oldValue, newValue) -> {
            ignorePriceStringListener = true;
            if (newValue != null)
                price.set(newValue.toString());
            else
                price.set("");

            ignorePriceStringListener = false;
        };
        volumeListener = (ov, oldValue, newValue) -> {
            ignoreVolumeStringListener = true;
            if (newValue != null)
                volume.set(newValue.toString());
            else
                volume.set("");

            ignoreVolumeStringListener = false;
        };

        securityDepositAsCoinListener = (ov, oldValue, newValue) -> {
            if (newValue != null)
                securityDeposit.set(formatter.formatCoin(newValue));
            else
                securityDeposit.set("");
        };


        isWalletFundedListener = (ov, oldValue, newValue) -> updateButtonDisableState();
       /* feeFromFundingTxListener = (ov, oldValue, newValue) -> {
            updateButtonDisableState();
        };*/

        currenciesUpdateListener = (observable, oldValue, newValue) -> {
            updateMarketPriceAvailable();
            updateButtonDisableState();
        };
    }

    private void updateMarketPriceAvailable() {
        marketPrice = priceFeedService.getMarketPrice(dataModel.tradeCurrencyCode.get());
        marketPriceAvailableProperty.set(marketPrice == null ? 0 : 1);
    }

    private void addListeners() {
        // Bidirectional bindings are used for all input fields: amount, price, volume and minAmount
        // We do volume/amount calculation during input, so user has immediate feedback
        amount.addListener(amountStringListener);
        minAmount.addListener(minAmountStringListener);
        price.addListener(priceStringListener);
        marketPriceMargin.addListener(marketPriceMarginStringListener);
        dataModel.useMarketBasedPrice.addListener(useMarketBasedPriceListener);
        volume.addListener(volumeStringListener);
        securityDeposit.addListener(securityDepositStringListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amount.addListener(amountAsCoinListener);
        dataModel.minAmount.addListener(minAmountAsCoinListener);
        dataModel.price.addListener(priceListener);
        dataModel.volume.addListener(volumeListener);
        dataModel.securityDeposit.addListener(securityDepositAsCoinListener);

        // dataModel.feeFromFundingTxProperty.addListener(feeFromFundingTxListener);
        dataModel.isWalletFunded.addListener(isWalletFundedListener);

        priceFeedService.currenciesUpdateFlagProperty().addListener(currenciesUpdateListener);
    }

    private void removeListeners() {
        amount.removeListener(amountStringListener);
        minAmount.removeListener(minAmountStringListener);
        price.removeListener(priceStringListener);
        marketPriceMargin.removeListener(marketPriceMarginStringListener);
        dataModel.useMarketBasedPrice.removeListener(useMarketBasedPriceListener);
        volume.removeListener(volumeStringListener);
        securityDeposit.removeListener(securityDepositStringListener);

        // Binding with Bindings.createObjectBinding does not work because of bi-directional binding
        dataModel.amount.removeListener(amountAsCoinListener);
        dataModel.minAmount.removeListener(minAmountAsCoinListener);
        dataModel.price.removeListener(priceListener);
        dataModel.volume.removeListener(volumeListener);
        dataModel.securityDeposit.removeListener(securityDepositAsCoinListener);

        //dataModel.feeFromFundingTxProperty.removeListener(feeFromFundingTxListener);
        dataModel.isWalletFunded.removeListener(isWalletFundedListener);

        if (offer != null && errorMessageListener != null)
            offer.errorMessageProperty().removeListener(errorMessageListener);

        priceFeedService.currenciesUpdateFlagProperty().removeListener(currenciesUpdateListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean initWithData(Offer.Direction direction, TradeCurrency tradeCurrency) {
        boolean result = dataModel.initWithData(direction, tradeCurrency);
        if (dataModel.paymentAccount != null)
            btcValidator.setMaxValueInBitcoin(dataModel.paymentAccount.getPaymentMethod().getMaxTradeLimit());

        return result;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onPlaceOffer(Offer offer, Runnable resultHandler) {
        errorMessage.set(null);
        createOfferRequested = true;

        if (timeoutTimer == null) {
            timeoutTimer = UserThread.runAfter(() -> {
                stopTimeoutTimer();
                createOfferRequested = false;
                errorMessage.set(Res.get("createOffer.timeoutAtPublishing"));

                updateButtonDisableState();
                updateSpinnerInfo();

                resultHandler.run();
            }, 30);
        }
        errorMessageListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                stopTimeoutTimer();
                createOfferRequested = false;
                if (offer.getState() == Offer.State.OFFER_FEE_PAID)
                    errorMessage.set(newValue + Res.get("createOffer.errorInfo"));
                else
                    errorMessage.set(newValue);

                updateButtonDisableState();
                updateSpinnerInfo();

                resultHandler.run();
            }
        };

        offer.errorMessageProperty().addListener(errorMessageListener);

        dataModel.onPlaceOffer(offer, transaction -> {
            stopTimeoutTimer();
            resultHandler.run();
            placeOfferCompleted.set(true);
            errorMessage.set(null);
        });

        updateButtonDisableState();
        updateSpinnerInfo();
    }

    public void onPaymentAccountSelected(PaymentAccount paymentAccount) {
        btcValidator.setMaxValueInBitcoin(paymentAccount.getPaymentMethod().getMaxTradeLimit());
        dataModel.onPaymentAccountSelected(paymentAccount);
        if (amount.get() != null)
            amountValidationResult.set(isBtcInputValid(amount.get()));
    }

    public void onCurrencySelected(TradeCurrency tradeCurrency) {
        dataModel.onCurrencySelected(tradeCurrency);

        marketPrice = priceFeedService.getMarketPrice(dataModel.tradeCurrencyCode.get());
        marketPriceAvailableProperty.set(marketPrice == null ? 0 : 1);
        updateButtonDisableState();
    }

    void onShowPayFundsScreen() {
        dataModel.requestTxFee();
        showPayFundsScreenDisplayed.set(true);
        updateSpinnerInfo();
    }

    boolean fundFromSavingsWallet() {
        dataModel.fundFromSavingsWallet();
        if (dataModel.isWalletFunded.get()) {
            updateButtonDisableState();
            return true;
        } else {
            new Popup().warning(Res.get("shared.notEnoughFunds",
                    formatter.formatCoinWithCode(dataModel.totalToPayAsCoin.get()),
                    formatter.formatCoinWithCode(dataModel.totalAvailableBalance)))
                    .actionButtonTextWithGoTo("navigation.funds.depositFunds")
                    .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, DepositView.class))
                    .show();
            return false;
        }

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handle focus
    ///////////////////////////////////////////////////////////////////////////////////////////

    // On focus out we do validation and apply the data to the model
    void onFocusOutAmountTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(amount.get());
            amountValidationResult.set(result);
            if (result.isValid) {
                setAmountToModel();
                ignoreAmountStringListener = true;
                amount.set(formatter.formatCoin(dataModel.amount.get()));
                ignoreAmountStringListener = false;
                dataModel.calculateVolume();

                if (!dataModel.isMinAmountLessOrEqualAmount())
                    minAmount.set(amount.get());
                else
                    amountValidationResult.set(result);

                if (minAmount.get() != null)
                    minAmountValidationResult.set(isBtcInputValid(minAmount.get()));
            }
        }
    }

    void onFocusOutMinAmountTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isBtcInputValid(minAmount.get());
            minAmountValidationResult.set(result);
            if (result.isValid) {
                setMinAmountToModel();
                minAmount.set(formatter.formatCoin(dataModel.minAmount.get()));

                if (!dataModel.isMinAmountLessOrEqualAmount()) {
                    amount.set(minAmount.get());
                } else {
                    minAmountValidationResult.set(result);
                    if (amount.get() != null)
                        amountValidationResult.set(isBtcInputValid(amount.get()));
                }
            }
        }
    }

    void onFocusOutPriceTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isPriceInputValid(price.get());
            boolean isValid = result.isValid;
            priceValidationResult.set(result);
            if (isValid) {
                setPriceToModel();
                ignorePriceStringListener = true;
                if (dataModel.price.get() != null)
                    price.set(dataModel.price.get().toString());
                ignorePriceStringListener = false;
                dataModel.calculateVolume();
                dataModel.calculateAmount();
            }
        }
    }

    void onFocusOutPriceAsPercentageTextField(boolean oldValue, boolean newValue) {
        inputIsMarketBasedPrice = !oldValue && newValue;
        if (oldValue && !newValue)
            marketPriceMargin.set(formatter.formatRoundedDoubleWithPrecision(dataModel.getMarketPriceMargin() * 100, 2));
    }

    void onFocusOutVolumeTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = isVolumeInputValid(volume.get());
            volumeValidationResult.set(result);
            if (result.isValid) {
                setVolumeToModel();
                ignoreVolumeStringListener = true;
                if (dataModel.volume.get() != null)
                    volume.set(dataModel.volume.get().toString());
                ignoreVolumeStringListener = false;

                dataModel.calculateAmount();

                if (!dataModel.isMinAmountLessOrEqualAmount()) {
                    minAmount.set(amount.getValue());
                } else {
                    if (amount.get() != null)
                        amountValidationResult.set(isBtcInputValid(amount.get()));

                    // We only check minAmountValidationResult if amountValidationResult is valid, otherwise we would get 
                    // triggered a close of the popup when the minAmountValidationResult is applied
                    if (amountValidationResult.getValue() != null && amountValidationResult.getValue().isValid && minAmount.get() != null)
                        minAmountValidationResult.set(isBtcInputValid(minAmount.get()));
                }
            }
        }
    }

    void onFocusOutSecurityDepositTextField(boolean oldValue, boolean newValue) {
        if (oldValue && !newValue) {
            InputValidator.ValidationResult result = securityDepositValidator.validate(securityDeposit.get());
            securityDepositValidationResult.set(result);
            if (result.isValid) {
                Coin defaultSecurityDeposit = Restrictions.DEFAULT_SECURITY_DEPOSIT;
                String securityDepositLowerAsDefault = "securityDepositLowerAsDefault";
                if (preferences.showAgain(securityDepositLowerAsDefault) &&
                        formatter.parseToCoin(securityDeposit.get()).compareTo(defaultSecurityDeposit) < 0) {
                    new Popup<>()
                            .warning(Res.get("createOffer.tooLowSecDeposit.warning",
                                    formatter.formatCoinWithCode(defaultSecurityDeposit)))
                            .width(800)
                            .actionButtonText(Res.get("createOffer.resetToDefault"))
                            .onAction(() -> {
                                dataModel.setSecurityDeposit(defaultSecurityDeposit);
                                ignoreSecurityDepositStringListener = true;
                                securityDeposit.set(formatter.formatCoin(dataModel.securityDeposit.get()));
                                ignoreSecurityDepositStringListener = false;
                            })
                            .closeButtonText(Res.get("createOffer.useLowerValue"))
                            .onClose(this::applySecurityDepositOnFocusOut)
                            .dontShowAgainId(securityDepositLowerAsDefault, preferences)
                            .show();
                } else {
                    applySecurityDepositOnFocusOut();
                }
            }
        }
    }

    private void applySecurityDepositOnFocusOut() {
        setSecurityDepositToModel();
        ignoreSecurityDepositStringListener = true;
        securityDeposit.set(formatter.formatCoin(dataModel.securityDeposit.get()));
        ignoreSecurityDepositStringListener = false;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isPriceInRange() {
        if (marketPriceMargin.get() != null && !marketPriceMargin.get().isEmpty()) {
            if (Math.abs(formatter.parsePercentStringToDouble(marketPriceMargin.get())) > preferences.getMaxPriceDistanceInPercent()) {
                displayPriceOutOfRangePopup();
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    private void displayPriceOutOfRangePopup() {
        Popup popup = new Popup();
        popup.warning(Res.get("createOffer.priceOutSideOfDeviation",
                formatter.formatToPercentWithSymbol(preferences.getMaxPriceDistanceInPercent())))
                .actionButtonText(Res.get("createOffer.changePrice"))
                .onAction(popup::hide)
                .closeButtonTextWithGoTo("navigation.settings.preferences")
                .onClose(() -> navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class))
                .show();
    }

    BSFormatter getFormatter() {
        return formatter;
    }

    boolean isSellOffer() {
        return dataModel.getDirection() == Offer.Direction.SELL;
    }

    public TradeCurrency getTradeCurrency() {
        return dataModel.getTradeCurrency();
    }

    public String getTradeAmount() {
        return formatter.formatCoinWithCode(dataModel.amount.get());
    }

    public String getSecurityDepositInfo() {
        return formatter.formatCoinWithCode(dataModel.getSecurityDeposit()) +
                GUIUtil.getPercentageOfTradeAmount(dataModel.getSecurityDeposit(), dataModel.amount.get(), formatter);
    }

    public String getCreateOfferFee() {
        return formatter.formatCoinWithCode(dataModel.getCreateOfferFeeAsCoin()) +
                GUIUtil.getPercentageOfTradeAmount(dataModel.getCreateOfferFeeAsCoin(), dataModel.amount.get(), formatter);
    }

    public String getTxFee() {
        return formatter.formatCoinWithCode(dataModel.getTxFeeAsCoin()) +
                GUIUtil.getPercentageOfTradeAmount(dataModel.getTxFeeAsCoin(), dataModel.amount.get(), formatter);
    }

    public PaymentAccount getPaymentAccount() {
        return dataModel.getPaymentAccount();
    }

    public String getAmountDescription() {
        return amountDescription;
    }

    public String getDirectionLabel() {
        return directionLabel;
    }

    public String getAddressAsString() {
        return addressAsString;
    }

    public String getPaymentLabel() {
        return paymentLabel;
    }

    public String formatCoin(Coin coin) {
        return formatter.formatCoin(coin);
    }

    public Offer createAndGetOffer() {
        offer = dataModel.createAndGetOffer();
        return offer;
    }

    boolean hasAcceptedArbitrators() {
        return dataModel.hasAcceptedArbitrators();
    }

    boolean isBootstrapped() {
        return p2PService.isBootstrapped();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setAmountToModel() {
        if (amount.get() != null && !amount.get().isEmpty()) {
            dataModel.setAmount(formatter.parseToCoinWith4Decimals(amount.get()));
            if (dataModel.minAmount.get() == null || dataModel.minAmount.get().equals(Coin.ZERO)) {
                minAmount.set(amount.get());
                setMinAmountToModel();
            }
        } else {
            dataModel.setAmount(null);
        }
        dataModel.updateTradeFee();
    }

    private void setMinAmountToModel() {
        if (minAmount.get() != null && !minAmount.get().isEmpty())
            dataModel.minAmount.set(formatter.parseToCoinWith4Decimals(minAmount.get()));
        else
            dataModel.minAmount.set(null);
    }

    private void setPriceToModel() {
        if (price.get() != null && !price.get().isEmpty()) {
            try {
                final Price price = Price.parse(this.price.get(), dataModel.tradeCurrencyCode.get());
                dataModel.price.set(price);
            } catch (Throwable t) {
                log.debug(t.getMessage());
            }
        } else {
            dataModel.price.set(null);
        }
    }

    private void setVolumeToModel() {
        if (volume.get() != null && !volume.get().isEmpty()) {
            try {
                final Volume value = Volume.parse(volume.get(), dataModel.tradeCurrencyCode.get());
                dataModel.volume.set(value);
            } catch (Throwable t) {
                log.debug(t.getMessage());
            }
        } else {
            dataModel.volume.set(null);
        }
    }

    private void setSecurityDepositToModel() {
        if (securityDeposit.get() != null && !securityDeposit.get().isEmpty()) {
            dataModel.setSecurityDeposit(formatter.parseToCoinWith4Decimals(securityDeposit.get()));
        } else {
            dataModel.setSecurityDeposit(null);
        }
    }


    private InputValidator.ValidationResult isBtcInputValid(String input) {
        return btcValidator.validate(input);
    }

    private InputValidator.ValidationResult isPriceInputValid(String input) {
        if (CurrencyUtil.isCryptoCurrency(getTradeCurrency().getCode()))
            fiatValidator.setMinValue(0.00000001);
        else
            fiatValidator.setMinValue(FiatValidator.MIN_FIAT_VALUE);

        return fiatValidator.validate(input);
    }

    private InputValidator.ValidationResult isVolumeInputValid(String input) {
        if (CurrencyUtil.isCryptoCurrency(getTradeCurrency().getCode()))
            fiatValidator.setMinValue(0.01);
        else
            fiatValidator.setMinValue(FiatValidator.MIN_FIAT_VALUE);

        return fiatValidator.validate(input);
    }

    private void updateSpinnerInfo() {
        if (!showPayFundsScreenDisplayed.get() ||
                errorMessage.get() != null ||
                showTransactionPublishedScreen.get()) {
            waitingForFundsText.set("");
        } else if (dataModel.isWalletFunded.get()) {
            waitingForFundsText.set("");
           /* if (dataModel.isFeeFromFundingTxSufficient.get()) {
                spinnerInfoText.set("");
            } else {
                spinnerInfoText.set("Check if funding tx miner fee is sufficient...");
            }*/
        } else {
            waitingForFundsText.set(Res.get("shared.waitingForFunds"));
        }

        isWaitingForFunds.set(!waitingForFundsText.get().isEmpty());
    }

    private void updateButtonDisableState() {
        log.debug("updateButtonDisableState");
        boolean inputDataValid = isBtcInputValid(amount.get()).isValid &&
                isBtcInputValid(minAmount.get()).isValid &&
                isPriceInputValid(price.get()).isValid &&
                securityDepositValidator.validate(securityDeposit.get()).isValid &&
                dataModel.price.get() != null &&
                dataModel.price.get().getValue() != 0 &&
                isVolumeInputValid(volume.get()).isValid &&
                dataModel.isMinAmountLessOrEqualAmount();

        isNextButtonDisabled.set(!inputDataValid);
        // boolean notSufficientFees = dataModel.isWalletFunded.get() && dataModel.isMainNet.get() && !dataModel.isFeeFromFundingTxSufficient.get();
        //isPlaceOfferButtonDisabled.set(createOfferRequested || !inputDataValid || notSufficientFees);
        isPlaceOfferButtonDisabled.set(createOfferRequested || !inputDataValid || !dataModel.isWalletFunded.get());
    }

    private PriceFeedService.Type getPriceFeedType() {
        if (CurrencyUtil.isCryptoCurrency(tradeCurrencyCode.get()))
            return dataModel.getDirection() == Offer.Direction.BUY ? PriceFeedService.Type.ASK : PriceFeedService.Type.BID;
        else
            return dataModel.getDirection() == Offer.Direction.SELL ? PriceFeedService.Type.ASK : PriceFeedService.Type.BID;
    }

    private void stopTimeoutTimer() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
