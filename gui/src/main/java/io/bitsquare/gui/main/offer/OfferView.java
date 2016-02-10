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

package io.bitsquare.gui.main.offer;

import io.bitsquare.btc.pricefeed.MarketPriceFeed;
import io.bitsquare.common.UserThread;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.View;
import io.bitsquare.gui.common.view.ViewLoader;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.offer.createoffer.CreateOfferView;
import io.bitsquare.gui.main.offer.offerbook.OfferBookView;
import io.bitsquare.gui.main.offer.takeoffer.TakeOfferView;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.trade.offer.Offer;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;

import java.util.List;

public abstract class OfferView extends ActivatableView<TabPane, Void> {

    private OfferBookView offerBookView;
    private CreateOfferView createOfferView;
    private TakeOfferView takeOfferView;
    private AnchorPane createOfferPane;
    private AnchorPane takeOfferPane;
    private Navigation.Listener listener;
    private Offer offer;

    private final ViewLoader viewLoader;
    private final Navigation navigation;
    private MarketPriceFeed marketPriceFeed;
    private final Offer.Direction direction;
    private Tab takeOfferTab, createOfferTab, offerBookTab;
    private TradeCurrency tradeCurrency;
    private boolean createOfferViewOpen, takeOfferViewOpen;

    protected OfferView(ViewLoader viewLoader, Navigation navigation, MarketPriceFeed marketPriceFeed) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
        this.marketPriceFeed = marketPriceFeed;
        this.direction = (this instanceof BuyOfferView) ? Offer.Direction.BUY : Offer.Direction.SELL;
    }

    @Override
    protected void initialize() {
        listener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(this.getClass()) == 1)
                loadView(viewPath.tip());
        };
    }

    @Override
    protected void activate() {
        // We need to remove open validation error popups
        // UserThread.execute needed as focus-out event is called after selectedIndexProperty changed
        // TODO Find a way to do that in the InputTextField directly, but a tab change does not trigger any event...
        TabPane tabPane = root;
        tabPane.getSelectionModel().selectedItemProperty()
                .addListener((observableValue, oldValue, newValue) -> {
                    UserThread.execute(InputTextField::hideErrorMessageDisplay);
                    if (newValue != null) {
                        if (newValue.equals(createOfferTab) && createOfferView != null) {
                            createOfferView.onTabSelected(true);
                        } else if (newValue.equals(takeOfferTab) && takeOfferView != null) {
                            takeOfferView.onTabSelected(true);
                        } else if (newValue.equals(offerBookTab) && offerBookView != null) {
                            offerBookView.onTabSelected(true);
                        }
                    }
                    if (oldValue != null) {
                        if (oldValue.equals(createOfferTab) && createOfferView != null) {
                            createOfferView.onTabSelected(false);
                        } else if (oldValue.equals(takeOfferTab) && takeOfferView != null) {
                            takeOfferView.onTabSelected(false);
                        } else if (oldValue.equals(offerBookTab) && offerBookView != null) {
                            offerBookView.onTabSelected(false);
                        }
                    }
                });

        // We want to get informed when a tab get closed
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            change.next();
            List<? extends Tab> removedTabs = change.getRemoved();
            if (removedTabs.size() == 1) {
                if (removedTabs.get(0).getContent().equals(createOfferPane))
                    onCreateOfferViewRemoved();
                else if (removedTabs.get(0).getContent().equals(takeOfferPane))
                    onTakeOfferViewRemoved();
            }
        });

        tradeCurrency = CurrencyUtil.getDefaultTradeCurrency();

        navigation.addListener(listener);
        navigation.navigateTo(MainView.class, this.getClass(), OfferBookView.class);
    }

    @Override
    protected void deactivate() {
        navigation.removeListener(listener);
    }

    private void loadView(Class<? extends View> viewClass) {
        TabPane tabPane = root;
        View view;
        boolean isBuy = direction == Offer.Direction.BUY;

        marketPriceFeed.setType(isBuy ? MarketPriceFeed.Type.ASK : MarketPriceFeed.Type.BID);

        if (viewClass == OfferBookView.class && offerBookView == null) {
            view = viewLoader.load(viewClass);
            // Offerbook must not be cached by ViewLoader as we use 2 instances for sell and buy screens.
            offerBookTab = new Tab(isBuy ? "Buy bitcoin" : "Sell bitcoin");
            offerBookTab.setClosable(false);
            offerBookTab.setContent(view.getRoot());
            tabPane.getTabs().add(offerBookTab);
            offerBookView = (OfferBookView) view;
            offerBookView.onTabSelected(true);
            
            OfferActionHandler offerActionHandler = new OfferActionHandler() {
                @Override
                public void onCreateOffer(TradeCurrency tradeCurrency) {
                    if (!createOfferViewOpen) {
                        OfferView.this.createOfferViewOpen = true;
                        OfferView.this.tradeCurrency = tradeCurrency;
                        OfferView.this.navigation.navigateTo(MainView.class, OfferView.this.getClass(),
                                CreateOfferView.class);
                    } else {
                        new Popup().information("You have already a \"Create offer\" tab open.").show();
                    }
                }

                @Override
                public void onTakeOffer(Offer offer) {
                    if (!takeOfferViewOpen) {
                        OfferView.this.takeOfferViewOpen = true;
                        OfferView.this.offer = offer;
                        OfferView.this.navigation.navigateTo(MainView.class, OfferView.this.getClass(),
                                TakeOfferView.class);
                    } else {
                        new Popup().information("You have already a \"Take offer\" tab open.").show();
                    }
                }
            };
            offerBookView.setOfferActionHandler(offerActionHandler);

            offerBookView.setDirection(direction);
        } else if (viewClass == CreateOfferView.class && createOfferView == null) {
            view = viewLoader.load(viewClass);
            // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
            // in different graphs
            createOfferView = (CreateOfferView) view;
            createOfferView.initWithData(direction, tradeCurrency);
            createOfferPane = createOfferView.getRoot();
            createOfferTab = new Tab(getCreateOfferTabName());
            // close handler from close on create offer action
            createOfferView.setCloseHandler(() -> tabPane.getTabs().remove(createOfferTab));
            createOfferTab.setContent(createOfferPane);
            tabPane.getTabs().add(createOfferTab);
            tabPane.getSelectionModel().select(createOfferTab);
        } else if (viewClass == TakeOfferView.class && takeOfferView == null && offer != null) {
            view = viewLoader.load(viewClass);
            // CreateOffer and TakeOffer must not be cached by ViewLoader as we cannot use a view multiple times
            // in different graphs
            takeOfferView = (TakeOfferView) view;
            takeOfferView.initWithData(offer);
            takeOfferPane = ((TakeOfferView) view).getRoot();
            takeOfferTab = new Tab(getTakeOfferTabName());
            // close handler from close on take offer action
            takeOfferView.setCloseHandler(() -> tabPane.getTabs().remove(takeOfferTab));
            takeOfferTab.setContent(takeOfferPane);
            tabPane.getTabs().add(takeOfferTab);
            tabPane.getSelectionModel().select(takeOfferTab);
        }
    }

    protected abstract String getCreateOfferTabName();

    protected abstract String getTakeOfferTabName();


    private void onCreateOfferViewRemoved() {
        createOfferViewOpen = false;
        if (createOfferView != null) {
            createOfferView.onClose();
            createOfferView = null;
        }
        offerBookView.enableCreateOfferButton();

        // update the navigation state
        navigation.navigateTo(MainView.class, this.getClass(), OfferBookView.class);
    }

    private void onTakeOfferViewRemoved() {
        offer = null;
        takeOfferViewOpen = false;
        if (takeOfferView != null) {
            takeOfferView.onClose();
            takeOfferView = null;
        }

        // update the navigation state
        navigation.navigateTo(MainView.class, this.getClass(), OfferBookView.class);
    }

    public interface OfferActionHandler {
        void onCreateOffer(TradeCurrency tradeCurrency);

        void onTakeOffer(Offer offer);
    }

    public interface CloseHandler {
        void close();
    }
}

