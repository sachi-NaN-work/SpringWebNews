package com.news.recapp.data.redis.adapter;

import com.news.recapp.application.port.out.RecSignalStorePort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Redis にリアルタイム推薦用シグナルを保存するアダプタ。
 *
 * 取り扱うシグナル
 * - ユーザー×記事 view（生回数）
 * - ユーザー×カテゴリ/タグ view/fav/good/bad（生回数）
 * - ユーザー合計 view/fav/good/bad
 * - 全体×記事 view（11で飽和してバケット化に利用）
 * - 全体×カテゴリ/タグ fav/good/bad + 全体合計
 */
@Component
public class RedisRecSignalStoreAdapter implements RecSignalStorePort {

    // Redis クライアントを保持
    private final ReactiveStringRedisTemplate redis;

    // コンストラクタ
    public RedisRecSignalStoreAdapter(
            ReactiveStringRedisTemplate redis
    ) {
        this.redis = redis;
    }

    /**
     * ユーザー別ニュース閲覧数ハッシュキーを生成
     */
    private static String uNewsViewKey(String userId) {
        // ユーザー別ニュース閲覧数キーを返却
        return "u:news:view:" + userId;
    }

    /**
     * ユーザー別カテゴリシグナルハッシュキーを生成
     */
    private static String uCatKey(String userId, String metric) {
        // ユーザー別カテゴリキーを返却
        return "u:cat:" + metric + ":" + userId;
    }

    /**
     * ユーザー別タグシグナルハッシュキーを生成
     */
    private static String uTagKey(String userId, String metric) {
        // ユーザー別タグキーを返却
        return "u:tag:" + metric + ":" + userId;
    }

    /**
     * ユーザー別合計シグナルキーを生成
     */
    private static String uTotalKey(String userId, String metric) {
        // ユーザー別合計キーを返却
        return "u:total:" + metric + ":" + userId;
    }

    /**
     * 全体ニュース閲覧数ハッシュキーを生成（A/B グループ別）
     */
    private static String gNewsViewKey(String abGroup) {

        // A/B 名を正規化
        String normalizedAbGroup = abGroup == null ? "" : abGroup.trim().toUpperCase();

        // A/B が不正な場合
        if (!"A".equals(normalizedAbGroup) && !"B".equals(normalizedAbGroup)) {
            throw new IllegalArgumentException("abGroup must be A or B");
        }

        // A/B 別キーを返却
        return "g:news:view:" + normalizedAbGroup;
    }

    /**
     * 全体カテゴリシグナルハッシュキーを生成
     */
    private static String gCatKey(String metric) {
        // 全体カテゴリキーを返却
        return "g:cat:" + metric;
    }

    /**
     * 全体タグシグナルハッシュキーを生成
     */
    private static String gTagKey(String metric) {
        // 全体タグキーを返却
        return "g:tag:" + metric;
    }

    /**
     * 全体合計シグナルキーを生成
     */
    private static String gTotalKey(String metric) {
        // 全体合計キーを返却
        return "g:total:" + metric;
    }

    /**
     * Redis 取得値を long に安全変換
     */
    private static long safeLong(Object value) {

        // 値が未設定の場合
        if (value == null) {
            // 0 を返却
            return 0L;
        }

        try {
            // 文字列化して long に変換して返却
            return Long.parseLong(String.valueOf(value));
        } catch (Exception exception) {
            // 変換できない場合は 0 を返却
            return 0L;
        }
    }

    /**
     * Redis 取得値を UUID に安全変換
     */
    private static UUID safeUuid(Object value) {

        // 値が未設定の場合
        if (value == null) {
            // 変換不可として null を返却
            return null;
        }

        try {
            // 文字列化して UUID に変換して返却
            return UUID.fromString(String.valueOf(value));
        } catch (Exception exception) {
            // 変換できない場合は null を返却
            return null;
        }
    }

