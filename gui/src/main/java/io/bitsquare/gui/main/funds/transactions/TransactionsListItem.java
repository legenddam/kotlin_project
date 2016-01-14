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

package io.bitsquare.gui.main.funds.transactions;

import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.AddressConfidenceListener;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import io.bitsquare.gui.util.BSFormatter;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Tooltip;
import org.bitcoinj.core.*;

public class TransactionsListItem {


    private final StringProperty date = new SimpleStringProperty();
    private final StringProperty amount = new SimpleStringProperty();
    private final StringProperty type = new SimpleStringProperty();

    private final WalletService walletService;

    private final ConfidenceProgressIndicator progressIndicator;

    private final Tooltip tooltip;
    private String addressString;
    private AddressConfidenceListener confidenceListener;

    public TransactionsListItem(Transaction transaction, WalletService walletService, BSFormatter formatter) {
        this.walletService = walletService;

        Coin valueSentToMe = transaction.getValueSentToMe(walletService.getWallet());
        Coin valueSentFromMe = transaction.getValueSentFromMe(walletService.getWallet());
        Address address = null;
        if (valueSentToMe.isZero()) {
            amount.set("-" + formatter.formatCoin(valueSentFromMe));

            for (TransactionOutput transactionOutput : transaction.getOutputs()) {
                if (!transactionOutput.isMine(walletService.getWallet())) {
                    type.set("Sent to");

                    if (transactionOutput.getScriptPubKey().isSentToAddress()
                            || transactionOutput.getScriptPubKey().isPayToScriptHash()) {
                        address = transactionOutput.getScriptPubKey().getToAddress(walletService.getWallet().getParams());
                        addressString = address.toString();
                    }
                }
            }
        } else if (valueSentFromMe.isZero()) {
            amount.set(formatter.formatCoin(valueSentToMe));
            type.set("Received with");

            for (TransactionOutput transactionOutput : transaction.getOutputs()) {
                if (transactionOutput.isMine(walletService.getWallet())) {
                    if (transactionOutput.getScriptPubKey().isSentToAddress() ||
                            transactionOutput.getScriptPubKey().isPayToScriptHash()) {
                        address = transactionOutput.getScriptPubKey().getToAddress(walletService.getWallet().getParams());
                        addressString = address.toString();
                    }
                }
            }
        } else {
            amount.set(formatter.formatCoin(valueSentToMe.subtract(valueSentFromMe)));
            boolean outgoing = false;
            for (TransactionOutput transactionOutput : transaction.getOutputs()) {
                if (!transactionOutput.isMine(walletService.getWallet())) {
                    outgoing = true;
                    if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey()
                            .isPayToScriptHash()) {
                        address = transactionOutput.getScriptPubKey().getToAddress(walletService.getWallet().getParams());
                        addressString = address.toString();
                    }
                }
            }

            if (outgoing) {
                type.set("Sent to");
            } else {
                type.set("Internal (TX Fee)");
                //addressString = "Internal swap between addresses.";
            }
        }

        date.set(formatter.formatDateTime(transaction.getUpdateTime()));

        // confidence
        progressIndicator = new ConfidenceProgressIndicator();
        progressIndicator.setId("funds-confidence");
        tooltip = new Tooltip("Not used yet");
        progressIndicator.setProgress(0);
        progressIndicator.setPrefHeight(30);
        progressIndicator.setPrefWidth(30);
        Tooltip.install(progressIndicator, tooltip);

        if (address != null) {
            confidenceListener = walletService.addAddressConfidenceListener(new AddressConfidenceListener(address) {
                @Override
                public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                    updateConfidence(confidence);
                }
            });

            updateConfidence(walletService.getConfidenceForAddress(address));
        }
    }


    public void cleanup() {
        walletService.removeAddressConfidenceListener(confidenceListener);
    }

    private void updateConfidence(TransactionConfidence confidence) {
        if (confidence != null) {
            //log.debug("Type numBroadcastPeers getDepthInBlocks " + confidence.getConfidenceType() + " / " +
            // confidence.numBroadcastPeers() + " / " + confidence.getDepthInBlocks());
            switch (confidence.getConfidenceType()) {
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


    public ConfidenceProgressIndicator getProgressIndicator() {
        return progressIndicator;
    }


    public final StringProperty dateProperty() {
        return this.date;
    }


    public final StringProperty amountProperty() {
        return this.amount;
    }


    public final StringProperty typeProperty() {
        return this.type;
    }

    public String getAddressString() {
        return addressString;
    }

    public boolean isNotAnAddress() {
        return addressString == null;
    }
}

