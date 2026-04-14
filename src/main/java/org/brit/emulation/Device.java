package org.brit.emulation;

import com.microsoft.playwright.options.ViewportSize;

import java.util.Objects;

/**
 * Created by Serhii Bryt
 * 29.03.2024 13:50
 **/
public class Device {
    private String userAgent;
    private ViewportSize viewport;
    private double deviceScaleFactor;
    private boolean isMobile;
    private boolean hasTouch;
    private String defaultBrowserType;

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public ViewportSize getViewport() { return viewport; }
    public void setViewport(ViewportSize viewport) { this.viewport = viewport; }

    public double getDeviceScaleFactor() { return deviceScaleFactor; }
    public void setDeviceScaleFactor(double deviceScaleFactor) { this.deviceScaleFactor = deviceScaleFactor; }

    public boolean isMobile() { return isMobile; }
    public void setMobile(boolean isMobile) { this.isMobile = isMobile; }

    public boolean isHasTouch() { return hasTouch; }
    public void setHasTouch(boolean hasTouch) { this.hasTouch = hasTouch; }

    public String getDefaultBrowserType() { return defaultBrowserType; }
    public void setDefaultBrowserType(String defaultBrowserType) { this.defaultBrowserType = defaultBrowserType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Device device = (Device) o;
        return Double.compare(device.deviceScaleFactor, deviceScaleFactor) == 0
                && isMobile == device.isMobile && hasTouch == device.hasTouch
                && Objects.equals(userAgent, device.userAgent)
                && Objects.equals(viewport, device.viewport)
                && Objects.equals(defaultBrowserType, device.defaultBrowserType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userAgent, viewport, deviceScaleFactor, isMobile, hasTouch, defaultBrowserType);
    }

    @Override
    public String toString() {
        return "Device(userAgent=" + userAgent + ", viewport=" + viewport
                + ", deviceScaleFactor=" + deviceScaleFactor + ", isMobile=" + isMobile
                + ", hasTouch=" + hasTouch + ", defaultBrowserType=" + defaultBrowserType + ")";
    }
}