    /**
     * ハッシュの加算結果が 0 未満になった場合に 0 へ補正
     */
    private Mono<Long> clampHashNonNegative(String key, String field, long value) {

        // 加算結果が 0 以上の場合
        if (value >= 0) {
            // そのまま加算結果を返却
            return Mono.just(value);
        }

        // 0 未満の場合は 0 を書き戻して返却
        return redis.opsForHash()
                // 0 を書き戻し
                .put(key, field, "0")
                // 書き戻し完了後に 0 を返却
                .thenReturn(0L);
    }

    /**
     * 値の加算結果が 0 未満になった場合に 0 へ補正
     */
    private Mono<Long> clampValueNonNegative(String key, long value) {

        // 加算結果が 0 以上の場合
        if (value >= 0) {
            // そのまま加算結果を返却
            return Mono.just(value);
        }

        // 0 未満の場合は 0 を書き戻して返却
        return redis.opsForValue()
                // 0 を書き戻し
                .set(key, "0")
                // 書き戻し完了後に 0 を返却
                .thenReturn(0L);
    }

    /**
     * ユーザー×ニュースの閲覧数を加算
     */
    @Override
    public Mono<Long> incrUserNewsView(String userId, UUID newsId, long delta) {

        // ユーザーIDが未指定、またはニュースIDが未指定の場合
        if (userId == null || userId.isBlank() || newsId == null) {
            // 変化量なしとして 0 を返却
            return Mono.just(0L);
        }

        // ユーザー別ニュース閲覧数キーを生成
        String key = uNewsViewKey(userId);
        // ハッシュのフィールドにニュースIDを設定
        String field = newsId.toString();

        // 閲覧数を加算して 0 未満を 0 に補正して返却
        return redis.opsForHash()
                // ハッシュ値を加算
                .increment(key, field, delta)
                // 加算結果を long に変換
                .map(Number::longValue)
                // 0 未満を 0 に補正
                .flatMap(updatedValue -> clampHashNonNegative(key, field, updatedValue));
    }

    /**
     * ユーザー×ニュースの閲覧数一覧を取得
     */
    @Override
    public Mono<List<Long>> getUserNewsViews(String userId, List<UUID> newsIds) {

        // ユーザーID未指定、またはニュースID一覧が空の場合
        if (userId == null || userId.isBlank() || newsIds == null || newsIds.isEmpty()) {
            // 空の一覧を返却
            return Mono.just(Collections.emptyList());
        }

        // ユーザー別ニュース閲覧数キーを生成
        String key = uNewsViewKey(userId);
        // 取得対象のフィールド一覧を生成
        List<String> fields = newsIds.stream().map(UUID::toString).toList();

        // 複数フィールドを取得して long 一覧へ変換して返却
        return redis.opsForHash()
                // 複数フィールドを取得
                .multiGet(key, new ArrayList<>(fields))
                // 実装差で空になる場合は空リストを補完
                .defaultIfEmpty(Collections.emptyList())
                // 取得値を long リストへ変換
                .map(list -> {

                    // 出力リストを生成
                    List<Long> out = new ArrayList<>(list.size());

                    // 取得値を順に long へ変換
                    for (Object value : list) {
                        // 変換結果を追加
                        out.add(safeLong(value));
                    }

                    // 変換後リストを返却
                    return out;
                });
    }

    /**
     * ユーザー×カテゴリのシグナルを加算
     */
    @Override
    public Mono<Long> incrUserCategory(String userId, UUID categoryId, String metric, long delta) {

        // ユーザーID未指定、またはカテゴリID未指定の場合
        if (userId == null || userId.isBlank() || categoryId == null) {
            // 変化量なしとして 0 を返却
            return Mono.just(0L);
        }

        // ユーザー別カテゴリキーを生成
        String key = uCatKey(userId, metric);
        // ハッシュのフィールドにカテゴリIDを設定
        String field = categoryId.toString();

        // カウントを加算して 0 未満を 0 に補正して返却
        return redis.opsForHash()
                // ハッシュ値を加算
                .increment(key, field, delta)
                // 加算結果を long に変換
                .map(Number::longValue)
                // 0 未満を 0 に補正
                .flatMap(updatedValue -> clampHashNonNegative(key, field, updatedValue));
    }

