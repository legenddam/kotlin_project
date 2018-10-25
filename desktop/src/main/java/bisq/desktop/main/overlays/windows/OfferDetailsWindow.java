/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.overlays.windows;

import bisq.desktop.Navigation;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;

import bisq.core.locale.BankUtil;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.User;
import bisq.core.util.BSFormatter;

import bisq.common.crypto.KeyRing;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import com.google.common.base.Joiner;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;

import javafx.geometry.Insets;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.FormBuilder.*;

public class OfferDetailsWindow extends Overlay<OfferDetailsWindow> {
    protected static final Logger log = LoggerFactory.getLogger(OfferDetailsWindow.class);

    private final BSFormatter formatter;
    private final User user;
    private final KeyRing keyRing;
    private final Navigation navigation;
    private Offer offer;
    private Coin tradeAmount;
    private Price tradePrice;
    private Optional<Runnable> placeOfferHandlerOptional = Optional.<Runnable>empty();
    private Optional<Runnable> takeOfferHandlerOptional = Optional.<Runnable>empty();
    private BusyAnimation busyAnimation;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferDetailsWindow(BSFormatter formatter, User user, KeyRing keyRing,
                              Navigation navigation) {
        this.formatter = formatter;
        this.user = user;
        this.keyRing = keyRing;
        this.navigation = navigation;
        type = Type.Confirmation;
    }

    public void show(Offer offer, Coin tradeAmount, Price tradePrice) {
        this.offer = offer;
        this.tradeAmount = tradeAmount;
        this.tradePrice = tradePrice;

        rowIndex = -1;
        width = 1118;
        createGridPane();
        addContent();
        display();
    }

    public void show(Offer offer) {
        this.offer = offer;
        rowIndex = -1;
        width = 1118;
        createGridPane();
        addContent();
        display();
    }

    public OfferDetailsWindow onPlaceOffer(Runnable placeOfferHandler) {
        this.placeOfferHandlerOptional = Optional.of(placeOfferHandler);
        return this;
    }

    public OfferDetailsWindow onTakeOffer(Runnable takeOfferHandler) {
        this.takeOfferHandlerOptional = Optional.of(takeOfferHandler);
        return this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onHidden() {
        if (busyAnimation != null)
            busyAnimation.stop();
    }

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setPadding(new Insets(35, 40, 30, 40));
        gridPane.getStyleClass().add("grid-pane");
    }

