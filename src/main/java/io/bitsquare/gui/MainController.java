package io.bitsquare.gui;

import com.google.inject.Inject;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.di.GuiceFXMLLoader;
import io.bitsquare.gui.components.NetworkSyncPane;
import io.bitsquare.gui.market.MarketController;
import io.bitsquare.gui.setup.SetupController;
import io.bitsquare.gui.util.Formatter;
import io.bitsquare.gui.util.Icons;
import io.bitsquare.gui.util.Localisation;
import io.bitsquare.trade.Direction;
import io.bitsquare.user.User;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.ResourceBundle;

public class MainController implements Initializable, NavigationController, WalletFacade.DownloadListener
{
    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private User user;
    private WalletFacade walletFacade;
    private ChildController childController;
    private ToggleGroup toggleGroup;
    private ToggleButton prevToggleButton;
    private Image prevToggleButtonIcon;
    private Pane setupView;
    private SetupController setupController;
    private NetworkSyncPane networkSyncPane;

    @FXML
    public Pane contentPane;
    @FXML
    public HBox leftNavPane, rightNavPane;
    @FXML
    public StackPane rootContainer;
    @FXML
    public AnchorPane anchorPane;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MainController(User user, WalletFacade walletFacade)
    {
        this.user = user;
        this.walletFacade = walletFacade;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        networkSyncPane = new NetworkSyncPane();
        networkSyncPane.setSpacing(10);
        networkSyncPane.setPrefHeight(20);

        walletFacade.addDownloadListener(this);
        walletFacade.initWallet();

        buildNavigation();
        if (user.getAccountID() == null)
        {
            buildSetupView();
            anchorPane.setOpacity(0);
            setupController.setNetworkSyncPane(networkSyncPane);
            rootContainer.getChildren().add(setupView);
        }

        AnchorPane.setBottomAnchor(networkSyncPane, 0.0);
        AnchorPane.setLeftAnchor(networkSyncPane, 0.0);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: NavigationController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ChildController navigateToView(String fxmlView, String title)
    {
        if (setupView != null)
        {
            anchorPane.getChildren().add(networkSyncPane);

            anchorPane.setOpacity(1);
            rootContainer.getChildren().remove(setupView);
            setupView = null;
            setupController = null;

            return null;
        }

        if (childController instanceof MarketController)
        {
            ((MarketController) childController).cleanup();
        }

        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(fxmlView), Localisation.getResourceBundle());
        try
        {
            final Node view = loader.load();
            contentPane.getChildren().setAll(view);
            childController = loader.getController();
            childController.setNavigationController(this);
            return childController;
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: WalletFacade.DownloadListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void progress(double percent, int blocksSoFar, Date date)
    {
        if (networkSyncPane != null)
            Platform.runLater(() -> networkSyncPane.setProgress(percent));
    }

    @Override
    public void doneDownload()
    {
        if (networkSyncPane != null)
            Platform.runLater(networkSyncPane::doneDownload);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void buildSetupView()
    {
        final GuiceFXMLLoader loader = new GuiceFXMLLoader(getClass().getResource(NavigationController.SETUP), Localisation.getResourceBundle());
        try
        {
            setupView = loader.load();
            setupController = loader.getController();
            setupController.setNavigationController(this);
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void buildNavigation()
    {
        toggleGroup = new ToggleGroup();

        ToggleButton homeButton = addNavButton(leftNavPane, "Overview", Icons.HOME, Icons.HOME, NavigationController.HOME);
        ToggleButton buyButton = addNavButton(leftNavPane, "Buy BTC", Icons.NAV_BUY, Icons.NAV_BUY_ACTIVE, NavigationController.MARKET, Direction.BUY);
        ToggleButton sellButton = addNavButton(leftNavPane, "Sell BTC", Icons.NAV_SELL, Icons.NAV_SELL_ACTIVE, NavigationController.MARKET, Direction.SELL);
        addNavButton(leftNavPane, "Orders", Icons.ORDERS, Icons.ORDERS, NavigationController.ORDERS);
        addNavButton(leftNavPane, "History", Icons.HISTORY, Icons.HISTORY, NavigationController.HISTORY);
        addNavButton(leftNavPane, "Funds", Icons.FUNDS, Icons.FUNDS, NavigationController.FUNDS);
        addNavButton(leftNavPane, "Message", Icons.MSG, Icons.MSG, NavigationController.MSG);
        addBalanceInfo(rightNavPane);
        addAccountComboBox(rightNavPane);

        addNavButton(rightNavPane, "Settings", Icons.SETTINGS, Icons.SETTINGS, NavigationController.SETTINGS);

        sellButton.fire();
        //homeButton.fire();
    }

    private ToggleButton addNavButton(Pane parent, String title, String iconId, String iconIdActivated, String navTarget)
    {
        return addNavButton(parent, title, iconId, iconIdActivated, navTarget, null);
    }

    private ToggleButton addNavButton(Pane parent, String title, String iconId, String iconIdActivated, String navTarget, Direction direction)
    {
        Pane pane = new Pane();
        pane.setPrefSize(50, 50);
        ToggleButton toggleButton = new ToggleButton("", Icons.getIconImageView(iconId));
        toggleButton.setToggleGroup(toggleGroup);
        toggleButton.setId("nav-button");
        toggleButton.setPrefSize(50, 50);
        toggleButton.setOnAction(e -> {
            if (prevToggleButton != null)
            {
                ((ImageView) (prevToggleButton.getGraphic())).setImage(prevToggleButtonIcon);
            }
            prevToggleButtonIcon = ((ImageView) (toggleButton.getGraphic())).getImage();
            ((ImageView) (toggleButton.getGraphic())).setImage(Icons.getIconImage(iconIdActivated));

            if (childController instanceof MarketController && direction != null)
            {
                ((MarketController) childController).setDirection(direction);
            }
            else
            {
                childController = navigateToView(navTarget, direction == Direction.BUY ? "Orderbook Buy" : "Orderbook Sell");
                if (childController instanceof MarketController && direction != null)
                {
                    ((MarketController) childController).setDirection(direction);
                }
            }

            prevToggleButton = toggleButton;

        });

        Label titleLabel = new Label(title);
        titleLabel.setPrefWidth(60);
        titleLabel.setLayoutY(40);
        titleLabel.setId("nav-button-label");
        titleLabel.setMouseTransparent(true);

        pane.getChildren().setAll(toggleButton, titleLabel);
        parent.getChildren().add(pane);

        return toggleButton;
    }

    private TextField addBalanceInfo(Pane parent)
    {
        TextField balanceLabel = new TextField();
        balanceLabel.setEditable(false);
        balanceLabel.setMouseTransparent(true);
        balanceLabel.setPrefWidth(90);
        balanceLabel.setId("nav-balance-label");
        balanceLabel.setText(Formatter.formatSatoshis(walletFacade.getBalance(), false));

        Label balanceCurrencyLabel = new Label("BTC");
        balanceCurrencyLabel.setPadding(new Insets(6, 0, 0, 0));
        HBox hBox = new HBox();
        hBox.setSpacing(2);
        hBox.getChildren().setAll(balanceLabel, balanceCurrencyLabel);

        VBox vBox = new VBox();
        vBox.setPadding(new Insets(12, 0, 0, 0));
        vBox.setSpacing(2);
        Label titleLabel = new Label("Balance");
        titleLabel.setMouseTransparent(true);
        titleLabel.setPrefWidth(90);
        titleLabel.setId("nav-button-label");

        vBox.getChildren().setAll(hBox, titleLabel);
        parent.getChildren().add(vBox);

        return balanceLabel;
    }

    private void addAccountComboBox(Pane parent)
    {
        if (user.getBankAccounts().size() > 1)
        {
            ComboBox accountComboBox = new ComboBox(FXCollections.observableArrayList(user.getBankAccounts()));
            accountComboBox.setLayoutY(12);
            accountComboBox.setValue(user.getCurrentBankAccount());
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

            VBox vBox = new VBox();
            vBox.setPadding(new Insets(12, 0, 0, 0));
            vBox.setSpacing(2);
            Label titleLabel = new Label("Bank account");
            titleLabel.setMouseTransparent(true);
            titleLabel.setPrefWidth(90);
            titleLabel.setId("nav-button-label");

            vBox.getChildren().setAll(accountComboBox, titleLabel);
            parent.getChildren().add(vBox);

            accountComboBox.valueProperty().addListener(new ChangeListener<BankAccount>()
            {
                @Override
                public void changed(ObservableValue ov, BankAccount oldValue, BankAccount newValue)
                {
                    user.setCurrentBankAccount(newValue);
                }
            });

        }
    }

}