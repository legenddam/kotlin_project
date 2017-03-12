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

package io.bitsquare.gui.main.portfolio.pendingtrades.steps.buyer;

import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeStepView;
import io.bitsquare.locale.Res;

public class BuyerStep3View extends TradeStepView {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerStep3View(PendingTradesViewModel model) {
        super(model);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Info
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getInfoBlockTitle() {
        return Res.get("portfolio.pending.step3_buyer.wait.headline");
    }

    @Override
    protected String getInfoText() {
        return Res.get("portfolio.pending.step3_buyer.wait.info", model.dataModel.getCurrencyCode());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getWarningText() {
        setInformationHeadline();
        String substitute = model.isBlockChainMethod() ?
                Res.get("portfolio.pending.step3_buyer.warn.part1a", model.dataModel.getCurrencyCode()) :
                Res.get("portfolio.pending.step3_buyer.warn.part1b");
        return Res.get("portfolio.pending.step3_buyer.warn.part2", substitute, model.getDateForOpenDispute());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getOpenForDisputeText() {
        return Res.get("portfolio.pending.step3_buyer.openForDispute");
    }

    @Override
    protected void applyOnDisputeOpened() {
    }
}


