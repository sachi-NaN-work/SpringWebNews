package com.news.recapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 無操作タイムアウト設定を保持
 */
@Component
@ConfigurationProperties(prefix = "app.security.idle-timeout")
public class IdleTimeoutProperties {

    // 無操作タイムアウトまでの時間（規定値）を保持
    private Duration duration = Duration.ofMinutes(5);

    /**
     * 無操作タイムアウトまでの時間を取得
     */
    public Duration getDuration() {
        // 無操作タイムアウトまでの時間を返却
        return duration;
    }

    /**
     * 無操作タイムアウトまでの時間を設定
     */
    public void setDuration(Duration duration) {
        // 無操作タイムアウトまでの時間を保持
        this.duration = duration;
    }

    /**
     * 無操作タイムアウトまでの時間をミリ秒で取得
     */
    public long getDurationMillis() {
        // 無操作タイムアウトまでの時間をミリ秒で返却
        return duration.toMillis();
    }

    /**
     * 無操作タイムアウトまでの時間を表示用文字列で取得
     */
    public String getDurationText() {

        // 無操作タイムアウトまでの時間を秒で取得
        long seconds = duration.toSeconds();

        // 時間単位で割り切れる場合
        if (seconds % 3600 == 0) {
            // 時間単位の表示用文字列を返却
            return (seconds / 3600) + "時間";
        }

        // 分単位で割り切れる場合
        if (seconds % 60 == 0) {
            // 分単位の表示用文字列を返却
            return (seconds / 60) + "分";
        }

        // 秒単位の表示用文字列を返却
        return seconds + "秒";
    }
}
