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

package io.bitsquare.app;

import com.google.inject.Inject;
import com.vinumeris.updatefx.Crypto;
import com.vinumeris.updatefx.UpdateFX;
import com.vinumeris.updatefx.UpdateSummary;
import com.vinumeris.updatefx.Updater;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.common.util.Utilities;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.bouncycastle.math.ec.ECPoint;
import org.reactfx.util.FxTimer;
import org.reactfx.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

public class UpdateProcess {
    private static final Logger log = LoggerFactory.getLogger(UpdateProcess.class);

    private static final List<ECPoint> UPDATE_SIGNING_KEYS = Crypto.decode("029EF2D0D33A2546CB15FB10D969B7D65CAFB811CB3AC902E8D9A46BE847B1DA21");
    private static final String UPDATES_BASE_URL = "https://bitsquare.io/updateFX/v03";
    private static final int UPDATE_SIGNING_THRESHOLD = 1;
    private static final Path ROOT_CLASS_PATH = UpdateFX.findCodePath(BitsquareAppMain.class);

    private final BitsquareEnvironment environment;
    private ResultHandler resultHandler;

    public enum State {
        CHECK_FOR_UPDATES,
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        NEW_RELEASE, // if a new minor release is out we inform the user to download the new binary
        FAILURE
    }

    public final ObjectProperty<State> state = new SimpleObjectProperty<>(State.CHECK_FOR_UPDATES);

    private String releaseUrl;
    private Timer timeoutTimer;

    @Inject
    public UpdateProcess(BitsquareEnvironment environment) {
        this.environment = environment;
    }

    public void restart() {
        UpdateFX.restartApp();
    }

    public void setResultHandler(ResultHandler resultHandler) {
        this.resultHandler = resultHandler;
    }

    public void init() {
        log.info("UpdateFX checking for patch version " + Version.PATCH_VERSION);

        // process.timeout() will cause an error state back but we don't want to break startup in case of an timeout
        timeoutTimer = FxTimer.runLater(Duration.ofMillis(10000), () -> {
            log.error("Timeout reached for UpdateFX");
            resultHandler.handleResult();
        });
        String userAgent = environment.getProperty(BitsquareEnvironment.APP_NAME_KEY) + Version.VERSION;

        // Check if there is a new minor version release out. The release_url should be empty if no release is available, otherwise the download url.
        try {
            releaseUrl = Utilities.readTextFileFromServer(UPDATES_BASE_URL + "/release_url", userAgent);
            if (releaseUrl != null && releaseUrl.length() > 0) {
                log.info("New release available at: " + releaseUrl);
                state.set(State.NEW_RELEASE);
                timeoutTimer.stop();
                return;
            }
            else {
                // All ok. Empty file if we have no new release.
            }
        } catch (IOException e) {
            // ignore. File might be missing
        }

        Updater updater = new Updater(UPDATES_BASE_URL, userAgent, Version.PATCH_VERSION,
                Paths.get(environment.getProperty(BitsquareEnvironment.APP_DATA_DIR_KEY)),
                ROOT_CLASS_PATH, UPDATE_SIGNING_KEYS, UPDATE_SIGNING_THRESHOLD) {
            @Override
            protected void updateProgress(long workDone, long max) {
                //log.trace("updateProgress " + workDone + "/" + max);
                super.updateProgress(workDone, max);
            }
        };

       /* updater.progressProperty().addListener((observableValue, oldValue, newValue) -> {
            log.trace("progressProperty newValue = " + newValue);
        });*/

        updater.setOnSucceeded(event -> {
            try {
                UpdateSummary summary = updater.get();
                //log.info("summary " + summary.toString());
                if (summary.descriptions != null && summary.descriptions.size() > 0) {
                    log.info("One liner: {}", summary.descriptions.get(0).getOneLiner());
                    log.info("{}", summary.descriptions.get(0).getDescription());
                }
                if (summary.highestVersion > Version.PATCH_VERSION) {
                    log.info("UPDATE_AVAILABLE");
                    state.set(State.UPDATE_AVAILABLE);
                    // We stop the timeout and treat it not completed. 
                    // The user should click the restart button manually if there are updates available.
                    timeoutTimer.stop();
                }
                else if (summary.highestVersion == Version.PATCH_VERSION) {
                    log.info("UP_TO_DATE");
                    state.set(State.UP_TO_DATE);
                    timeoutTimer.stop();
                    resultHandler.handleResult();
                }
            } catch (Throwable e) {
                log.error("Exception at processing UpdateSummary: " + e.getMessage());

                // we treat errors as update not as critical errors to prevent startup, 
                // so we use state.onCompleted() instead of state.onError()
                state.set(State.FAILURE);
                timeoutTimer.stop();
                resultHandler.handleResult();
            }
        });
        updater.setOnFailed(event -> {
            log.error("Update failed: " + updater.getException());
            updater.getException().printStackTrace();

            // we treat errors as update not as critical errors to prevent startup, 
            // so we use state.onCompleted() instead of state.onError()
            state.set(State.FAILURE);
            timeoutTimer.stop();
            resultHandler.handleResult();
        });

        Thread thread = new Thread(updater, "Online update check");
        thread.setDaemon(true);
        thread.start();
    }

    public String getReleaseUrl() {
        return releaseUrl;
    }
}