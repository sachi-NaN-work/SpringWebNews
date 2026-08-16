package com.news.recapp.presentation.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ログインロックアウト機能の設定値を保持
 */
@ConfigurationProperties(prefix = "security.lockout")
public class LockoutProperties {

    // 最大試行回数を保持
    private int maxAttempts = 3;

    // ロックアウト時間を保持
    private Duration duration = Duration.ofMinutes(15);

    /**
     * 最大試行回数を取得
     */
    public int getMaxAttempts() {
        // 最大試行回数を返却
        return maxAttempts;
    }

    /**
     * 最大試行回数を設定
     */
    public void setMaxAttempts(int maxAttempts) {
        // 最大試行回数を設定
        this.maxAttempts = maxAttempts;
    }

    /**
     * ロックアウト時間を取得
     */
    public Duration getDuration() {
        // ロックアウト時間を返却
        return duration;
    }

    /**
     * ロックアウト時間を設定
     */
    public void setDuration(Duration duration) {
        // ロックアウト時間を設定
        this.duration = duration;
    }

}
