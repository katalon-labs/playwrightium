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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Click behavior on &lt;option&gt; inside a multi-select. Selenium's semantics:
 * clicking an option toggles its selection (like ctrl+click). Playwrightium
 * delegates option clicks to the parent select's {@code selectOption} API,
 * which by default REPLACES the selection — so multi-select must explicitly
 * preserve the current selection and toggle the clicked value.
 */
public class MultiSelectToggleTest {

    private PlaywrightiumDriver driver;

    @BeforeMethod
    public void setUp() {
        PlaywrightiumOptions opts = new PlaywrightiumOptions();
        opts.setBrowserName(Browsers.CHROMIUM.getValue());
        opts.setHeadless(true);
        driver = new PlaywrightiumDriver(opts);
        driver.get("data:text/html,<html><body>" +
                "<select id='m' multiple size='4'>" +
                "  <option value='a'>Alpha</option>" +
                "  <option value='b'>Bravo</option>" +
                "  <option value='c'>Charlie</option>" +
                "  <option value='d'>Delta</option>" +
                "</select></body></html>");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (driver != null) driver.quit();
    }

    @SuppressWarnings("unchecked")
    private List<String> selectedValues() {
        return (List<String>) ((JavascriptExecutor) driver).executeScript(
                "return Array.from(document.getElementById('m').selectedOptions).map(o => o.value)");
    }

    @Test
    public void clickingMultipleOptionsAddsEachToSelection() {
        driver.findElement(By.cssSelector("#m option[value='a']")).click();
        driver.findElement(By.cssSelector("#m option[value='c']")).click();
        assertThat(selectedValues()).containsExactlyInAnyOrder("a", "c");
    }

    @Test
    public void clickingSelectedOptionTogglesItOff() {
        WebElement alpha = driver.findElement(By.cssSelector("#m option[value='a']"));
        alpha.click();
        assertThat(selectedValues()).containsExactly("a");
        alpha.click(); // toggle off
        assertThat(selectedValues()).isEmpty();
    }

    @Test
    public void singleSelectClickReplacesSelection() {
        driver.get("data:text/html,<html><body>" +
                "<select id='s'>" +
                "  <option value='a'>Alpha</option>" +
                "  <option value='b'>Bravo</option>" +
                "</select></body></html>");
        driver.findElement(By.cssSelector("#s option[value='a']")).click();
        assertThat(((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('s').value")).isEqualTo("a");
        driver.findElement(By.cssSelector("#s option[value='b']")).click();
        assertThat(((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('s').value")).isEqualTo("b");
    }
}
