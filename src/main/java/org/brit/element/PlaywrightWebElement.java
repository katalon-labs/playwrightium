package org.brit.element;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import org.apache.commons.text.CaseUtils;
import org.brit.element.adapters.GetAttributeAdapter;
import org.brit.driver.adapters.FindElementAdapter;
import org.brit.locators.ArialSearchOptions;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.*;
import org.openqa.selenium.remote.RemoteWebElement;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * @author Serhii Bryt
 * WebElement implementation
 * @see org.openqa.selenium.WebElement
 */
public class PlaywrightWebElement extends RemoteWebElement {

    private static final Pattern NOT_CHECKBOX_OR_RADIO = Pattern.compile("Not a checkbox or radio button");

    private final Locator locator;

    public Locator getLocator() {
        return locator;
    }

    public PlaywrightWebElement(Locator locator) {
        this.locator = locator;
    }

    @Override
    public void click() {
        // Playwright cannot click <option> elements directly because they're hidden
        // inside the native select dropdown. Selenium's Select class clicks options
        // to select them; to keep that compatible, route option.click() through the
        // parent <select>'s selectOption() call.
        String tagName = getTagName();
        if ("option".equals(tagName)) {
            // Capture the parent <select>, whether it's multi-select, and the
            // clicked option's value. For a multi-select, clicking an already-
            // unselected option adds to the selection (like ctrl+click); we
            // simulate this by collecting current values + the new one and
            // handing the full list to selectOption. For a single-select,
            // selectOption(value) replaces — which matches click semantics.
            String value = locator.evaluate("node => node.value").toString();
            Locator parentSelect = locator.locator("xpath=ancestor::select[1]");
            Boolean isMulti = (Boolean) parentSelect.evaluate("s => s.multiple");
            if (Boolean.TRUE.equals(isMulti)) {
                // Multi-select: click toggles the clicked option (like ctrl+click).
                // Gather the current selection, flip membership of the clicked
                // value, and hand the full updated list to selectOption.
                Object current = parentSelect.evaluate(
                        "s => Array.from(s.selectedOptions).map(o => o.value)");
                java.util.List<String> values = new java.util.ArrayList<>();
                if (current instanceof java.util.Collection) {
                    for (Object v : (java.util.Collection<?>) current) values.add(String.valueOf(v));
                }
                if (values.contains(value)) {
                    values.remove(value);
                } else {
                    values.add(value);
                }
                parentSelect.selectOption(values.toArray(new String[0]));
            } else {
                parentSelect.selectOption(new com.microsoft.playwright.options.SelectOption().setValue(value));
            }
            return;
        }
        locator.click();
    }

    public void clickWithAlert(Consumer<Dialog> clickWithAlertOptions) {
        locator
                .page()
                .onDialog(clickWithAlertOptions);
        locator.click();
    }

    public File download() {
        Download download = locator.page().waitForDownload(() -> {
            locator.click();
        });
        return download.path().toFile();
    }

    public void upload(File file) {
        locator.setInputFiles(file.toPath());
    }

    @Override
    public void submit() {
        // Selenium semantics: submit() on any element submits its enclosing form
        // as if the user had clicked a submit button — so the submit event fires
        // and onsubmit handlers run. If the form has a submit-type button, click
        // it inside waitForNavigation so Playwright auto-waits for the resulting
        // navigation (avoids "Execution context destroyed" in callers that race
        // against an in-flight navigation). Otherwise fall back to requestSubmit.
        Page page = locator.page();
        Object submitButton = locator.evaluate(
                "el => {" +
                "  const form = el.tagName === 'FORM' ? el : (el.form || el.closest('form'));" +
                "  if (!form) throw new Error('Element is not inside a form');" +
                "  return form.querySelector('button[type=submit], input[type=submit]');" +
                "}");
        try {
            page.waitForNavigation(
                    new Page.WaitForNavigationOptions().setTimeout(5000),
                    () -> {
                        if (submitButton != null) {
                            Locator btn = locator.locator(
                                    "xpath=ancestor-or-self::form[1]//*[(self::button or self::input) and @type='submit'][1]");
                            btn.click();
                        } else {
                            locator.evaluate(
                                    "el => {" +
                                    "  const form = el.tagName === 'FORM' ? el : (el.form || el.closest('form'));" +
                                    "  if (typeof form.requestSubmit === 'function') form.requestSubmit();" +
                                    "  else form.submit();" +
                                    "}");
                        }
                    });
        } catch (PlaywrightException navTimeout) {
            // Submit didn't cause navigation (AJAX form, SPA, same-page handler).
            // Nothing more to do — the submit event already fired above.
        }
    }

