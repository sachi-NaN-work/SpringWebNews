package com.news.recapp.presentation.security;

import com.news.recapp.application.port.out.LoginLockoutPort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

/**
 * ロックアウト対応の認証処理を提供
 */
public class LockoutReactiveAuthManager implements ReactiveAuthenticationManager {

    // 認証処理の委譲先を保持
    private final ReactiveAuthenticationManager delegate;

    // ロックアウトポートを保持
    private final LoginLockoutPort lockout;

    // コンストラクタ
    public LockoutReactiveAuthManager(
        ReactiveAuthenticationManager delegate,
        LoginLockoutPort lockout
    ) {
        this.delegate = delegate;
        this.lockout = lockout;
    }

    /**
     * 認証を実行し、成功・失敗をロックアウト機構へ記録
     */
    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {

        // ユーザー名を取得
        String username = authentication.getName();

        // ロックアウト状態を取得
        Mono<Boolean> lockedMono = lockout.isLocked(username);

        // ロックアウト状態に応じて認証処理を分岐して返却
        return lockedMono.flatMap(locked -> {

            // ロックアウト中の場合
            if (locked) {
                // ロックアウト例外を返却
                return Mono.error(new LockedException("locked"));
            }

            // 実認証処理を委譲して取得
            Mono<Authentication> authenticatedMono = delegate.authenticate(authentication);

            // 認証成功時に成功記録を行い、認証結果を返却
            Mono<Authentication> onSuccess = authenticatedMono.flatMap(ok -> {
                // ログイン成功を記録
                Mono<Void> saved = lockout.onAuthSuccess(username);
                // 成功記録後に認証結果を返却
                return saved.thenReturn(ok);
            });

            // 認証失敗時に失敗記録を行い、例外を返却
            return onSuccess.onErrorResume(ex -> {

                // 例外が資格情報不一致の場合
                if (ex instanceof BadCredentialsException) {
                    // ログイン失敗を記録
                    Mono<Void> tracked = lockout.onBadCredentials(username);
                    // 失敗記録後に例外を返却
                    return tracked.then(Mono.error(ex));
                }

                // 想定外の例外を返却
                return Mono.error(ex);
            });
        });
    }
}
