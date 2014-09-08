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

package io.bitsquare.gui;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.components.NetworkSyncPane;
import io.bitsquare.gui.orders.OrdersController;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.gui.util.Profiler;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.BootstrapListener;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;
import io.bitsquare.util.AWTSystemTray;

import com.google.bitcoin.core.Coin;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import net.tomp2p.peers.PeerAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the splash screen and the application views. It builds up all the views and initializes the facades.
 * We use a sequence of Platform.runLater cascaded calls to make the startup more smooth, otherwise the rendering is
 * frozen for too long. Pre-loading of views is not implemented yet, and after a quick test it seemed that it does not
 * give much improvements.
 */
public class MainController extends ViewController {
    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private static MainController INSTANCE;

    private final User user;
    private final WalletFacade walletFacade;
    private final MessageFacade messageFacade;
    private final TradeManager tradeManager;
    private final Persistence persistence;
    private final ViewBuilder viewBuilder;
    private final ToggleGroup navButtonsGroup = new ToggleGroup();

    private ToggleButton buyButton, sellButton, homeButton, msgButton, ordersButton, fundsButton, settingsButton,
            accountButton;
    private Pane ordersButtonButtonHolder;
    private boolean messageFacadeInited;
    private boolean walletFacadeInited;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static MainController GET_INSTANCE() {
        return INSTANCE;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MainController(User user, WalletFacade walletFacade, MessageFacade messageFacade,
                           TradeManager tradeManager, Persistence persistence) {
        this.user = user;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;
        this.tradeManager = tradeManager;
        this.persistence = persistence;

        viewBuilder = new ViewBuilder();

        MainController.INSTANCE = this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        Profiler.printMsgWithTime("MainController.initialize");
        Platform.runLater(() -> viewBuilder.buildSplashScreen((StackPane) root, this));
    }

    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Initializable loadViewAndGetChildController(NavigationItem navigationItem) {
        switch (navigationItem) {
            case HOME:
                homeButton.fire();
                break;
            case FUNDS:
                fundsButton.fire();
                break;
            case MSG:
                msgButton.fire();
                break;
            case ORDERS:
                ordersButton.fire();
                break;
            case SETTINGS:
                settingsButton.fire();
                break;
            case SELL:
                sellButton.fire();
                break;
            case BUY:
                buyButton.fire();
                break;
            case ACCOUNT:
                accountButton.fire();
                break;
        }
        return childController;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Blur effect
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void removeContentScreenBlur() {
        Transitions.removeBlur(viewBuilder.baseContentContainer);
    }

    public void blurContentScreen() {
        Transitions.blur(viewBuilder.baseContentContainer);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Startup Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onViewInitialized() {
        Profiler.printMsgWithTime("MainController.onViewInitialized");
        Platform.runLater(this::initFacades);
    }

    private void onFacadesInitialised() {
        Profiler.printMsgWithTime("MainController.onFacadesInitialised");
        // never called on regtest
        walletFacade.addDownloadListener(new WalletFacade.DownloadListener() {
            @Override
            public void progress(double percent) {
                viewBuilder.loadingLabel.setText("Synchronise with network...");
                if (viewBuilder.networkSyncPane == null)
                    viewBuilder.setShowNetworkSyncPane();
            }

            @Override
            public void downloadComplete() {
                viewBuilder.loadingLabel.setText("Synchronise with network done.");
                if (viewBuilder.networkSyncPane != null)
                    viewBuilder.networkSyncPane.downloadComplete();
            }
        });

        tradeManager.addTakeOfferRequestListener(this::onTakeOfferRequested);
        Platform.runLater(this::addNavigation);
    }

    private void onNavigationAdded() {
        Profiler.printMsgWithTime("MainController.onNavigationAdded");
        Platform.runLater(this::loadContentView);
    }

    private void onContentViewLoaded() {
        Profiler.printMsgWithTime("MainController.onContentViewLoaded");
        Platform.runLater(this::fadeOutSplash);
    }

    private void fadeOutSplash() {
        Profiler.printMsgWithTime("MainController.fadeOutSplash");
        Transitions.blur(viewBuilder.splashVBox, 700, false, true);
        Transitions.fadeIn(viewBuilder.menuBar);
        Transitions.fadeIn(viewBuilder.contentScreen);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO make ordersButton also reacting to jump to pending tab
    private void onTakeOfferRequested(String offerId, PeerAddress sender) {
        final Button alertButton = new Button("", ImageUtil.getIconImageView(ImageUtil.MSG_ALERT));
        alertButton.setId("nav-alert-button");
        alertButton.relocate(36, 19);
        alertButton.setOnAction((e) -> {
            ordersButton.fire();
            OrdersController.GET_INSTANCE().setSelectedTabIndex(1);
        });
        Tooltip.install(alertButton, new Tooltip("Someone accepted your offer"));
        ordersButtonButtonHolder.getChildren().add(alertButton);

        AWTSystemTray.setAlert();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private startup methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void initFacades() {
        Profiler.printMsgWithTime("MainController.initFacades");
        messageFacade.init(new BootstrapListener() {
            @Override
            public void onCompleted() {
                messageFacadeInited = true;
                if (walletFacadeInited) onFacadesInitialised();
            }

            @Override
            public void onFailed(Throwable throwable) {
                log.error(throwable.toString());
            }
        });

        walletFacade.initialize(() -> {
            walletFacadeInited = true;
            if (messageFacadeInited) onFacadesInitialised();
        });
    }

    private void addNavigation() {
        Profiler.printMsgWithTime("MainController.addNavigation");

        homeButton = addNavButton(viewBuilder.leftNavPane, "Overview", NavigationItem.HOME);
        buyButton = addNavButton(viewBuilder.leftNavPane, "Buy BTC", NavigationItem.BUY);
        sellButton = addNavButton(viewBuilder.leftNavPane, "Sell BTC", NavigationItem.SELL);

        ordersButtonButtonHolder = new Pane();
        ordersButton = addNavButton(ordersButtonButtonHolder, "Orders", NavigationItem.ORDERS);
        viewBuilder.leftNavPane.getChildren().add(ordersButtonButtonHolder);

        fundsButton = addNavButton(viewBuilder.leftNavPane, "Funds", NavigationItem.FUNDS);

        final Pane msgButtonHolder = new Pane();
        msgButton = addNavButton(msgButtonHolder, "Message", NavigationItem.MSG);
        viewBuilder.leftNavPane.getChildren().add(msgButtonHolder);

        addBalanceInfo(viewBuilder.rightNavPane);

        addAccountComboBox(viewBuilder.rightNavPane);

        settingsButton = addNavButton(viewBuilder.rightNavPane, "Settings", NavigationItem.SETTINGS);
        accountButton = addNavButton(viewBuilder.rightNavPane, "Account", NavigationItem.ACCOUNT);

        Platform.runLater(this::onNavigationAdded);
    }

    private void loadContentView() {
        Profiler.printMsgWithTime("MainController.loadContentView");
        NavigationItem selectedNavigationItem = (NavigationItem) persistence.read(this, "selectedNavigationItem");
        if (selectedNavigationItem == null)
            selectedNavigationItem = NavigationItem.BUY;

        loadViewAndGetChildController(selectedNavigationItem);

        Platform.runLater(this::onContentViewLoaded);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void loadView(NavigationItem navigationItem) {
        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            final Node view = loader.load();
            viewBuilder.contentPane.getChildren().setAll(view);
            childController = loader.getController();

            //TODO Remove that when all UIs are converted to CodeBehind
            if (childController instanceof ViewController)
                ((ViewController) childController).setParentController(this);
            else if (childController instanceof CodeBehind)
                ((CodeBehind) childController).setParentController(this);

            persistence.write(this, "selectedNavigationItem", navigationItem);
        } catch (IOException e) {
            log.error("Loading view failed. FxmlUrl = " + navigationItem.getFxmlUrl());
            log.error(e.getCause().toString());
            log.error(e.getMessage());
            log.error(e.getStackTrace().toString());
        }
    }

    private ToggleButton addNavButton(Pane parent, String title, NavigationItem navigationItem) {
        ImageView icon = ImageUtil.getIconImageView(navigationItem.getIcon());
        icon.setFitWidth(32);
        icon.setFitHeight(32);

        final ToggleButton toggleButton = new ToggleButton(title, icon);
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
                Image activeIcon = ImageUtil.getIconImage(navigationItem.getActiveIcon());
                ((ImageView) toggleButton.getGraphic()).setImage(activeIcon);
            }
            else {
                Image activeIcon = ImageUtil.getIconImage(navigationItem.getIcon());
                ((ImageView) toggleButton.getGraphic()).setImage(activeIcon);
            }
        });

        toggleButton.setOnAction(e -> loadView(navigationItem));
        parent.getChildren().add(toggleButton);
        return toggleButton;
    }

    private void addBalanceInfo(Pane parent) {
        final TextField balanceTextField = new TextField();
        balanceTextField.setEditable(false);
        balanceTextField.setPrefWidth(110);
        balanceTextField.setId("nav-balance-label");
        balanceTextField.setText(BSFormatter.formatCoinWithCode(walletFacade.getWalletBalance()));
        walletFacade.addBalanceListener(new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance) {
                balanceTextField.setText(BSFormatter.formatCoinWithCode(walletFacade.getWalletBalance()));
            }
        });

        final Label titleLabel = new Label("Balance");
        titleLabel.setMouseTransparent(true);
        titleLabel.setId("nav-button-label");
        balanceTextField.widthProperty().addListener((ov, o, n) ->
                titleLabel.setLayoutX(((double) n - titleLabel.getWidth()) / 2));

        final VBox vBox = new VBox();
        vBox.setPadding(new Insets(12, 5, 0, 0));
        vBox.setSpacing(2);
        vBox.getChildren().setAll(balanceTextField, titleLabel);
        vBox.setAlignment(Pos.CENTER);
        parent.getChildren().add(vBox);
    }

