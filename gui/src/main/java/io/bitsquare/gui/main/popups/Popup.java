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

package io.bitsquare.gui.main.popups;

import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.locale.BSResources;
import io.bitsquare.user.Preferences;
import javafx.beans.value.ChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.apache.commons.lang3.StringUtils;
import org.reactfx.util.FxTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.Timer;

import static io.bitsquare.gui.util.FormBuilder.addCheckBox;

public class Popup {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    protected final static double DEFAULT_WIDTH = 500;
    protected int rowIndex = -1;
    protected String headLine;
    protected String message;
    protected String closeButtonText;
    protected String actionButtonText;
    protected double width = DEFAULT_WIDTH;
    protected Pane owner;
    protected GridPane gridPane;
    protected Button closeButton;
    protected Optional<Runnable> closeHandlerOptional = Optional.empty();
    protected Optional<Runnable> actionHandlerOptional = Optional.empty();
    protected Stage stage;
    private boolean showReportErrorButtons;
    protected Label messageLabel;
    protected String truncatedMessage;
    private ProgressIndicator progressIndicator;
    private boolean showProgressIndicator;
    private Button actionButton;
    protected Label headLineLabel;
    protected String dontShowAgainId;
    private Preferences preferences;
    private ChangeListener<Number> positionListener;
    private Timer centerTime;
    protected double buttonDistance = 20;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Popup() {
    }

    public void show() {
        createGridPane();
        addHeadLine();
        addSeparator();

        if (showProgressIndicator)
            addProgressIndicator();

        addMessage();
        if (showReportErrorButtons)
            addReportErrorButtons();

        addCloseButton();
        addDontShowAgainCheckBox();
        applyStyles();
        PopupManager.queueForDisplay(this);
    }

    public void hide() {
        animateHide(() -> {
            Window window = owner.getScene().getWindow();
            window.xProperty().removeListener(positionListener);
            window.yProperty().removeListener(positionListener);
            window.widthProperty().removeListener(positionListener);

            if (centerTime != null)
                centerTime.cancel();

            removeEffectFromBackground();

            if (stage != null)
                stage.hide();
            else
                log.warn("Stage is null");

            cleanup();
            PopupManager.isHidden(Popup.this);
        });
    }

    protected void animateHide(Runnable onFinishedHandler) {
        onFinishedHandler.run();
    }

    protected void cleanup() {

    }

    public Popup onClose(Runnable closeHandler) {
        this.closeHandlerOptional = Optional.of(closeHandler);
        return this;
    }

    public Popup onAction(Runnable actionHandler) {
        this.actionHandlerOptional = Optional.of(actionHandler);
        return this;
    }

    public Popup headLine(String headLine) {
        this.headLine = headLine;
        return this;
    }

    public Popup notification(String message) {
        // TODO use icons
        this.headLine = "Notification";
        this.message = message;
        setTruncatedMessage();
        return this;
    }

    public Popup information(String message) {
        this.headLine = "Information";
        this.message = message;
        setTruncatedMessage();
        return this;
    }

    public Popup warning(String message) {
        this.headLine = "Warning";
        this.message = message;
        setTruncatedMessage();
        return this;
    }

    public Popup error(String message) {
        showReportErrorButtons();
        this.headLine = "Error";
        this.message = message;
        setTruncatedMessage();
        return this;
    }

    public Popup showReportErrorButtons() {
        this.showReportErrorButtons = true;
        return this;
    }

    public Popup hideReportErrorButtons() {
        this.showReportErrorButtons = false;
        return this;
    }

    public Popup message(String message) {
        this.message = message;
        setTruncatedMessage();
        return this;
    }

    public Popup closeButtonText(String closeButtonText) {
        this.closeButtonText = closeButtonText;
        return this;
    }

    public Popup actionButtonText(String actionButtonText) {
        this.actionButtonText = actionButtonText;
        return this;
    }

    public Popup width(double width) {
        this.width = width;
        return this;
    }

    public Popup showProgressIndicator() {
        this.showProgressIndicator = true;
        return this;
    }

