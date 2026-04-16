package org.brit.element;

import org.brit.driver.PlaywrightiumDriver;
import org.brit.options.Browsers;
import org.brit.options.PlaywrightiumOptions;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

import java.io.File;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for {@code WebElement.sendKeys} — especially the mixing of
 * plain text and Selenium {@link Keys} constants. The CURA login flow sends
 * username, then {@code Keys.TAB} as a separate call; earlier the TAB call
 * would wipe the field because the adapter called {@code locator.fill("")}
 * unconditionally at the end.
 */
public class SendKeysTest {

    private PlaywrightiumDriver driver;

    @BeforeMethod
    public void setUp() {
        PlaywrightiumOptions opts = new PlaywrightiumOptions();
        opts.setBrowserName(Browsers.CHROMIUM.getValue());
        opts.setHeadless(true);
        driver = new PlaywrightiumDriver(opts);
        driver.get("data:text/html," +
                "<html><body>" +
                "<input id='a' type='text' />" +
                "<input id='b' type='text' />" +
                "</body></html>");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (driver != null) driver.quit();
    }

    private String value(String id) {
        return (String) ((JavascriptExecutor) driver)
                .executeScript("return document.getElementById(arguments[0]).value", id);
    }

    /** sendKeys(Keys.TAB) in a separate call must not wipe the previously typed text. */
    @Test
    public void tabAfterTextPreservesValue() {
        WebElement a = driver.findElement(By.id("a"));
        a.sendKeys("John Doe");
        a.sendKeys(Keys.TAB);
        assertThat(value("a")).isEqualTo("John Doe");
    }

    /** Text and TAB interleaved in one call — text is typed, TAB moves focus. */
    @Test
    public void tabInSameCallMovesFocusAndPreservesValue() {
        driver.findElement(By.id("a")).sendKeys("hello", Keys.TAB, "world");
        assertThat(value("a")).isEqualTo("hello");
        assertThat(value("b")).isEqualTo("world");
    }

    /** Successive sendKeys calls must append (Selenium semantics), not replace. */
    @Test
    public void successiveSendKeysAppends() {
        WebElement a = driver.findElement(By.id("a"));
        a.sendKeys("foo");
        a.sendKeys("bar");
        assertThat(value("a")).isEqualTo("foobar");
    }

    /** Typing plain text still works unchanged. */
    @Test
    public void plainTextSendKeysWorks() {
        driver.findElement(By.id("a")).sendKeys("Playwrightium");
        assertThat(value("a")).isEqualTo("Playwrightium");
    }

    /**
     * Regression: typing into one input after another must fire a real
     * document-level mousedown so popups like jQuery UI's datepicker (which
     * listen for outside clicks via {@code document.mousedown}) close. A
     * programmatic {@code focus()} on the second input isn't enough —
     * Playwrightium must physically click.
     */
    @Test
    public void typingIntoSecondInputFiresDocumentMousedown() {
        driver.get("data:text/html,<html><body>" +
                "<input id='one' type='text' />" +
                "<input id='two' type='text' />" +
                "<script>window.__mdCount = 0; document.addEventListener('mousedown', function(){ window.__mdCount++; });</script>" +
                "</body></html>");
        WebElement two = driver.findElement(By.id("two"));
        two.sendKeys("hi");
        Number count = (Number) ((JavascriptExecutor) driver)
                .executeScript("return window.__mdCount");
        assertThat(count.intValue()).isGreaterThanOrEqualTo(1);
        assertThat(value("two")).isEqualTo("hi");
    }

    /**
     * Regression: Katalon's uploadFile keyword calls {@code clear()} before
     * {@code sendKeys(path)}. For file inputs, {@code clear()} must be a no-op
     * because Playwright's fill-based clear rejects file inputs. Without this,
     * uploadFile fails with "Input of type 'file' cannot be filled".
     */
    @Test
    public void fileInputClearThenSendKeysWorks() throws Exception {
        driver.get("data:text/html,<html><body><input id='f' type='file' /></body></html>");
        File tmp = File.createTempFile("pwium-upload-", ".txt");
        try {
            tmp.deleteOnExit();
            java.nio.file.Files.writeString(tmp.toPath(), "hello");
            WebElement el = driver.findElement(By.id("f"));
            el.clear(); // must not throw
            el.sendKeys(tmp.getAbsolutePath());
            String attached = (String) ((JavascriptExecutor) driver).executeScript(
                    "return document.getElementById('f').files[0]?.name || ''");
            assertThat(attached).isEqualTo(tmp.getName());
        } finally {
            tmp.delete();
        }
    }
}