    private void addAccountComboBox(Pane parent) {
        final ObservableList<BankAccount> accounts = user.getBankAccounts();
        final ComboBox<BankAccount> comboBox =
                new ComboBox<>(FXCollections.observableArrayList(accounts));
        comboBox.setLayoutY(12);
        comboBox.setVisibleRowCount(5);
        comboBox.setConverter(new StringConverter<BankAccount>() {
            @Override
            public String toString(BankAccount bankAccount) {
                return bankAccount.getAccountTitle();
            }

            @Override
            public BankAccount fromString(String s) {
                return null;
            }
        });

        comboBox.setItems(accounts);
        comboBox.valueProperty().addListener((ov, oldValue, newValue) -> user.setCurrentBankAccount(newValue));
        accounts.addListener((Observable observable) -> {
            comboBox.setPromptText((accounts.size() == 0) ? "No accounts" : "");
            comboBox.setDisable((accounts.isEmpty()));
        });
        comboBox.setPromptText((accounts.isEmpty()) ? "No accounts" : "");
        comboBox.setDisable((accounts.isEmpty()));
        user.currentBankAccountProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue != null)
                comboBox.getSelectionModel().select(newValue);
        });
        comboBox.getSelectionModel().select(user.getCurrentBankAccount());

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


class ViewBuilder {
    HBox leftNavPane, rightNavPane;
    AnchorPane contentPane;
    NetworkSyncPane networkSyncPane;
    BorderPane baseContentContainer;
    AnchorPane contentScreen;
    VBox splashVBox;
    MenuBar menuBar;
    StackPane root;
    Label loadingLabel;
    boolean showNetworkSyncPane;