    public Popup dontShowAgainId(String dontShowAgainId, Preferences preferences) {
        this.dontShowAgainId = dontShowAgainId;
        this.preferences = preferences;
        return this;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void createGridPane() {
        gridPane = new GridPane();
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        gridPane.setPadding(new Insets(30, 30, 30, 30));
        gridPane.setPrefWidth(width);

        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.SOMETIMES);
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
    }

    protected void blurAgain() {
        FxTimer.runLater(Duration.ofMillis(Transitions.DEFAULT_DURATION), MainView::blurLight);
    }

    public void display() {
        if (owner == null)
            owner = MainView.getRootContainer();

        stage = new Stage();
        Scene scene = new Scene(gridPane);
        scene.getStylesheets().setAll(owner.getScene().getStylesheets());
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        setModality();
        stage.initStyle(StageStyle.TRANSPARENT);
        Window window = owner.getScene().getWindow();
        stage.initOwner(window);
        stage.show();

        layout();

        addEffectToBackground();

        // On Linux the owner stage does not move the child stage as it does on Mac
        // So we need to apply centerPopup. Further with fast movements the handler loses
        // the latest position, with a delay it fixes that.
        // Also on Mac sometimes the popups are positioned outside of the main app, so keep it for all OS
        positionListener = (observable, oldValue, newValue) -> {
            if (stage != null) {
                layout();
                if (centerTime != null)
                    centerTime.cancel();

                centerTime = UserThread.runAfter(this::layout, 3);
            }
        };
        window.xProperty().addListener(positionListener);
        window.yProperty().addListener(positionListener);
        window.widthProperty().addListener(positionListener);

        animateDisplay();
    }

    protected void animateDisplay() {
    }

    protected void setModality() {
        stage.initModality(Modality.WINDOW_MODAL);
    }

    protected void applyStyles() {
        gridPane.setId("popup-bg");
        if (headLineLabel != null)
            headLineLabel.setId("popup-headline");
    }

    protected void addEffectToBackground() {
        MainView.blurLight();
    }

    protected void removeEffectFromBackground() {
        MainView.removeBlur();
    }

    protected void layout() {
        Window window = owner.getScene().getWindow();
        double titleBarHeight = window.getHeight() - owner.getScene().getHeight();
        stage.setX(Math.round(window.getX() + (owner.getWidth() - stage.getWidth()) / 2));
        stage.setY(Math.round(window.getY() + titleBarHeight + (owner.getHeight() - stage.getHeight()) / 2));
    }

    protected void addHeadLine() {
        if (headLine != null) {
            headLineLabel = new Label(BSResources.get(headLine));
            headLineLabel.setMouseTransparent(true);
            GridPane.setHalignment(headLineLabel, HPos.LEFT);
            GridPane.setRowIndex(headLineLabel, ++rowIndex);
            GridPane.setColumnSpan(headLineLabel, 2);
            gridPane.getChildren().addAll(headLineLabel);
        }
    }

    protected void addSeparator() {
        if (headLine != null) {
            Separator separator = new Separator();
            separator.setMouseTransparent(true);
            separator.setOrientation(Orientation.HORIZONTAL);
            separator.setStyle("-fx-background: #ccc;");
            GridPane.setHalignment(separator, HPos.CENTER);
            GridPane.setRowIndex(separator, ++rowIndex);
            GridPane.setColumnSpan(separator, 2);

            gridPane.getChildren().add(separator);
        }
    }

    protected void addMessage() {
        if (message != null) {
            messageLabel = new Label(truncatedMessage);
            messageLabel.setMouseTransparent(true);
            messageLabel.setWrapText(true);
            messageLabel.setId("popup-message");
            GridPane.setHalignment(messageLabel, HPos.LEFT);
            GridPane.setHgrow(messageLabel, Priority.ALWAYS);
            GridPane.setMargin(messageLabel, new Insets(3, 0, 0, 0));
            GridPane.setRowIndex(messageLabel, ++rowIndex);
            GridPane.setColumnIndex(messageLabel, 0);
            GridPane.setColumnSpan(messageLabel, 2);
            gridPane.getChildren().add(messageLabel);
        }
    }

