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

package bisq.desktop.main.market.offerbook;

import bisq.desktop.main.offer.offerbook.OfferBook;
import bisq.desktop.main.offer.offerbook.OfferBookListItem;
import bisq.desktop.main.offer.offerbook.OfferBookListItemMaker;

import bisq.core.locale.GlobalSettings;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.util.BSFormatter;

import javafx.beans.property.SimpleIntegerProperty;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static bisq.core.locale.TradeCurrencyMakers.usd;
import static bisq.core.user.PreferenceMakers.empty;
import static bisq.desktop.main.offer.offerbook.OfferBookListItemMaker.btcBuyItem;
import static bisq.desktop.main.offer.offerbook.OfferBookListItemMaker.btcSellItem;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({OfferBook.class, PriceFeedService.class})
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class OfferBookChartViewModelTest {

    @Before
    public void setUp() {
        GlobalSettings.setDefaultTradeCurrency(usd);
    }

    @Test
    public void testMaxCharactersForBuyPriceWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, null, null, null, new BSFormatter());
        assertEquals(0, model.maxPlacesForBuyPrice.intValue());
    }

    @Test
    public void testMaxCharactersForBuyPriceWithOfflinePriceFeedService() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);


        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        final OfferBookListItem item = make(OfferBookListItemMaker.btcBuyItem.but(with(OfferBookListItemMaker.useMarketBasedPrice, true)));
        item.getOffer().setPriceFeedService(priceFeedService);
        offerBookListItems.addAll(item);

        when(priceFeedService.getMarketPrice(anyString())).thenReturn(null);
        when(priceFeedService.updateCounterProperty()).thenReturn(new SimpleIntegerProperty());
        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, priceFeedService, null, null, new BSFormatter());
        model.activate();
        assertEquals(0, model.maxPlacesForBuyPrice.intValue());
    }

    @Test
    public void testMaxCharactersForBuyPrice() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService service = mock(PriceFeedService.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(OfferBookListItemMaker.btcBuyItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, service, null, null, new BSFormatter());
        model.activate();
        assertEquals(7, model.maxPlacesForBuyPrice.intValue());
        offerBookListItems.addAll(make(btcBuyItem.but(with(OfferBookListItemMaker.price, 94016475L))));
        assertEquals(9, model.maxPlacesForBuyPrice.intValue());
        offerBookListItems.addAll(make(btcBuyItem.but(with(OfferBookListItemMaker.price, 101016475L))));
        assertEquals(10, model.maxPlacesForBuyPrice.intValue());
    }

    @Test
    public void testMaxCharactersForBuyVolumeWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, null, null, null, new BSFormatter());
        assertEquals(0, model.maxPlacesForBuyVolume.intValue());
    }

    @Test
    public void testMaxCharactersForBuyVolume() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService service = mock(PriceFeedService.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(OfferBookListItemMaker.btcBuyItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, service, null, null, new BSFormatter());
        model.activate();
        assertEquals(4, model.maxPlacesForBuyVolume.intValue()); //0.01
        offerBookListItems.addAll(make(btcBuyItem.but(with(OfferBookListItemMaker.amount, 100000000L))));
        assertEquals(5, model.maxPlacesForBuyVolume.intValue()); //10.00
        offerBookListItems.addAll(make(btcBuyItem.but(with(OfferBookListItemMaker.amount, 22128600000L))));
        assertEquals(7, model.maxPlacesForBuyVolume.intValue()); //2212.86
    }

    @Test
    public void testMaxCharactersForSellPriceWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, null, null, null, new BSFormatter());
        assertEquals(0, model.maxPlacesForSellPrice.intValue());
    }

    @Test
    public void testMaxCharactersForSellPriceWithOfflinePriceFeedService() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);


        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        final OfferBookListItem item = make(OfferBookListItemMaker.btcSellItem.but(with(OfferBookListItemMaker.useMarketBasedPrice, true)));
        item.getOffer().setPriceFeedService(priceFeedService);
        offerBookListItems.addAll(item);

        when(priceFeedService.getMarketPrice(anyString())).thenReturn(null);
        when(priceFeedService.updateCounterProperty()).thenReturn(new SimpleIntegerProperty());
        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, priceFeedService, null, null, new BSFormatter());
        model.activate();
        assertEquals(0, model.maxPlacesForSellPrice.intValue());
    }

    @Test
    public void testMaxCharactersForSellPrice() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService service = mock(PriceFeedService.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(OfferBookListItemMaker.btcSellItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, service, null, null, new BSFormatter());
        model.activate();
        assertEquals(7, model.maxPlacesForSellPrice.intValue());
        offerBookListItems.addAll(make(btcSellItem.but(with(OfferBookListItemMaker.price, 94016475L))));
        assertEquals(9, model.maxPlacesForSellPrice.intValue());
        offerBookListItems.addAll(make(btcSellItem.but(with(OfferBookListItemMaker.price, 101016475L))));
        assertEquals(10, model.maxPlacesForSellPrice.intValue());
    }

    @Test
    public void testMaxCharactersForSellVolumeWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, null, null, null, new BSFormatter());
        assertEquals(0, model.maxPlacesForSellVolume.intValue());
    }

    @Test
    public void testMaxCharactersForSellVolume() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService service = mock(PriceFeedService.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(OfferBookListItemMaker.btcSellItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, service, null, null, new BSFormatter());
        model.activate();
        assertEquals(4, model.maxPlacesForSellVolume.intValue()); //0.01
        offerBookListItems.addAll(make(btcSellItem.but(with(OfferBookListItemMaker.amount, 100000000L))));
        assertEquals(5, model.maxPlacesForSellVolume.intValue()); //10.00
        offerBookListItems.addAll(make(btcSellItem.but(with(OfferBookListItemMaker.amount, 22128600000L))));
        assertEquals(7, model.maxPlacesForSellVolume.intValue()); //2212.86
    }
}