    void buildSplashScreen(StackPane root, MainController controller) {
        Profiler.printMsgWithTime("MainController.ViewBuilder.buildSplashScreen");

        this.root = root;

        baseContentContainer = new BorderPane();
        baseContentContainer.setId("base-content-container");
        splashVBox = getSplashScreen();

        root.getChildren().addAll(baseContentContainer, splashVBox);

        Platform.runLater(() -> buildContentView(controller));
    }

    void buildContentView(MainController controller) {
        Profiler.printMsgWithTime("MainController.ViewBuilder.buildContentView");

        menuBar = getMenuBar();
        baseContentContainer.setTop(menuBar);

        contentScreen = getContentScreen();
        baseContentContainer.setCenter(contentScreen);

        Platform.runLater(controller::onViewInitialized);
    }

    AnchorPane getContentScreen() {
        AnchorPane anchorPane = new AnchorPane();
        anchorPane.setId("content-pane");

        leftNavPane = new HBox();
        // leftNavPane.setAlignment(Pos.CENTER);
        leftNavPane.setSpacing(10);
        AnchorPane.setLeftAnchor(leftNavPane, 10d);
        AnchorPane.setTopAnchor(leftNavPane, 0d);

        rightNavPane = new HBox();
        // rightNavPane.setAlignment(Pos.CENTER);
        rightNavPane.setSpacing(10);
        AnchorPane.setRightAnchor(rightNavPane, 10d);
        AnchorPane.setTopAnchor(rightNavPane, 0d);

        contentPane = new AnchorPane();
        contentPane.setId("content-pane");
        AnchorPane.setLeftAnchor(contentPane, 0d);
        AnchorPane.setRightAnchor(contentPane, 0d);
        AnchorPane.setTopAnchor(contentPane, 60d);
        AnchorPane.setBottomAnchor(contentPane, 20d);

        anchorPane.getChildren().addAll(leftNavPane, rightNavPane, contentPane);
        anchorPane.setOpacity(0);

        if (showNetworkSyncPane)
            addNetworkSyncPane();

        return anchorPane;
    }