    private void addReportErrorButtons() {
        messageLabel.setText(truncatedMessage
                + "\n\nTo help us to improve the software please report the bug at our issue tracker at Github or send it by email to the developers.\n" +
                "The error message will be copied to clipboard when you click the below buttons.\n" +
                "It will make debugging easier if you can attach the bitsquare.log file which you can find in the application directory.");

        Button githubButton = new Button("Report to Github issue tracker");
        GridPane.setMargin(githubButton, new Insets(20, 0, 0, 0));
        GridPane.setHalignment(githubButton, HPos.RIGHT);
        GridPane.setRowIndex(githubButton, ++rowIndex);
        GridPane.setColumnIndex(githubButton, 1);
        gridPane.getChildren().add(githubButton);

        githubButton.setOnAction(event -> {
            Utilities.copyToClipboard(message);
            Utilities.openWebPage("https://github.com/bitsquare/bitsquare/issues");
        });

        Button mailButton = new Button("Report by email");
        GridPane.setHalignment(mailButton, HPos.RIGHT);
        GridPane.setRowIndex(mailButton, ++rowIndex);
        GridPane.setColumnIndex(mailButton, 1);
        gridPane.getChildren().add(mailButton);
        mailButton.setOnAction(event -> {
            Utilities.copyToClipboard(message);
            Utilities.openMail("manfred@bitsquare.io",
                    "Error report",
                    "Error message:\n" + message);
        });
    }

    protected void addProgressIndicator() {
        progressIndicator = new ProgressIndicator(-1);
        progressIndicator.setMaxSize(36, 36);
        progressIndicator.setMouseTransparent(true);
        progressIndicator.setPadding(new Insets(0, 0, 20, 0));
        GridPane.setHalignment(progressIndicator, HPos.CENTER);
        GridPane.setRowIndex(progressIndicator, ++rowIndex);
        GridPane.setColumnSpan(progressIndicator, 2);
        gridPane.getChildren().add(progressIndicator);
    }

    private void addDontShowAgainCheckBox() {
        if (dontShowAgainId != null && preferences != null) {
            CheckBox dontShowAgainCheckBox = addCheckBox(gridPane, rowIndex, "Don't show again", 30);
            GridPane.setColumnIndex(dontShowAgainCheckBox, 0);
            GridPane.setHalignment(dontShowAgainCheckBox, HPos.LEFT);
            dontShowAgainCheckBox.setOnAction(e -> {
                if (dontShowAgainCheckBox.isSelected())
                    preferences.dontShowAgain(dontShowAgainId);
            });
        }
    }

    protected void addCloseButton() {
        closeButton = new Button(closeButtonText == null ? "Close" : closeButtonText);
        closeButton.setOnAction(event -> {
            hide();
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
        });

        if (actionHandlerOptional.isPresent() || actionButtonText != null) {
            actionButton = new Button(actionButtonText == null ? "Ok" : actionButtonText);
            actionButton.setDefaultButton(true);
            //TODO app wide focus
            //actionButton.requestFocus();
            actionButton.setOnAction(event -> {
                hide();
                actionHandlerOptional.ifPresent(actionHandler -> actionHandler.run());
            });

            Pane spacer = new Pane();
            HBox hBox = new HBox();
            hBox.setSpacing(10);
            hBox.getChildren().addAll(spacer, closeButton, actionButton);
            HBox.setHgrow(spacer, Priority.ALWAYS);

            GridPane.setHalignment(hBox, HPos.RIGHT);
            GridPane.setRowIndex(hBox, ++rowIndex);
            GridPane.setColumnSpan(hBox, 2);
            GridPane.setMargin(hBox, new Insets(buttonDistance, 0, 0, 0));
            gridPane.getChildren().add(hBox);
        } else {
            closeButton.setDefaultButton(true);
            GridPane.setHalignment(closeButton, HPos.RIGHT);
            if (!showReportErrorButtons)
                GridPane.setMargin(closeButton, new Insets(buttonDistance, 0, 0, 0));
            GridPane.setRowIndex(closeButton, ++rowIndex);
            GridPane.setColumnIndex(closeButton, 1);
            gridPane.getChildren().add(closeButton);
        }
    }

    protected void setTruncatedMessage() {
        if (message != null && message.length() > 900)
            truncatedMessage = StringUtils.abbreviate(message, 900);
        else
            truncatedMessage = message;
    }

    @Override
    public String toString() {
        return "Popup{" +
                "headLine='" + headLine + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
