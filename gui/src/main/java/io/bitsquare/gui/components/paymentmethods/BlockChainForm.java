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

package io.bitsquare.gui.components.paymentmethods;

import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.AltCoinAddressValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.payment.CryptoCurrencyAccount;
import io.bitsquare.payment.CryptoCurrencyAccountContractData;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.*;

public class BlockChainForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(BlockChainForm.class);

    private final CryptoCurrencyAccount cryptoCurrencyAccount;
    private final AltCoinAddressValidator altCoinAddressValidator;
    private InputTextField addressInputTextField;

    private ComboBox<TradeCurrency> currencyComboBox;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Cryptocurrency address:", ((CryptoCurrencyAccountContractData) paymentAccountContractData).getAddress());
        if (((CryptoCurrencyAccountContractData) paymentAccountContractData).getPaymentId() != null)
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Payment ID:", ((CryptoCurrencyAccountContractData) paymentAccountContractData).getPaymentId());

        return gridRow;
    }

    public BlockChainForm(PaymentAccount paymentAccount, AltCoinAddressValidator altCoinAddressValidator, InputValidator inputValidator, GridPane gridPane,
                          int gridRow) {
        super(paymentAccount, inputValidator, gridPane, gridRow);
        this.cryptoCurrencyAccount = (CryptoCurrencyAccount) paymentAccount;
        this.altCoinAddressValidator = altCoinAddressValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        addTradeCurrencyComboBox();
        currencyComboBox.setPrefWidth(250);
        addressInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Cryptocurrency address:").second;
        addressInputTextField.setValidator(altCoinAddressValidator);

        addressInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cryptoCurrencyAccount.setAddress(newValue);
            updateFromInputs();
        });

        addAllowedPeriod();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String method = BSResources.get(paymentAccount.getPaymentMethod().getId());
            String address = addressInputTextField.getText();
            address = StringUtils.abbreviate(address, 9);
            String currency = paymentAccount.getSingleTradeCurrency() != null ? paymentAccount.getSingleTradeCurrency().getCode() : "?";
            accountNameTextField.setText(method.concat(", ").concat(currency).concat(", ").concat(address));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, "Account name:", cryptoCurrencyAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(cryptoCurrencyAccount.getPaymentMethod().getId()));
        TextField field = addLabelTextField(gridPane, ++gridRow, "Cryptocurrency address:", cryptoCurrencyAccount.getAddress()).second;
        field.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, "Cryptocurrency:", cryptoCurrencyAccount.getSingleTradeCurrency().getNameAndCode());
        addAllowedPeriod();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && altCoinAddressValidator.validate(cryptoCurrencyAccount.getAddress()).isValid
                && cryptoCurrencyAccount.getSingleTradeCurrency() != null);
    }

    @Override
    protected void addTradeCurrencyComboBox() {
        currencyComboBox = addLabelComboBox(gridPane, ++gridRow, "Cryptocurrency:", Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        currencyComboBox.setPromptText("Select cryptocurrency");
        currencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getAllSortedCryptoCurrencies()));
        currencyComboBox.setVisibleRowCount(Math.min(currencyComboBox.getItems().size(), 20));
        currencyComboBox.setConverter(new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                return tradeCurrency.getNameAndCode();
            }

            @Override
            public TradeCurrency fromString(String s) {
                return null;
            }
        });
        currencyComboBox.setOnAction(e -> {
            paymentAccount.setSingleTradeCurrency(currencyComboBox.getSelectionModel().getSelectedItem());
            updateFromInputs();
        });
    }
}
