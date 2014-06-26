package io.bitsquare.gui.funds;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.TransactionConfidence;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.listeners.ConfidenceListener;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

import java.math.BigInteger;

public class AddressListItem
{
    private final StringProperty addressString = new SimpleStringProperty();
    private final BalanceListener balanceListener;
    private final Label balanceLabel;
    private AddressEntry addressEntry;
    private WalletFacade walletFacade;
    private ConfidenceListener confidenceListener;
    private ConfidenceProgressIndicator progressIndicator;
    private Tooltip tooltip;

    public AddressListItem(AddressEntry addressEntry, WalletFacade walletFacade)
    {
        this.addressEntry = addressEntry;
        this.walletFacade = walletFacade;
        this.addressString.set(getAddress().toString());

        progressIndicator = new ConfidenceProgressIndicator();
        progressIndicator.setId("funds-confidence");
        tooltip = new Tooltip("Not used yet");
        progressIndicator.setProgress(0);
        progressIndicator.setPrefHeight(30);
        progressIndicator.setPrefWidth(30);
        Tooltip.install(progressIndicator, tooltip);

        confidenceListener = walletFacade.addConfidenceListener(new ConfidenceListener(getAddress())
        {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence)
            {
                updateConfidence(confidence);
            }
        });

        updateConfidence(walletFacade.getConfidenceForAddress(getAddress()));


        balanceListener = new BalanceListener(getAddress());
        balanceLabel = new Label();
        walletFacade.addBalanceListener(new BalanceListener(getAddress())
        {
            @Override
            public void onBalanceChanged(BigInteger balance)
            {
                updateBalance(balance);
            }
        });

        updateBalance(walletFacade.getBalanceForAddress(getAddress()));
    }

    public void cleanup()
    {
        walletFacade.removeConfidenceListener(confidenceListener);
        walletFacade.removeBalanceListener(balanceListener);
    }

    private void updateBalance(BigInteger balance)
    {
        if (balance != null)
        {
            balanceLabel.setText(BtcFormatter.btcToString(balance));
        }
    }

    private void updateConfidence(TransactionConfidence confidence)
    {
        if (confidence != null)
        {
            //log.debug("Type numBroadcastPeers getDepthInBlocks " + confidence.getConfidenceType() + " / " + confidence.numBroadcastPeers() + " / " + confidence.getDepthInBlocks());
            switch (confidence.getConfidenceType())
            {
                case UNKNOWN:
                    tooltip.setText("Unknown transaction status");
                    progressIndicator.setProgress(0);
                    break;
                case PENDING:
                    tooltip.setText("Seen by " + confidence.numBroadcastPeers() + " peer(s) / 0 confirmations");
                    progressIndicator.setProgress(-1.0);
                    break;
                case BUILDING:
                    tooltip.setText("Confirmed in " + confidence.getDepthInBlocks() + " block(s)");
                    progressIndicator.setProgress(Math.min(1, (double) confidence.getDepthInBlocks() / 6.0));
                    break;
                case DEAD:
                    tooltip.setText("Transaction is invalid.");
                    progressIndicator.setProgress(0);
                    break;
            }

            progressIndicator.setPrefSize(24, 24);
        }
    }

    public final String getLabel()
    {
        switch (addressEntry.getAddressContext())
        {
            case REGISTRATION_FEE:
                return "Registration fee";
            case TRADE:
                if (addressEntry.getTradeId() != null)
                    return "Trade ID: " + addressEntry.getTradeId();
                else
                    return "Trade (not used yet)";
            case ARBITRATOR_DEPOSIT:
                return "Arbitration deposit";
        }
        return "";
    }

    public final StringProperty addressStringProperty()
    {
        return this.addressString;
    }


    public Address getAddress()
    {
        return addressEntry.getAddress();
    }

    public AddressEntry getAddressEntry()
    {
        return addressEntry;
    }

    public ConfidenceListener getConfidenceListener()
    {
        return confidenceListener;
    }

    public ConfidenceProgressIndicator getProgressIndicator()
    {
        return progressIndicator;
    }

    public Tooltip getTooltip()
    {
        return tooltip;
    }

    public BalanceListener getBalanceListener()
    {
        return balanceListener;
    }

    public Label getBalanceLabel()
    {
        return balanceLabel;
    }
}
