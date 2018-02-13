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

import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.UpholdAccount;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.UpholdAccountPayload;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.InputValidator;
import io.bisq.gui.util.validation.UpholdValidator;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;

import static io.bisq.gui.util.FormBuilder.*;

public class UpholdForm extends PaymentMethodForm {
    private final UpholdAccount upholdAccount;
    private UpholdValidator upholdValidator;
    private InputTextField accountIdInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.uphold.accountId"),
                ((UpholdAccountPayload) paymentAccountPayload).getAccountId());
        return gridRow;
    }

    public UpholdForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService,
                      UpholdValidator upholdValidator, InputValidator inputValidator, GridPane gridPane,
                      int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.upholdAccount = (UpholdAccount) paymentAccount;
        this.upholdValidator = upholdValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        accountIdInputTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.uphold.accountId")).second;
        accountIdInputTextField.setValidator(upholdValidator);
        accountIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            upholdAccount.setAccountId(newValue);
            updateFromInputs();
        });

        addCurrenciesGrid(true);
        addLimitations();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    private void addCurrenciesGrid(boolean isEditable) {
        Label label = addLabel(gridPane, ++gridRow, Res.get("payment.supported.uphold"), 0);
        GridPane.setValignment(label, VPos.TOP);
        FlowPane flowPane = new FlowPane();
        flowPane.setPadding(new Insets(10, 10, 10, 10));
        flowPane.setVgap(10);
        flowPane.setHgap(10);

        if (isEditable)
            flowPane.setId("flow-pane-checkboxes-bg");
        else
            flowPane.setId("flow-pane-checkboxes-non-editable-bg");

        CurrencyUtil.getAllUpholdCurrencies().stream().forEach(e ->
        {
            CheckBox checkBox = new CheckBox(e.getCode());
            checkBox.setMouseTransparent(!isEditable);
            checkBox.setSelected(upholdAccount.getTradeCurrencies().contains(e));
            checkBox.setMinWidth(60);
            checkBox.setMaxWidth(checkBox.getMinWidth());
            checkBox.setTooltip(new Tooltip(e.getName()));
            checkBox.setOnAction(event -> {
                if (checkBox.isSelected())
                    upholdAccount.addCurrency(e);
                else
                    upholdAccount.removeCurrency(e);

                updateAllInputsValid();
            });
            flowPane.getChildren().add(checkBox);
        });

        GridPane.setRowIndex(flowPane, gridRow);
        GridPane.setColumnIndex(flowPane, 1);
        gridPane.getChildren().add(flowPane);
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String AccountId = accountIdInputTextField.getText();
            AccountId = StringUtils.abbreviate(AccountId, 9);
            String method = Res.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(": ").concat(AccountId));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                upholdAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(upholdAccount.getPaymentMethod().getId()));
        TextField field = addLabelTextField(gridPane, ++gridRow, Res.get("payment.uphold.accountId"),
                upholdAccount.getAccountId()).second;
        field.setMouseTransparent(false);
        addLimitations();
        addCurrenciesGrid(false);
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && upholdValidator.validate(upholdAccount.getAccountId()).isValid
                && upholdAccount.getTradeCurrencies().size() > 0);
    }

}
