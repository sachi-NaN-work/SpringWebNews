package com.news.recapp.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * R2DBC 接続の必須設定を保持
 */
@Validated
@ConfigurationProperties(prefix = "spring.r2dbc")
public class RequiredR2dbcProperties {

    // R2DBC 接続 URL
    @NotBlank(message = "{config.r2dbc.url.required}")
    private String url;

    // R2DBC 接続ユーザー名
    @NotBlank(message = "{config.r2dbc.username.required}")
    private String username;

    // R2DBC 接続パスワード
    @NotBlank(message = "{config.r2dbc.password.required}")
    private String password;

    /**
     * R2DBC 接続 URL を取得
     */
    public String getUrl() {
        // R2DBC 接続 URL を返却
        return url;
    }

    /**
     * R2DBC 接続 URL を設定
     */
    public void setUrl(String url) {
        // R2DBC 接続 URL を保持
        this.url = url;
    }

    /**
     * R2DBC 接続ユーザー名を取得
     */
    public String getUsername() {
        // R2DBC 接続ユーザー名を返却
        return username;
    }

    /**
     * R2DBC 接続ユーザー名を設定
     */
    public void setUsername(String username) {
        // R2DBC 接続ユーザー名を保持
        this.username = username;
    }

    /**
     * R2DBC 接続パスワードを取得
     */
    public String getPassword() {
        // R2DBC 接続パスワードを返却
        return password;
    }

    /**
     * R2DBC 接続パスワードを設定
     */
    public void setPassword(String password) {
        // R2DBC 接続パスワードを保持
        this.password = password;
    }
}
