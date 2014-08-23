package io.bitsquare.gui;

import com.google.bitcoin.core.Coin;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.components.NetworkSyncPane;
import io.bitsquare.gui.orders.OrdersController;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.gui.util.Profiler;
import io.bitsquare.gui.util.Transitions;
import io.bitsquare.msg.BootstrapListener;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.storage.Persistence;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;
import io.bitsquare.util.AWTSystemTray;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import javax.inject.Inject;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the splash screen and the application views.
 * It builds up all the views and initializes the facades.
 * We use a sequence of Platform.runLater cascaded calls to make the startup more smooth, otherwise the rendering is frozen for too long.
 * Pre-loading of views is not implemented yet, and after a quick test it seemed that it does not give much improvements.
 */
public class MainController implements Initializable, NavigationController
{
    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private static MainController INSTANCE;

    private final User user;
    private final WalletFacade walletFacade;
    private final MessageFacade messageFacade;
    private final TradeManager tradeManager;
    private final Persistence persistence;
    private final ToggleGroup toggleGroup = new ToggleGroup();
    private final ViewBuilder viewBuilder;

    private ChildController childController;
    private ToggleButton prevToggleButton;
    private Image prevToggleButtonIcon;
    private ToggleButton buyButton, sellButton, homeButton, msgButton, ordersButton, fundsButton, settingsButton;
    private Pane ordersButtonButtonHolder;
    private boolean messageFacadeInited;
    private boolean walletFacadeInited;

