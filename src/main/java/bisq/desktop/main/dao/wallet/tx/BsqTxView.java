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

package bisq.desktop.main.dao.wallet.tx;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AddressWithIconAndDirection;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.main.dao.wallet.BsqBalanceUtil;
import bisq.desktop.util.BsqFormatter;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.consensus.period.PeriodStateChangeListener;
import bisq.core.dao.consensus.state.blockchain.Tx;
import bisq.core.dao.consensus.state.blockchain.TxType;
import bisq.core.dao.presentation.period.PeriodServiceFacade;
import bisq.core.dao.presentation.state.StateServiceFacade;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@FxmlView
public class BsqTxView extends ActivatableView<GridPane, Void> implements BsqBalanceListener, PeriodStateChangeListener {

    private TableView<BsqTxListItem> tableView;
    private Pane rootParent;

    private final BsqFormatter bsqFormatter;
    private final BsqWalletService bsqWalletService;
    private final StateServiceFacade stateServiceFacade;
    private final PeriodServiceFacade periodServiceFacade;
    private final BtcWalletService btcWalletService;
    private final BsqBalanceUtil bsqBalanceUtil;
    private final Preferences preferences;
    private final ObservableList<BsqTxListItem> observableList = FXCollections.observableArrayList();
    // Need to be DoubleProperty as we pass it as reference
    private final DoubleProperty initialOccupiedHeight = new SimpleDoubleProperty(-1);
    private final SortedList<BsqTxListItem> sortedList = new SortedList<>(observableList);
    private ChangeListener<Number> parentHeightListener;
    private ListChangeListener<Transaction> walletBsqTransactionsListener;
    private int gridRow = 0;
    private Label chainHeightLabel;
    private ProgressBar chainSyncIndicator;
    private ChangeListener<Number> chainHeightChangedListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqTxView(BsqFormatter bsqFormatter,
                      BsqWalletService bsqWalletService,
                      Preferences preferences,
                      StateServiceFacade stateServiceFacade,
                      PeriodServiceFacade periodServiceFacade,
                      BtcWalletService btcWalletService,
                      BsqBalanceUtil bsqBalanceUtil) {
        this.bsqFormatter = bsqFormatter;
        this.bsqWalletService = bsqWalletService;
        this.preferences = preferences;
        this.stateServiceFacade = stateServiceFacade;
        this.periodServiceFacade = periodServiceFacade;
        this.btcWalletService = btcWalletService;
        this.bsqBalanceUtil = bsqBalanceUtil;
    }

    @Override
    public void initialize() {
        gridRow = bsqBalanceUtil.addGroup(root, gridRow);

        tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        addDateColumn();
        addTxIdColumn();
        addInformationColumn();
        addAmountColumn();
        addConfidenceColumn();
        addTxTypeColumn();

        chainSyncIndicator = new ProgressBar();
        chainSyncIndicator.setPrefWidth(120);
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq())
            chainSyncIndicator.setProgress(-1);
        else
            chainSyncIndicator.setProgress(0);
        chainSyncIndicator.setPadding(new Insets(-6, 0, -10, 5));

        chainHeightLabel = FormBuilder.addLabel(root, ++gridRow, "");
        chainHeightLabel.setId("num-offers");
        chainHeightLabel.setPadding(new Insets(-5, 0, -10, 5));

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(chainHeightLabel, chainSyncIndicator);

        VBox vBox = new VBox();
        vBox.setSpacing(10);
        GridPane.setRowIndex(vBox, ++gridRow);
        GridPane.setColumnSpan(vBox, 2);
        GridPane.setMargin(vBox, new Insets(40, -10, 5, -10));
        vBox.getChildren().addAll(tableView, hBox);
        root.getChildren().add(vBox);

