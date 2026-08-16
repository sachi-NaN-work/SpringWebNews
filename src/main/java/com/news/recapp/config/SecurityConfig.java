package com.news.recapp.config;

import com.news.recapp.application.port.out.LoginLockoutPort;
import com.news.recapp.presentation.security.LockoutProperties;
import com.news.recapp.presentation.security.LockoutReactiveAuthManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;

import java.net.URI;

/**
 * 認証・認可の WebFlux セキュリティ設定を定義
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@EnableConfigurationProperties({LockoutProperties.class, SecurityPathsProperties.class})
public class SecurityConfig {

    /**
     * パスワードエンコーダを生成
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // DelegatingPasswordEncoder を生成して返却
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * ロックアウト判定付きの認証マネージャを生成
     */
    @Bean
    public ReactiveAuthenticationManager reactiveAuthenticationManager(
        ReactiveUserDetailsService uds,
        PasswordEncoder encoder,
        LoginLockoutPort lockoutPort
    ) {

        // ユーザー詳細サービスを利用する認証マネージャを生成
        var base = new UserDetailsRepositoryReactiveAuthenticationManager(uds);
        // パスワードエンコーダを設定
        base.setPasswordEncoder(encoder);

        // ロックアウト判定付き認証マネージャを生成して返却
        return new LockoutReactiveAuthManager(base, lockoutPort);
    }

    /**
     * 認証成功時のリダイレクト処理を定義
     */
    @Bean
    public ServerAuthenticationSuccessHandler authenticationSuccessHandler() {

        // 認証成功ハンドラを返却
        return (exchange, authentication) -> {

            // レスポンスを取得
            var res = exchange.getExchange().getResponse();
            // リダイレクトステータスを設定
            res.setStatusCode(HttpStatus.FOUND);
            // Location ヘッダに遷移先 URI を設定
            res.getHeaders().setLocation(URI.create("/"));

            // レスポンス完了を返却
            return res.setComplete();
        };
    }

    /**
     * 認証失敗時のリダイレクト処理を定義
     */
    @Bean
    public ServerAuthenticationFailureHandler authenticationFailureHandler() {

        // 認証失敗ハンドラを返却
        return (exchange, ex) -> {

            // 遷移先 URL を初期化
            String location;
            // ロックアウト例外か判定
            boolean isLockedException = ex instanceof LockedException;

            // ロックアウト例外の場合
            if (isLockedException) {
                // ロックアウト表示へ遷移
                location = "/login?locked";
            } else {
                // エラー表示へ遷移
                location = "/login?error";
            }

            // レスポンスを取得
            var res = exchange.getExchange().getResponse();
            // リダイレクトステータスを設定
            res.setStatusCode(HttpStatus.FOUND);
            // Location ヘッダに遷移先 URI を設定
            res.getHeaders().setLocation(URI.create(location));

            // レスポンス完了を返却
            return res.setComplete();
        };
    }

    /**
     * ログアウト成功時のリダイレクト処理を定義
     */
    @Bean
    public ServerLogoutSuccessHandler logoutSuccessHandler() {

        // リダイレクト型ログアウト成功ハンドラを生成
        RedirectServerLogoutSuccessHandler redirectLogoutSuccessHandler = new RedirectServerLogoutSuccessHandler();
        // ログアウト成功時の遷移先 URI を設定
        redirectLogoutSuccessHandler.setLogoutSuccessUrl(URI.create("/login?logout"));

        // ログアウト成功ハンドラを返却
        return redirectLogoutSuccessHandler;
    }

    /**
     * WebFlux セキュリティフィルタチェーンを定義
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
        ServerHttpSecurity http,
        ReactiveAuthenticationManager authManager,
        ServerAuthenticationSuccessHandler successHandler,
        ServerAuthenticationFailureHandler failureHandler,
        ServerLogoutSuccessHandler logoutSuccessHandler,
        SecurityPathsProperties paths
    ) {

        // 認可不要の経路一覧を取得
        var permitAll = paths.getPermitAll();

        // セキュリティフィルタチェーンを生成して返却
        return http
                // CSRF を無効化
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 認証マネージャを設定
                .authenticationManager(authManager)
                // 認可ルールを設定
                .authorizeExchange(ex -> {

                    // 認可不要の経路一覧が空でない場合
                    if (permitAll != null && !permitAll.isEmpty()) {
                        // 認可不要の経路を permitAll として許可
                        ex.pathMatchers(permitAll.toArray(String[]::new)).permitAll();
                    }

                    // 管理画面は認証済みユーザーのみ許可し、機能別の権限判定は各コントローラで行う
                    ex.pathMatchers("/mypage/admin/**").authenticated();
                    // それ以外の経路を認証必須に設定
                    ex.anyExchange().authenticated();
                })
                // フォームログイン設定を適用
                .formLogin(form -> form
                        // ログインページを設定
                        .loginPage("/login")
                        // 認証成功ハンドラを設定
                        .authenticationSuccessHandler(successHandler)
                        // 認証失敗ハンドラを設定
                        .authenticationFailureHandler(failureHandler)
                )
                // ログアウト設定を適用
                .logout(logout -> logout
                        // ログアウト URL を設定
                        .logoutUrl("/logout")
                        // ログアウト成功ハンドラを設定
                        .logoutSuccessHandler(logoutSuccessHandler)
                )
                // 設定内容からフィルタチェーンを生成
                .build();
    }
}
