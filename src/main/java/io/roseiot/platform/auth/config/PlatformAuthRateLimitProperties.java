package io.roseiot.platform.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "roseiot.auth.rate-limit")
public class PlatformAuthRateLimitProperties {

    private boolean enabled = true;
    private int windowMinutes = 15;
    private int loginMaxAttempts = 10;
    private int recoveryMaxAttempts = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public void setWindowMinutes(int windowMinutes) {
        this.windowMinutes = windowMinutes;
    }

    public int getLoginMaxAttempts() {
        return loginMaxAttempts;
    }

    public void setLoginMaxAttempts(int loginMaxAttempts) {
        this.loginMaxAttempts = loginMaxAttempts;
    }

    public int getRecoveryMaxAttempts() {
        return recoveryMaxAttempts;
    }

    public void setRecoveryMaxAttempts(int recoveryMaxAttempts) {
        this.recoveryMaxAttempts = recoveryMaxAttempts;
    }
}