    /**
     * ユーザー×タグのシグナルを加算
     */
    @Override
    public Mono<Long> incrUserTag(String userId, UUID tagId, String metric, long delta) {

        // ユーザーID未指定、またはタグID未指定の場合
        if (userId == null || userId.isBlank() || tagId == null) {
            // 変化量なしとして 0 を返却
            return Mono.just(0L);
        }

        // ユーザー別タグキーを生成
        String key = uTagKey(userId, metric);
        // ハッシュのフィールドにタグIDを設定
        String field = tagId.toString();

        // カウントを加算して 0 未満を 0 に補正して返却
        return redis.opsForHash()
                // ハッシュ値を加算
                .increment(key, field, delta)
                // 加算結果を long に変換
                .map(Number::longValue)
                // 0 未満を 0 に補正
                .flatMap(updatedValue -> clampHashNonNegative(key, field, updatedValue));
    }

    /**
     * ユーザー合計シグナルを加算
     */
    @Override
    public Mono<Long> incrUserTotal(String userId, String metric, long delta) {

        // ユーザーID未指定の場合
        if (userId == null || userId.isBlank()) {
            // 変化量なしとして 0 を返却
            return Mono.just(0L);
        }

        // ユーザー別合計キーを生成
        String key = uTotalKey(userId, metric);

        // 値を加算して 0 未満を 0 に補正して返却
        return redis.opsForValue()
                // 値をインクリメント
                .increment(key, delta)
                // 加算結果を long に変換
                .map(Number::longValue)
                // 0 未満を 0 に補正
                .flatMap(updatedValue -> clampValueNonNegative(key, updatedValue));
    }

    /**
     * ユーザー×カテゴリのシグナル一覧を取得
     */
    @Override
    public Mono<Map<UUID, Long>> getUserCategoryCounts(String userId, String metric) {

        // ユーザーID未指定の場合
        if (userId == null || userId.isBlank()) {
            // 空のマップを返却
            return Mono.just(Collections.emptyMap());
        }

        // ユーザー別カテゴリキーを生成
        String key = uCatKey(userId, metric);

        // ハッシュ全件を UUID→long マップへ変換して返却
        return redis.opsForHash()
                // ハッシュエントリを取得
                .entries(key)
                // キーと値をマップへ収集
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                // UUID→long マップへ変換
                .map(this::toUuidLongMap);
    }

    /**
     * ユーザー×タグのシグナル一覧を取得
     */
    @Override
    public Mono<Map<UUID, Long>> getUserTagCounts(String userId, String metric) {

        // ユーザーID未指定の場合
        if (userId == null || userId.isBlank()) {
            // 空のマップを返却
            return Mono.just(Collections.emptyMap());
        }

        // ユーザー別タグキーを生成
        String key = uTagKey(userId, metric);

        // ハッシュ全件を UUID→long マップへ変換して返却
        return redis.opsForHash()
                // ハッシュエントリを取得
                .entries(key)
                // キーと値をマップへ収集
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                // UUID→long マップへ変換
                .map(this::toUuidLongMap);
    }

    /**
     * ユーザー合計シグナルを取得
     */
    @Override
    public Mono<Long> getUserTotal(String userId, String metric) {

        // ユーザーID未指定の場合
        if (userId == null || userId.isBlank()) {
            // 0 を返却
            return Mono.just(0L);
        }

        // ユーザー別合計キーを生成
        String key = uTotalKey(userId, metric);

        // 合計値を取得して long へ変換して返却
        return redis.opsForValue()
                // 値を取得
                .get(key)
                // 取得値を long へ変換
                .map(RedisRecSignalStoreAdapter::safeLong)
                // 未設定の場合は 0 を返却
                .defaultIfEmpty(0L);
    }

