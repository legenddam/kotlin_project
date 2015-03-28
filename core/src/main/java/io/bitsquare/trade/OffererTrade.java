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

package io.bitsquare.trade;

import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.Peer;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.protocol.trade.ProcessModel;
import io.bitsquare.trade.protocol.trade.offerer.OffererProtocol;
import io.bitsquare.trade.protocol.trade.offerer.models.OffererProcessModel;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.utils.Fiat;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import javax.annotation.Nullable;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererTrade extends Trade implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(OffererTrade.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum OffererLifeCycleState implements LifeCycleState {
        OFFER_OPEN,
        OFFER_RESERVED,
        OFFER_CANCELED,
        PENDING,
        COMPLETED,
        FAILED
    }

    public enum OffererProcessState implements ProcessState {
        UNDEFINED,
        DEPOSIT_PUBLISHED,
        DEPOSIT_CONFIRMED,

        FIAT_PAYMENT_STARTED,

        PAYOUT_PUBLISHED,

        MESSAGE_SENDING_FAILED,
        EXCEPTION
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Transient/Immutable
    transient private ObjectProperty<OffererProcessState> processStateProperty;
    transient private ObjectProperty<OffererLifeCycleState> lifeCycleStateProperty;

    // Mutable
    private Coin tradeAmount;
    private Peer tradingPeer;
    private OffererProcessState processState;
    private OffererLifeCycleState lifeCycleState;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OffererTrade(Offer offer, Storage<? extends TradeList> storage) {
        super(offer, storage);
        log.trace("Created by constructor");

        processState = OffererProcessState.UNDEFINED;
        lifeCycleState = OffererLifeCycleState.OFFER_OPEN;

        processStateProperty = new SimpleObjectProperty<>(processState);
        lifeCycleStateProperty = new SimpleObjectProperty<>(lifeCycleState);

        tradeAmountProperty = new SimpleObjectProperty<>();
        tradeVolumeProperty = new SimpleObjectProperty<>();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        log.trace("Created from serialized form.");

        processStateProperty = new SimpleObjectProperty<>(processState);
        lifeCycleStateProperty = new SimpleObjectProperty<>(lifeCycleState);

        tradeAmountProperty = new SimpleObjectProperty<>();
        tradeVolumeProperty = new SimpleObjectProperty<>();

        if (tradeAmount != null) {
            tradeAmountProperty.set(tradeAmount);
            tradeVolumeProperty.set(getTradeVolume());
        }
    }

    @Override
    protected ProcessModel createProcessModel() {
        return new OffererProcessModel();
    }

    @Override
    public void createProtocol() {
        protocol = new OffererProtocol(this);
    }

    public void onFiatPaymentStarted() {
        assert protocol != null;
        ((OffererProtocol) protocol).onFiatPaymentStarted();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter only
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ReadOnlyObjectProperty<OffererProcessState> processStateProperty() {
        return processStateProperty;
    }

    @Override
    public ReadOnlyObjectProperty<OffererLifeCycleState> lifeCycleStateProperty() {
        return lifeCycleStateProperty;
    }

    public OffererProcessModel getProcessModel() {
        return (OffererProcessModel) processModel;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setProcessState(OffererProcessState processState) {
        this.processState = processState;
        processStateProperty.set(processState);

        switch (processState) {
            case EXCEPTION:
                disposeProtocol();
                setLifeCycleState(OffererLifeCycleState.FAILED);
                break;
        }
    }

    public void setLifeCycleState(OffererLifeCycleState lifeCycleState) {
        switch (lifeCycleState) {
            case FAILED:
                disposeProtocol();
                break;
            case COMPLETED:
                disposeProtocol();
                break;
        }
        this.lifeCycleState = lifeCycleState;
        lifeCycleStateProperty.set(lifeCycleState);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter/Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setTradingPeer(Peer tradingPeer) {
        this.tradingPeer = tradingPeer;
    }

    @Nullable
    public Peer getTradingPeer() {
        return tradingPeer;
    }

    public void setTradeAmount(Coin tradeAmount) {
        this.tradeAmount = tradeAmount;
        tradeAmountProperty.set(tradeAmount);
        tradeVolumeProperty.set(getTradeVolume());
    }

    @Nullable
    @Override
    public Coin getTradeAmount() {
        return tradeAmount;
    }

    @Nullable
    @Override
    public Fiat getTradeVolume() {
        if (tradeAmount != null)
            return offer.getVolumeByAmount(tradeAmount);
        else
            return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void setupConfidenceListener() {
        assert depositTx != null;
        TransactionConfidence transactionConfidence = depositTx.getConfidence();
        ListenableFuture<TransactionConfidence> future = transactionConfidence.getDepthFuture(1);
        Futures.addCallback(future, new FutureCallback<TransactionConfidence>() {
            @Override
            public void onSuccess(TransactionConfidence result) {
                if (processState.ordinal() < OffererProcessState.DEPOSIT_CONFIRMED.ordinal())
                    setProcessState(OffererProcessState.DEPOSIT_CONFIRMED);
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                t.printStackTrace();
                log.error(t.getMessage());
                Throwables.propagate(t);
            }
        });
    }

    @Override
    public String toString() {
        return "OffererTrade{" +
                "tradeAmount=" + tradeAmount +
                ", tradingPeer=" + tradingPeer +
                ", processState=" + processState +
                ", lifeCycleState=" + lifeCycleState +
                '}';
    }
}
