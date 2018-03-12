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

package io.bisq.gui.components.paymentmethods;

import bisq.common.locale.Res;
import bisq.common.locale.TradeCurrency;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.PopmoneyAccount;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.PopmoneyAccountPayload;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.InputValidator;
import io.bisq.gui.util.validation.PopmoneyValidator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;

import static io.bisq.gui.util.FormBuilder.*;

public class PopmoneyForm extends PaymentMethodForm {
    private final PopmoneyAccount account;
    private final PopmoneyValidator validator;
    private InputTextField accountIdInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                ((PopmoneyAccountPayload) paymentAccountPayload).getHolderName());
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.popmoney.accountId"), ((PopmoneyAccountPayload) paymentAccountPayload).getAccountId());
        return gridRow;
    }

    public PopmoneyForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, PopmoneyValidator aliPayValidator, InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.account = (PopmoneyAccount) paymentAccount;
        this.validator = aliPayValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = addLabelInputTextField(gridPane, ++gridRow,
                Res.getWithCol("payment.account.owner")).second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            account.setHolderName(newValue);
            updateFromInputs();
        });

        accountIdInputTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.popmoney.accountId")).second;
        accountIdInputTextField.setValidator(validator);
        accountIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            account.setAccountId(newValue);
            updateFromInputs();
        });

        final TradeCurrency singleTradeCurrency = account.getSingleTradeCurrency();
        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addLimitations();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String accountNr = accountIdInputTextField.getText();
            accountNr = StringUtils.abbreviate(accountNr, 9);
            String method = Res.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(": ").concat(accountNr));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), account.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"), Res.get(account.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                account.getHolderName());
        TextField field = addLabelTextField(gridPane, ++gridRow, Res.get("payment.popmoney.accountId"), account.getAccountId()).second;
        field.setMouseTransparent(false);
        final TradeCurrency singleTradeCurrency = account.getSingleTradeCurrency();
        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addLimitations();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && inputValidator.validate(account.getHolderName()).isValid
                && validator.validate(account.getAccountId()).isValid
                && account.getTradeCurrencies().size() > 0);
    }
}
