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

package io.bitsquare.gui.main.portfolio.closedtrades;

import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.main.overlays.windows.OfferDetailsWindow;
import io.bitsquare.gui.main.overlays.windows.TradeDetailsWindow;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.trade.Tradable;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.offer.OpenOffer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import javax.inject.Inject;

@FxmlView
public class ClosedTradesView extends ActivatableViewAndModel<VBox, ClosedTradesViewModel> {

    @FXML
    TableView<ClosedTradableListItem> table;
    @FXML
    TableColumn<ClosedTradableListItem, ClosedTradableListItem> priceColumn, amountColumn, volumeColumn,
            directionColumn, dateColumn, tradeIdColumn, stateColumn, avatarColumn;
    private final BSFormatter formatter;
    private final OfferDetailsWindow offerDetailsWindow;
    private final TradeDetailsWindow tradeDetailsWindow;

    @Inject
    public ClosedTradesView(ClosedTradesViewModel model, BSFormatter formatter, OfferDetailsWindow offerDetailsWindow, TradeDetailsWindow tradeDetailsWindow) {
        super(model);
        this.formatter = formatter;
        this.offerDetailsWindow = offerDetailsWindow;
        this.tradeDetailsWindow = tradeDetailsWindow;
    }

    @Override
    public void initialize() {
        setTradeIdColumnCellFactory();
        setDirectionColumnCellFactory();
        setAmountColumnCellFactory();
        setPriceColumnCellFactory();
        setVolumeColumnCellFactory();
        setDateColumnCellFactory();
        setStateColumnCellFactory();
        setAvatarColumnCellFactory();

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No closed trades available"));
    }

    @Override
    protected void activate() {
        table.setItems(model.getList());
    }


    private void setTradeIdColumnCellFactory() {
        tradeIdColumn.setCellValueFactory((offerListItem) -> new ReadOnlyObjectWrapper<>(offerListItem.getValue()));
        tradeIdColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {

                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(TableColumn<ClosedTradableListItem,
                            ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    field = new HyperlinkWithIcon(model.getTradeId(item), true);
                                    field.setOnAction(event -> {
                                        Tradable tradable = item.getTradable();
                                        if (tradable instanceof Trade)
                                            tradeDetailsWindow.show((Trade) tradable);
                                        else if (tradable instanceof OpenOffer)
                                            offerDetailsWindow.show(tradable.getOffer());
                                    });
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
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(model.getDate(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
    }

    private void setStateColumnCellFactory() {
        stateColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        stateColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(model.getState(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
    }

    private TableColumn<ClosedTradableListItem, ClosedTradableListItem> setAvatarColumnCellFactory() {
        avatarColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        avatarColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {

                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);

                                if (newItem != null && !empty && newItem.getTradable() instanceof Trade) {

                                    String hostName = ((Trade) newItem.getTradable()).getTradingPeerNodeAddress().hostName;
                                    Node identIcon = ImageUtil.getIdentIcon(hostName, "Trading peers onion address: " + hostName, true);
                                    if (identIcon != null)
                                        setGraphic(identIcon);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return avatarColumn;
    }

    private void setAmountColumnCellFactory() {
        amountColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        amountColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getAmount(item));
                            }
                        };
                    }
                });
    }

    private void setPriceColumnCellFactory() {
        priceColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        priceColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getPrice(item));
                            }
                        };
                    }
                });
    }

    private void setVolumeColumnCellFactory() {
        volumeColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        volumeColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(model.getVolume(item));
                                else
                                    setText("");
                            }
                        };
                    }
                });
    }

    private void setDirectionColumnCellFactory() {
        directionColumn.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        directionColumn.setCellFactory(
                new Callback<TableColumn<ClosedTradableListItem, ClosedTradableListItem>, TableCell<ClosedTradableListItem,
                        ClosedTradableListItem>>() {
                    @Override
                    public TableCell<ClosedTradableListItem, ClosedTradableListItem> call(
                            TableColumn<ClosedTradableListItem, ClosedTradableListItem> column) {
                        return new TableCell<ClosedTradableListItem, ClosedTradableListItem>() {
                            @Override
                            public void updateItem(final ClosedTradableListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(model.getDirectionLabel(item));
                            }
                        };
                    }
                });
    }
}

