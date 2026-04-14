package org.brit.element;

import org.brit.driver.PlaywrightiumDriver;
import org.brit.options.Browsers;
import org.brit.options.PlaywrightiumOptions;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests covering Selenium's {@link Select} class against
 * {@link PlaywrightWebElement}. Two known-broken behaviors prompted these:
 * <ul>
 *   <li>{@code option.click()} hangs in Playwright because {@code <option>} elements
 *       are not directly clickable inside a native select.</li>
 *   <li>{@code option.isSelected()} returned false because Playwright's
 *       {@code locator.isChecked()} only supports checkboxes/radios.</li>
 * </ul>
 *
 * Test fixture is the public CURA Healthcare Service demo, which has a single-select
 * dropdown with three options and no placeholder.
 */
public class SeleniumSelectCompatibilityTest {

    private static final String CURA_URL = "https://katalon-demo-cura.herokuapp.com";

    private PlaywrightiumDriver driver;
    private WebElement selectEl;

    @BeforeClass
    public void setUp() {
        PlaywrightiumOptions opts = new PlaywrightiumOptions();
        opts.setBrowserName(Browsers.CHROMIUM.getValue());
        opts.setHeadless(true);
        driver = new PlaywrightiumDriver(opts);
        driver.get(CURA_URL);
        // Login to reach the appointment page that has the facility <select>
        driver.findElement(By.id("btn-make-appointment")).click();
        driver.findElement(By.id("txt-username")).sendKeys("John Doe");
        driver.findElement(By.id("txt-password")).sendKeys("ThisIsNotAPassword");
        driver.findElement(By.id("btn-login")).click();
        selectEl = driver.findElement(By.id("combo_facility"));
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (driver != null) driver.quit();
    }

    @Test
    public void firstOptionSelectedByDefault() {
        List<WebElement> options = selectEl.findElements(By.tagName("option"));
        assertThat(options).hasSize(3);
        assertThat(options.get(0).isSelected()).isTrue();
        assertThat(options.get(1).isSelected()).isFalse();
        assertThat(options.get(2).isSelected()).isFalse();
    }

    @Test
    public void selectByVisibleText() {
        new Select(selectEl).selectByVisibleText("Hongkong CURA Healthcare Center");
        List<WebElement> options = selectEl.findElements(By.tagName("option"));
        assertThat(options.get(1).isSelected()).isTrue();
        assertThat(options.get(0).isSelected()).isFalse();
    }

    @Test
    public void selectByValue() {
        new Select(selectEl).selectByValue("Seoul CURA Healthcare Center");
        List<WebElement> options = selectEl.findElements(By.tagName("option"));
        assertThat(options.get(2).isSelected()).isTrue();
    }

    @Test
    public void selectByIndex() {
        new Select(selectEl).selectByIndex(0);
        List<WebElement> options = selectEl.findElements(By.tagName("option"));
        assertThat(options.get(0).isSelected()).isTrue();
    }

    @Test
    public void getAllSelectedOptionsReturnsExactlyOneForSingleSelect() {
        new Select(selectEl).selectByIndex(1);
        long selectedCount = selectEl.findElements(By.tagName("option")).stream()
                .filter(WebElement::isSelected)
                .count();
        assertThat(selectedCount).isEqualTo(1);
    }
}
