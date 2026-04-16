package org.brit.element;

import org.brit.driver.PlaywrightiumDriver;
import org.brit.options.Browsers;
import org.brit.options.PlaywrightiumOptions;
import org.openqa.selenium.JavascriptExecutor;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for PlaywrightiumDriver's {@code executeScript} and
 * {@code executeAsyncScript} implementations.
 */
public class JsExecutionTest {

    private PlaywrightiumDriver driver;

    @BeforeClass
    public void setUp() {
        PlaywrightiumOptions opts = new PlaywrightiumOptions();
        opts.setBrowserName(Browsers.CHROMIUM.getValue());
        opts.setHeadless(true);
        driver = new PlaywrightiumDriver(opts);
        driver.get("data:text/html,<html><body>empty</body></html>");
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (driver != null) driver.quit();
    }

    // --- Argument handling ---

    /**
     * Playwright's native serializer rejects {@code Long} values with
     * "Unsupported type of argument". We convert Long (and related) to numeric
     * types Playwright can serialize.
     */
    @Test
    public void executeScript_acceptsLongArgument() {
        Object result = ((JavascriptExecutor) driver).executeScript("return arguments[0]", 9999L);
        assertThat(((Number) result).longValue()).isEqualTo(9999L);
    }

    @Test
    public void executeScript_acceptsFloatArgument() {
        Object result = ((JavascriptExecutor) driver).executeScript("return arguments[0]", 3.5f);
        assertThat(((Number) result).doubleValue()).isEqualTo(3.5);
    }

    @Test
    public void executeScript_acceptsShortAndByteArguments() {
        Object r1 = ((JavascriptExecutor) driver).executeScript("return arguments[0]", (short) 7);
        Object r2 = ((JavascriptExecutor) driver).executeScript("return arguments[0]", (byte) 3);
        assertThat(((Number) r1).intValue()).isEqualTo(7);
        assertThat(((Number) r2).intValue()).isEqualTo(3);
    }

    // --- Return value normalization ---

    /**
     * Selenium's contract: executeScript returns numeric values as Long. Playwright
     * returns Integer for small numbers. The adapter normalizes them.
     */
    @Test
    public void executeScript_returnsLongForIntegerValues() {
        Object result = ((JavascriptExecutor) driver).executeScript("return 42");
        assertThat(result).isInstanceOf(Long.class);
        assertThat(result).isEqualTo(42L);
    }

    // --- Multi-statement scripts ---

    /**
     * Selenium callers routinely pass multi-statement scripts (var declarations,
     * DOM mutation, etc.). The script must be wrapped in a block, not an
     * expression body — otherwise the JS parser rejects {@code var}/{@code let}
     * as "Unexpected token".
     */
    @Test
    public void executeScript_acceptsMultiStatementScript() {
        Object result = ((JavascriptExecutor) driver).executeScript(
                "var x = 1;"
              + "var y = 2;"
              + "return x + y;");
        assertThat(((Number) result).longValue()).isEqualTo(3L);
    }

    @Test
    public void executeScript_acceptsMultiStatementScriptWithNoReturn() {
        // Mutates the DOM; no return value. Must not throw.
        ((JavascriptExecutor) driver).executeScript(
                "var b = document.createElement('div');"
              + "b.id = 'multi-stmt-banner';"
              + "document.body.appendChild(b);");
        Object found = ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('multi-stmt-banner') !== null");
        assertThat(found).isEqualTo(true);
    }

    // --- Async scripts ---

    /**
     * executeAsyncScript must make {@code arguments} available in the user script,
     * including the callback appended as the last element.
     */
    @Test
    public void executeAsyncScript_callbackResolvesWithValue() {
        Object result = ((JavascriptExecutor) driver).executeAsyncScript(
                "var cb = arguments[arguments.length - 1]; cb(arguments[0] * 2);",
                21);
        assertThat(((Number) result).longValue()).isEqualTo(42);
    }

    /**
     * When the user script throws, our Promise wrapper rejects so the caller fails
     * fast instead of hanging forever waiting for a callback that will never come.
     */
    @Test
    public void executeAsyncScript_rejectsOnError() {
        assertThatThrownBy(() -> ((JavascriptExecutor) driver).executeAsyncScript(
                "throw new Error('synthetic test failure');"))
            .hasMessageContaining("synthetic test failure");
    }

    // --- SmartWait bypass ---

    /**
     * Katalon's SmartWait scripts reference {@code window.katalonWaiter}, which is
     * provided by a Chrome extension that doesn't run under Playwright. To avoid
     * hanging, any script that mentions {@code window.katalonWaiter} is
     * short-circuited to return null. SmartWait's own try/catch will swallow the
     * null return.
     */
    @Test
    public void executeAsyncScript_bypassesSmartWaitScripts() {
        Object result = ((JavascriptExecutor) driver).executeAsyncScript(
                "var cb = arguments[arguments.length - 1];"
              + "window.katalonWaiter.katalon_smart_waiter_do_ajax_wait(cb, 5000);",
                5000);
        assertThat(result).isNull();
    }
}
