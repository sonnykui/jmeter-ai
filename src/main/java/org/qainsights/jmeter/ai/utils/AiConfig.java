package org.qainsights.jmeter.ai.utils;

import org.apache.jmeter.util.JMeterUtils;

public class AiConfig {
    public static String getProperty(String key, String defaultValue) {
        // First try JMeter properties, then fall back to system properties for testing
        String value = JMeterUtils.getPropDefault(key, null);
        if (value == null || value.isEmpty()) {
            value = System.getProperty(key, defaultValue);
        }
        return value != null ? value : defaultValue;
    }
}
