/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.locale;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public class Res {
    private static final Logger log = LoggerFactory.getLogger(Res.class);

    private static ResourceBundle resourceBundle;

    static {
        applyLocaleToResourceBundle(Locale.US);
    }

    public static void applyLocaleToResourceBundle(Locale locale) {
        resourceBundle = ResourceBundle.getBundle("i18n.displayStrings", locale, new UTF8Control());
    }

    public static String getWithCol(String key) {
        return get(key) + ":";
    }

    public static String getWithColAndCap(String key) {
        return StringUtils.capitalize(get(key)) + ":";
    }

    // Capitalize first character
    public static String getWithCap(String key) {
        return StringUtils.capitalize(get(key));
    }

    public static String getWithCol(String key, Object... arguments) {
        return get(key, arguments) + ":";
    }

    public static String get(String key) {
        // TODO remove once translation done
        // for testing missing translation strings
        //if (true) return "#";
        try {
            return resourceBundle.getString(key);
        } catch (MissingResourceException e) {
            log.warn("Missing resource for key: " + key);
            return key;
        }
    }

    public static String get(String key, Object... arguments) {
        return MessageFormat.format(Res.get(key), arguments);
    }

    public static ResourceBundle getResourceBundle() {
        return resourceBundle;
    }
}

// Adds UTF8 support for property files
class UTF8Control extends ResourceBundle.Control {

    public ResourceBundle newBundle(String baseName, @NotNull Locale locale, @NotNull String format, ClassLoader loader, boolean reload)
            throws IllegalAccessException, InstantiationException, IOException {
        // The below is a copy of the default implementation.
        final String bundleName = toBundleName(baseName, locale);
        final String resourceName = toResourceName(bundleName, "properties");
        ResourceBundle bundle = null;
        InputStream stream = null;
        if (reload) {
            final URL url = loader.getResource(resourceName);
            if (url != null) {
                final URLConnection connection = url.openConnection();
                if (connection != null) {
                    connection.setUseCaches(false);
                    stream = connection.getInputStream();
                }
            }
        } else {
            stream = loader.getResourceAsStream(resourceName);
        }
        if (stream != null) {
            try {
                // Only this line is changed to make it to read properties files as UTF-8.
                bundle = new PropertyResourceBundle(new InputStreamReader(stream, "UTF-8"));
            } finally {
                stream.close();
            }
        }
        return bundle;
    }
}