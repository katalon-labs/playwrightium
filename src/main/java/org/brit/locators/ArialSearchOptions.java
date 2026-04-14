package org.brit.locators;

import java.util.regex.Pattern;

/**
 * @author Serhii Bryt
 *
 * This is options to use with searching with Playwright getByRole method
 * @see <a href='https://playwright.dev/java/docs/locators#locate-by-role'>Playwright. GetByRole</a>
 */
public class ArialSearchOptions {
    public Boolean checked;
    public Boolean disabled;
    public Boolean exact;
    public Boolean expanded;
    public Boolean includeHidden;
    public Integer level;
    public Object name;
    public Boolean pressed;
    public Boolean selected;

    public Boolean getChecked() { return checked; }
    public Boolean getDisabled() { return disabled; }
    public Boolean getExact() { return exact; }
    public Boolean getExpanded() { return expanded; }
    public Boolean getIncludeHidden() { return includeHidden; }
    public Integer getLevel() { return level; }
    public Object getName() { return name; }
    public Boolean getPressed() { return pressed; }
    public Boolean getSelected() { return selected; }

    public ArialSearchOptions setChecked(Boolean checked) { this.checked = checked; return this; }
    public ArialSearchOptions setDisabled(Boolean disabled) { this.disabled = disabled; return this; }
    public ArialSearchOptions setExact(Boolean exact) { this.exact = exact; return this; }
    public ArialSearchOptions setExpanded(Boolean expanded) { this.expanded = expanded; return this; }
    public ArialSearchOptions setIncludeHidden(Boolean includeHidden) { this.includeHidden = includeHidden; return this; }
    public ArialSearchOptions setLevel(Integer level) { this.level = level; return this; }
    public ArialSearchOptions setPressed(Boolean pressed) { this.pressed = pressed; return this; }
    public ArialSearchOptions setSelected(Boolean selected) { this.selected = selected; return this; }

    public ArialSearchOptions setName(String name) {
        this.name = name;
        return this;
    }

    public ArialSearchOptions setName(Pattern name) {
        this.name = name;
        return this;
    }

}
