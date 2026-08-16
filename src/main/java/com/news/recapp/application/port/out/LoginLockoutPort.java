package com.news.recapp.application.port.out;

import reactor.core.publisher.Mono;

/**
 * ロックアウトのポート
 */
public interface LoginLockoutPort {

    /**
     * ログインロック状態を判定
     */
    Mono<Boolean> isLocked(String username);

    /**
     * 認証成功を記録
     */
    Mono<Void> onAuthSuccess(String username);

    /**
     * 認証失敗を記録
     */
    Mono<Void> onBadCredentials(String username);
}
