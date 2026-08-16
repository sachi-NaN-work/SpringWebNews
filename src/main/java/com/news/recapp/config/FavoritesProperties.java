package com.news.recapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * お気に入りの設定を保持
 */
@Component
@ConfigurationProperties(prefix = "rec.favorites")
public class FavoritesProperties {

    // お気に入りの最大件数
    private int maxFav = 10;

    /**
     * お気に入りの最大件数を取得
     */
    public int getMaxFav() {
        // お気に入りの最大件数を返却
        return maxFav;
    }

    /**
     * お気に入りの最大件数を設定
     */
    public void setMaxFav(int maxFav) {

        // 設定値が 0 より大きい場合
        if (maxFav > 0) {
            // お気に入りの最大件数を更新
            this.maxFav = maxFav;
        }
    }
}
