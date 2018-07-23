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

package bisq.desktop.main.dao.bonding.lockup;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.wallet.BsqBalanceUtil;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.funds.deposit.DepositView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.Restrictions;
import bisq.core.btc.wallet.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.bonding.BondingConsensus;
import bisq.core.dao.bonding.lockup.LockupType;
import bisq.core.dao.role.BondedRole;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;
import bisq.core.util.validation.IntegerValidator;

import bisq.network.p2p.P2PService;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.util.Arrays;

import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addLabelComboBox;
import static bisq.desktop.util.FormBuilder.addLabelInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class LockupView extends ActivatableView<GridPane, Void> implements BsqBalanceListener {
    private final BsqWalletService bsqWalletService;
    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;
    private final BsqFormatter bsqFormatter;
    private final Navigation navigation;
    private final BsqBalanceUtil bsqBalanceUtil;
    private final BsqValidator bsqValidator;
    private final DaoFacade daoFacade;
    private final IntegerValidator timeInputTextFieldValidator;

    private int gridRow = 0;
    private InputTextField amountInputTextField;
    private InputTextField timeInputTextField;
    private ComboBox<LockupType> lockupTypeComboBox;
    private ComboBox<BondedRole> bondedRolesComboBox;
    private Button lockupButton;
    private ChangeListener<Boolean> focusOutListener;
    private ChangeListener<String> inputTextFieldListener;
    private ChangeListener<BondedRole> bondedRolesListener;
    private ChangeListener<LockupType> lockupTypeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private LockupView(BsqWalletService bsqWalletService,
                       WalletsSetup walletsSetup,
                       P2PService p2PService,
                       BsqFormatter bsqFormatter,
                       Navigation navigation,
                       BsqBalanceUtil bsqBalanceUtil,
                       BsqValidator bsqValidator,
                       DaoFacade daoFacade) {
        this.bsqWalletService = bsqWalletService;
        this.walletsSetup = walletsSetup;
        this.p2PService = p2PService;
        this.bsqFormatter = bsqFormatter;
        this.navigation = navigation;
        this.bsqBalanceUtil = bsqBalanceUtil;
        this.bsqValidator = bsqValidator;
        this.daoFacade = daoFacade;

        timeInputTextFieldValidator = new IntegerValidator();
        timeInputTextFieldValidator.setMinValue(BondingConsensus.getMinLockTime());
        timeInputTextFieldValidator.setMaxValue(BondingConsensus.getMaxLockTime());
    }

    @Override
    public void initialize() {
        gridRow = bsqBalanceUtil.addGroup(root, gridRow);

        addTitledGroupBg(root, ++gridRow, 4, Res.get("dao.bonding.lock.lockBSQ"), Layout.GROUP_DISTANCE);

        amountInputTextField = addLabelInputTextField(root, gridRow, Res.get("dao.bonding.lock.amount"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        amountInputTextField.setPromptText(Res.get("dao.bonding.lock.setAmount", bsqFormatter.formatCoinWithCode(Restrictions.getMinNonDustOutput())));
        amountInputTextField.setValidator(bsqValidator);

        timeInputTextField = addLabelInputTextField(root, ++gridRow, Res.get("dao.bonding.lock.time")).second;
        timeInputTextField.setPromptText(Res.get("dao.bonding.lock.setTime",
                String.valueOf(BondingConsensus.getMinLockTime()), String.valueOf(BondingConsensus.getMaxLockTime())));
        timeInputTextField.setValidator(timeInputTextFieldValidator);

        lockupTypeComboBox = addLabelComboBox(root, ++gridRow, Res.get("dao.bonding.lock.type")).second;
        lockupTypeComboBox.setPromptText(Res.get("shared.select"));
        lockupTypeComboBox.setConverter(new StringConverter<LockupType>() {
            @Override
            public String toString(LockupType lockupType) {
                return lockupType.getDisplayString();
            }

            @Override
            public LockupType fromString(String string) {
                return null;
            }
        });
        lockupTypeComboBox.setItems(FXCollections.observableArrayList(Arrays.asList(LockupType.values())));
        lockupTypeListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                bondedRolesComboBox.getSelectionModel().clearSelection();
            }
        };
        //TODO handle trade type
        lockupTypeComboBox.getSelectionModel().select(0);

        bondedRolesComboBox = addLabelComboBox(root, ++gridRow, Res.get("dao.bonding.lock.bondedRoles")).second;
        bondedRolesComboBox.setPromptText(Res.get("shared.select"));
        bondedRolesComboBox.setConverter(new StringConverter<BondedRole>() {
            @Override
            public String toString(BondedRole bondedRole) {
                return bondedRole.getDisplayString();
            }

            @Override
            public BondedRole fromString(String string) {
                return null;
            }
        });
        bondedRolesListener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                amountInputTextField.setText(bsqFormatter.formatCoin(Coin.valueOf(newValue.getBondedRoleType().getRequiredBond())));
                timeInputTextField.setText(String.valueOf(newValue.getBondedRoleType().getUnlockTime()));
                amountInputTextField.resetValidation();
                timeInputTextField.resetValidation();
                amountInputTextField.setEditable(false);
                timeInputTextField.setEditable(false);
            } else {
                amountInputTextField.clear();
                timeInputTextField.clear();
                amountInputTextField.resetValidation();
                timeInputTextField.resetValidation();
                amountInputTextField.setEditable(true);
                timeInputTextField.setEditable(true);
            }
        };

        lockupButton = addButtonAfterGroup(root, ++gridRow, Res.get("dao.bonding.lock.lockupButton"));
        lockupButton.setOnAction((event) -> {
            if (GUIUtil.isReadyForTxBroadcast(p2PService, walletsSetup)) {
                Coin lockupAmount = bsqFormatter.parseToCoin(amountInputTextField.getText());
                int lockupTime = Integer.parseInt(timeInputTextField.getText());
                LockupType lockupType = lockupTypeComboBox.getValue();
                BondedRole bondedRole = bondedRolesComboBox.getValue();
                new Popup<>().headLine(Res.get("dao.bonding.lock.sendFunds.headline"))
                        .confirmation(Res.get("dao.bonding.lock.sendFunds.details",
                                bsqFormatter.formatCoinWithCode(lockupAmount),
                                lockupTime
                        ))
                        .actionButtonText(Res.get("shared.yes"))
                        .onAction(() -> {
                            daoFacade.publishLockupTx(lockupAmount,
                                    lockupTime,
                                    lockupType,
                                    bondedRole,
                                    () -> {
                                        new Popup<>().feedback(Res.get("dao.tx.published.success")).show();
                                    },
                                    this::handleError
                            );
                            amountInputTextField.setText("");
                            timeInputTextField.setText("");
                        })
                        .closeButtonText(Res.get("shared.cancel"))
                        .show();
            } else {
                GUIUtil.showNotReadyForTxBroadcastPopups(p2PService, walletsSetup);
            }
        });

        focusOutListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                updateButtonState();
                onUpdateBalances();
            }
        };
        inputTextFieldListener = (observable, oldValue, newValue) -> updateButtonState();
    }

    @Override
    protected void activate() {
        bsqBalanceUtil.activate();

        amountInputTextField.textProperty().addListener(inputTextFieldListener);
        timeInputTextField.textProperty().addListener(inputTextFieldListener);
        amountInputTextField.focusedProperty().addListener(focusOutListener);
        lockupTypeComboBox.getSelectionModel().selectedItemProperty().addListener(lockupTypeListener);
        bondedRolesComboBox.getSelectionModel().selectedItemProperty().addListener(bondedRolesListener);

        bsqWalletService.addBsqBalanceListener(this);

        bondedRolesComboBox.setItems(FXCollections.observableArrayList(daoFacade.getBondedRoleList()));
        onUpdateBalances();
    }

    @Override
    protected void deactivate() {
        bsqBalanceUtil.deactivate();

        amountInputTextField.textProperty().removeListener(inputTextFieldListener);
        timeInputTextField.textProperty().removeListener(inputTextFieldListener);
        amountInputTextField.focusedProperty().removeListener(focusOutListener);
        lockupTypeComboBox.getSelectionModel().selectedItemProperty().removeListener(lockupTypeListener);
        bondedRolesComboBox.getSelectionModel().selectedItemProperty().removeListener(bondedRolesListener);

        bsqWalletService.removeBsqBalanceListener(this);
    }

    @Override
    public void onUpdateBalances(Coin confirmedBalance,
                                 Coin availableNonBsqBalance,
                                 Coin pendingBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockupBondsBalance,
                                 Coin unlockingBondsBalance) {
        bsqValidator.setAvailableBalance(confirmedBalance);
        boolean isValid = bsqValidator.validate(amountInputTextField.getText()).isValid;
        lockupButton.setDisable(!isValid);
    }

    private void onUpdateBalances() {
        onUpdateBalances(bsqWalletService.getAvailableBalance(),
                bsqWalletService.getAvailableNonBsqBalance(),
                bsqWalletService.getUnverifiedBalance(),
                bsqWalletService.getLockedForVotingBalance(),
                bsqWalletService.getLockupBondsBalance(),
                bsqWalletService.getUnlockingBondsBalance());
    }

    private void updateButtonState() {
        lockupButton.setDisable(!bsqValidator.validate(amountInputTextField.getText()).isValid ||
                !timeInputTextFieldValidator.validate(timeInputTextField.getText()).isValid ||
                bondedRolesComboBox.getSelectionModel().getSelectedItem() == null ||
                lockupTypeComboBox.getSelectionModel().getSelectedItem() == null);
    }

    private void handleError(Throwable throwable) {
        if (throwable instanceof InsufficientMoneyException) {
            final Coin missingCoin = ((InsufficientMoneyException) throwable).missing;
            final String missing = missingCoin != null ? missingCoin.toFriendlyString() : "null";
            //noinspection unchecked
            new Popup<>().warning(Res.get("popup.warning.insufficientBtcFundsForBsqTx", missing))
                    .actionButtonTextWithGoTo("navigation.funds.depositFunds")
                    .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, DepositView.class))
                    .show();
        } else {
            log.error(throwable.toString());
            throwable.printStackTrace();
            new Popup<>().warning(throwable.toString()).show();
        }
    }
}
