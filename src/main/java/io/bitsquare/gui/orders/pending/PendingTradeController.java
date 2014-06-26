package io.bitsquare.gui.orders.pending;

import com.google.inject.Inject;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.Hibernate;
import io.bitsquare.gui.NavigationController;
import javafx.fxml.Initializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

public class PendingTradeController implements Initializable, ChildController, Hibernate
{
    private static final Logger log = LoggerFactory.getLogger(PendingTradeController.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradeController()
    {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        log.debug("setNavigationController" + this);
    }

    @Override
    public void cleanup()
    {
        log.debug("cleanup" + this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Hibernate
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void sleep()
    {
        cleanup();
    }

    @Override
    public void awake()
    {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // GUI Event handlers
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}

