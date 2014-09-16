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

package io.bitsquare.gui.util;

import io.bitsquare.locale.Country;

import javafx.scene.image.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ImageUtil {
    private static final Logger log = LoggerFactory.getLogger(ImageUtil.class);

    public static final String SYS_TRAY = "/images/system_tray_icon_44_32.png";
    public static final String SYS_TRAY_ALERT = "/images/system_tray_notify_icon_44_32.png";

    public static final String MSG_ALERT = "/images/nav/alertRound.png";

    public static final String BUY_ICON = "/images/buy.png";
    public static final String SELL_ICON = "/images/sell.png";
    public static final String REMOVE_ICON = "/images/removeOffer.png";

    public static final String EXPAND = "/images/expand.png";
    public static final String COLLAPSE = "/images/collapse.png";

    public static Image getImage(String url) {
        return new Image(ImageUtil.class.getResourceAsStream(url));
    }

    public static ImageView getImageView(String url) {
        return new ImageView(getImage(url));
    }

    public static ImageView getCountryIconImageView(Country country) {
        try {
            return ImageUtil.getImageView("/images/countries/" + country.getCode().toLowerCase() + ".png");

        } catch (Exception e) {
            log.error("Country icon not found URL = /images/countries/" + country.getCode().toLowerCase() +
                    ".png / country name = " + country.getName());
            return null;
        }
    }


}