    void setShowNetworkSyncPane() {
        showNetworkSyncPane = true;

        if (contentScreen != null)
            addNetworkSyncPane();
    }

    private void addNetworkSyncPane() {
        networkSyncPane = new NetworkSyncPane();
        networkSyncPane.setSpacing(10);
        networkSyncPane.setPrefHeight(20);
        AnchorPane.setLeftAnchor(networkSyncPane, 0d);
        AnchorPane.setBottomAnchor(networkSyncPane, 5d);

        contentScreen.getChildren().addAll(networkSyncPane);
    }

    VBox getSplashScreen() {
        VBox splashVBox = new VBox();
        splashVBox.setAlignment(Pos.CENTER);
        splashVBox.setSpacing(10);
        splashVBox.setId("splash");

        ImageView logo = ImageUtil.getIconImageView(ImageUtil.SPLASH_LOGO);
        logo.setFitWidth(300);
        logo.setFitHeight(300);

        Label subTitle = new Label("The decentralized Bitcoin exchange");
        subTitle.setAlignment(Pos.CENTER);
        subTitle.setId("logo-sub-title-label");

        loadingLabel = new Label("Initializing...");
        loadingLabel.setAlignment(Pos.CENTER);
        loadingLabel.setPadding(new Insets(80, 0, 0, 0));

        splashVBox.getChildren().addAll(logo, subTitle, loadingLabel);
        return splashVBox;
    }

    MenuBar getMenuBar() {
        MenuBar menuBar = new MenuBar();
        // on mac we could place menu bar in the systems menu
        // menuBar.setUseSystemMenuBar(true);
        menuBar.setUseSystemMenuBar(false);

        Menu fileMenu = new Menu("_File");
        fileMenu.setMnemonicParsing(true);
        MenuItem backupMenuItem = new MenuItem("Backup wallet");
        fileMenu.getItems().addAll(backupMenuItem);

        Menu settingsMenu = new Menu("_Settings");
        settingsMenu.setMnemonicParsing(true);
        MenuItem changePwMenuItem = new MenuItem("Change password");
        settingsMenu.getItems().addAll(changePwMenuItem);

        Menu helpMenu = new Menu("_Help");
        helpMenu.setMnemonicParsing(true);
        MenuItem faqMenuItem = new MenuItem("FAQ");
        MenuItem forumMenuItem = new MenuItem("Forum");
        helpMenu.getItems().addAll(faqMenuItem, forumMenuItem);

        menuBar.getMenus().setAll(fileMenu, settingsMenu, helpMenu);
        menuBar.setOpacity(0);
        return menuBar;
    }
}
