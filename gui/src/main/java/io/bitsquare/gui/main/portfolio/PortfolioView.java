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

package io.bitsquare.gui.main.portfolio;

import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.CachingViewLoader;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.common.view.View;
import io.bitsquare.gui.common.view.ViewLoader;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.portfolio.closedtrades.ClosedTradesView;
import io.bitsquare.gui.main.portfolio.openoffer.OpenOffersView;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesView;
import io.bitsquare.trade.TradeManager;

import javax.inject.Inject;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;

@FxmlView
public class PortfolioView extends ActivatableViewAndModel<TabPane, Activatable> {

    @FXML Tab offersTab, openTradesTab, closedTradesTab;

    private Tab currentTab;
    private Navigation.Listener navigationListener;
    private ChangeListener<Tab> tabChangeListener;

    private final ViewLoader viewLoader;
    private final Navigation navigation;

    @Inject
    public PortfolioView(CachingViewLoader viewLoader, Navigation navigation, TradeManager tradeManager) {
        this.viewLoader = viewLoader;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        navigationListener = viewPath -> {
            if (viewPath.size() == 3 && viewPath.indexOf(PortfolioView.class) == 1)
                loadView(viewPath.tip());
        };

        tabChangeListener = (ov, oldValue, newValue) -> {
            if (newValue == offersTab)
                navigation.navigateTo(MainView.class, PortfolioView.class, OpenOffersView.class);
            else if (newValue == openTradesTab)
                navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class);
            else if (newValue == closedTradesTab)
                navigation.navigateTo(MainView.class, PortfolioView.class, ClosedTradesView.class);
        };
    }

    @Override
    public void doActivate() {
        root.getSelectionModel().selectedItemProperty().addListener(tabChangeListener);
        navigation.addListener(navigationListener);

      /*  if (tradeManager.getPendingTrades().size() == 0)
            navigation.navigateTo(MainView.class, PortfolioView.class, OffersView.class);
        else
            navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class);*/
    }

    @Override
    public void doDeactivate() {
        root.getSelectionModel().selectedItemProperty().removeListener(tabChangeListener);
        navigation.removeListener(navigationListener);
        currentTab = null;
    }

    private void loadView(Class<? extends View> viewClass) {
        // we want to get activate/deactivate called, so we remove the old view on tab change
        if (currentTab != null)
            currentTab.setContent(null);

        View view = viewLoader.load(viewClass);

        if (view instanceof OpenOffersView) currentTab = offersTab;
        else if (view instanceof PendingTradesView) currentTab = openTradesTab;
        else if (view instanceof ClosedTradesView) currentTab = closedTradesTab;

        currentTab.setContent(view.getRoot());
        root.getSelectionModel().select(currentTab);
    }
}

