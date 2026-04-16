package org.brit.driver.adapters;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.JSHandle;
import com.microsoft.playwright.Page;
import org.brit.element.converters.ElementHandleConverter;
import org.brit.element.PlaywrightWebElement;
import org.jspecify.annotations.Nullable;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WrapsElement;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public class JsExecutionAdapter {
  private static final ElementHandleConverter converter = new ElementHandleConverter();

  /**
   * Executes a JavaScript script on a Playwright page.
   *
   * @param page   The Playwright page object where the script will be executed.
   * @param script The JavaScript code to be executed.
   * @param args   The arguments to pass to the script.
   * @return The result of executing the script.
   */
  public Object executeScript(Page page, String script, Object... args) {
    var arguments = args.length > 0 ? transformArguments(args) : List.of();
    // Wrap as block body so multi-statement scripts (var/let/const, loops, etc.)
    // are legal. Selenium users write `return X;` to return a value — that works
    // inside a block. An arrow *expression* body would reject anything but a
    // single expression.
    String wrapped = "(arguments) => {\n" + script + "\n}";
    JSHandle jsHandle = page.evaluateHandle(wrapped, arguments);
    String type = jsHandle.evaluate(
        """
            (node) => {
                if (node instanceof HTMLCollection) return 'HTMLCollection';
                if (node instanceof HTMLElement) return 'HTMLElement';
                if (Array.isArray(node)) return 'Array';
                return 'Other';
            }
            """).toString();

    return switch (type) {
      case "HTMLCollection" -> processHtmlCollection(page, jsHandle);
      case "HTMLElement" -> converter.toPwElement(page, jsHandle.asElement());
      case "Array" -> processArray(page, jsHandle);
      default -> normalizeReturnValue(jsHandle.jsonValue());
    };
  }

  /**
   * Normalize return values to match Selenium's conventions.
   * Selenium's executeScript always returns numeric values as Long, not Integer.
   */
  private Object normalizeReturnValue(Object value) {
    if (value instanceof Integer) {
      return ((Integer) value).longValue();
    }
    return value;
  }

  /**
   * Executes an asynchronous JavaScript script on a Playwright page.
   *
   * @param page   The Playwright page object where the script will be executed.
   * @param script The JavaScript code to be executed.
   * @param args   The arguments to pass to the script.
   * @return A promise of the result of executing the asynchronous script.
   */
  public Object executeAsyncScript(Page page, String script, Object... args) {
    // Skip Katalon SmartWait scripts — they depend on window.katalonWaiter
    // which is injected by a Chrome extension that doesn't run under Playwright.
    // Playwright's built-in auto-wait handles what SmartWait was designed for.
    if (script != null && script.contains("window.katalonWaiter")) {
      return null;
    }

    // Selenium's executeAsyncScript convention: the last argument passed to the
    // JS script is a callback that the script must invoke with the result.
    // Playwright has no such callback mechanism, so we simulate it: wrap the
    // script in a Promise, and inject a resolve function as the last element of
    // the `arguments` array.
    String modifiedScript = removeReturnKeyword(script);
    String wrappedScript = """
        async (arguments) => {
          return await new Promise((resolve, reject) => {
            arguments.push(resolve);
            try {
              (function() {
                %s
              }).apply(null, arguments);
            } catch (e) {
              reject(e);
            }
          });
        }
        """.formatted(modifiedScript);
    var transformedArgs = args.length > 0 ? transformArguments(args) : List.of();
    return normalizeReturnValue(page.evaluate(wrappedScript, transformedArgs));
  }

  private String removeReturnKeyword(String script) {
    return script.replaceFirst("^return", "").trim();
  }

  private List<PlaywrightWebElement> processHtmlCollection(Page page, JSHandle jsHandle) {
    int length = (int) page.evaluate("node => node.length", jsHandle);
    return IntStream.range(0, length)
        .mapToObj(i -> converter.toPwElement(page, page.evaluateHandle("node => node.item(%s)".formatted(i), jsHandle).asElement()))
        .toList();
  }

  private List<@Nullable String> processArray(Page page, JSHandle jsHandle) {
    int length = (int) page.evaluate("node => node.length", jsHandle);
    return IntStream.range(0, length)
        .mapToObj(i -> Optional.ofNullable(page.evaluate("node => node['%s']".formatted(i), jsHandle)).map(Object::toString).orElse(null))
        .toList();
  }

  private List<Object> transformArguments(Object... args) {
    return Arrays.stream(args)
        .map(this::transformArgument)
        .toList();
  }

  private Object transformArgument(Object arg) {
    if (arg instanceof WrapsElement) {
      return getElementHandleFrom((WrapsElement) arg);
    } else if (arg instanceof WebElement) {
      return getElementHandleFrom((WebElement) arg);
    } else if (arg instanceof Collection) {
      return transformCollection((Collection<?>) arg);
    } else if (arg instanceof Long) {
      // Playwright's Serialization only supports Integer/Double, not Long
      return ((Long) arg).doubleValue();
    } else if (arg instanceof Short || arg instanceof Byte) {
      return ((Number) arg).intValue();
    } else if (arg instanceof Float) {
      return ((Float) arg).doubleValue();
    }
    return arg;
  }

  private Object transformCollection(Collection<?> collection) {
    if (collection.isEmpty()) return List.of();

    if (collection.iterator().next() instanceof WebElement) {
      return collection.stream()
          .map(e -> ((PlaywrightWebElement) e).getLocator().elementHandle())
          .toList();
    }
    return List.copyOf(collection);
  }

  private ElementHandle getElementHandleFrom(WrapsElement wrapsElement) {
    return ((PlaywrightWebElement) wrapsElement.getWrappedElement()).getElementHandle();
  }

  private ElementHandle getElementHandleFrom(WebElement webElement) {
    return ((PlaywrightWebElement) webElement).getLocator().elementHandle();
  }
}
