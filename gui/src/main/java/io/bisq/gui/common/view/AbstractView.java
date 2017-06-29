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

package io.bisq.gui.common.view;

import javafx.fxml.FXML;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractView<R extends Node, M> implements View {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    protected
    @FXML
    R root;
    protected final M model;

    public AbstractView(M model) {
        this.model = model;
    }

    public AbstractView() {
        this(null);
    }

    public R getRoot() {
        return root;
    }
}
