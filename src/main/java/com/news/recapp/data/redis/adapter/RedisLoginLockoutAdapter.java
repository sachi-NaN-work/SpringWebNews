package com.news.recapp.data.redis.adapter;

import com.news.recapp.application.port.out.LoginLockoutPort;
import com.news.recapp.presentation.security.LockoutProperties;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * ログインロックアウト情報を Redis に保存するアダプタ
 */
@Component
public class RedisLoginLockoutAdapter implements LoginLockoutPort {

    // Redis クライアントを保持
    private final ReactiveStringRedisTemplate redis;
    // ロックアウト判定の最大失敗回数を保持
    private final int maxAttempts;
    // 失敗回数/ロックの TTL を保持
    private final Duration duration;

    // コンストラクタ
    public RedisLoginLockoutAdapter(
            ReactiveStringRedisTemplate redis,
            LockoutProperties props
    ) {
        this.redis = redis;
        this.maxAttempts = props.getMaxAttempts();
        this.duration = props.getDuration();
    }

    /**
     * 失敗回数カウンタのキーを生成
     */
    private String failKey(String username) {
        // 失敗回数キーを返却
        return "login:fail:" + username;
    }

    /**
     * ロックアウト状態のキーを生成
     */
    private String lockKey(String username) {
        // ロックキーを返却
        return "login:lock:" + username;
    }

    /**
     * ロックアウト中か判定
     */
    @Override
    public Mono<Boolean> isLocked(String username) {
        // ロックキーの存在有無を返却
        return redis.hasKey(lockKey(username))
            // 実装差で空になる場合は false を返却
            .defaultIfEmpty(false);
    }

    /**
     * 認証成功をロックアウト機構へ記録（カウンタ/ロックを削除）
     */
    @Override
    public Mono<Void> onAuthSuccess(String username) {
        // 失敗回数とロック状態を削除して完了を返却
        return redis.delete(failKey(username))
            // 失敗回数削除後にロックキーを削除
            .then(redis.delete(lockKey(username)))
            // 削除完了を Void に変換
            .then();
    }

    /**
     * 認証失敗をロックアウト機構へ記録（カウント増加と閾値判定）
     */
    @Override
    public Mono<Void> onBadCredentials(String username) {

        // 失敗回数キーを生成
        String fk = failKey(username);
        // ロックキーを生成
        String lk = lockKey(username);

        // 失敗回数を増加して TTL とロック状態を更新
        return redis.opsForValue()
            // 失敗回数をインクリメント
            .increment(fk)
            // インクリメント後の失敗回数で分岐
            .flatMap(cnt -> {

                // 初回失敗時にのみ TTL を付与する処理を用意
                Mono<Boolean> ensureTtl;

                // 失敗回数が 1 の場合
                if (cnt == 1) {
                    // 失敗回数キーへ TTL を設定
                    ensureTtl = redis.expire(fk, duration);
                // 初回以外の場合
                } else {
                    // TTL 設定は不要として true を返却
                    ensureTtl = Mono.just(true);
                }

                // 失敗回数が閾値以上の場合
                if (cnt >= maxAttempts) {
                    // TTL 設定後にロックキーをセットして完了を返却
                    return ensureTtl
                        // TTL 設定完了後にロックキーをセット
                        .then(redis.opsForValue().set(lk, "1", duration))
                        // 完了を Void に変換
                        .then();
                }

                // 閾値未満の場合は TTL 設定のみ行って完了を返却
                return ensureTtl
                    // 完了を Void に変換
                    .then();
            });
    }
}
