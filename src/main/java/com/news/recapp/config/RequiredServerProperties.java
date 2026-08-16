package com.news.recapp.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * サーバの必須設定を保持
 */
@Validated
@ConfigurationProperties(prefix = "server")
public class RequiredServerProperties {

    /**
     * 待受ポートは未設定だと 0 になるため、
     * 起動時の検証で止まらないように初期値を設定する。
     * 設定で指定された場合は、その内容を優先する。
     */
    @Min(value = 1, message = "{config.server.port.required}")
    private int port = 8080;

    /**
     * サーバ待受ポートを取得
     */
    public int getPort() {
        // サーバ待受ポートを返却
        return port;
    }

    /**
     * サーバ待受ポートを設定
     */
    public void setPort(int port) {
        // サーバ待受ポートを保持
        this.port = port;
    }
}
