/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao.compensation;

import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import io.bisq.common.UserThread;
import io.bisq.common.app.DevEnv;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.common.storage.Storage;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.dao.DaoPeriodService;
import io.bisq.core.dao.blockchain.BsqBlockChainListener;
import io.bisq.core.dao.blockchain.parse.PeriodVerification;
import io.bisq.core.dao.blockchain.parse.VotingVerification;
import io.bisq.core.dao.vote.VotingDefaultValues;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.storage.HashMapChangedListener;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import lombok.Getter;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.util.List;
import java.util.Optional;

public class CompensationRequestManager implements PersistedDataHost, BsqBlockChainListener {
    private static final Logger log = LoggerFactory.getLogger(CompensationRequestManager.class);

    private static final int GENESIS_BLOCK_HEIGHT = 391; // TODO dev version regtest

    private final P2PService p2PService;
    private final DaoPeriodService daoPeriodService;
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final VotingDefaultValues votingDefaultValues;
    private final PeriodVerification periodVerification;
    private final VotingVerification votingVerification;

    private final KeyRing keyRing;
    private final Storage<CompensationRequestList> compensationRequestsStorage;

    private CompensationRequest selectedCompensationRequest;
    @Getter
    private final ObservableList<CompensationRequest> allRequests = FXCollections.observableArrayList();
    @Getter
    private final FilteredList<CompensationRequest> activeRequests = new FilteredList<>(allRequests);
    @Getter
    private final FilteredList<CompensationRequest> pastRequests = new FilteredList<>(allRequests);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CompensationRequestManager(P2PService p2PService,
                                      BtcWalletService btcWalletService,
                                      BsqWalletService bsqWalletService,
                                      DaoPeriodService daoPeriodService,
                                      VotingDefaultValues votingDefaultValues,
                                      PeriodVerification periodVerification,
                                      VotingVerification votingVerification,
                                      KeyRing keyRing,
                                      Storage<CompensationRequestList> compensationRequestsStorage) {
        this.p2PService = p2PService;
        this.daoPeriodService = daoPeriodService;
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.votingDefaultValues = votingDefaultValues;
        this.periodVerification = periodVerification;
        this.votingVerification = votingVerification;
        this.keyRing = keyRing;
        this.compensationRequestsStorage = compensationRequestsStorage;
    }

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            CompensationRequestList persisted = compensationRequestsStorage.initAndGetPersistedWithFileName("CompensationRequestList", 100);
            if (persisted != null)
                setPersistedCompensationRequest(persisted.getList());
        }
    }

    public void onAllServicesInitialized() {
        /*if (daoPeriodService.getPhase() == DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS) {

        }*/


        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            p2PService.addHashSetChangedListener(new HashMapChangedListener() {
                @Override
                public void onAdded(ProtectedStorageEntry data) {
                    final ProtectedStoragePayload protectedStoragePayload = data.getProtectedStoragePayload();
                    if (protectedStoragePayload instanceof CompensationRequestPayload)
                        addCompensationRequestPayload((CompensationRequestPayload) protectedStoragePayload, true);
                }

                @Override
                public void onRemoved(ProtectedStorageEntry data) {
                    final ProtectedStoragePayload protectedStoragePayload = data.getProtectedStoragePayload();
                    if (protectedStoragePayload instanceof CompensationRequestPayload) {
                        findCompensationRequest((CompensationRequestPayload) protectedStoragePayload).ifPresent(compensationRequest -> {
                            if (daoPeriodService.isInCompensationRequestPhase(compensationRequest)) {
                                removeCompensationRequestFromList(compensationRequest);
                                compensationRequestsStorage.queueUpForSave(new CompensationRequestList(getAllRequests()), 500);
                            } else {
                                final String msg = "onRemoved called of a CompensationRequest which is outside of the CompensationRequest phase is invalid and we ignore it.";
                                log.warn(msg);
                                if (DevEnv.DEV_MODE)
                                    throw new RuntimeException(msg);
                            }
                        });
                    }
                }
            });

            // At startup the P2PDataStorage initializes earlier, otherwise we ge the listener called.
            p2PService.getP2PDataStorage().getMap().values().forEach(e -> {
                final ProtectedStoragePayload protectedStoragePayload = e.getProtectedStoragePayload();
                if (protectedStoragePayload instanceof CompensationRequestPayload)
                    addCompensationRequestPayload((CompensationRequestPayload) protectedStoragePayload, false);
            });
        }

        // TODO optimize (only own?)
        // Republish
        PublicKey signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
        UserThread.runAfter(() -> {
            activeRequests.stream()
                    .filter(e -> e.getCompensationRequestPayload().getOwnerPubKey().equals(signaturePubKey))
                    .forEach(e -> addToP2PNetwork(e.getCompensationRequestPayload()));
        }, 1); // TODO increase delay to about 30 sec.

        bsqWalletService.addNewBestBlockListener(block -> {
            onChainHeightChanged();
        });
        onChainHeightChanged();
    }

    @Override
    public void onBsqBlockChainChanged() {
        updateFilteredLists();
    }

    private void onChainHeightChanged() {
        updateFilteredLists();
    }

    public void addToP2PNetwork(CompensationRequestPayload compensationRequestPayload) {
        p2PService.addProtectedStorageEntry(compensationRequestPayload, true);
    }

    private void addCompensationRequestPayload(CompensationRequestPayload compensationRequestPayload, boolean storeLocally) {
        if (!contains(compensationRequestPayload)) {
            addCompensationRequest(new CompensationRequest(compensationRequestPayload));
            if (storeLocally)
                compensationRequestsStorage.queueUpForSave(new CompensationRequestList(getAllRequests()), 500);
        } else {
            log.warn("We have already an item with the same CompensationRequest.");
        }
    }

    public boolean removeCompensationRequest(CompensationRequest compensationRequest) {
        if (daoPeriodService.isInCompensationRequestPhase(compensationRequest)) {
            if (isMyCompensationRequest(compensationRequest)) {
                removeCompensationRequestFromList(compensationRequest);
                compensationRequestsStorage.queueUpForSave(new CompensationRequestList(getAllRequests()), 500);
                return p2PService.removeData(compensationRequest.getCompensationRequestPayload(), true);
            } else {
                final String msg = "removeCompensationRequest called for a CompensationRequest which is not ours.";
                log.warn(msg);
                if (DevEnv.DEV_MODE)
                    throw new RuntimeException(msg);
                return false;
            }
        } else {
            final String msg = "removeCompensationRequest called with a CompensationRequest which is outside of the CompensationRequest phase.";
            log.warn(msg);
            if (DevEnv.DEV_MODE)
                throw new RuntimeException(msg);
            return false;
        }
    }

    private void removeCompensationRequestFromList(CompensationRequest compensationRequest) {
        allRequests.remove(compensationRequest);
        updateFilteredLists();
    }

    public boolean isMyCompensationRequest(CompensationRequest compensationRequest) {
        return keyRing.getPubKeyRing().getSignaturePubKey().equals(compensationRequest.getCompensationRequestPayload().getOwnerPubKey());
    }

    private boolean contains(CompensationRequestPayload compensationRequestPayload) {
        return allRequests.stream().filter(e -> e.getCompensationRequestPayload().equals(compensationRequestPayload)).findAny().isPresent();
    }

    public List<CompensationRequest> getCompensationRequestsList() {
        return allRequests;
    }

    public void fundCompensationRequest(CompensationRequest compensationRequest, Coin amount, FutureCallback<Transaction> callback) {
        btcWalletService.fundCompensationRequest(amount, compensationRequest.getCompensationRequestPayload().getBsqAddress(), bsqWalletService.getUnusedAddress(), callback);
    }

    public void setSelectedCompensationRequest(CompensationRequest selectedCompensationRequest) {
        this.selectedCompensationRequest = selectedCompensationRequest;
    }

    public CompensationRequest getSelectedCompensationRequest() {
        return selectedCompensationRequest;
    }

    private void updateFilteredLists() {
        activeRequests.setPredicate(daoPeriodService::isInCurrentCycle);
        pastRequests.setPredicate(compensationRequest -> {
            return !daoPeriodService.isInCurrentCycle(compensationRequest);
        });
    }

    private void setPersistedCompensationRequest(List<CompensationRequest> list) {
        this.allRequests.clear();
        this.allRequests.addAll(list);
        updateFilteredLists();
    }

    public Optional<CompensationRequest> findByAddress(String address) {
        return allRequests.stream()
                .filter(e -> e.getCompensationRequestPayload().getBsqAddress().equals(address))
                .findAny();
    }

    private void addCompensationRequest(CompensationRequest compensationRequest) {
        allRequests.add(compensationRequest);
        updateFilteredLists();
    }

    private Optional<CompensationRequest> findCompensationRequest(CompensationRequestPayload compensationRequestPayload) {
        return allRequests.stream().filter(e -> e.getCompensationRequestPayload().equals(compensationRequestPayload)).findAny();
    }
}
