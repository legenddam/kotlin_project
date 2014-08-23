package io.bitsquare.gui.orders;

import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.Hibernate;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.NavigationItem;
import io.bitsquare.gui.components.CachingTabPane;
import io.bitsquare.storage.Persistence;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrdersController implements Initializable, ChildController, NavigationController, Hibernate
{
    private static final Logger log = LoggerFactory.getLogger(OrdersController.class);
    private static int SELECTED_TAB_INDEX = -1;
    private static OrdersController INSTANCE;
    private final Persistence persistence;
    private ChildController childController;

    @FXML
    private CachingTabPane tabPane;

    @Inject
    private OrdersController(Persistence persistence)
    {
        this.persistence = persistence;
        INSTANCE = this;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static OrdersController GET_INSTANCE()
    {
        return INSTANCE;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setSelectedTabIndex(int index)
    {
        log.trace("setSelectedTabIndex " + index);
        tabPane.setSelectedTabIndex(index);
        persistence.write(this, "selectedTabIndex", index);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        log.trace("initialize ");
        tabPane.initialize(this, persistence, NavigationItem.OFFER.getFxmlUrl(), NavigationItem.PENDING_TRADE.getFxmlUrl(), NavigationItem.CLOSED_TRADE.getFxmlUrl());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
    }

    @Override
    public void cleanup()
    {
        tabPane.cleanup();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: NavigationController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ChildController navigateToView(NavigationItem navigationItem)
    {
        childController = tabPane.navigateToView(navigationItem.getFxmlUrl());
        return childController;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Hibernate
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void sleep()
    {
        if (childController instanceof Hibernate)
            ((Hibernate) childController).sleep();
    }

    @Override
    public void awake()
    {
        if (childController instanceof Hibernate)
            ((Hibernate) childController).awake();
    }
    
}

