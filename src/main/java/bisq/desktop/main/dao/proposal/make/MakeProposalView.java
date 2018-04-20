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

package bisq.desktop.main.dao.proposal.make;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.dao.proposal.ProposalDisplay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.BsqFormatter;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.InsufficientBsqException;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.dao.ValidationException;
import bisq.core.dao.consensus.vote.proposal.Ballot;
import bisq.core.dao.consensus.vote.proposal.ProposalConsensus;
import bisq.core.dao.consensus.vote.proposal.ProposalType;
import bisq.core.dao.consensus.vote.proposal.compensation.CompensationService;
import bisq.core.dao.consensus.vote.proposal.generic.GenericProposalService;
import bisq.core.dao.consensus.vote.proposal.param.ChangeParamService;
import bisq.core.dao.presentation.proposal.MyProposalService;
import bisq.core.dao.presentation.state.StateServiceFacade;
import bisq.core.locale.Res;
import bisq.core.provider.fee.FeeService;

import bisq.network.p2p.P2PService;

import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addLabelComboBox;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static com.google.common.base.Preconditions.checkArgument;

@FxmlView
public class MakeProposalView extends ActivatableView<GridPane, Void> {

    private ProposalDisplay proposalDisplay;
    private Button createButton;

    private final BsqWalletService bsqWalletService;
    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;
    private final FeeService feeService;
    private final MyProposalService myProposalService;
    private final CompensationService compensationService;
    private final GenericProposalService genericProposalService;
    private final StateServiceFacade stateService;
    private final ChangeParamService changeParamService;
    private final BSFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;
    private ComboBox<ProposalType> proposalTypeComboBox;
    private ChangeListener<ProposalType> proposalTypeChangeListener;
    private ProposalType selectedProposalType;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MakeProposalView(BsqWalletService bsqWalletService,
                             WalletsSetup walletsSetup,
                             P2PService p2PService,
                             FeeService feeService,
                             MyProposalService myProposalService,
                             CompensationService compensationService,
                             GenericProposalService genericProposalService,
                             StateServiceFacade stateService,
                             ChangeParamService changeParamService,
                             BSFormatter btcFormatter,
                             BsqFormatter bsqFormatter) {
        this.bsqWalletService = bsqWalletService;
        this.walletsSetup = walletsSetup;
        this.p2PService = p2PService;
        this.feeService = feeService;
        this.myProposalService = myProposalService;
        this.compensationService = compensationService;
        this.genericProposalService = genericProposalService;
        this.stateService = stateService;
        this.changeParamService = changeParamService;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;
    }


    @Override
    public void initialize() {
        addTitledGroupBg(root, 0, 1, Res.get("dao.proposal.create.selectProposalType"));
        proposalTypeComboBox = addLabelComboBox(root, 0,
                Res.getWithCol("dao.proposal.create.proposalType"), Layout.FIRST_ROW_DISTANCE).second;
        proposalTypeComboBox.setConverter(new StringConverter<ProposalType>() {
            @Override
            public String toString(ProposalType object) {
                return Res.get(object.name());
            }

            @Override
            public ProposalType fromString(String string) {
                return null;
            }
        });
        proposalTypeComboBox.setPromptText(Res.get("shared.select"));
        proposalTypeChangeListener = (observable, oldValue, newValue) -> {
            selectedProposalType = newValue;
            addProposalDisplay();
        };

        proposalTypeComboBox.setItems(FXCollections.observableArrayList(Arrays.asList(ProposalType.values())));
    }

    @Override
    protected void activate() {
        proposalTypeComboBox.getSelectionModel().selectedItemProperty().addListener(proposalTypeChangeListener);

        if (createButton != null)
            setCreateButtonHandler();
    }

    @Override
    protected void deactivate() {
        proposalTypeComboBox.getSelectionModel().selectedItemProperty().removeListener(proposalTypeChangeListener);
        if (createButton != null)
            createButton.setOnAction(null);
    }