    /**
     * 全体×ニュースの閲覧数を加算（A/B グループ別、0〜11 に飽和）
     */
    @Override
    public Mono<Long> incrGlobalNewsViewCapped11(String abGroup, UUID newsId, long delta) {

        // ニュースID未指定の場合
        if (newsId == null) {
            // 変化量なしとして 0 を返却
            return Mono.just(0L);
        }

        // A/B 別の全体ニュース閲覧数キーを生成
        String key = gNewsViewKey(abGroup);
        // ハッシュのフィールドにニュースIDを設定
        String field = newsId.toString();

        // 閲覧数を加算して 0〜11 に飽和させて返却
        return redis.opsForHash()
                // ハッシュ値を加算
                .increment(key, field, delta)
                // 加算結果を long に変換
                .map(Number::longValue)
                // 0〜11 の範囲に飽和して必要なら書き戻し
                .flatMap(incrementedValue -> {

                    // 0〜11 の範囲に丸めた値を算出
                    long clamped = Math.min(11L, Math.max(0L, incrementedValue));

                    // 既に範囲内の場合
                    if (clamped == incrementedValue) {
                        // 加算結果を返却
                        return Mono.just(incrementedValue);
                    }

                    // 範囲外の場合は飽和値を書き戻して返却
                    return redis.opsForHash()
                            // 飽和値をハッシュへ書き戻し
                            .put(key, field, String.valueOf(clamped))
                            // 書き戻し完了後に飽和値を返却
                            .thenReturn(clamped);
                });
    }

    /**
     * 全体×ニュースの閲覧数一覧を取得（A/B グループ別、0〜11 に飽和）
     */
    @Override
    public Mono<List<Long>> getGlobalNewsViewsCapped11(String abGroup, List<UUID> newsIds) {

        // ニュースID一覧が空の場合
        if (newsIds == null || newsIds.isEmpty()) {
            // 空の一覧を返却
            return Mono.just(Collections.emptyList());
        }

        // A/B 別の全体ニュース閲覧数キーを生成
        String key = gNewsViewKey(abGroup);
        // 取得対象のフィールド一覧を生成
        List<String> fields = newsIds.stream().map(UUID::toString).toList();

        // 複数フィールドを取得して 0〜11 に飽和して返却
        return redis.opsForHash()
                // 複数フィールドを取得
                .multiGet(key, new ArrayList<>(fields))
                // 実装差で空になる場合は空リストを補完
                .defaultIfEmpty(Collections.emptyList())
                // 取得値を飽和させて long リストへ変換
                .map(list -> {

                    // 出力リストを生成
                    List<Long> out = new ArrayList<>(list.size());

                    // 取得値を順に変換
                    for (Object value : list) {
                        // 取得値を long へ変換
                        long countValue = safeLong(value);
                        // 0〜11 に飽和させて追加
                        out.add(Math.min(11L, Math.max(0L, countValue)));
                    }

                    // 変換後リストを返却
                    return out;
                });
    }

    /**
     * 全体×カテゴリのシグナルを加算
     */
    @Override
    public Mono<Long> incrGlobalCategory(String metric, UUID categoryId, long delta) {

        // カテゴリID未指定の場合
        if (categoryId == null) {
            // 変化量なしとして 0 を返却
            return Mono.just(0L);
        }

        // 全体カテゴリキーを生成
        String key = gCatKey(metric);
        // ハッシュのフィールドにカテゴリIDを設定
        String field = categoryId.toString();

        // カウントを加算して 0 未満を 0 に補正して返却
        return redis.opsForHash()
                // ハッシュ値を加算
                .increment(key, field, delta)
                // 加算結果を long に変換
                .map(Number::longValue)
                // 0 未満を 0 に補正
                .flatMap(updatedValue -> clampHashNonNegative(key, field, updatedValue));
    }

