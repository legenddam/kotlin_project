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

package io.bisq.gui.main.overlays.windows;

import com.google.common.base.Joiner;
import io.bisq.common.locale.CountryUtil;
import io.bisq.common.locale.Res;
import io.bisq.core.arbitration.DisputeManager;
import io.bisq.core.user.Preferences;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.overlays.Overlay;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.Layout;
import io.bisq.wire.payload.arbitration.Dispute;
import io.bisq.wire.payload.offer.OfferPayload;
import io.bisq.wire.payload.payment.PaymentAccountContractData;
import io.bisq.wire.payload.payment.PaymentMethod;
import io.bisq.wire.payload.trade.Contract;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.ExchangeRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

import static io.bisq.gui.util.FormBuilder.*;

public class ContractWindow extends Overlay<ContractWindow> {
    protected static final Logger log = LoggerFactory.getLogger(ContractWindow.class);

    private DisputeManager disputeManager;
    private final BSFormatter formatter;
    private Dispute dispute;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ContractWindow(DisputeManager disputeManager, BSFormatter formatter) {
        this.disputeManager = disputeManager;
        this.formatter = formatter;
        type = Type.Confirmation;
    }

    public void show(Dispute dispute) {
        this.dispute = dispute;

        rowIndex = -1;
        width = 1100;
        createGridPane();
        addContent();
        display();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setPadding(new Insets(35, 40, 30, 40));
        gridPane.setStyle("-fx-background-color: -bs-content-bg-grey;" +
                        "-fx-background-radius: 5 5 5 5;" +
                        "-fx-effect: dropshadow(gaussian, #999, 10, 0, 0, 0);" +
                        "-fx-background-insets: 10;"
        );
    }

