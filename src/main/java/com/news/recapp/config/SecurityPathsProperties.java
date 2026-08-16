package com.news.recapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 認可設定で許可する経路一覧を保持
 */
@ConfigurationProperties(prefix = "app.security")
public class SecurityPathsProperties {

    /**
     * 許可する経路一覧が空だと認可設定が不足するため、
     * ログイン画面や静的資材など最小限の経路を既定値として設定する。
     * 設定で上書きされた場合は、その内容を優先する。
     */
    private List<String> permitAll = new ArrayList<>(
            List.of(
                    "/login",
                    "/logout",
                    "/error",
                    "/timeout",
                    "/actuator/health/**",
                    "/favicon.ico",
                    "/favicon_news_512.png",
                    "/css/**",
                    "/js/**",
                    "/img/**"
            )
    );

    /**
     * 許可する経路一覧を取得
     */
    public List<String> getPermitAll() {
        // 許可する経路一覧を返却
        return permitAll;
    }

    /**
     * 許可する経路一覧を設定
     */
    public void setPermitAll(List<String> permitAll) {
        // 許可する経路一覧を保持
        this.permitAll = permitAll;
    }
}
