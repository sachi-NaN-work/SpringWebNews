package com.news.recapp.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Redis 接続の必須設定を保持
 */
@Validated
@ConfigurationProperties(prefix = "spring.data.redis")
public class RequiredRedisProperties {

    // Redis 接続ホスト
    @NotBlank(message = "{config.redis.host.required}")
    private String host;

    // Redis 接続ポート
    @Min(value = 1, message = "{config.redis.port.required}")
    private int port;

    // Redis 接続パスワード
    @NotBlank(message = "{config.redis.password.required}")
    private String password;

    /**
     * Redis 接続ホストを取得
     */
    public String getHost() {
        // Redis 接続ホストを返却
        return host;
    }

    /**
     * Redis 接続ホストを設定
     */
    public void setHost(String host) {
        // Redis 接続ホストを保持
        this.host = host;
    }

    /**
     * Redis 接続ポートを取得
     */
    public int getPort() {
        // Redis 接続ポートを返却
        return port;
    }

    /**
     * Redis 接続ポートを設定
     */
    public void setPort(int port) {
        // Redis 接続ポートを保持
        this.port = port;
    }

    /**
     * Redis 接続パスワードを取得
     */
    public String getPassword() {
        // Redis 接続パスワードを返却
        return password;
    }

    /**
     * Redis 接続パスワードを設定
     */
    public void setPassword(String password) {
        // Redis 接続パスワードを保持
        this.password = password;
    }
}
