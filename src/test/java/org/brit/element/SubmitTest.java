package org.brit.element;

import org.brit.driver.PlaywrightiumDriver;
import org.brit.options.Browsers;
import org.brit.options.PlaywrightiumOptions;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Selenium's {@code WebElement.submit()} submits the enclosing form regardless
 * of which element it's called on. Only {@code HTMLFormElement} has a native
 * {@code submit()} method, so the adapter must walk up to the form.
 */
public class SubmitTest {

    private PlaywrightiumDriver driver;

    @BeforeMethod
    public void setUp() {
        PlaywrightiumOptions opts = new PlaywrightiumOptions();
        opts.setBrowserName(Browsers.CHROMIUM.getValue());
        opts.setHeadless(true);
        driver = new PlaywrightiumDriver(opts);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (driver != null) driver.quit();
    }

    private void loadForm() {
        // Instrument both `action=javascript:` (fires regardless) AND onsubmit
        // (only fires if the submit event is dispatched — i.e. requestSubmit, not
        // the low-level form.submit()).
        driver.get("data:text/html," +
                "<html><body>" +
                "<form id='f' action='javascript:window.__submitted=true;void(0)'" +
                "      onsubmit='window.__submitEventFired=true;'>" +
                "  <input id='u' name='u' type='text' />" +
                "  <input id='p' name='p' type='password' />" +
                "  <button id='b' type='submit'>go</button>" +
                "</form>" +
                "</body></html>");
    }

    @Test
    public void submitFromInputSubmitsEnclosingFormAndFiresEvent() {
        loadForm();
        driver.findElement(By.id("u")).submit();
        JavascriptExecutor js = (JavascriptExecutor) driver;
        assertThat(js.executeScript("return window.__submitted === true")).isEqualTo(true);
        // requestSubmit dispatches the submit event; the low-level form.submit()
        // would skip it. Selenium callers expect event-firing semantics.
        assertThat(js.executeScript("return window.__submitEventFired === true")).isEqualTo(true);
    }

    @Test
    public void submitFromButtonInsideFormSubmitsEnclosingForm() {
        loadForm();
        driver.findElement(By.id("b")).submit();
        assertThat(((JavascriptExecutor) driver).executeScript("return window.__submitted === true"))
                .isEqualTo(true);
    }

    @Test
    public void submitFromFormElementSubmitsForm() {
        loadForm();
        driver.findElement(By.id("f")).submit();
        assertThat(((JavascriptExecutor) driver).executeScript("return window.__submitted === true"))
                .isEqualTo(true);
    }

    @Test
    public void submitOnElementOutsideFormThrows() {
        driver.get("data:text/html,<html><body><div id='orphan'>no form</div></body></html>");
        WebElement orphan = driver.findElement(By.id("orphan"));
        assertThatThrownBy(orphan::submit).hasMessageContaining("not inside a form");
    }
}
