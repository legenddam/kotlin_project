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

package io.bitsquare.gui.main;

import io.bitsquare.Bitsquare;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.OverlayManager;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.SystemNotification;
import io.bitsquare.gui.util.Profiler;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.util.ViewLoader;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainViewCB extends ViewCB<MainPM> {
    private static final Logger log = LoggerFactory.getLogger(MainViewCB.class);

    private final Navigation navigation;
    private final OverlayManager overlayManager;
    private Settings settings;

    private final ToggleGroup navButtonsGroup = new ToggleGroup();

    private BorderPane baseApplicationContainer;
    private VBox splashScreen;
    private AnchorPane contentContainer;
    private HBox leftNavPane, rightNavPane;
    private ToggleButton buyButton, sellButton, homeButton, msgButton, portfolioButton, fundsButton, settingsButton,
            accountButton;
    private Pane portfolioButtonButtonPane;
    private Label numPendingTradesLabel;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MainViewCB(MainPM presentationModel, Navigation navigation, OverlayManager overlayManager,
                       TradeManager tradeManager, Settings settings) {
        super(presentationModel);

        this.navigation = navigation;
        this.overlayManager = overlayManager;
        this.settings = settings;

        tradeManager.featureNotImplementedWarningProperty().addListener((ov, oldValue, newValue) -> {
            if (oldValue == null && newValue != null) {
                Popups.openWarningPopup(newValue);
                tradeManager.setFeatureNotImplementedWarning(null);
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);
        Profiler.printMsgWithTime("MainController.initialize");

        // just temp. ugly hack... Popups will be removed
        Popups.setOverlayManager(overlayManager);

        navigation.addListener(navigationItems -> {
            if (navigationItems != null && navigationItems.length == 2) {
                if (navigationItems[0] == Navigation.Item.MAIN) {
                    loadView(navigationItems[1]);
                    selectMainMenuButton(navigationItems[1]);
                }
            }
        });

        overlayManager.addListener(new OverlayManager.OverlayListener() {
            @Override
            public void onBlurContentRequested() {
                if (settings.getUseAnimations())
                    Transitions.blur(baseApplicationContainer);
            }

            @Override
            public void onRemoveBlurContentRequested() {
                if (settings.getUseAnimations())
                    Transitions.removeBlur(baseApplicationContainer);
            }
        });

        startup();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected Initializable loadView(Navigation.Item navigationItem) {
        super.loadView((navigationItem));
        final ViewLoader loader = new ViewLoader(navigationItem);
        try {
            final Node view = loader.load();
            contentContainer.getChildren().setAll(view);
            childController = loader.getController();

            if (childController instanceof ViewCB)
                ((ViewCB) childController).setParent(this);

            return childController;
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Loading view failed. FxmlUrl = " + navigationItem.getFxmlUrl());
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods: Startup
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void startup() {
        baseApplicationContainer = getBaseApplicationContainer();
        splashScreen = getSplashScreen();
        ((StackPane) root).getChildren().addAll(baseApplicationContainer, splashScreen);
        baseApplicationContainer.setCenter(getApplicationContainer());

        Platform.runLater(() -> onSplashScreenAdded());
    }

    private void onSplashScreenAdded() {
        presentationModel.backendReady.addListener((ov, oldValue, newValue) -> {
            if (newValue)
                onBackendReady();
        });
        presentationModel.initBackend();
    }

    private void onBackendReady() {
        Profiler.printMsgWithTime("MainController.onBackendInited");
        addMainNavigation();
    }

    private void applyPendingTradesInfoIcon(int numPendingTrades) {
        log.debug("numPendingTrades " + numPendingTrades);
        if (numPendingTrades > 0) {
            if (portfolioButtonButtonPane.getChildren().size() == 1) {
                ImageView icon = new ImageView();
                icon.setLayoutX(0.5);
                icon.setId("image-alert-round");

                numPendingTradesLabel = new Label(String.valueOf(numPendingTrades));
                numPendingTradesLabel.relocate(5, 1);
                numPendingTradesLabel.setId("nav-alert-label");

                Pane alert = new Pane();
                alert.relocate(30, 9);
                alert.setMouseTransparent(true);
                alert.setEffect(new DropShadow(4, 1, 2, Color.GREY));
                alert.getChildren().addAll(icon, numPendingTradesLabel);
                portfolioButtonButtonPane.getChildren().add(alert);
            }
            else {
                numPendingTradesLabel.setText(String.valueOf(numPendingTrades));
            }

            log.trace("openInfoNotification " + Bitsquare.getAppName());
            SystemNotification.openInfoNotification(Bitsquare.getAppName(), "You got a new trade message.");
        }
        else {
            if (portfolioButtonButtonPane.getChildren().size() > 1)
                portfolioButtonButtonPane.getChildren().remove(1);
        }
    }

    private void onMainNavigationAdded() {
        Profiler.printMsgWithTime("MainController.ondMainNavigationAdded");

        presentationModel.numPendingTrades.addListener((ov, oldValue, newValue) ->
        {
            //if ((int) newValue > (int) oldValue)
            applyPendingTradesInfoIcon((int) newValue);
        });
        applyPendingTradesInfoIcon(presentationModel.numPendingTrades.get());
        navigation.navigateToLastStoredItem();
        onContentAdded();
    }

    private void onContentAdded() {
        Profiler.printMsgWithTime("MainController.onContentAdded");
        Transitions.fadeOutAndRemove(splashScreen, 1500).setInterpolator(Interpolator.EASE_IN);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void selectMainMenuButton(Navigation.Item item) {
        switch (item) {
            case HOME:
                homeButton.setSelected(true);
                break;
            case FUNDS:
                fundsButton.setSelected(true);
                break;
            case MSG:
                msgButton.setSelected(true);
                break;
            case PORTFOLIO:
                portfolioButton.setSelected(true);
                break;
            case SETTINGS:
                settingsButton.setSelected(true);
                break;
            case SELL:
                sellButton.setSelected(true);
                break;
            case BUY:
                buyButton.setSelected(true);
                break;
            case ACCOUNT:
                accountButton.setSelected(true);
                break;
            default:
                log.error(item.getFxmlUrl() + " is no main navigation item");
                break;
        }
    }

    private BorderPane getBaseApplicationContainer() {
        BorderPane borderPane = new BorderPane();
        borderPane.setId("base-content-container");
        return borderPane;
    }

    private VBox getSplashScreen() {
        VBox vBox = new VBox();
        vBox.setAlignment(Pos.CENTER);
        vBox.setSpacing(10);
        vBox.setId("splash");

        ImageView logo = new ImageView();
        logo.setId("image-splash-logo");

        Label loadingLabel = new Label();
        loadingLabel.setAlignment(Pos.CENTER);
        loadingLabel.setPadding(new Insets(60, 0, 0, 0));
        loadingLabel.textProperty().bind(presentationModel.splashScreenInfoText);

        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(240);
        progressBar.progressProperty().bind(presentationModel.networkSyncProgress);

        vBox.getChildren().addAll(logo, loadingLabel, progressBar);

        return vBox;
    }

    private AnchorPane getApplicationContainer() {
        AnchorPane anchorPane = new AnchorPane();
        anchorPane.setId("content-pane");

        leftNavPane = new HBox();
        leftNavPane.setSpacing(10);
        AnchorPane.setLeftAnchor(leftNavPane, 10d);
        AnchorPane.setTopAnchor(leftNavPane, 0d);

        rightNavPane = new HBox();
        rightNavPane.setSpacing(10);
        AnchorPane.setRightAnchor(rightNavPane, 10d);
        AnchorPane.setTopAnchor(rightNavPane, 0d);

        contentContainer = new AnchorPane();
        contentContainer.setId("content-pane");
        AnchorPane.setLeftAnchor(contentContainer, 0d);
        AnchorPane.setRightAnchor(contentContainer, 0d);
        AnchorPane.setTopAnchor(contentContainer, 60d);
        AnchorPane.setBottomAnchor(contentContainer, 25d);

        anchorPane.getChildren().addAll(leftNavPane, rightNavPane, contentContainer);
        return anchorPane;
    }

    private void addMainNavigation() {
        homeButton = addNavButton(leftNavPane, "Overview", Navigation.Item.HOME);
        buyButton = addNavButton(leftNavPane, "Buy BTC", Navigation.Item.BUY);
        sellButton = addNavButton(leftNavPane, "Sell BTC", Navigation.Item.SELL);

        portfolioButtonButtonPane = new Pane();
        portfolioButton = addNavButton(portfolioButtonButtonPane, "Portfolio", Navigation.Item.PORTFOLIO);
        leftNavPane.getChildren().add(portfolioButtonButtonPane);

        fundsButton = addNavButton(leftNavPane, "Funds", Navigation.Item.FUNDS);

        final Pane msgButtonHolder = new Pane();
        msgButton = addNavButton(msgButtonHolder, "Messages", Navigation.Item.MSG);
        leftNavPane.getChildren().add(msgButtonHolder);

        addBankAccountComboBox(rightNavPane);

        settingsButton = addNavButton(rightNavPane, "Preferences", Navigation.Item.SETTINGS);
        accountButton = addNavButton(rightNavPane, "Account", Navigation.Item.ACCOUNT);


        // for irc demo
        homeButton.setDisable(true);
        msgButton.setDisable(true);
        settingsButton.setDisable(true);

        onMainNavigationAdded();
    }

    private ToggleButton addNavButton(Pane parent, String title, Navigation.Item navigationItem) {
        final String url = navigationItem.getFxmlUrl();
        int lastSlash = url.lastIndexOf("/") + 1;
        int end = url.lastIndexOf("View.fxml");
        final String id = url.substring(lastSlash, end).toLowerCase();

        ImageView iconImageView = new ImageView();
        iconImageView.setId("image-nav-" + id);

        final ToggleButton toggleButton = new ToggleButton(title, iconImageView);
        toggleButton.setToggleGroup(navButtonsGroup);
        toggleButton.setId("nav-button");
        toggleButton.setPadding(new Insets(0, -10, -10, -10));
        toggleButton.setMinSize(50, 50);
        toggleButton.setMaxSize(50, 50);
        toggleButton.setContentDisplay(ContentDisplay.TOP);
        toggleButton.setGraphicTextGap(0);

        toggleButton.selectedProperty().addListener((ov, oldValue, newValue) -> {
            toggleButton.setMouseTransparent(newValue);
            toggleButton.setMinSize(50, 50);
            toggleButton.setMaxSize(50, 50);
            toggleButton.setGraphicTextGap(newValue ? -1 : 0);
            if (newValue) {
                toggleButton.getGraphic().setId("image-nav-" + id + "-active");
            }
            else {
                toggleButton.getGraphic().setId("image-nav-" + id);
            }
        });

        toggleButton.setOnAction(e -> navigation.navigationTo(Navigation.Item.MAIN, navigationItem));

        parent.getChildren().add(toggleButton);
        return toggleButton;
    }

    private void addBankAccountComboBox(Pane parent) {
        final ComboBox<BankAccount> comboBox = new ComboBox<>(presentationModel.getBankAccounts());
        comboBox.setLayoutY(12);
        comboBox.setVisibleRowCount(5);
        comboBox.setConverter(presentationModel.getBankAccountsConverter());

        comboBox.valueProperty().addListener((ov, oldValue, newValue) ->
                presentationModel.setCurrentBankAccount(newValue));

        comboBox.disableProperty().bind(presentationModel.bankAccountsComboBoxDisable);
        comboBox.promptTextProperty().bind(presentationModel.bankAccountsComboBoxPrompt);

        presentationModel.currentBankAccountProperty().addListener((ov, oldValue, newValue) ->
                comboBox.getSelectionModel().select(newValue));

        comboBox.getSelectionModel().select(presentationModel.currentBankAccountProperty().get());

        final Label titleLabel = new Label("Bank account");
        titleLabel.setMouseTransparent(true);
        titleLabel.setId("nav-button-label");
        comboBox.widthProperty().addListener((ov, o, n) ->
                titleLabel.setLayoutX(((double) n - titleLabel.getWidth()) / 2));

        VBox vBox = new VBox();
        vBox.setPadding(new Insets(12, 8, 0, 5));
        vBox.setSpacing(2);
        vBox.setAlignment(Pos.CENTER);
        vBox.getChildren().setAll(comboBox, titleLabel);
        parent.getChildren().add(vBox);
    }
}