    private void addContent() {
        Contract contract = dispute.getContract();
        OfferPayload offer = contract.offer;

        List<String> acceptedBanks = offer.getAcceptedBankIds();
        boolean showAcceptedBanks = acceptedBanks != null && !acceptedBanks.isEmpty();
        List<String> acceptedCountryCodes = offer.getAcceptedCountryCodes();
        boolean showAcceptedCountryCodes = acceptedCountryCodes != null && !acceptedCountryCodes.isEmpty();

        int rows = 17;
        if (dispute.getDepositTxSerialized() != null)
            rows++;
        if (dispute.getPayoutTxSerialized() != null)
            rows++;
        if (showAcceptedCountryCodes)
            rows++;
        if (showAcceptedBanks)
            rows++;

        PaymentAccountContractData sellerPaymentAccountContractData = contract.getSellerPaymentAccountContractData();
        addTitledGroupBg(gridPane, ++rowIndex, rows, Res.get("contractWindow.title"));
        addLabelTextFieldWithCopyIcon(gridPane, rowIndex, Res.getWithCol("shared.offerId"), offer.getId(),
                Layout.FIRST_ROW_DISTANCE).second.setMouseTransparent(false);
        addLabelTextField(gridPane, ++rowIndex, Res.get("contractWindow.dates"),
                formatter.formatDateTime(offer.getDate()) + " / " + formatter.formatDateTime(dispute.getTradeDate()));
        String currencyCode = offer.getCurrencyCode();
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.offerType"),
                formatter.getDirectionBothSides(offer.getDirection(), currencyCode));
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.tradePrice"),
                formatter.formatPrice(contract.getTradePrice()));
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.tradeAmount"),
                formatter.formatCoinWithCode(contract.getTradeAmount()));
        addLabelTextField(gridPane, ++rowIndex, formatter.formatVolumeLabel(currencyCode, ":"),
                formatter.formatVolumeWithCode(new ExchangeRate(contract.getTradePrice()).coinToFiat(contract.getTradeAmount())));
        String securityDeposit = Res.getWithColAndCap("shared.buyer") +
                " " +
                formatter.formatCoinWithCode(offer.getBuyerSecurityDeposit()) +
                " / " +
                Res.getWithColAndCap("shared.seller") +
                " " +
                formatter.formatCoinWithCode(offer.getSellerSecurityDeposit());
        addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.securityDeposit"), securityDeposit);
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("contractWindow.btcAddresses"),
                contract.getBuyerPayoutAddressString() + " / " +
                        contract.getSellerPayoutAddressString()).second.setMouseTransparent(false);
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("contractWindow.onions"),
                contract.getBuyerNodeAddress().getFullAddress() + " / " + contract.getSellerNodeAddress().getFullAddress());

        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("contractWindow.numDisputes"),
                disputeManager.getNrOfDisputes(true, contract) + " / " + disputeManager.getNrOfDisputes(false, contract));

        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("shared.paymentDetails", Res.get("shared.buyer")),
                contract.getBuyerPaymentAccountContractData().getPaymentDetails()).second.setMouseTransparent(false);
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("shared.paymentDetails", Res.get("shared.seller")),
                sellerPaymentAccountContractData.getPaymentDetails()).second.setMouseTransparent(false);

        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("shared.arbitrator"), contract.arbitratorNodeAddress.getFullAddress());

        if (showAcceptedCountryCodes) {
            String countries;
            Tooltip tooltip = null;
            if (CountryUtil.containsAllSepaEuroCountries(acceptedCountryCodes, Preferences.getDefaultLocale())) {
                countries = Res.getWithCol("shared.allEuroCountries");
            } else {
                countries = CountryUtil.getCodesString(acceptedCountryCodes);
                tooltip = new Tooltip(CountryUtil.getNamesByCodesString(acceptedCountryCodes, Preferences.getDefaultLocale()));
            }
            TextField acceptedCountries = addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.acceptedTakerCountries"), countries).second;
            if (tooltip != null) acceptedCountries.setTooltip(new Tooltip());
        }

        if (showAcceptedBanks) {
            if (offer.getPaymentMethod().equals(PaymentMethod.SAME_BANK)) {
                addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.bankName"), acceptedBanks.get(0));
            } else if (offer.getPaymentMethod().equals(PaymentMethod.SPECIFIC_BANKS)) {
                String value = Joiner.on(", ").join(acceptedBanks);
                Tooltip tooltip = new Tooltip(Res.getWithCol("shared.acceptedBanks") + value);
                TextField acceptedBanksTextField = addLabelTextField(gridPane, ++rowIndex, Res.getWithCol("shared.acceptedBanks"), value).second;
                acceptedBanksTextField.setMouseTransparent(false);
                acceptedBanksTextField.setTooltip(tooltip);
            }
        }

        addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.makerFeeTxId"), offer.getOfferFeePaymentTxID());
        addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.takerFeeTxId"), contract.takeOfferFeeTxID);
        if (dispute.getDepositTxSerialized() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, Res.getWithCol("shared.depositTransactionId"), dispute.getDepositTxId());
        if (dispute.getPayoutTxSerialized() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, Res.get("shared.payoutTxId"), dispute.getPayoutTxId());

        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, Res.get("contractWindow.contractHash"),
                Utils.HEX.encode(dispute.getContractHash())).second.setMouseTransparent(false);

        if (contract != null) {
            Button viewContractButton = addLabelButton(gridPane, ++rowIndex, Res.get("shared.contractAsJson"),
                    Res.get("shared.viewContractAsJson"), 0).second;
            viewContractButton.setDefaultButton(false);
            viewContractButton.setOnAction(e -> {
                TextArea textArea = new TextArea();
                textArea.setText(dispute.getContractAsJson());
                textArea.setPrefHeight(50);
                textArea.setEditable(false);
                textArea.setWrapText(true);
                textArea.setPrefSize(800, 600);

                Scene viewContractScene = new Scene(textArea);
                Stage viewContractStage = new Stage();
                viewContractStage.setTitle(Res.get("shared.contract.title", dispute.getShortTradeId()));
                viewContractStage.setScene(viewContractScene);
                if (owner == null)
                    owner = MainView.getRootContainer();
                Scene rootScene = owner.getScene();
                viewContractStage.initOwner(rootScene.getWindow());
                viewContractStage.initModality(Modality.NONE);
                viewContractStage.initStyle(StageStyle.UTILITY);
                viewContractStage.show();

                Window window = rootScene.getWindow();
                double titleBarHeight = window.getHeight() - rootScene.getHeight();
                viewContractStage.setX(Math.round(window.getX() + (owner.getWidth() - viewContractStage.getWidth()) / 2) + 200);
                viewContractStage.setY(Math.round(window.getY() + titleBarHeight + (owner.getHeight() - viewContractStage.getHeight()) / 2) + 50);
            });
        }

        Button closeButton = addButtonAfterGroup(gridPane, ++rowIndex, Res.get("shared.close"));
        //TODO app wide focus
        //closeButton.requestFocus();
        closeButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(Runnable::run);
            hide();
        });
    }
}
