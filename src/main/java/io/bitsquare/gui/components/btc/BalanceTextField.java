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

package io.bitsquare.gui.components.btc;

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.listeners.ConfidenceListener;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import io.bitsquare.gui.util.BSFormatter;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.TransactionConfidence;

import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BalanceTextField extends AnchorPane {
    private static final Logger log = LoggerFactory.getLogger(BalanceTextField.class);

    private final TextField balanceTextField;
    private final Tooltip progressIndicatorTooltip;
    private final ConfidenceProgressIndicator progressIndicator;

    private final Effect fundedEffect = new DropShadow(BlurType.GAUSSIAN, Color.GREEN, 4, 0.0, 0, 0);
    private final Effect notFundedEffect = new DropShadow(BlurType.GAUSSIAN, Color.ORANGERED, 4, 0.0, 0, 0);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BalanceTextField() {
        balanceTextField = new TextField();
        balanceTextField.setFocusTraversable(false);
        balanceTextField.setEditable(false);

        progressIndicator = new ConfidenceProgressIndicator();
        progressIndicator.setFocusTraversable(false);
        progressIndicator.setPrefSize(24, 24);
        progressIndicator.setId("funds-confidence");
        progressIndicator.setLayoutY(1);
        progressIndicator.setProgress(0);
        progressIndicator.setVisible(false);

        progressIndicatorTooltip = new Tooltip("-");
        Tooltip.install(progressIndicator, progressIndicatorTooltip);

        AnchorPane.setRightAnchor(progressIndicator, 0.0);
        AnchorPane.setRightAnchor(balanceTextField, 55.0);
        AnchorPane.setLeftAnchor(balanceTextField, 0.0);

        getChildren().addAll(balanceTextField, progressIndicator);
    }

    public void setup(WalletFacade walletFacade, Address address) {
        walletFacade.addConfidenceListener(new ConfidenceListener(address) {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                updateConfidence(confidence);
            }
        });
        updateConfidence(walletFacade.getConfidenceForAddress(address));

        walletFacade.addBalanceListener(new BalanceListener(address) {
            @Override
            public void onBalanceChanged(Coin balance) {
                updateBalance(balance);
            }
        });
        updateBalance(walletFacade.getBalanceForAddress(address));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateConfidence(TransactionConfidence confidence) {
        if (confidence != null) {
            switch (confidence.getConfidenceType()) {
                case UNKNOWN:
                    progressIndicatorTooltip.setText("Unknown transaction status");
                    progressIndicator.setProgress(0);
                    break;
                case PENDING:
                    progressIndicatorTooltip.setText(
                            "Seen by " + confidence.numBroadcastPeers() + " peer(s) / 0 " + "confirmations");
                    progressIndicator.setProgress(-1.0);
                    break;
                case BUILDING:
                    progressIndicatorTooltip.setText("Confirmed in " + confidence.getDepthInBlocks() + " block(s)");
                    progressIndicator.setProgress(Math.min(1, (double) confidence.getDepthInBlocks() / 6.0));
                    break;
                case DEAD:
                    progressIndicatorTooltip.setText("Transaction is invalid.");
                    progressIndicator.setProgress(0);
                    break;
            }

            if (progressIndicator.getProgress() != 0) {
                progressIndicator.setVisible(true);
                AnchorPane.setRightAnchor(progressIndicator, 0.0);
                AnchorPane.setRightAnchor(balanceTextField, 35.0);
            }
        }
    }

    private void updateBalance(Coin balance) {
        balanceTextField.setText(BSFormatter.formatCoin(balance));
        if (balance.isPositive())
            balanceTextField.setEffect(fundedEffect);
        else
            balanceTextField.setEffect(notFundedEffect);
    }

}
