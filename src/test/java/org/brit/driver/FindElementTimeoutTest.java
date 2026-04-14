package org.brit.driver;

import org.brit.options.Browsers;
import org.brit.options.PlaywrightiumOptions;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that {@code findElement} fails fast when the element is missing, instead
 * of blocking on Playwright's default 30-second waitFor timeout. Katalon's
 * keyword layer manages its own timeouts and retries, so this layer should be
 * as quick as possible — a stuck 30s wait inside a 5s keyword retry loop is
 * effectively a hang.
 */
public class FindElementTimeoutTest {

    private PlaywrightiumDriver driver;

    @BeforeClass
    public void setUp() {
        PlaywrightiumOptions opts = new PlaywrightiumOptions();
        opts.setBrowserName(Browsers.CHROMIUM.getValue());
        opts.setHeadless(true);
        driver = new PlaywrightiumDriver(opts);
        driver.get("data:text/html,<body>no match here</body>");
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (driver != null) driver.quit();
    }

    @Test
    public void findElement_missingElementFailsInUnderThreeSeconds() {
        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> driver.findElement(By.id("does-not-exist")))
                .isInstanceOf(NoSuchElementException.class);
        long elapsed = System.currentTimeMillis() - start;
        // 1s cap + overhead; should be well under 3s. Anything close to 30s means
        // the timeout regressed to Playwright's default.
        assertThat(elapsed).isLessThan(3000);
    }
}