    @Override
    public void sendKeys(CharSequence... keysToSend) {
        if ("file".equals(locator.getAttribute("type"))) {
            StringBuilder toSend = new StringBuilder();
            for (CharSequence charSequence : keysToSend) {
                toSend.append(charSequence);
            }
            locator.setInputFiles(Paths.get(toSend.toString()));
            return;
        }
        // Selenium semantics: sendKeys appends at the cursor and may interleave
        // plain text with special keys (e.g. Keys.TAB). CLICK (not focus) the
        // element first so a real mousedown bubbles to the document — popups
        // like jQuery UI datepicker listen to document.mousedown to close
        // themselves, and a programmatic .focus() doesn't trigger it.
        //
        // Use force:true to bypass Playwright's "receives events" hit-test.
        // Selenium's sendKeys doesn't do that check, and some pages (e.g. CURA
        // healthcare's table-based forms) have inputs whose center returns the
        // parent <td> from elementFromPoint, which makes the default hit-test
        // fail and the click retry for 30 s. force:true still dispatches real
        // mouse events — the only thing skipped is the pre-click DOM check.
        //
        // After the click we route all subsequent key events through
        // page.keyboard so Tab/Shift+Tab can legitimately move focus and later
        // chars land on the now-focused element (matching Selenium's Actions).
        locator.click(new Locator.ClickOptions().setForce(true));
        com.microsoft.playwright.Keyboard keyboard = locator.page().keyboard();
        StringBuilder run = new StringBuilder();
        for (CharSequence charSequence : keysToSend) {
            for (int i = 0; i < charSequence.length(); i++) {
                char c = charSequence.charAt(i);
                Keys special = Keys.getKeyFromUnicode(c);
                if (special != null) {
                    if (run.length() > 0) {
                        keyboard.type(run.toString());
                        run.setLength(0);
                    }
                    String keyToPress = CaseUtils.toCamelCase(special.name(), true, ' ');
                    keyToPress = switch (keyToPress) {
                        case "Left" -> "ArrowLeft";
                        case "Up" -> "ArrowUp";
                        case "Down" -> "ArrowDown";
                        case "Right" -> "ArrowRight";
                        default -> keyToPress;
                    };
                    keyboard.press(keyToPress);
                } else {
                    run.append(c);
                }
            }
        }
        if (run.length() > 0) {
            keyboard.type(run.toString());
        }
    }

    @Override
    public void clear() {
        // Playwright's clear() uses fill('') internally, which rejects file
        // inputs ("Input of type 'file' cannot be filled"). For file inputs,
        // clearing is a no-op — setInputFiles replaces the selection anyway.
        if ("file".equals(locator.getAttribute("type"))) {
            return;
        }
        locator.clear(new Locator.ClearOptions().setForce(true));
    }

    @Override
    public String getTagName() {
        return String.valueOf(locator.evaluate("node => node.tagName")).toLowerCase();
    }

    @Nullable
    @Override
    public String getAttribute(String name) {
        return GetAttributeAdapter.getAttribute(locator, name);
    }

