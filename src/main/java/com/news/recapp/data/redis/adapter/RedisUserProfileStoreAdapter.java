package com.news.recapp.data.redis.adapter;

import com.news.recapp.application.port.out.UserProfileStorePort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * ユーザープロファイル情報を Redis に保存するアダプタ
 */
@Component
public class RedisUserProfileStoreAdapter implements UserProfileStorePort {

    // Redis クライアントを保持
    private final ReactiveStringRedisTemplate redis;

    // コンストラクタ
    public RedisUserProfileStoreAdapter(
            ReactiveStringRedisTemplate redis
    ) {
        this.redis = redis;
    }

    /**
     * ユーザー別プロファイルのハッシュキーを生成
     */
    private String key(String userId) {
        // ユーザー別プロファイルキーを返却
        return "uprofile:" + userId;
    }

    /**
     * プロファイルを取得
     */
    @Override
    public Mono<Map<Object, Object>> getProfile(String userId) {
        // プロファイルの全エントリをマップとして返却
        return redis.opsForHash()
            // ハッシュエントリを取得
            .entries(key(userId))
            // エントリをマップへ収集
            .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    /**
     * カテゴリ別カウントを加算（0 未満は 0 に補正）
     */
    @Override
    public Mono<Long> incrementCategory(String userId, UUID categoryId, long delta) {

        // ハッシュフィールド名を生成
        String field = "category:" + categoryId;

        // カテゴリカウントを加算して 0 未満を補正して返却
        return redis.opsForHash()
            // ハッシュ値を加算
            .increment(key(userId), field, delta)
            // 加算結果を long に変換
            .map(Number::longValue)
            // 0 未満の場合は 0 を書き戻し
            .flatMap(newValue -> {

                // 加算結果が 0 以上の場合
                if (newValue >= 0) {
                    // 加算結果を返却
                    return Mono.just(newValue);
                }

                // 0 未満の場合は 0 を書き戻して返却
                return redis.opsForHash()
                    // 0 を書き戻し
                    .put(key(userId), field, "0")
                    // 書き戻し完了後に 0 を返却
                    .thenReturn(0L);
            });
    }
}
