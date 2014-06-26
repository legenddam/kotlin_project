package io.bitsquare.gui.market.orderbook;

import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import io.bitsquare.bank.BankAccountTypeInfo;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.MainController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.market.createOffer.CreateOfferController;
import io.bitsquare.gui.market.trade.TakerTradeController;
import io.bitsquare.gui.util.BitSquareConverter;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.gui.util.Icons;
import io.bitsquare.gui.util.Popups;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.Localisation;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.orderbook.OrderBook;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.User;
import io.bitsquare.util.DSAKeyUtil;
import io.bitsquare.util.Utilities;
import javafx.animation.AnimationTimer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;
import org.controlsfx.dialog.Dialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class OrderBookController implements Initializable, ChildController
{
    private static final Logger log = LoggerFactory.getLogger(OrderBookController.class);
    private NavigationController navigationController;
    private OrderBook orderBook;
    private SortedList<OrderBookListItem> offerList;
    private final OrderBookFilter orderBookFilter;
    private User user;
    private MessageFacade messageFacade;
    private WalletFacade walletFacade;
    private Settings settings;
    private Storage storage;
    private AnimationTimer pollingTimer;
    private Image buyIcon = Icons.getIconImage(Icons.BUY);
    private Image sellIcon = Icons.getIconImage(Icons.SELL);

    @FXML
    public AnchorPane holderPane;
    @FXML
    public HBox topHBox;
    @FXML
    public TextField volume, amount, price;
    @FXML
    public TableView<OrderBookListItem> orderBookTable;
    @FXML
    public TableColumn priceColumn, amountColumn, volumeColumn;
    @FXML
    private TableColumn<String, OrderBookListItem> directionColumn, countryColumn, bankAccountTypeColumn;
    @FXML
    public Button createOfferButton;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OrderBookController(OrderBook orderBook, OrderBookFilter orderBookFilter, User user, MessageFacade messageFacade, WalletFacade walletFacade, Settings settings, Storage storage)
    {
        this.orderBook = orderBook;
        this.orderBookFilter = orderBookFilter;
        this.user = user;
        this.messageFacade = messageFacade;
        this.walletFacade = walletFacade;
        this.settings = settings;
        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        orderBook.init();

        // setup table
        setCountryColumnCellFactory();
        setBankAccountTypeColumnCellFactory();
        setDirectionColumnCellFactory();
        offerList = orderBook.getOfferList();
        offerList.comparatorProperty().bind(orderBookTable.comparatorProperty());
        orderBookTable.setItems(offerList);
        orderBookTable.getSortOrder().add(priceColumn);
        orderBookTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        orderBook.loadOffers();

        // handlers
        amount.textProperty().addListener(new ChangeListener<String>()
        {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
            {
                orderBookFilter.setAmount(textInputToNumber(oldValue, newValue));
                updateVolume();
            }
        });

        price.textProperty().addListener(new ChangeListener<String>()
        {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
            {
                orderBookFilter.setPrice(textInputToNumber(oldValue, newValue));
                updateVolume();
            }
        });

        orderBookFilter.getDirectionChangedProperty().addListener(new ChangeListener<Boolean>()
        {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue)
            {
                applyOffers();
            }
        });

        user.getBankAccountChangedProperty().addListener(new ChangeListener<Boolean>()
        {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue)
            {
                orderBook.loadOffers();
            }
        });

        createOfferButton.setOnAction(e -> createOffer());

        //TODO do polling until broadcast works
        setupPolling();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        this.navigationController = navigationController;
    }

    @Override
    public void cleanup()
    {
        orderBook.cleanup();

        orderBookTable.setItems(null);
        orderBookTable.getSortOrder().clear();
        offerList.comparatorProperty().unbind();

        if (pollingTimer != null)
        {
            pollingTimer.stop();
            pollingTimer = null;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setDirection(Direction direction)
    {
        orderBookTable.getSelectionModel().clearSelection();
        price.setText("");
        orderBookFilter.setDirection(direction);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean isRegistered()
    {
        return user.getAccountID() != null;
    }

    private boolean areSettingsValid()
    {
        return settings.getAcceptedLanguageLocales().size() > 0 &&
                settings.getAcceptedCountries().size() > 0 &&
                settings.getAcceptedArbitrators().size() > 0 &&
                user.getCurrentBankAccount() != null;
    }

    private void showRegistrationDialog()
    {
        int selectedIndex = -1;
        if (areSettingsValid())
        {
            if (walletFacade.isRegistrationFeeBalanceNonZero())
            {
                if (walletFacade.isRegistrationFeeBalanceSufficient())
                {
                    if (walletFacade.isRegistrationFeeConfirmed())
                    {
                        selectedIndex = 2;
                    }
                    else
                    {
                        Action response = Popups.openErrorPopup("Registration fee not confirmed yet", "The registration fee transaction has not been confirmed yet in the blockchain. Please wait until it has at least 1 confirmation.");
                        if (response == Dialog.Actions.OK)
                        {
                            MainController.getInstance().navigateToView(NavigationController.FUNDS);
                        }
                    }
                }
                else
                {
                    Action response = Popups.openErrorPopup("Missing registration fee", "You have not funded the full registration fee of " + BtcFormatter.satoshiToString(FeePolicy.ACCOUNT_REGISTRATION_FEE) + " BTC.");
                    if (response == Dialog.Actions.OK)
                    {
                        MainController.getInstance().navigateToView(NavigationController.FUNDS);
                    }
                }
            }
            else
            {
                selectedIndex = 1;
            }
        }
        else
        {
            selectedIndex = 0;
        }

        if (selectedIndex >= 0)
        {
            Dialogs.CommandLink settingsCommandLink = new Dialogs.CommandLink("Open settings", "You need to configure your settings before you can actively trade.");
            Dialogs.CommandLink depositFeeCommandLink = new Dialogs.CommandLink("Deposit funds", "You need to pay the registration fee before you can actively trade. That is needed as prevention against fraud.");
            Dialogs.CommandLink sendRegistrationCommandLink = new Dialogs.CommandLink("Publish registration", "When settings are configured and the fee deposit is done your registration transaction will be published to the Bitcoin \nnetwork.");
            List<Dialogs.CommandLink> commandLinks = Arrays.asList(settingsCommandLink, depositFeeCommandLink, sendRegistrationCommandLink);
            Action registrationMissingAction = Popups.openRegistrationMissingPopup("Not registered yet", "Please follow these steps:", "You need to register before you can place an offer.", commandLinks, selectedIndex);
            if (registrationMissingAction == settingsCommandLink)
            {
                MainController.getInstance().navigateToView(NavigationController.SETTINGS);
            }
            else if (registrationMissingAction == depositFeeCommandLink)
            {
                MainController.getInstance().navigateToView(NavigationController.FUNDS);
            }
            else if (registrationMissingAction == sendRegistrationCommandLink)
            {
                payRegistrationFee();
            }
        }
    }

    private void payRegistrationFee()
    {
        FutureCallback<Transaction> callback = new FutureCallback<Transaction>()
        {
            @Override
            public void onSuccess(Transaction transaction)
            {
                log.debug("payRegistrationFee onSuccess");
                log.info("payRegistrationFee onSuccess txid:" + transaction.getHashAsString());
            }

            @Override
            public void onFailure(Throwable t)
            {
                log.debug("payRegistrationFee onFailure");
            }
        };
        try
        {
            walletFacade.payRegistrationFee(user.getStringifiedBankAccounts(), callback);
            user.setAccountID(walletFacade.getRegistrationAddressInfo().toString());
            user.setMessagePubKeyAsHex(DSAKeyUtil.getHexStringFromPublicKey(messageFacade.getPubKey()));

            storage.write(user.getClass().getName(), user);
        } catch (InsufficientMoneyException e1)
        {
            Popups.openInsufficientMoneyPopup();
        }
    }


    private void createOffer()
    {
        if (isRegistered())
        {
            if (walletFacade.isUnusedTradeAddressBalanceAboveCreationFee())
            {
                ChildController nextController = navigationController.navigateToView(NavigationController.CREATE_OFFER, "Create offer");
                ((CreateOfferController) nextController).setOrderBookFilter(orderBookFilter);
            }
            else
            {
                Action response = Popups.openErrorPopup("No funds for a trade", "You have to add some funds before you create a new offer.");
                if (response == Dialog.Actions.OK)
                {
                    MainController.getInstance().navigateToView(NavigationController.FUNDS);
                }
            }
        }
        else
        {
            showRegistrationDialog();
        }
    }

    private void takeOffer(Offer offer)
    {
        if (isRegistered())
        {
            String title = offer.getDirection() == Direction.BUY ? "Trade: Sell Bitcoin" : "Trade: Buy Bitcoin";
            TakerTradeController takerTradeController = (TakerTradeController) navigationController.navigateToView(NavigationController.TAKER_TRADE, title);

            BigInteger requestedAmount = offer.getAmount();
            if (!amount.getText().equals(""))
                requestedAmount = BtcFormatter.stringValueToSatoshis(amount.getText());

            takerTradeController.initWithData(offer, requestedAmount);
        }
        else
        {
            showRegistrationDialog();
        }
    }

    private void removeOffer(Offer offer)
    {
        try
        {
            orderBook.removeOffer(offer);
        } catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private void applyOffers()
    {
        orderBook.applyFilter(orderBookFilter);

        priceColumn.setSortType((orderBookFilter.getDirection() == Direction.BUY) ? TableColumn.SortType.ASCENDING : TableColumn.SortType.DESCENDING);
        orderBookTable.sort();

        if (orderBookTable.getItems() != null)
            createOfferButton.setDefaultButton(orderBookTable.getItems().size() == 0);
    }

    private void setupPolling()
    {

        pollingTimer = Utilities.setInterval(1000, (AnimationTimer animationTimer) -> {
            try
            {
                if (user.getCurrentBankAccount() != null)
                    messageFacade.getDirtyFlag(user.getCurrentBankAccount().getCurrency());
                else
                    messageFacade.getDirtyFlag(CurrencyUtil.getDefaultCurrency());

            } catch (IOException e)
            {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            return null;
        });
        messageFacade.getIsDirtyProperty().addListener(new ChangeListener<Boolean>()
        {
            @Override
            public void changed(ObservableValue<? extends Boolean> observableValue, Boolean oldValue, Boolean newValue)
            {
                //log.info("getIsDirtyProperty changed " + oldValue + "/" + newValue);
                orderBook.loadOffers();
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table columns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setDirectionColumnCellFactory()
    {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        directionColumn.setCellFactory(new Callback<TableColumn<String, OrderBookListItem>, TableCell<String, OrderBookListItem>>()
        {
            @Override
            public TableCell<String, OrderBookListItem> call(TableColumn<String, OrderBookListItem> directionColumn)
            {
                return new TableCell<String, OrderBookListItem>()
                {
                    final ImageView iconView = new ImageView();
                    final Button button = new Button();

                    {
                        button.setGraphic(iconView);
                        button.setMinWidth(70);
                    }

                    @Override
                    public void updateItem(final OrderBookListItem orderBookListItem, boolean empty)
                    {
                        super.updateItem(orderBookListItem, empty);

                        if (orderBookListItem != null)
                        {
                            String title;
                            Image icon;
                            Offer offer = orderBookListItem.getOffer();

                            if (offer.getMessagePubKeyAsHex().equals(user.getMessagePubKeyAsHex()))
                            {
                                icon = Icons.getIconImage(Icons.REMOVE);
                                title = "Remove";
                                button.setOnAction(event -> removeOffer(orderBookListItem.getOffer()));
                            }
                            else
                            {
                                if (offer.getDirection() == Direction.SELL)
                                {
                                    icon = buyIcon;
                                    title = BitSquareFormatter.formatDirection(Direction.BUY, true);
                                }
                                else
                                {
                                    icon = sellIcon;
                                    title = BitSquareFormatter.formatDirection(Direction.SELL, true);
                                }

                                button.setDefaultButton(getIndex() == 0);
                                button.setOnAction(event -> takeOffer(orderBookListItem.getOffer()));
                            }


                            iconView.setImage(icon);
                            button.setText(title);
                            setGraphic(button);
                        }
                        else
                        {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
    }

    private void setCountryColumnCellFactory()
    {
        countryColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        countryColumn.setCellFactory(new Callback<TableColumn<String, OrderBookListItem>, TableCell<String, OrderBookListItem>>()
        {
            @Override
            public TableCell<String, OrderBookListItem> call(TableColumn<String, OrderBookListItem> directionColumn)
            {
                return new TableCell<String, OrderBookListItem>()
                {
                    final HBox hBox = new HBox();

                    {
                        hBox.setSpacing(3);
                        hBox.setAlignment(Pos.CENTER);
                        setGraphic(hBox);
                    }

                    @Override
                    public void updateItem(final OrderBookListItem orderBookListItem, boolean empty)
                    {
                        super.updateItem(orderBookListItem, empty);

                        hBox.getChildren().clear();
                        if (orderBookListItem != null)
                        {
                            Country country = orderBookListItem.getOffer().getBankAccountCountry();
                            try
                            {
                                hBox.getChildren().add(Icons.getIconImageView("/images/countries/" + country.getCode().toLowerCase() + ".png"));

                            } catch (Exception e)
                            {
                                log.warn("Country icon not found: " + "/images/countries/" + country.getCode().toLowerCase() + ".png country name: " + country.getName());
                            }
                            Tooltip.install(this, new Tooltip(country.getName()));
                        }
                    }
                };
            }
        });
    }

    private void setBankAccountTypeColumnCellFactory()
    {
        bankAccountTypeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper(offer.getValue()));
        bankAccountTypeColumn.setCellFactory(new Callback<TableColumn<String, OrderBookListItem>, TableCell<String, OrderBookListItem>>()
        {
            @Override
            public TableCell<String, OrderBookListItem> call(TableColumn<String, OrderBookListItem> directionColumn)
            {
                return new TableCell<String, OrderBookListItem>()
                {
                    @Override
                    public void updateItem(final OrderBookListItem orderBookListItem, boolean empty)
                    {
                        super.updateItem(orderBookListItem, empty);

                        if (orderBookListItem != null)
                        {
                            BankAccountTypeInfo.BankAccountType bankAccountType = orderBookListItem.getOffer().getBankAccountType();
                            setText(Localisation.get(bankAccountType.toString()));
                        }
                        else
                        {
                            setText("");
                        }
                    }
                };
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private double textInputToNumber(String oldValue, String newValue)
    {
        //TODO use regex.... or custom textfield component
        double d = 0.0;
        if (!newValue.equals(""))
        {
            try
            {
                DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(Locale.getDefault());
                d = decimalFormat.parse(newValue).doubleValue();
            } catch (ParseException e)
            {
                amount.setText(oldValue);
                d = BitSquareConverter.stringToDouble2(oldValue);
            }
        }
        return d;
    }

    private void updateVolume()
    {
        double a = textInputToNumber(amount.getText(), amount.getText());
        double p = textInputToNumber(price.getText(), price.getText());
        volume.setText(BitSquareFormatter.formatPrice(a * p));
    }
}

