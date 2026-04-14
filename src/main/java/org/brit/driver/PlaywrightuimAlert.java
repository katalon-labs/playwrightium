package org.brit.driver;

import com.microsoft.playwright.Dialog;
import org.openqa.selenium.Alert;

/**
 * Bridges Selenium's post-hoc Alert API onto Playwright's pre-registered dialog
 * handler model.
 *
 * <p>In Selenium, user code triggers a dialog, then calls
 * {@code driver.switchTo().alert().accept()} to handle it. In Playwright, a
 * handler must be registered <em>before</em> the dialog fires — otherwise the
 * dialog is auto-dismissed. The {@link PlaywrightiumDriver} registers a handler
 * at construction that:
 *
 * <ol>
 *   <li>Captures the dialog's message and outcome in
 *       {@link DialogState} so user code can read them later.</li>
 *   <li>Immediately {@code accept()}s the dialog with the currently registered
 *       {@code sendKeys} text (if any), so the triggering JS/click does not
 *       block.</li>
 * </ol>
 *
 * <p>This is sufficient for the common "trigger alert, then call
 * {@code acceptAlert()}" pattern. Callers that need to <em>dismiss</em> an
 * alert must call {@link #sendKeys(String)} and {@link #dismiss()} <em>before</em>
 * triggering the alert, or arrange for the trigger to run on a separate thread.
 */
public class PlaywrightuimAlert implements Alert {

    /**
     * Driver-scoped state maintained by the perpetual dialog handler. Updated
     * each time a dialog fires; read by Alert instances returned from
     * {@code switchTo().alert()}.
     */
    public static class DialogState {
        private volatile String lastMessage;
        private volatile String pendingSendKeys;
        private volatile boolean nextShouldDismiss;

        /** Invoked by the driver's page.onDialog handler. */
        public void onDialog(Dialog dialog) {
            this.lastMessage = dialog.message();
            if (nextShouldDismiss) {
                nextShouldDismiss = false;
                dialog.dismiss();
            } else if (pendingSendKeys != null) {
                String keys = pendingSendKeys;
                pendingSendKeys = null;
                dialog.accept(keys);
            } else {
                dialog.accept();
            }
        }

        String getLastMessage() { return lastMessage; }
        void requestDismissNext() { nextShouldDismiss = true; }
        void setPendingSendKeys(String keys) { pendingSendKeys = keys; }
    }

    private final DialogState state;

    public PlaywrightuimAlert(PlaywrightiumDriver driver) {
        this.state = driver.getDialogState();
    }

    /**
     * A no-op — the dialog has already been accepted by the driver-level
     * handler. Provided so Selenium code that calls
     * {@code alert.accept()} after triggering works unchanged.
     */
    @Override
    public void accept() {
        // Already accepted by the dialog handler. Nothing to do.
    }

    /**
     * Requests that the <em>next</em> dialog be dismissed rather than accepted.
     * Must be called before the dialog-triggering action runs.
     */
    @Override
    public void dismiss() {
        state.requestDismissNext();
    }

    @Override
    public String getText() {
        return state.getLastMessage();
    }

    /**
     * Registers text to pass to the <em>next</em> dialog's {@code accept(text)}
     * call. Must be called before the dialog-triggering action runs.
     */
    @Override
    public void sendKeys(String keysToSend) {
        state.setPendingSendKeys(keysToSend);
    }
}
