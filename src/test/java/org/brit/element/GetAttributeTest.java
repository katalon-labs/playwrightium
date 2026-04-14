package org.brit.element;

import org.brit.driver.PlaywrightiumDriver;
import org.brit.options.Browsers;
import org.brit.options.PlaywrightiumOptions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link PlaywrightWebElement#getAttribute(String)} mirrors Selenium's
 * hybrid behavior — returning whichever of the HTML attribute or DOM property
 * has a non-empty value. This matters for {@code value} on text inputs: the HTML
 * attribute stays empty after the user types, but the DOM {@code value} property
 * reflects the current input.
 */
public class GetAttributeTest {

    private PlaywrightiumDriver driver;

    @BeforeClass
    public void setUp() {
        PlaywrightiumOptions opts = new PlaywrightiumOptions();
        opts.setBrowserName(Browsers.CHROMIUM.getValue());
        opts.setHeadless(true);
        driver = new PlaywrightiumDriver(opts);
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (driver != null) driver.quit();
    }

    @Test
    public void getAttributeValue_returnsCurrentDomPropertyForInput() {
        driver.get("data:text/html,<input id='u' type='text' />");
        WebElement input = driver.findElement(By.id("u"));
        input.sendKeys("hello");
        assertThat(input.getAttribute("value")).isEqualTo("hello");
    }

    @Test
    public void getAttributeValue_returnsHtmlAttributeWhenSetInMarkup() {
        driver.get("data:text/html,<input id='u' type='text' value='initial' />");
        WebElement input = driver.findElement(By.id("u"));
        assertThat(input.getAttribute("value")).isEqualTo("initial");
    }

    @Test
    public void getAttributeValue_reflectsCurrentValueAfterClearAndType() {
        driver.get("data:text/html,<input id='u' type='text' value='initial' />");
        WebElement input = driver.findElement(By.id("u"));
        input.clear();
        input.sendKeys("replaced");
        assertThat(input.getAttribute("value")).isEqualTo("replaced");
    }

    @Test
    public void getAttributeValue_returnsCurrentDomPropertyForTextarea() {
        driver.get("data:text/html,<textarea id='t'></textarea>");
        WebElement ta = driver.findElement(By.id("t"));
        ta.sendKeys("comment body");
        assertThat(ta.getAttribute("value")).isEqualTo("comment body");
    }
}
