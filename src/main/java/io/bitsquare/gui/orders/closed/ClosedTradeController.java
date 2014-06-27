package io.bitsquare.gui.orders.closed;

import com.google.inject.Inject;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.Hibernate;
import io.bitsquare.gui.NavigationController;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.Initializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClosedTradeController implements Initializable, ChildController, Hibernate
{
    private static final Logger log = LoggerFactory.getLogger(ClosedTradeController.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ClosedTradeController()
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

