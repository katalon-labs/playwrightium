package org.brit.driver;

import org.brit.options.Browsers;
import org.brit.options.PlaywrightiumOptions;
import org.openqa.selenium.Alert;
import org.openqa.selenium.JavascriptExecutor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that exercise PlaywrightiumDriver's dialog/alert bridge. Playwright
 * requires a dialog handler to be registered <em>before</em> the dialog fires,
 * while Selenium callers register intent <em>after</em>. The bridge resolves
 * this by auto-accepting dialogs in a perpetual handler (good enough for the
 * common {@code acceptAlert()} case) and by supporting pre-registered intent
 * for dismiss/sendKeys via {@code switchTo().alert().dismiss()/.sendKeys(...)}
 * <em>before</em> the triggering action runs.
 *
 * <p>Each test gets a fresh driver so stale dialog state doesn't bleed across
 * tests.
 */
public class AlertHandlingTest {

    private PlaywrightiumDriver driver;

    @BeforeMethod
    public void setUp() {
        PlaywrightiumOptions opts = new PlaywrightiumOptions();
        opts.setBrowserName(Browsers.CHROMIUM.getValue());
        opts.setHeadless(true);
        driver = new PlaywrightiumDriver(opts);
        driver.get("data:text/html,<html><body>empty</body></html>");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (driver != null) driver.quit();
    }

    private JavascriptExecutor js() {
        return (JavascriptExecutor) driver;
    }

    @Test
    public void alertTextAvailableAfterTrigger() {
        js().executeScript("alert('hello from js')");
        assertThat(driver.switchTo().alert().getText()).isEqualTo("hello from js");
    }

    @Test
    public void acceptAlertIsNoOpAndSafeToCall() {
        js().executeScript("alert('hi')");
        Alert alert = driver.switchTo().alert();
        // Already handled by the perpetual handler — should not throw.
        alert.accept();
        assertThat(alert.getText()).isEqualTo("hi");
    }

    @Test
    public void dismissRequiresPreRegisteredIntent() {
        // Register intent before the confirm fires
        driver.switchTo().alert().dismiss();
        js().executeScript("window.__result = confirm('Are you sure?')");

        Object result = js().executeScript("return window.__result");
        assertThat(result).isEqualTo(false);
    }

    @Test
    public void sendKeysBeforePromptPassesTextToAccept() {
        driver.switchTo().alert().sendKeys("typed by playwrightium");
        js().executeScript("window.__promptResult = prompt('enter value:')");

        Object result = js().executeScript("return window.__promptResult");
        assertThat(result).isEqualTo("typed by playwrightium");
    }

    @Test
    public void dismissIntentIsConsumedOnce() {
        // First confirm: registered to dismiss
        driver.switchTo().alert().dismiss();
        js().executeScript("window.__r1 = confirm('first')");
        assertThat(js().executeScript("return window.__r1")).isEqualTo(false);

        // Second confirm: no intent, should auto-accept
        js().executeScript("window.__r2 = confirm('second')");
        assertThat(js().executeScript("return window.__r2")).isEqualTo(true);
    }
}
