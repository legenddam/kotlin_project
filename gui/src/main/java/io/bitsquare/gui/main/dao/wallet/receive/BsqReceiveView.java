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

package io.bitsquare.gui.main.dao.wallet.receive;

import io.bitsquare.app.DevEnv;
import io.bitsquare.btc.wallet.BsqWalletService;
import io.bitsquare.common.UserThread;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.AddressTextField;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.dao.wallet.BalanceUtil;
import io.bitsquare.gui.main.overlays.windows.QRCodeWindow;
import io.bitsquare.gui.util.BsqFormatter;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.Res;
import javafx.geometry.Insets;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;
import org.bitcoinj.core.Coin;
import org.bitcoinj.uri.BitcoinURI;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class BsqReceiveView extends ActivatableView<GridPane, Void> {

    private ImageView qrCodeImageView;
    private AddressTextField addressTextField;
    private InputTextField amountTextField;
    private TextField balanceTextField;

    private final BsqWalletService bsqWalletService;
    private final BsqFormatter formatter;
    private BalanceUtil balanceUtil;

    private int gridRow = 0;
    private final String paymentLabelString;
    private Subscription amountTextFieldSubscription;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BsqReceiveView(BsqWalletService bsqWalletService, BsqFormatter formatter, BalanceUtil balanceUtil) {
        this.bsqWalletService = bsqWalletService;
        this.formatter = formatter;
        this.balanceUtil = balanceUtil;
        paymentLabelString = Res.get("dao.wallet.receive.fundBSQWallet");
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 1, Res.get("shared.balance"));
        balanceTextField = addLabelTextField(root, gridRow, Res.get("shared.bsqBalance"), Layout.FIRST_ROW_DISTANCE).second;
        balanceUtil.setBalanceTextField(balanceTextField);
        balanceUtil.initialize();

        addTitledGroupBg(root, ++gridRow, 3, Res.get("dao.wallet.receive.fundYourWallet"), Layout.GROUP_DISTANCE);

        qrCodeImageView = new ImageView();
        qrCodeImageView.setStyle("-fx-cursor: hand;");
        Tooltip.install(qrCodeImageView, new Tooltip(Res.get("shared.openLargeQRWindow")));
        GridPane.setRowIndex(qrCodeImageView, gridRow);
        GridPane.setColumnIndex(qrCodeImageView, 1);
        GridPane.setMargin(qrCodeImageView, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        root.getChildren().add(qrCodeImageView);

        addressTextField = addLabelAddressTextField(root, ++gridRow, Res.getWithCol("shared.address")).second;
        addressTextField.setPaymentLabel(paymentLabelString);

        amountTextField = addLabelInputTextField(root, ++gridRow, Res.get("dao.wallet.receive.amountOptional")).second;
        if (DevEnv.DEV_MODE)
            amountTextField.setText("10");
    }

    @Override
    protected void activate() {
        balanceUtil.activate();

        amountTextFieldSubscription = EasyBind.subscribe(amountTextField.textProperty(), t -> {
            addressTextField.setAmountAsCoin(formatter.parseToCoin(t));
            updateQRCode();
        });
        qrCodeImageView.setOnMouseClicked(e -> GUIUtil.showFeeInfoBeforeExecute(
                () -> UserThread.runAfter(
                        () -> new QRCodeWindow(getBitcoinURI()).show(),
                        200, TimeUnit.MILLISECONDS)
        ));
        addressTextField.setAddress(bsqWalletService.freshReceiveAddress().toString());
        updateQRCode();
    }

    @Override
    protected void deactivate() {
        balanceUtil.deactivate();

        qrCodeImageView.setOnMouseClicked(null);
        amountTextFieldSubscription.unsubscribe();
    }

    private void updateQRCode() {
        if (addressTextField.getAddress() != null && !addressTextField.getAddress().isEmpty()) {
            final byte[] imageBytes = QRCode
                    .from(getBitcoinURI())
                    .withSize(150, 150) // code has 41 elements 8 px is border with 150 we get 3x scale and min. border
                    .to(ImageType.PNG)
                    .stream()
                    .toByteArray();
            Image qrImage = new Image(new ByteArrayInputStream(imageBytes));
            qrCodeImageView.setImage(qrImage);
        }
    }

    private Coin getAmountAsCoin() {
        return formatter.parseToCoin(amountTextField.getText());
    }

    private String getBitcoinURI() {
        return BitcoinURI.convertToBitcoinURI(addressTextField.getAddress(),
                getAmountAsCoin(),
                paymentLabelString,
                null);
    }
}

