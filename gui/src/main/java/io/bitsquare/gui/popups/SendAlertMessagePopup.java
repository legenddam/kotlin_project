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

package io.bitsquare.gui.popups;

import io.bitsquare.alert.Alert;
import io.bitsquare.app.BitsquareApp;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.components.InputTextField;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static io.bitsquare.gui.util.FormBuilder.*;

public class SendAlertMessagePopup extends Popup {
    private static final Logger log = LoggerFactory.getLogger(SendAlertMessagePopup.class);
    private Button sendButton;
    private SendAlertMessageHandler sendAlertMessageHandler;
    private RemoveAlertMessageHandler removeAlertMessageHandler;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////
    public interface SendAlertMessageHandler {
        boolean handle(Alert alert, String privKey);
    }

    public interface RemoveAlertMessageHandler {
        boolean handle(String privKey);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SendAlertMessagePopup() {
    }

    public void show() {
        if (headLine == null)
            headLine = "Send global notification";

        width = 600;
        createGridPane();
        addHeadLine();
        addContent();
        PopupManager.queueForDisplay(this);
    }

    public SendAlertMessagePopup onAddAlertMessage(SendAlertMessageHandler sendAlertMessageHandler) {
        this.sendAlertMessageHandler = sendAlertMessageHandler;
        return this;
    }

    public SendAlertMessagePopup onRemoveAlertMessage(RemoveAlertMessageHandler removeAlertMessageHandler) {
        this.removeAlertMessageHandler = removeAlertMessageHandler;
        return this;
    }

    public SendAlertMessagePopup onClose(Runnable closeHandler) {
        this.closeHandlerOptional = Optional.of(closeHandler);
        return this;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addContent() {
        InputTextField keyInputTextField = addLabelInputTextField(gridPane, ++rowIndex, "Alert private key:", 10).second;

        Tuple2<Label, TextArea> labelTextAreaTuple2 = addLabelTextArea(gridPane, ++rowIndex, "Alert message:", "Enter message");
        TextArea alertMessageTextArea = labelTextAreaTuple2.second;
        Label first = labelTextAreaTuple2.first;
        first.setMinWidth(150);
        CheckBox isUpdateCheckBox = addLabelCheckBox(gridPane, ++rowIndex, "Is update notification:", "").second;
        isUpdateCheckBox.setSelected(true);

        if (BitsquareApp.DEV_MODE)
            keyInputTextField.setText("2e41038992f89eef2e4634ff3586e342c68ad9a5a7ffafee866781687f77a9b1");

        sendButton = new Button("Send notification");
        sendButton.setOnAction(e -> {
            if (alertMessageTextArea.getText().length() > 0 && keyInputTextField.getText().length() > 0) {
                if (sendAlertMessageHandler.handle(
                        new Alert(alertMessageTextArea.getText(), isUpdateCheckBox.isSelected()),
                        keyInputTextField.getText()))
                    hide();
                else
                    new Popup().warning("The key you entered was not correct.").width(300).onClose(() -> blurAgain()).show();
            }
        });

        Button removeAlertMessageButton = new Button("Remove notification");
        removeAlertMessageButton.setOnAction(e -> {
            if (keyInputTextField.getText().length() > 0) {
                if (removeAlertMessageHandler.handle(keyInputTextField.getText()))
                    hide();
                else
                    new Popup().warning("The key you entered was not correct.").width(300).onClose(() -> blurAgain()).show();
            }
        });

        closeButton = new Button("Cancel");
        closeButton.setOnAction(e -> {
            hide();
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
        });

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        GridPane.setRowIndex(hBox, ++rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        hBox.getChildren().addAll(sendButton, removeAlertMessageButton, closeButton);
        gridPane.getChildren().add(hBox);
        GridPane.setMargin(hBox, new Insets(10, 0, 0, 0));
    }


}