        walletBsqTransactionsListener = change -> updateList();
        parentHeightListener = (observable, oldValue, newValue) -> layout();
        //TODO do we want to get notified from wallet side?
        chainHeightChangedListener = (observable, oldValue, newValue) -> onChainHeightChanged((int) newValue);
    }

    @Override
    protected void activate() {
        bsqBalanceUtil.activate();
        bsqWalletService.getWalletTransactions().addListener(walletBsqTransactionsListener);
        bsqWalletService.addBsqBalanceListener(this);
        btcWalletService.getChainHeightProperty().addListener(chainHeightChangedListener);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        periodServiceFacade.addPeriodStateChangeListener(this);

        if (root.getParent() instanceof Pane) {
            rootParent = (Pane) root.getParent();
            rootParent.heightProperty().addListener(parentHeightListener);
        }

        updateList();
        onChainHeightChanged(periodServiceFacade.getChainHeight());
        layout();
    }

    @Override
    protected void deactivate() {
        bsqBalanceUtil.deactivate();
        sortedList.comparatorProperty().unbind();
        bsqWalletService.getWalletTransactions().removeListener(walletBsqTransactionsListener);
        bsqWalletService.removeBsqBalanceListener(this);
        btcWalletService.getChainHeightProperty().removeListener(chainHeightChangedListener);
        periodServiceFacade.removePeriodStateChangeListener(this);

        observableList.forEach(BsqTxListItem::cleanup);

        if (rootParent != null)
            rootParent.heightProperty().removeListener(parentHeightListener);
    }


    @Override
    public void onUpdateBalances(Coin confirmedBalance,
                                 Coin pendingBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockedInBondsBalance) {
        updateList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BlockListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onChainHeightChanged(int chainHeight) {
        final int bsqWalletChainHeight = bsqWalletService.getChainHeightProperty().get();
        final int bsqBlockChainHeight = periodServiceFacade.getChainHeight();
        if (bsqWalletChainHeight > 0) {
            final boolean synced = bsqWalletChainHeight == bsqBlockChainHeight;
            chainSyncIndicator.setVisible(!synced);
            chainSyncIndicator.setManaged(!synced);
            if (bsqBlockChainHeight > 0)
                chainSyncIndicator.setProgress((double) bsqBlockChainHeight / (double) bsqWalletService.getBestChainHeight());

            if (synced)
                chainHeightLabel.setText(Res.get("dao.wallet.chainHeightSynced",
                        bsqBlockChainHeight,
                        bsqWalletService.getBestChainHeight()));
            else
                chainHeightLabel.setText(Res.get("dao.wallet.chainHeightSyncing",
                        bsqBlockChainHeight,
                        bsqWalletService.getBestChainHeight()));
        } else {
            chainHeightLabel.setText(Res.get("dao.wallet.chainHeightSyncing",
                    bsqBlockChainHeight,
                    bsqWalletService.getBestChainHeight()));
        }
        updateList();
    }

    private void updateList() {
        observableList.forEach(BsqTxListItem::cleanup);

        // copy list to avoid ConcurrentModificationException
        final List<Transaction> walletTransactions = new ArrayList<>(bsqWalletService.getWalletTransactions());
        List<BsqTxListItem> items = walletTransactions.stream()
                .map(transaction -> {
                    final Optional<Tx> optionalTx = stateServiceFacade.getTx(transaction.getHashAsString());
                    return new BsqTxListItem(transaction,
                            optionalTx,
                            bsqWalletService,
                            btcWalletService,
                            stateServiceFacade,
                            stateServiceFacade.hasTxBurntFee(transaction.getHashAsString()),
                            transaction.getUpdateTime(),
                            bsqFormatter);
                })
                .collect(Collectors.toList());
        observableList.setAll(items);
    }

    private void layout() {
        GUIUtil.fillAvailableHeight(root, tableView, initialOccupiedHeight);
    }

    private void addDateColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.dateTime"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(180);
        column.setMaxWidth(column.getMinWidth() + 20);

        column.setCellFactory(
                new Callback<TableColumn<BsqTxListItem, BsqTxListItem>, TableCell<BsqTxListItem,
                        BsqTxListItem>>() {

                    @Override
                    public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                            BsqTxListItem> column) {
                        return new TableCell<BsqTxListItem, BsqTxListItem>() {

                            @Override
                            public void updateItem(final BsqTxListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setText(bsqFormatter.formatDateTime(item.getDate()));
                                } else {
                                    setText("");
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);
        column.setComparator(Comparator.comparing(BsqTxListItem::getDate));
        column.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getSortOrder().add(column);
    }

    private void addTxIdColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.txId"));

        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(60);
        column.setCellFactory(
                new Callback<TableColumn<BsqTxListItem, BsqTxListItem>, TableCell<BsqTxListItem,
                        BsqTxListItem>>() {

                    @Override
                    public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                            BsqTxListItem> column) {
                        return new TableCell<BsqTxListItem, BsqTxListItem>() {
                            private HyperlinkWithIcon hyperlinkWithIcon;

                            @Override
                            public void updateItem(final BsqTxListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    String transactionId = item.getTxId();
                                    hyperlinkWithIcon = new HyperlinkWithIcon(transactionId, AwesomeIcon.EXTERNAL_LINK);
                                    hyperlinkWithIcon.setOnAction(event -> openTxInBlockExplorer(item));
                                    hyperlinkWithIcon.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForTx", transactionId)));
                                    setGraphic(hyperlinkWithIcon);
                                } else {
                                    setGraphic(null);
                                    if (hyperlinkWithIcon != null)
                                        hyperlinkWithIcon.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        tableView.getColumns().add(column);
    }

    private void addInformationColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.information"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(160);
        column.setCellFactory(
                new Callback<TableColumn<BsqTxListItem, BsqTxListItem>, TableCell<BsqTxListItem,
                        BsqTxListItem>>() {

                    @Override
                    public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                            BsqTxListItem> column) {
                        return new TableCell<BsqTxListItem, BsqTxListItem>() {

                            private AddressWithIconAndDirection field;

                            @Override
                            public void updateItem(final BsqTxListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    final TxType txType = item.getTxType();
                                    String labelString = Res.get("dao.tx.type.enum." + txType.name());
                                    Label label;
                                    if (item.getConfirmations() > 0 && txType.ordinal() > TxType.INVALID.ordinal()) {
                                        final Optional<Tx> optionalTx = item.getOptionalTx();

                                        if (txType == TxType.COMPENSATION_REQUEST &&
                                                optionalTx.isPresent() &&
                                                stateServiceFacade.isIssuanceTx(optionalTx.get().getId())) {
                                            if (field != null)
                                                field.setOnAction(null);

                                            labelString = Res.get("dao.tx.issuance");
                                            label = new AutoTooltipLabel(labelString);
                                            setGraphic(label);
                                        } else if (item.isBurnedBsqTx() || item.getAmount().isZero()) {
                                            if (field != null)
                                                field.setOnAction(null);

                                            if (txType == TxType.TRANSFER_BSQ) {
                                                if (item.getAmount().isZero())
                                                    labelString = Res.get("funds.tx.direction.self");
                                            }

                                            label = new AutoTooltipLabel(labelString);
                                            setGraphic(label);
                                        } else {
                                            // Received
                                            String addressString = item.getAddress();
                                            field = new AddressWithIconAndDirection(item.getDirection(), addressString,
                                                    AwesomeIcon.EXTERNAL_LINK, item.isReceived());
                                            field.setOnAction(event -> openAddressInBlockExplorer(item));
                                            field.setTooltip(new Tooltip(Res.get("tooltip.openBlockchainForAddress", addressString)));
                                            setGraphic(field);
                                        }
                                    } else {
                                        label = new AutoTooltipLabel(labelString);
                                        setGraphic(label);
                                    }
                                } else {
                                    setGraphic(null);
                                    if (field != null)
                                        field.setOnAction(null);
                                }
                            }
                        };
                    }
                });

        tableView.getColumns().add(column);
    }

    private void addAmountColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.amountWithCur", "BSQ"));
        column.setMinWidth(120);
        column.setMaxWidth(column.getMinWidth());

        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<TableColumn<BsqTxListItem, BsqTxListItem>,
                TableCell<BsqTxListItem, BsqTxListItem>>() {

            @Override
            public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                    BsqTxListItem> column) {
                return new TableCell<BsqTxListItem, BsqTxListItem>() {

                    @Override
                    public void updateItem(final BsqTxListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            TxType txType = item.getTxType();
                            setText(item.getConfirmations() > 0 && txType.ordinal() > TxType.INVALID.ordinal() ?
                                    bsqFormatter.formatCoin(item.getAmount()) :
                                    Res.get("shared.na"));
                        } else
                            setText("");
                    }
                };
            }
        });
        tableView.getColumns().add(column);
    }

    private void addConfidenceColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.confirmations"));
        column.setMinWidth(130);
        column.setMaxWidth(column.getMinWidth());

        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<TableColumn<BsqTxListItem, BsqTxListItem>,
                TableCell<BsqTxListItem, BsqTxListItem>>() {

            @Override
            public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                    BsqTxListItem> column) {
                return new TableCell<BsqTxListItem, BsqTxListItem>() {

                    @Override
                    public void updateItem(final BsqTxListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            setGraphic(item.getTxConfidenceIndicator());
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
        tableView.getColumns().add(column);
    }

    private void addTxTypeColumn() {
        TableColumn<BsqTxListItem, BsqTxListItem> column = new AutoTooltipTableColumn<>(Res.get("dao.wallet.tx.type"));
        column.setCellValueFactory(item -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setMinWidth(70);
        column.setMaxWidth(column.getMinWidth());
        column.setCellFactory(
                new Callback<TableColumn<BsqTxListItem, BsqTxListItem>, TableCell<BsqTxListItem,
                        BsqTxListItem>>() {

                    @Override
                    public TableCell<BsqTxListItem, BsqTxListItem> call(TableColumn<BsqTxListItem,
                            BsqTxListItem> column) {
                        return new TableCell<BsqTxListItem, BsqTxListItem>() {

                            @Override
                            public void updateItem(final BsqTxListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    String style;
                                    AwesomeIcon awesomeIcon;
                                    TxType txType = item.getTxType();
                                    String toolTipText = Res.get("dao.tx.type.enum." + txType.name());
                                    boolean doRotate = false;
                                    switch (txType) {
                                        case UNDEFINED_TX_TYPE:
                                        case UNVERIFIED:
                                            awesomeIcon = AwesomeIcon.QUESTION_SIGN;
                                            style = "dao-tx-type-unverified-icon";
                                            break;
                                        case INVALID:
                                            awesomeIcon = AwesomeIcon.WARNING_SIGN;
                                            style = "dao-tx-type-invalid-icon";
                                            break;
                                        case GENESIS:
                                            awesomeIcon = AwesomeIcon.ROCKET;
                                            style = "dao-tx-type-genesis-icon";
                                            break;
                                        case TRANSFER_BSQ:
                                            if (item.getAmount().isZero()) {
                                                awesomeIcon = AwesomeIcon.EXCHANGE;
                                                style = "dao-tx-type-default-icon";
                                            } else {
                                                awesomeIcon = item.isReceived() ? AwesomeIcon.SIGNIN : AwesomeIcon.SIGNOUT;
                                                doRotate = item.isReceived();
                                                style = item.isReceived() ? "dao-tx-type-received-funds-icon" : "dao-tx-type-sent-funds-icon";
                                                toolTipText = item.isReceived() ?
                                                        Res.get("dao.tx.type.enum.received." + txType.name()) :
                                                        Res.get("dao.tx.type.enum.sent." + txType.name());
                                            }
                                            break;
                                        case PAY_TRADE_FEE:
                                            awesomeIcon = AwesomeIcon.TICKET;
                                            style = "dao-tx-type-default-icon";
                                            break;
                                        case PROPOSAL:
                                        case COMPENSATION_REQUEST:
                                            final String txId = item.getOptionalTx().get().getId();
                                            if (item.getOptionalTx().isPresent() && stateServiceFacade.isIssuanceTx(txId)) {
                                                awesomeIcon = AwesomeIcon.MONEY;
                                                style = "dao-tx-type-issuance-icon";
                                                final int issuanceBlockHeight = stateServiceFacade.getIssuanceBlockHeight(txId);
                                                long blockTimeInSec = stateServiceFacade.getBlockTime(issuanceBlockHeight);
                                                final String formattedDate = bsqFormatter.formatDateTime(new Date(blockTimeInSec * 1000));
                                                toolTipText = Res.get("dao.tx.issuance.tooltip", formattedDate);
                                            } else {
                                                awesomeIcon = AwesomeIcon.FILE;
                                                style = "dao-tx-type-fee-icon";
                                            }
                                            break;
                                        case BLIND_VOTE:
                                            awesomeIcon = AwesomeIcon.THUMBS_UP;
                                            style = "dao-tx-type-vote-icon";
                                            break;
                                        case VOTE_REVEAL:
                                            awesomeIcon = AwesomeIcon.LIGHTBULB;
                                            style = "dao-tx-type-vote-reveal-icon";
                                            break;
                                        default:
                                            awesomeIcon = AwesomeIcon.QUESTION_SIGN;
                                            style = "dao-tx-type-unverified-icon";
                                            break;
                                    }
                                    Label label = AwesomeDude.createIconLabel(awesomeIcon);
                                    label.getStyleClass().addAll("icon", style);
                                    label.setTooltip(new Tooltip(toolTipText));
                                    if (doRotate)
                                        label.setRotate(180);
                                    setGraphic(label);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });

        tableView.getColumns().add(column);
    }

    private void openTxInBlockExplorer(BsqTxListItem item) {
        if (item.getTxId() != null)
            GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().txUrl + item.getTxId());
    }

    private void openAddressInBlockExplorer(BsqTxListItem item) {
        if (item.getAddress() != null) {
            GUIUtil.openWebPage(preferences.getBsqBlockChainExplorer().addressUrl + item.getAddress());
        }
    }
}