    private void addContent() {
        int rows = 5;
        List<String> acceptedBanks = offer.getAcceptedBankIds();
        boolean showAcceptedBanks = acceptedBanks != null && !acceptedBanks.isEmpty();
        List<String> acceptedCountryCodes = offer.getAcceptedCountryCodes();
        boolean showAcceptedCountryCodes = acceptedCountryCodes != null && !acceptedCountryCodes.isEmpty();
        boolean isF2F = offer.getPaymentMethod().equals(PaymentMethod.F2F);
        if (!takeOfferHandlerOptional.isPresent())
            rows++;
        if (showAcceptedBanks)
            rows++;
        if (showAcceptedCountryCodes)
            rows++;
        if (isF2F)
            rows += 2;

        addTitledGroupBg(gridPane, ++rowIndex, rows, Res.get("shared.Offer"));

        String fiatDirectionInfo = ":";
        String btcDirectionInfo = ":";
        OfferPayload.Direction direction = offer.getDirection();
        String currencyCode = offer.getCurrencyCode();
        String offerTypeLabel = Res.getWithCol("shared.offerType");
        String toReceive = " " + Res.get("shared.toReceive");
        String toSpend = " " + Res.get("shared.toSpend");
        double firstRowDistance = Layout.FIRST_ROW_DISTANCE;
        if (takeOfferHandlerOptional.isPresent()) {
            FormBuilder.addTopLabelTextField(gridPane, rowIndex, offerTypeLabel,
                    formatter.getDirectionForTakeOffer(direction, currencyCode), firstRowDistance);
            fiatDirectionInfo = direction == OfferPayload.Direction.BUY ? toReceive : toSpend;
            btcDirectionInfo = direction == OfferPayload.Direction.SELL ? toReceive : toSpend;
        } else if (placeOfferHandlerOptional.isPresent()) {
            FormBuilder.addTopLabelTextField(gridPane, rowIndex, offerTypeLabel,
                    formatter.getOfferDirectionForCreateOffer(direction, currencyCode), firstRowDistance);
            fiatDirectionInfo = direction == OfferPayload.Direction.SELL ? toReceive : toSpend;
            btcDirectionInfo = direction == OfferPayload.Direction.BUY ? toReceive : toSpend;
        } else {
            FormBuilder.addTopLabelTextField(gridPane, rowIndex, offerTypeLabel,
                    formatter.getDirectionBothSides(direction, currencyCode), firstRowDistance);
        }
        String btcAmount = Res.get("shared.btcAmount");
        if (takeOfferHandlerOptional.isPresent()) {
            FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, btcAmount + btcDirectionInfo,
                    formatter.formatCoinWithCode(tradeAmount));
            FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, formatter.formatVolumeLabel(currencyCode) + fiatDirectionInfo,
                    formatter.formatVolumeWithCode(offer.getVolumeByAmount(tradeAmount)));
        } else {
            FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, btcAmount + btcDirectionInfo,
                    formatter.formatCoinWithCode(offer.getAmount()));
            FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, Res.get("offerDetailsWindow.minBtcAmount"),
                    formatter.formatCoinWithCode(offer.getMinAmount()));
            String volume = formatter.formatVolumeWithCode(offer.getVolume());
            String minVolume = "";
            if (offer.getVolume() != null && offer.getMinVolume() != null &&
                    !offer.getVolume().equals(offer.getMinVolume()))
                minVolume = " " + Res.get("offerDetailsWindow.min", formatter.formatVolumeWithCode(offer.getMinVolume()));
            FormBuilder.addTopLabelTextField(gridPane, ++rowIndex,
                    formatter.formatVolumeLabel(currencyCode) + fiatDirectionInfo, volume + minVolume);
        }

        String priceLabel = Res.getWithCol("shared.price");
        if (takeOfferHandlerOptional.isPresent()) {
            FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, priceLabel, formatter.formatPrice(tradePrice));
        } else {
            Price price = offer.getPrice();
            if (offer.isUseMarketBasedPrice()) {
                FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, priceLabel, formatter.formatPrice(price) +
                        " " + Res.get("offerDetailsWindow.distance",
                        formatter.formatPercentagePrice(offer.getMarketPriceMargin())));
            } else {
                FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, priceLabel, formatter.formatPrice(price));
            }
        }
        final PaymentMethod paymentMethod = offer.getPaymentMethod();
        String bankId = offer.getBankId();
        if (bankId == null || bankId.equals("null"))
            bankId = "";
        else
            bankId = " (" + bankId + ")";
        final boolean isSpecificBanks = paymentMethod.equals(PaymentMethod.SPECIFIC_BANKS);
        final boolean isNationalBanks = paymentMethod.equals(PaymentMethod.NATIONAL_BANK);
        final boolean isSepa = paymentMethod.equals(PaymentMethod.SEPA);
        final String makerPaymentAccountId = offer.getMakerPaymentAccountId();
        final PaymentAccount myPaymentAccount = user.getPaymentAccount(makerPaymentAccountId);
        String countryCode = offer.getCountryCode();
        if (offer.isMyOffer(keyRing) && makerPaymentAccountId != null && myPaymentAccount != null) {
            FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, Res.get("offerDetailsWindow.myTradingAccount"), myPaymentAccount.getAccountName());
        } else {
            final String method = Res.get(paymentMethod.getId());
            String methodWithBankId = method + bankId;
            String paymentMethodLabel = Res.get("shared.paymentMethod");
            if (countryCode != null && (isNationalBanks || isSpecificBanks || isSepa)) {
                if (BankUtil.isBankIdRequired(countryCode))
                    FormBuilder.addTopLabelTextField(gridPane, ++rowIndex,
                            paymentMethodLabel + " " + Res.get("offerDetailsWindow.offererBankId"),
                            methodWithBankId);
                else if (BankUtil.isBankNameRequired(countryCode))
                    FormBuilder.addTopLabelTextField(gridPane, ++rowIndex,
                            paymentMethodLabel + " " + Res.get("offerDetailsWindow.offerersBankName"),
                            methodWithBankId);
            } else if (paymentMethod.equals(PaymentMethod.CASH_DEPOSIT)) {
                FormBuilder.addTopLabelTextField(gridPane, ++rowIndex,
                        paymentMethodLabel + " " + Res.get("offerDetailsWindow.offererBankId"),
                        methodWithBankId);
            } else {
                FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.paymentMethod"), method);
            }
        }
        if (showAcceptedBanks) {
            if (paymentMethod.equals(PaymentMethod.SAME_BANK)) {
                FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, Res.get("offerDetailsWindow.bankId"), acceptedBanks.get(0));
            } else if (isSpecificBanks) {
                String value = Joiner.on(", ").join(acceptedBanks);
                String acceptedBanksLabel = Res.getWithCol("shared.acceptedBanks");
                Tooltip tooltip = new Tooltip(acceptedBanksLabel + " " + value);
                TextField acceptedBanksTextField = FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, acceptedBanksLabel, value).second;
                acceptedBanksTextField.setMouseTransparent(false);
                acceptedBanksTextField.setTooltip(tooltip);
            }
        }
        if (showAcceptedCountryCodes) {
            String countries;
            Tooltip tooltip = null;
            if (CountryUtil.containsAllSepaEuroCountries(acceptedCountryCodes)) {
                countries = Res.getWithCol("shared.allEuroCountries");
            } else {
                if (acceptedCountryCodes.size() == 1) {
                    countries = CountryUtil.getNameAndCode(acceptedCountryCodes.get(0));
                    tooltip = new Tooltip(countries);
                } else {
                    countries = CountryUtil.getCodesString(acceptedCountryCodes);
                    tooltip = new Tooltip(CountryUtil.getNamesByCodesString(acceptedCountryCodes));
                }
            }
            TextField acceptedCountries = FormBuilder.addTopLabelTextField(gridPane, ++rowIndex,
                    Res.getWithCol("shared.acceptedTakerCountries"), countries).second;
            if (tooltip != null) {
                acceptedCountries.setMouseTransparent(false);
                acceptedCountries.setTooltip(tooltip);
            }
        }

        if (isF2F) {
            FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, Res.getWithCol("payment.f2f.city"), offer.getF2FCity());
            TextArea textArea = addTopLabelTextArea(gridPane, ++rowIndex, Res.getWithCol("payment.f2f.extra"), "").second;
            textArea.setText(offer.getF2FExtraInfo());
            textArea.setMinHeight(33);
            textArea.setMaxHeight(textArea.getMinHeight());
            textArea.setEditable(false);
        }

        rows = 4;
        String paymentMethodCountryCode = countryCode;
        if (paymentMethodCountryCode != null)
            rows++;
        if (offer.getOfferFeePaymentTxId() != null)
            rows++;
        if (!isF2F)
            rows++;

        addTitledGroupBg(gridPane, ++rowIndex, rows, Res.get("shared.details"), Layout.GROUP_DISTANCE);
        addTopLabelTextFieldWithCopyIcon(gridPane, rowIndex, Res.getWithCol("shared.offerId"), offer.getId(),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addTopLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("offerDetailsWindow.makersOnion"),
                offer.getMakerNodeAddress().getFullAddress());
        FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, Res.get("offerDetailsWindow.creationDate"),
                formatter.formatDateTime(offer.getDate()));
        String value = Res.getWithColAndCap("shared.buyer") +
                " " +
                formatter.formatCoinWithCode(offer.getBuyerSecurityDeposit()) +
                " / " +
                Res.getWithColAndCap("shared.seller") +
                " " +
                formatter.formatCoinWithCode(offer.getSellerSecurityDeposit());
        FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.securityDeposit"), value);

        if (paymentMethodCountryCode != null && !isF2F)
            FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, Res.get("offerDetailsWindow.countryBank"),
                    CountryUtil.getNameAndCode(paymentMethodCountryCode));

        addTopLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("offerDetailsWindow.acceptedArbitrators"),
                formatter.arbitratorAddressesToString(offer.getArbitratorNodeAddresses()));
        if (offer.getOfferFeePaymentTxId() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.makerFeeTxId"), offer.getOfferFeePaymentTxId());

        if (placeOfferHandlerOptional.isPresent()) {
            addTitledGroupBg(gridPane, ++rowIndex, 1, Res.get("offerDetailsWindow.commitment"), Layout.GROUP_DISTANCE);
            FormBuilder.addTopLabelTextField(gridPane, rowIndex, Res.get("offerDetailsWindow.agree"), Res.get("createOffer.tac"),
                    Layout.FIRST_ROW_AND_GROUP_DISTANCE);

            addConfirmAndCancelButtons(true);
        } else if (takeOfferHandlerOptional.isPresent()) {
            addTitledGroupBg(gridPane, ++rowIndex, 1, Res.get("shared.contract"), Layout.GROUP_DISTANCE);
            FormBuilder.addTopLabelTextField(gridPane, rowIndex, Res.get("offerDetailsWindow.tac"), Res.get("takeOffer.tac"),
                    Layout.FIRST_ROW_AND_GROUP_DISTANCE);

            addConfirmAndCancelButtons(false);
        } else {
            Button closeButton = addButtonAfterGroup(gridPane, ++rowIndex, Res.get("shared.close"));
            closeButton.setOnAction(e -> {
                closeHandlerOptional.ifPresent(Runnable::run);
                hide();
            });
        }
    }

    private void addConfirmAndCancelButtons(boolean isPlaceOffer) {
        boolean isBuyOffer = offer.isBuyOffer();
        boolean isBuyerRole = isPlaceOffer ? isBuyOffer : !isBuyOffer;
        String placeOfferButtonText = isBuyerRole ?
                Res.get("offerDetailsWindow.confirm.maker", Res.get("shared.buy")) :
                Res.get("offerDetailsWindow.confirm.maker", Res.get("shared.sell"));
        String takeOfferButtonText = isBuyerRole ?
                Res.get("offerDetailsWindow.confirm.taker", Res.get("shared.buy")) :
                Res.get("offerDetailsWindow.confirm.taker", Res.get("shared.sell"));

        ImageView iconView = new ImageView();
        iconView.setId(isBuyerRole ? "image-buy-white" : "image-sell-white");

        Tuple3<Button, BusyAnimation, Label> placeOfferTuple = addButtonBusyAnimationLabelAfterGroup(gridPane,
                ++rowIndex,
                isPlaceOffer ? placeOfferButtonText : takeOfferButtonText);

        AutoTooltipButton button = (AutoTooltipButton) placeOfferTuple.first;
        button.setMinHeight(40);
        button.setPadding(new Insets(0, 20, 0, 20));
        button.setGraphic(iconView);
        button.setGraphicTextGap(10);
        button.setId(isBuyerRole ? "buy-button-big" : "sell-button-big");
        button.updateText(isPlaceOffer ? placeOfferButtonText : takeOfferButtonText);

        busyAnimation = placeOfferTuple.second;
        Label spinnerInfoLabel = placeOfferTuple.third;

        Button cancelButton = addButton(gridPane, ++rowIndex, Res.get("shared.cancel"));
        cancelButton.setDefaultButton(false);
        cancelButton.setId("cancel-button");
        cancelButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(Runnable::run);
            hide();
        });

        button.setOnAction(e -> {
            if (user.getAcceptedArbitrators() != null &&
                    user.getAcceptedArbitrators().size() > 0) {
                button.setDisable(true);
                cancelButton.setDisable(true);
                busyAnimation.play();
                if (isPlaceOffer) {
                    spinnerInfoLabel.setText(Res.get("createOffer.fundsBox.placeOfferSpinnerInfo"));
                    placeOfferHandlerOptional.get().run();
                } else {
                    spinnerInfoLabel.setText(Res.get("takeOffer.fundsBox.takeOfferSpinnerInfo"));
                    takeOfferHandlerOptional.get().run();
                }
            } else {
                new Popup<>().warning(Res.get("popup.warning.noArbitratorsAvailable")).show();
            }
        });
    }
}
