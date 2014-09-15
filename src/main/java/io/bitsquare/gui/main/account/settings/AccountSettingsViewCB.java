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

package io.bitsquare.gui.main.account.settings;

import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.NavigationManager;
import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.gui.main.account.content.ContextAware;
import io.bitsquare.util.ViewLoader;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountSettingsViewCB extends CachedViewCB<AccountSettingsPM> {

    private static final Logger log = LoggerFactory.getLogger(AccountSettingsViewCB.class);

    public NavigationItem subMenuNavigationItem;

    public VBox leftVBox;
    public AnchorPane content;
    private MenuItem seedWords, password, restrictions, fiatAccount, registration;
    private NavigationManager navigationManager;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private AccountSettingsViewCB(AccountSettingsPM presentationModel, NavigationManager navigationManager) {
        super(presentationModel);

        this.navigationManager = navigationManager;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        ToggleGroup toggleGroup = new ToggleGroup();
        seedWords = new MenuItem(this, content, "Wallet seed",
                NavigationItem.SEED_WORDS, toggleGroup);
        password = new MenuItem(this, content, "Wallet password",
                NavigationItem.CHANGE_PASSWORD, toggleGroup);
        restrictions = new MenuItem(this, content, "Trading restrictions",
                NavigationItem.RESTRICTIONS, toggleGroup);
        fiatAccount = new MenuItem(this, content, "Payments account(s)",
                NavigationItem.FIAT_ACCOUNT, toggleGroup);
        registration = new MenuItem(this, content, "Renew your account",
                NavigationItem.REGISTRATION, toggleGroup);

        registration.setDisable(true);

        leftVBox.getChildren().addAll(seedWords, password,
                restrictions, fiatAccount, registration);


    }

    @Override
    public void activate() {
        super.activate();

        NavigationItem[] navigationItems = navigationManager.getCurrentNavigationItems();
        for (int i = 0; i < navigationItems.length; i++) {
            if (navigationItems[i].getLevel() == 3) {
                subMenuNavigationItem = navigationItems[i];
                break;
            }
        }

        if (subMenuNavigationItem == null)
            subMenuNavigationItem = NavigationItem.SEED_WORDS;

        loadView(subMenuNavigationItem);

        switch (subMenuNavigationItem) {
            case SEED_WORDS:
                seedWords.setSelected(true);
                break;
            case CHANGE_PASSWORD:
                password.setSelected(true);
                break;
            case RESTRICTIONS:
                restrictions.setSelected(true);
                break;
            case FIAT_ACCOUNT:
                fiatAccount.setSelected(true);
                break;
            case REGISTRATION:
                registration.setSelected(true);
                break;
            default:
                log.error(subMenuNavigationItem.getFxmlUrl() + " is no subMenuNavigationItem");
                break;
        }
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Initializable loadView(NavigationItem navigationItem) {
        final ViewLoader loader = new ViewLoader(getClass().getResource(navigationItem.getFxmlUrl()));
        try {
            final Pane view = loader.load();
            content.getChildren().setAll(view);
            childController = loader.getController();
            ((ViewCB<? extends PresentationModel>) childController).setParent(this);
            ((ContextAware) childController).useSettingsContext(true);
            return childController;
        } catch (IOException e) {
            log.error("Loading view failed. FxmlUrl = " + navigationItem.getFxmlUrl());
            e.getStackTrace();
        }
        return null;
    }
}

class MenuItem extends ToggleButton {
    private static final Logger log = LoggerFactory.getLogger(MenuItem.class);

    private ViewCB<? extends PresentationModel> childController;

    private final AccountSettingsViewCB parentCB;
    private final Parent content;
    private final NavigationItem navigationItem;

    MenuItem(AccountSettingsViewCB parentCB, Parent content, String title, NavigationItem navigationItem,
             ToggleGroup toggleGroup) {
        this.parentCB = parentCB;
        this.content = content;
        this.navigationItem = navigationItem;

        setToggleGroup(toggleGroup);
        setText(title);
        setId("account-settings-item-background-active");
        setPrefHeight(40);
        setPrefWidth(200);
        setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label();
        icon.setTextFill(Paint.valueOf("#999"));
        if (navigationItem.equals(NavigationItem.SEED_WORDS))
            AwesomeDude.setIcon(icon, AwesomeIcon.INFO_SIGN);
        else if (navigationItem.equals(NavigationItem.REGISTRATION))
            AwesomeDude.setIcon(icon, AwesomeIcon.BRIEFCASE);
        else
            AwesomeDude.setIcon(icon, AwesomeIcon.EDIT_SIGN);

        setGraphic(icon);

        setOnAction((event) -> parentCB.loadView(navigationItem));

        selectedProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                setId("account-settings-item-background-selected");
                icon.setTextFill(Paint.valueOf("#0096c9"));
            }
            else {
                setId("account-settings-item-background-active");
                icon.setTextFill(Paint.valueOf("#999"));
            }
        });

        disableProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                setId("account-settings-item-background-disabled");
                icon.setTextFill(Paint.valueOf("#ccc"));
            }
            else {
                setId("account-settings-item-background-active");
                icon.setTextFill(Paint.valueOf("#999"));
            }
        });
    }
}

