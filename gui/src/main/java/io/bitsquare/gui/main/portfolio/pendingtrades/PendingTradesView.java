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

package io.bitsquare.gui.main.portfolio.pendingtrades;

import io.bitsquare.common.UserThread;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.popups.OpenEmergencyTicketPopup;
import io.bitsquare.gui.popups.TradeDetailsPopup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.Trade;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;

@FxmlView
public class PendingTradesView extends ActivatableViewAndModel<VBox, PendingTradesViewModel> {

    private final TradeDetailsPopup tradeDetailsPopup;
    private BSFormatter formatter;
    @FXML
    TableView<PendingTradesListItem> table;
    @FXML
    TableColumn<PendingTradesListItem, Fiat> priceColumn, tradeVolumeColumn;
    @FXML
    TableColumn<PendingTradesListItem, PendingTradesListItem> roleColumn, paymentMethodColumn, idColumn, dateColumn;
    @FXML
    TableColumn<PendingTradesListItem, Coin> tradeAmountColumn;

    private ChangeListener<PendingTradesListItem> selectedItemChangeListener;
    private TradeSubView currentSubView;
    private ChangeListener<Boolean> appFocusChangeListener;
    private ReadOnlyBooleanProperty appFocusProperty;
    private ChangeListener<Trade> currentTradeChangeListener;
    private EventHandler<KeyEvent> keyEventEventHandler;
    private Scene scene;
    private ListChangeListener<PendingTradesListItem> listChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PendingTradesView(PendingTradesViewModel model, TradeDetailsPopup tradeDetailsPopup, BSFormatter formatter) {
        super(model);
        this.tradeDetailsPopup = tradeDetailsPopup;
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        setTradeIdColumnCellFactory();
        setDateColumnCellFactory();
        setAmountColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
        setPaymentMethodColumnCellFactory();
        setRoleColumnCellFactory();

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No pending trades available"));
        table.setMinHeight(100);
        selectedItemChangeListener = (ov, oldValue, newValue) -> {
            model.onSelectTrade(newValue);
            log.debug("selectedItemChangeListener {} ", newValue);
            if (newValue != null)
                setNewSubView(newValue.getTrade());
        };
        listChangeListener = c -> updateSelectedItem();

        appFocusChangeListener = (observable, oldValue, newValue) -> {
            if (newValue && model.getSelectedItem() != null) {
                // Focus selectedItem from model
                int index = table.getItems().indexOf(model.getSelectedItem());
                UserThread.execute(() -> {
                    //TODO app wide focus
                    //table.requestFocus();
                    //UserThread.execute(() -> table.getFocusModel().focus(index));
                });
            }
        };

        currentTradeChangeListener = (observable, oldValue, newValue) -> {
            log.debug("currentTradeChangeListener {} ", newValue);
            // setNewSubView(newValue);
        };

        // we use a hidden emergency shortcut to open support ticket
        keyEventEventHandler = event -> {
            if (new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN).match(event))
                new OpenEmergencyTicketPopup().onOpenTicket(model.dataModel::onOpenSupportTicket).show();
        };
    }

    @Override
    protected void activate() {
        scene = root.getScene();
        if (scene != null) {
            appFocusProperty = scene.getWindow().focusedProperty();
            scene.addEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
        }

        appFocusProperty.addListener(appFocusChangeListener);
        model.currentTrade().addListener(currentTradeChangeListener);
        //setNewSubView(model.currentTrade().get());
        table.setItems(model.getList());
        table.getSelectionModel().selectedItemProperty().addListener(selectedItemChangeListener);

        if (model.getSelectedItem() == null)
            model.getList().addListener(listChangeListener);


        updateSelectedItem();
    }

    @Override
    protected void deactivate() {
        table.getSelectionModel().selectedItemProperty().removeListener(selectedItemChangeListener);

        model.getList().removeListener(listChangeListener);

        if (model.currentTrade() != null)
            model.currentTrade().removeListener(currentTradeChangeListener);

        if (appFocusProperty != null) {
            appFocusProperty.removeListener(appFocusChangeListener);
            appFocusProperty = null;
        }

        if (currentSubView != null)
            currentSubView.deactivate();

        if (scene != null)
            scene.removeEventHandler(KeyEvent.KEY_RELEASED, keyEventEventHandler);
    }

    private void updateSelectedItem() {
        PendingTradesListItem selectedItem = model.getSelectedItem();
        if (selectedItem != null) {
            // Select and focus selectedItem from model
            int index = table.getItems().indexOf(selectedItem);
            UserThread.execute(() -> {
                //TODO app wide focus
                table.getSelectionModel().select(index);
                //table.requestFocus();
                //UserThread.execute(() -> table.getFocusModel().focus(index));
            });
        }
    }
    
    
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Subviews
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setNewSubView(Trade trade) {
        if (currentSubView != null) {
            currentSubView.deactivate();
            root.getChildren().remove(currentSubView);
        }

        if (trade != null) {
            if (model.isOfferer()) {
                if (model.isBuyOffer())
                    currentSubView = new BuyerSubView(model);
                else
                    currentSubView = new SellerSubView(model);
            } else {
                if (model.isBuyOffer())
                    currentSubView = new SellerSubView(model);
                else
                    currentSubView = new BuyerSubView(model);
            }
            currentSubView.setMinHeight(430);
            VBox.setVgrow(currentSubView, Priority.ALWAYS);
            root.getChildren().add(1, currentSubView);

            currentSubView.activate();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CellFactories
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setTradeIdColumnCellFactory() {
        idColumn.setCellValueFactory((pendingTradesListItem) -> new ReadOnlyObjectWrapper<>(pendingTradesListItem.getValue()));
        idColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem, PendingTradesListItem>>() {

                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(TableColumn<PendingTradesListItem,
                            PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    field = new HyperlinkWithIcon(item.getId(), true);
                                    field.setOnAction(event -> tradeDetailsPopup.show(item.getTrade()));
                                    field.setTooltip(new Tooltip("Open popup for details"));
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);
                                    if (field != null)
                                        field.setOnAction(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setDateColumnCellFactory() {
        dateColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    if (model.showDispute(item.getTrade())) {
                                        setStyle("-fx-text-fill: -bs-error-red");
                                    } else if (model.showWarning(item.getTrade())) {
                                        setStyle("-fx-text-fill: -bs-orange");
                                    } else {
                                        setId("-fx-text-fill: black");
                                    }
                                    setText(formatter.formatDateTime(item.getTrade().getDate()));
                                } else {
                                    setText(null);
                                }
                            }
                        };
                    }
                });
    }

    private void setAmountColumnCellFactory() {
        tradeAmountColumn.setCellFactory(TextFieldTableCell.<PendingTradesListItem, Coin>forTableColumn(
                new StringConverter<Coin>() {
                    @Override
                    public String toString(Coin value) {
                        return formatter.formatCoinWithCode(value);
                    }

                    @Override
                    public Coin fromString(String string) {
                        return null;
                    }
                }));
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellFactory(TextFieldTableCell.<PendingTradesListItem, Fiat>forTableColumn(
                new StringConverter<Fiat>() {
                    @Override
                    public String toString(Fiat value) {
                        return formatter.formatPriceWithCode(value);
                    }

                    @Override
                    public Fiat fromString(String string) {
                        return null;
                    }
                }));

    }

    private void setVolumeColumnCellFactory() {
        tradeVolumeColumn.setCellFactory(TextFieldTableCell.<PendingTradesListItem, Fiat>forTableColumn(
                new StringConverter<Fiat>() {
                    @Override
                    public String toString(Fiat value) {
                        return formatter.formatFiatWithCode(value);
                    }

                    @Override
                    public Fiat fromString(String string) {
                        return null;
                    }
                }));
    }

    private void setPaymentMethodColumnCellFactory() {
        paymentMethodColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        paymentMethodColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(model.getPaymentMethod(item));
                                else
                                    setText(null);
                            }
                        };
                    }
                });
    }

    private void setRoleColumnCellFactory() {
        roleColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        roleColumn.setCellFactory(
                new Callback<TableColumn<PendingTradesListItem, PendingTradesListItem>, TableCell<PendingTradesListItem,
                        PendingTradesListItem>>() {
                    @Override
                    public TableCell<PendingTradesListItem, PendingTradesListItem> call(
                            TableColumn<PendingTradesListItem, PendingTradesListItem> column) {
                        return new TableCell<PendingTradesListItem, PendingTradesListItem>() {
                            @Override
                            public void updateItem(final PendingTradesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setText(model.getMyRole(item));
                                else
                                    setText(null);
                            }
                        };
                    }
                });
    }
}

