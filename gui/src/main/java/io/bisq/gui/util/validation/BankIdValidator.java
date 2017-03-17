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

package io.bisq.gui.util.validation;


import io.bisq.locale.BankUtil;
import io.bisq.locale.Res;

public final class BankIdValidator extends BankValidator {
    public BankIdValidator(String countryCode) {
        super(countryCode);
    }

    @Override
    public ValidationResult validate(String input) {
        int length;

        switch (countryCode) {
            case "CA":
                length = 3;
                if (isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, Res.get("validation.bankIdNumber", getLabel(), length));
            case "HK":
                length = 3;
                if (isNumberWithFixedLength(input, length))
                    return super.validate(input);
                else
                    return new ValidationResult(false, Res.get("validation.bankIdNumber", getLabel(), length));
            default:
                return super.validate(input);
        }
    }

    private String getLabel() {
        String label = BankUtil.getBankIdLabel(countryCode);
        return label.substring(0, label.length() - 1);
    }

}