    /**
     * RemoteWebElement.equals compares on {@code this.id}, which we don't set.
     * Katalon keywords (e.g. verifyOptionSelectedByLabel) call equals to dedupe
     * matching elements — without this override, they NPE. Compare by the
     * underlying Playwright locator identity.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PlaywrightWebElement)) return false;
        return locator.equals(((PlaywrightWebElement) other).locator);
    }

    @Override
    public int hashCode() {
        return locator.hashCode();
    }

    @Override
    public boolean isSelected() {
        // Selenium's WebElement.isSelected() works for any selectable element:
        // checkboxes/radios use the `checked` property, <option> uses `selected`.
        // Playwright's isChecked() handles only checkboxes/radios — for <option>
        // it returns false (or throws "Not a checkbox or radio button" on older
        // versions). For options, we read node.selected directly.
        if ("option".equals(getTagName())) {
            return Boolean.TRUE.equals(locator.evaluate("node => node.selected"));
        }
        try {
            return locator.isChecked();
        } catch (PlaywrightException e) {
            if (NOT_CHECKBOX_OR_RADIO.matcher(e.getMessage()).find()) {
                return Boolean.TRUE.equals(locator.evaluate("node => !!node.selected || !!node.checked"));
            }
            throw e;
        }
    }

    @Override
    public boolean isEnabled() {
        return locator.isEnabled();
    }

    @Override
    public String getText() {
        return locator.textContent();
    }

    @Override
    public List<WebElement> findElements(By by) {
        return FindElementAdapter.findElements(getLocatorFromBy(by));
    }

    @Override
    public WebElement findElement(By by) {
        return FindElementAdapter.findElement(getLocatorFromBy(by), by);
    }

    @Nullable
    private Locator getLocatorFromBy(By by) {
        String using = ((By.Remotable) by).getRemoteParameters().using();
        String value = ((By.Remotable) by).getRemoteParameters().value().toString();
        return switch (using) {
            case "css selector" -> locator.locator(value);
            case "class name" -> locator.locator("xpath=.//*[@class='%s']".formatted(value));
            case "xpath" -> locator.locator("xpath=" + value);
            case "tag name" -> locator.locator("xpath=.//" + value);
            case "name" -> locator.locator("[name='%s']".formatted(value));
            case "partial link text", "link text" ->
                    locator.locator("a", new Locator.LocatorOptions().setHasText(value));
            case "id" -> locator.locator("#%s".formatted(value));
            default -> {
                List<Object> list = (List<Object>) ((By.Remotable) by).getRemoteParameters().value();
                yield switch (using) {
                    case "getByRole" -> {
                        AriaRole role = (AriaRole) list.get(0);
                        ArialSearchOptions getByRoleOptions = ((ArialSearchOptions) list.get(1));
                        yield locator.getByRole(role, convertOption(getByRoleOptions));
                    }
                    case "getByTestId" -> locator.getByTestId((String) list.get(0));
                    case "getByAltText" -> locator.getByAltText((String) list.get(0),
                            new Locator.GetByAltTextOptions().setExact((Boolean) list.get(1)));
                    case "getByLabel" -> locator.getByLabel((String) list.get(0),
                            new Locator.GetByLabelOptions().setExact((Boolean) list.get(1)));
                    case "getByPlaceholder" -> locator.getByPlaceholder((String) list.get(0),
                            new Locator.GetByPlaceholderOptions().setExact((Boolean) list.get(1)));
                    case "getByText" -> locator.getByText((String) list.get(0),
                            new Locator.GetByTextOptions().setExact((Boolean) list.get(1)));
                    case "getByTitle" -> locator.getByTitle((String) list.get(0),
                            new Locator.GetByTitleOptions().setExact((Boolean) list.get(1)));
                    default -> null;
                };
            }
        };
    }

    private Locator.GetByRoleOptions convertOption(ArialSearchOptions arialSearchOptions) {
        Object name = arialSearchOptions.getName();
        Locator.GetByRoleOptions getByRoleOptions = new Locator.GetByRoleOptions();
        if (arialSearchOptions.checked != null) {
            getByRoleOptions.setChecked(arialSearchOptions.getChecked());
        }
        if (arialSearchOptions.exact != null) {
            getByRoleOptions.setExact(arialSearchOptions.getExact());
        }
        if (arialSearchOptions.disabled != null) {
            getByRoleOptions.setDisabled(arialSearchOptions.getDisabled());
        }
        if (arialSearchOptions.expanded != null) {
            getByRoleOptions.setExpanded(arialSearchOptions.getExpanded());
        }
        if (arialSearchOptions.pressed != null) {
            getByRoleOptions.setPressed(arialSearchOptions.getPressed());
        }
        if (arialSearchOptions.selected != null) {
            getByRoleOptions.setSelected(arialSearchOptions.getSelected());
        }
        if (arialSearchOptions.includeHidden != null) {
            getByRoleOptions.setIncludeHidden(arialSearchOptions.getIncludeHidden());
        }
        if (arialSearchOptions.level != null) {
            getByRoleOptions.setLevel(arialSearchOptions.getLevel());
        }
        if (arialSearchOptions.name != null) {
            if (name instanceof Pattern) {
                getByRoleOptions.setName((Pattern) name);
            } else if (name instanceof String) {
                getByRoleOptions.setName((String) name);
            }
        }
        return getByRoleOptions;
    }

    @Override
    public boolean isDisplayed() {
        return locator.isVisible();
    }

    @Override
    public Point getLocation() {
        // Playwright's boundingBox returns viewport-relative coordinates.
        // Selenium's contract is document-relative (scroll-independent), which
        // Katalon's verifyElement{In,NotIn}Viewport depends on — it checks
        // whether getRect() falls inside the viewport box (0,0,vpW,vpH), so
        // viewport-relative coordinates would incorrectly report a scrolled-off
        // element as visible.
        BoundingBox boundingBox = locator.boundingBox();
        Number scrollX = (Number) locator.page().evaluate("() => window.scrollX || window.pageXOffset || 0");
        Number scrollY = (Number) locator.page().evaluate("() => window.scrollY || window.pageYOffset || 0");
        return new Point(
                (int) (boundingBox.x + scrollX.doubleValue()),
                (int) (boundingBox.y + scrollY.doubleValue()));
    }

    @Override
    public Dimension getSize() {
        BoundingBox boundingBox = locator.boundingBox();
        return new Dimension((int) boundingBox.width, (int) boundingBox.height);
    }

    @Override
    public Rectangle getRect() {
        return new Rectangle(getLocation(), getSize());
    }

    @Override
    public String getCssValue(String propertyName) {
        return locator
                .evaluate("element => " +
                        "window.getComputedStyle(element).getPropertyValue('%s')"
                                .formatted(propertyName))
                .toString();

    }

    @Override
    public <X> X getScreenshotAs(OutputType<X> target) throws WebDriverException {
        byte[] screenshot = locator.screenshot();
        return target.convertFromPngBytes(screenshot);
    }

    public ElementHandle getElementHandle() {
        Page page = locator.page();

        try {
            Field selector = locator.getClass().getDeclaredField("selector");
            selector.setAccessible(true);
            String selectorString = selector.get(locator).toString();
            return page.querySelector(selectorString);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public String getDomAttribute(String name) {
        return GetAttributeAdapter.getDomAttribute(locator, name);
    }

    @Nullable
    @Override
    public String getDomProperty(String name) {
        return GetAttributeAdapter.getDomProperty(locator, name);
    }

}
