package com.news.recapp.data.redis.adapter;

import com.news.recapp.application.port.out.FavoriteStorePort;
import com.news.recapp.config.FavoritesProperties;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * お気に入り情報を Redis に保存するアダプタ
 */
@Component
public class RedisFavoriteStoreAdapter implements FavoriteStorePort {

    // Redis クライアントを保持
    private final ReactiveStringRedisTemplate redis;
    // お気に入り最大件数などの設定を保持
    private final FavoritesProperties favProps;

    // コンストラクタ
    public RedisFavoriteStoreAdapter(
            ReactiveStringRedisTemplate redis,
            FavoritesProperties favProps
    ) {
        this.redis = redis;
        this.favProps = favProps;
    }

    /**
     * お気に入り ZSET のキーを生成
     */
    private String key(String userId) {
        // ユーザー別お気に入りキーを返却
        return "favz:" + userId;
    }

    /**
     * 指定ニュースがお気に入りか判定
     */
    @Override
    public Mono<Boolean> isFavorite(String userId, UUID newsId) {
        // ZSET にメンバーが存在するかをスコアで判定して返却
        return redis.opsForZSet()
            // メンバーのスコアを取得
            .score(key(userId), newsId.toString())
            // スコアが取得できた場合は true を返却
            .map(score -> true)
            // 未登録の場合は false を返却
            .defaultIfEmpty(false);
    }

    /**
     * お気に入りニュース ID 一覧を取得
     */
    @Override
    public Flux<UUID> listFavoriteNewsIds(String userId) {
        // 文字列のニュース ID を UUID に変換して返却
        return redis.opsForZSet()
            // ZSET の全メンバーを取得
            .range(key(userId), Range.closed(0L, Long.MAX_VALUE))
            // 文字列を UUID に変換
            .flatMap(newsIdString -> {
                try {
                    // UUID へ変換して返却
                    return Mono.just(UUID.fromString(newsIdString));
                } catch (Exception exception) {
                    // 変換できない値は除外
                    return Mono.empty();
                }
            });
    }

    /**
     * お気に入りの追加/削除を切り替え
     */
    @Override
    public Mono<ToggleResult> toggle(String userId, UUID newsId) {

        // ユーザー別お気に入りキーを生成
        final String storeKey = key(userId);
        // ニュース ID をメンバー文字列へ変換
        final String member = newsId.toString();

        // 既に登録済みかどうかをスコアで判定して切り替え
        return redis.opsForZSet()
            // 既存スコアを取得
            .score(storeKey, member)
            // 既に登録済みの場合は削除処理を実行
            .flatMap(score -> redis.opsForZSet()
                // メンバーを削除
                .remove(storeKey, member)
                // 削除後の件数を取得
                .then(redis.opsForZSet().size(storeKey))
                // 未作成の場合は 0 を返却
                .defaultIfEmpty(0L)
                // 削除結果を ToggleResult に変換
                .map(cnt -> new ToggleResult(false, cnt.intValue(), false))
            )
            // 未登録の場合は最大件数を確認して追加処理を実行
            .switchIfEmpty(Mono.defer(() -> {

                // お気に入り最大件数を取得
                int max = favProps.getMaxFav();

                // 現在のお気に入り件数を取得
                return redis.opsForZSet()
                    // ZSET サイズを取得
                    .size(storeKey)
                    // 未作成の場合は 0 を返却
                    .defaultIfEmpty(0L)
                    // 最大件数を超える場合は追加せず警告フラグを返却
                    .flatMap(cnt -> {

                        // 最大件数に達している場合
                        if (cnt >= max) {
                            // 追加せず上限到達フラグを返却
                            return Mono.just(new ToggleResult(false, cnt.intValue(), true));
                        }

                        // ZSET スコアとして現在時刻を使用
                        double score = (double) System.currentTimeMillis();

                        // メンバーを追加して件数を返却
                        return redis.opsForZSet()
                            // メンバーを追加
                            .add(storeKey, member, score)
                            // 追加後の件数を取得
                            .then(redis.opsForZSet().size(storeKey))
                            // 件数が取得できない場合は 1 件増として補完
                            .defaultIfEmpty(cnt + 1)
                            // 追加結果を ToggleResult に変換
                            .map(newCnt -> new ToggleResult(true, newCnt.intValue(), false));
                    });
            }));
    }
}