    private void publishProposal(ProposalType type) {
        try {
            final Tuple2<Ballot, Transaction> tuple2 = createProposal(type);
            Ballot ballot = tuple2.first;
            Transaction transaction = tuple2.second;
            Coin miningFee = transaction.getFee();
            int txSize = transaction.bitcoinSerialize().length;
            final Coin fee = ProposalConsensus.getFee(changeParamService, stateService.getChainHeight());

            GUIUtil.showBsqFeeInfoPopup(fee, miningFee, txSize, bsqFormatter, btcFormatter,
                    Res.get("dao.proposal"), () -> publishProposal(ballot, transaction));

        } catch (InsufficientMoneyException e) {
            BSFormatter formatter = e instanceof InsufficientBsqException ? bsqFormatter : btcFormatter;
            new Popup<>().warning(Res.get("dao.proposal.create.missingFunds",
                    formatter.formatCoinWithCode(e.missing))).show();
        } catch (ValidationException e) {
            String message;
            if (e.getMinRequestAmount() != null) {
                message = Res.get("validation.bsq.amountBelowMinAmount",
                        bsqFormatter.formatCoinWithCode(e.getMinRequestAmount()));
            } else {
                message = e.getMessage();
            }
            new Popup<>().warning(message).show();
        } catch (TransactionVerificationException | WalletException | IOException e) {
            log.error(e.toString());
            e.printStackTrace();
            new Popup<>().warning(e.toString()).show();
        }
    }

    private void publishProposal(Ballot ballot, Transaction transaction) {
        myProposalService.publishProposal(ballot,
                transaction,
                () -> {
                    proposalDisplay.clearForm();
                    proposalTypeComboBox.getSelectionModel().clearSelection();
                    new Popup<>().confirmation(Res.get("dao.tx.published.success")).show();
                },
                errorMessage -> new Popup<>().warning(errorMessage).show());
    }

    private Tuple2<Ballot, Transaction> createProposal(ProposalType type)
            throws InsufficientMoneyException, TransactionVerificationException, ValidationException,
            WalletException, IOException {

        validateInputs();

        switch (type) {
            case COMPENSATION_REQUEST:
                return compensationService.makeTxAndGetCompensationRequest(proposalDisplay.nameTextField.getText(),
                        proposalDisplay.titleTextField.getText(),
                        proposalDisplay.descriptionTextArea.getText(),
                        proposalDisplay.linkInputTextField.getText(),
                        bsqFormatter.parseToCoin(Objects.requireNonNull(proposalDisplay.requestedBsqTextField).getText()),
                        Objects.requireNonNull(proposalDisplay.bsqAddressTextField).getText());
            case GENERIC:
                //TODO
                throw new RuntimeException("Not implemented yet");
                /*
                return genericProposalService.makeTxAndGetGenericProposal(
                        proposalDisplay.nameTextField.getText(),
                        proposalDisplay.titleTextField.getText(),
                        proposalDisplay.descriptionTextArea.getText(),
                        proposalDisplay.linkInputTextField.getText());*/
            case CHANGE_PARAM:
                //TODO
                throw new RuntimeException("Not implemented yet");
            case REMOVE_ALTCOIN:
                //TODO
                throw new RuntimeException("Not implemented yet");
            default:
                final String msg = "Undefined ProposalType " + selectedProposalType;
                log.error(msg);
                throw new RuntimeException(msg);
        }
    }

    private void addProposalDisplay() {
        // TODO need to update removed fields when switching.
        if (proposalDisplay != null) {
            proposalDisplay.removeAllFields();
            proposalDisplay = null;
        }
        if (selectedProposalType != null) {
            proposalDisplay = new ProposalDisplay(root, bsqFormatter, bsqWalletService, feeService);
            proposalDisplay.createAllFields(Res.get("dao.proposal.create.createNew"), 1, Layout.GROUP_DISTANCE,
                    selectedProposalType, true, true);
            proposalDisplay.fillWithMock();

            createButton = addButtonAfterGroup(root, proposalDisplay.incrementAndGetGridRow(), Res.get("dao.proposal.create.create.button"));
            setCreateButtonHandler();
        }
    }

    private void setCreateButtonHandler() {
        createButton.setOnAction(event -> {
            // TODO break up in methods
            if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
                publishProposal(selectedProposalType);
            } else {
                GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
            }
        });
    }

    private void validateInputs() {
        // We check in proposalDisplay that no invalid input as allowed
        checkArgument(ProposalConsensus.isDescriptionSizeValid(proposalDisplay.descriptionTextArea.getText()), "descriptionText must not be longer than " +
                ProposalConsensus.getMaxLengthDescriptionText() + " chars");

        // TODO add more checks for all input fields
    }

}