    /**
     * 全体×タグのシグナルを加算
     */
    @Override
    public Mono<Long> incrGlobalTag(String metric, UUID tagId, long delta) {

        // タグID未指定の場合
        if (tagId == null) {
            // 変化量なしとして 0 を返却
            return Mono.just(0L);
        }

        // 全体タグキーを生成
        String key = gTagKey(metric);
        // ハッシュのフィールドにタグIDを設定
        String field = tagId.toString();

        // カウントを加算して 0 未満を 0 に補正して返却
        return redis.opsForHash()
                // ハッシュ値を加算
                .increment(key, field, delta)
                // 加算結果を long に変換
                .map(Number::longValue)
                // 0 未満を 0 に補正
                .flatMap(updatedValue -> clampHashNonNegative(key, field, updatedValue));
    }

    /**
     * 全体合計シグナルを加算
     */
    @Override
    public Mono<Long> incrGlobalTotal(String metric, long delta) {

        // 全体合計キーを生成
        String key = gTotalKey(metric);

        // 値を加算して 0 未満を 0 に補正して返却
        return redis.opsForValue()
                // 値をインクリメント
                .increment(key, delta)
                // 加算結果を long に変換
                .map(Number::longValue)
                // 0 未満を 0 に補正
                .flatMap(updatedValue -> clampValueNonNegative(key, updatedValue));
    }

    /**
     * 全体×カテゴリのシグナル一覧を取得
     */
    @Override
    public Mono<Map<UUID, Long>> getGlobalCategoryCounts(String metric) {

        // 全体カテゴリキーを生成
        String key = gCatKey(metric);

        // ハッシュ全件を UUID→long マップへ変換して返却
        return redis.opsForHash()
                // ハッシュエントリを取得
                .entries(key)
                // キーと値をマップへ収集
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                // UUID→long マップへ変換
                .map(this::toUuidLongMap);
    }

    /**
     * 全体×タグのシグナル一覧を取得
     */
    @Override
    public Mono<Map<UUID, Long>> getGlobalTagCounts(String metric) {

        // 全体タグキーを生成
        String key = gTagKey(metric);

        // ハッシュ全件を UUID→long マップへ変換して返却
        return redis.opsForHash()
                // ハッシュエントリを取得
                .entries(key)
                // キーと値をマップへ収集
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                // UUID→long マップへ変換
                .map(this::toUuidLongMap);
    }

    /**
     * 全体合計シグナルを取得
     */
    @Override
    public Mono<Long> getGlobalTotal(String metric) {

        // 全体合計キーを生成
        String key = gTotalKey(metric);

        // 合計値を取得して long へ変換して返却
        return redis.opsForValue()
                // 値を取得
                .get(key)
                // 取得値を long へ変換
                .map(RedisRecSignalStoreAdapter::safeLong)
                // 未設定の場合は 0 を返却
                .defaultIfEmpty(0L);
    }

    /**
     * ハッシュの Object→Object マップを UUID→long マップへ変換
     */
    private Map<UUID, Long> toUuidLongMap(Map<Object, Object> raw) {

        // 入力マップが未設定、または空の場合
        if (raw == null || raw.isEmpty()) {
            // 空のマップを返却
            return Collections.emptyMap();
        }

        // 変換結果マップを生成
        Map<UUID, Long> out = new HashMap<>();

        // 入力エントリを順に変換
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {

            // キーを UUID に変換
            UUID id = safeUuid(entry.getKey());

            // UUID へ変換できない場合
            if (id == null) {
                // 当該エントリは除外
                continue;
            }

            // 値を long に変換して設定
            out.put(id, safeLong(entry.getValue()));
        }

        // 変換結果を返却
        return out;
    }
}