    @FXML private BorderPane root;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MainController(User user, WalletFacade walletFacade, MessageFacade messageFacade, TradeManager tradeManager, Persistence persistence)
    {
        this.user = user;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;
        this.tradeManager = tradeManager;
        this.persistence = persistence;

        viewBuilder = new ViewBuilder();

        MainController.INSTANCE = this;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////


    public static MainController GET_INSTANCE()
    {
        return INSTANCE;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        Profiler.printMsgWithTime("MainController.initialize");
        Platform.runLater(() -> viewBuilder.buildSplashScreen(root, this));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: NavigationController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ChildController navigateToView(NavigationItem navigationItem)
    {
        switch (navigationItem)
        {
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
        }
        return childController;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Startup Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onViewInitialized()
    {
        Profiler.printMsgWithTime("MainController.onViewInitialized");
        Platform.runLater(this::initFacades);
    }

    private void onFacadesInitialised()
    {
        Profiler.printMsgWithTime("MainController.onFacadesInitialised");
        // never called on regtest
        walletFacade.addDownloadListener(new WalletFacade.DownloadListener()
        {
            @Override
            public void progress(double percent)
            {
                viewBuilder.loadingLabel.setText("Synchronise with network...");
                if (viewBuilder.networkSyncPane == null)
                    viewBuilder.setShowNetworkSyncPane();
            }

            @Override
            public void downloadComplete()
            {
                viewBuilder.loadingLabel.setText("Synchronise with network done.");
                if (viewBuilder.networkSyncPane != null)
                    viewBuilder.networkSyncPane.downloadComplete();
            }
        });

        tradeManager.addTakeOfferRequestListener(this::onTakeOfferRequested);
        Platform.runLater(this::addNavigation);
    }

    private void onNavigationAdded()
    {
        Profiler.printMsgWithTime("MainController.onNavigationAdded");
        Platform.runLater(this::loadContentView);
    }

    private void onContentViewLoaded()
    {
        Profiler.printMsgWithTime("MainController.onContentViewLoaded");
        root.setId("main-view");
        Platform.runLater(this::fadeOutSplash);
    }

    private void fadeOutSplash()
    {
        Profiler.printMsgWithTime("MainController.fadeOutSplash");
        Transitions.blurOutAndRemove(viewBuilder.splashVBox);
        Transitions.fadeIn(viewBuilder.menuBar);
        Transitions.fadeIn(viewBuilder.contentScreen);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO make ordersButton also reacting to jump to pending tab
    private void onTakeOfferRequested(String offerId, PeerAddress sender)
    {
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

    private void initFacades()
    {
        Profiler.printMsgWithTime("MainController.initFacades");
        messageFacade.init(new BootstrapListener()
        {
            @Override
            public void onCompleted()
            {
                messageFacadeInited = true;
                if (walletFacadeInited) onFacadesInitialised();
            }

            @Override
            public void onFailed(Throwable throwable)
            {
                log.error(throwable.toString());
            }
        });

        walletFacade.initialize(() -> {
            walletFacadeInited = true;
            if (messageFacadeInited) onFacadesInitialised();
        });
    }

    private void addNavigation()
    {
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

        Platform.runLater(this::onNavigationAdded);
    }

    private void loadContentView()
    {
        Profiler.printMsgWithTime("MainController.loadContentView");
        NavigationItem selectedNavigationItem = (NavigationItem) persistence.read(this, "selectedNavigationItem");
        if (selectedNavigationItem == null)
            selectedNavigationItem = NavigationItem.BUY;

        navigateToView(selectedNavigationItem);

        Platform.runLater(this::onContentViewLoaded);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ChildController loadView(NavigationItem navigationItem)
    {
        if (childController != null)
        {
            childController.cleanup();
            if (childController instanceof Hibernate)
                ((Hibernate) childController).sleep();
        }
        
        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try
        {
            final Node view = loader.load();
            viewBuilder.contentPane.getChildren().setAll(view);
            childController = loader.getController();
            childController.setNavigationController(this);
        } catch (IOException e)
        {
            e.printStackTrace();
            log.error("Loading view failed. " + navigationItem.getFxmlUrl());
        }
        if (childController instanceof Hibernate)
            ((Hibernate) childController).awake();

        return childController;
    }

    private ToggleButton addNavButton(Pane parent, String title, NavigationItem navigationItem)
    {
        final Pane pane = new Pane();
        pane.setPrefSize(50, 50);
        final ToggleButton toggleButton = new ToggleButton("", ImageUtil.getIconImageView(navigationItem.getIcon()));
        toggleButton.setToggleGroup(toggleGroup);
        toggleButton.setId("nav-button");
        toggleButton.setPrefSize(50, 50);
        toggleButton.setOnAction(e -> {
            if (prevToggleButton != null)
            {
                ((ImageView) (prevToggleButton.getGraphic())).setImage(prevToggleButtonIcon);
            }
            prevToggleButtonIcon = ((ImageView) (toggleButton.getGraphic())).getImage();
            ((ImageView) (toggleButton.getGraphic())).setImage(ImageUtil.getIconImage(navigationItem.getActiveIcon()));

            childController = loadView(navigationItem);

            persistence.write(this, "selectedNavigationItem", navigationItem);

            prevToggleButton = toggleButton;
        });

        final Label titleLabel = new Label(title);
        titleLabel.setPrefWidth(60);
        titleLabel.setLayoutY(40);
        titleLabel.setId("nav-button-label");
        titleLabel.setMouseTransparent(true);

        pane.getChildren().setAll(toggleButton, titleLabel);
        parent.getChildren().add(pane);

        return toggleButton;
    }

    private void addBalanceInfo(Pane parent)
    {
        final TextField balanceTextField = new TextField();
        balanceTextField.setEditable(false);
        balanceTextField.setPrefWidth(110);
        balanceTextField.setId("nav-balance-label");
        balanceTextField.setText(walletFacade.getWalletBalance().toFriendlyString());
        walletFacade.addBalanceListener(new BalanceListener()
        {
            @Override
            public void onBalanceChanged(Coin balance)
            {
                balanceTextField.setText(balance.toFriendlyString());
            }
        });

        final HBox hBox = new HBox();
        hBox.setSpacing(2);
        hBox.getChildren().setAll(balanceTextField);

        final Label titleLabel = new Label("Balance");
        titleLabel.setMouseTransparent(true);
        titleLabel.setPrefWidth(90);
        titleLabel.setId("nav-button-label");

        final VBox vBox = new VBox();
        vBox.setPadding(new Insets(12, 0, 0, 0));
        vBox.setSpacing(2);
        vBox.getChildren().setAll(hBox, titleLabel);
        parent.getChildren().add(vBox);
    }

    private void addAccountComboBox(Pane parent)
    {
        if (user.getBankAccounts().size() > 1)
        {
            final ComboBox<BankAccount> accountComboBox = new ComboBox<>(FXCollections.observableArrayList(user.getBankAccounts()));
            accountComboBox.setLayoutY(12);
            accountComboBox.setValue(user.getCurrentBankAccount());
            accountComboBox.valueProperty().addListener((ov, oldValue, newValue) -> user.setCurrentBankAccount(newValue));
            accountComboBox.setConverter(new StringConverter<BankAccount>()
            {

                @Override
                public String toString(BankAccount bankAccount)
                {
                    return bankAccount.getAccountTitle();
                }


                @Override
                public BankAccount fromString(String s)
                {
                    return null;
                }
            });


            final Label titleLabel = new Label("Bank account");
            titleLabel.setMouseTransparent(true);
            titleLabel.setPrefWidth(90);
            titleLabel.setId("nav-button-label");

            final VBox vBox = new VBox();
            vBox.setPadding(new Insets(12, 0, 0, 0));
            vBox.setSpacing(2);
            vBox.getChildren().setAll(accountComboBox, titleLabel);
            parent.getChildren().add(vBox);
        }
    }

}


class ViewBuilder
{
    HBox leftNavPane, rightNavPane;
    AnchorPane contentPane;
    NetworkSyncPane networkSyncPane;
    StackPane stackPane;
    AnchorPane contentScreen;
    VBox splashVBox;
    MenuBar menuBar;
    BorderPane root;
    Label loadingLabel;
    boolean showNetworkSyncPane;

    void buildSplashScreen(BorderPane root, MainController controller)
    {
        Profiler.printMsgWithTime("MainController.ViewBuilder.buildSplashScreen");
        this.root = root;

        stackPane = new StackPane();
        splashVBox = getSplashScreen();
        stackPane.getChildren().add(splashVBox);
        root.setCenter(stackPane);

        menuBar = getMenuBar();
        root.setTop(menuBar);

        Platform.runLater(() -> buildContentView(controller));
    }

    void buildContentView(MainController controller)
    {
        Profiler.printMsgWithTime("MainController.ViewBuilder.buildContentView");
        contentScreen = getContentScreen();
        stackPane.getChildren().add(contentScreen);

        Platform.runLater(controller::onViewInitialized);
    }

    AnchorPane getContentScreen()
    {
        AnchorPane anchorPane = new AnchorPane();
        anchorPane.setId("content-pane");

        leftNavPane = new HBox();
        leftNavPane.setAlignment(Pos.CENTER);
        leftNavPane.setSpacing(10);
        AnchorPane.setLeftAnchor(leftNavPane, 0d);
        AnchorPane.setTopAnchor(leftNavPane, 0d);

        rightNavPane = new HBox();
        rightNavPane.setAlignment(Pos.CENTER);
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

    void setShowNetworkSyncPane()
    {
        showNetworkSyncPane = true;

        if (contentScreen != null)
            addNetworkSyncPane();
    }

    private void addNetworkSyncPane()
    {
        networkSyncPane = new NetworkSyncPane();
        networkSyncPane.setSpacing(10);
        networkSyncPane.setPrefHeight(20);
        AnchorPane.setLeftAnchor(networkSyncPane, 0d);
        AnchorPane.setBottomAnchor(networkSyncPane, 5d);

        contentScreen.getChildren().addAll(networkSyncPane);
    }

    VBox getSplashScreen()
    {
        VBox splashVBox = new VBox();
        splashVBox.setAlignment(Pos.CENTER);
        splashVBox.setSpacing(10);

        ImageView logo = ImageUtil.getIconImageView(ImageUtil.SPLASH_LOGO);
        logo.setFitWidth(270);
        logo.setFitHeight(200);

        ImageView titleLabel = ImageUtil.getIconImageView(ImageUtil.SPLASH_LABEL);
        titleLabel.setFitWidth(300);
        titleLabel.setFitHeight(79);

        Label subTitle = new Label("The P2P Fiat-Bitcoin Exchange");
        subTitle.setAlignment(Pos.CENTER);
        subTitle.setId("logo-sub-title-label");

        loadingLabel = new Label("Initializing...");
        loadingLabel.setAlignment(Pos.CENTER);
        loadingLabel.setPadding(new Insets(80, 0, 0, 0));

        splashVBox.getChildren().addAll(logo, titleLabel, subTitle, loadingLabel);
        return splashVBox;
    }

    MenuBar getMenuBar()
    {
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
