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

package io.bitsquare.common;

import static java.lang.String.format;

public class ViewfxException extends RuntimeException {

    private static final long serialVersionUID = -6266047448442595372L;

    public ViewfxException(Throwable cause, String format, Object... args) {
        super(format(format, args), cause);
    }

    public ViewfxException(String format, Object... args) {
        super(format(format, args));
    }
}
