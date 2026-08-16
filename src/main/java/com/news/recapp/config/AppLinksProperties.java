package com.news.recapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 画面に表示する外部リンク設定を保持
 */
@Component
@ConfigurationProperties(prefix = "app.links")
public class AppLinksProperties {

    // リンク先 URL（ログイン画面などに表示）
    private String linkUrl;

    // 表示コメント（ログイン画面などに表示）
    private String textComment;

    /**
     * リンク先 URL を取得
     */
    public String getLinkUrl() {
        // リンク先 URL を返却
        return linkUrl;
    }

    /**
     * リンク先 URL を設定
     */
    public void setLinkUrl(String linkUrl) {
        // リンク先 URL を保持
        this.linkUrl = linkUrl;
    }

    /**
     * 表示コメントを取得
     */
    public String getTextComment() {
        // 表示コメントを返却
        return textComment;
    }

    /**
     * 表示コメントを設定
     */
    public void setTextComment(String textComment) {
        // 表示コメントを保持
        this.textComment = textComment;
    }
}
