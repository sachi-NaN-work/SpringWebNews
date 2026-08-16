package com.news.recapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ニュース取得の設定を保持
 */
@Component
@ConfigurationProperties(prefix = "news")
public class NewsProperties {

    // 最新記事の最大件数
    private int latestLimit = 10;

    /**
     * 最新記事の最大件数を取得
     */
    public int getLatestLimit() {
        // 最新記事の最大件数を返却
        return latestLimit;
    }

    /**
     * 最新記事の最大件数を設定
     */
    public void setLatestLimit(int latestLimit) {

        // 設定値が 0 より大きい場合
        if (latestLimit > 0) {
            // 最新記事の最大件数を更新
            this.latestLimit = latestLimit;
        }
    }
}
