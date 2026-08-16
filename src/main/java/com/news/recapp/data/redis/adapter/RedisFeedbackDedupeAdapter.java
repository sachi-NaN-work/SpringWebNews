package com.news.recapp.data.redis.adapter;

import com.news.recapp.application.port.out.FeedbackDedupePort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * 閲覧フィードバックの重複処理を Redis で抑止するアダプタ
 */
@Component
public class RedisFeedbackDedupeAdapter implements FeedbackDedupePort {

    // Redis クライアントを保持
    private final ReactiveStringRedisTemplate redis;

    // 閲覧イベントの重複抑止 TTL（10分）を保持
    private final Duration viewDedupeTtl = Duration.ofMinutes(10);

    // コンストラクタ
    public RedisFeedbackDedupeAdapter(
            ReactiveStringRedisTemplate redis
    ) {
        this.redis = redis;
    }

    /**
     * フィードバックを処理すべきか判定（閲覧のみ重複抑止）
     */
    @Override
    public Mono<Boolean> shouldProcess(String userId, UUID newsId, String feedbackType, String source, String eventId) {

        // 閲覧以外のフィードバックは重複抑止せず処理対象とする
        if (!"view".equalsIgnoreCase(feedbackType)) {
            // 処理対象として true を返却
            return Mono.just(true);
        }

        // 重複抑止キーを初期化
        String key;

        // eventId が指定されている場合は eventId ベースで抑止
        if (eventId != null && !eventId.isBlank()) {
            // eventId ベースの重複抑止キーを設定
            key = "dedupe:event:" + eventId;

        // eventId が未指定の場合は userId/newsId/source ベースで抑止
        } else {

            // 出典を正規化して取得
            String src;

            // 出典が未指定の場合
            if (source == null || source.isBlank()) {
                // 未指定出典として unknown を設定
                src = "unknown";
            // 出典が指定されている場合
            } else {
                // 小文字化した出典を設定
                src = source.toLowerCase();
            }

            // userId/newsId/source ベースの重複抑止キーを設定
            key = "dedupe:view:" + userId + ":" + newsId + ":" + src;
        }

        // TTL 付きでキーを setIfAbsent して初回のみ true を返却
        return redis.opsForValue()
            // 初回のみ値をセットして true を返却
            .setIfAbsent(key, "1", viewDedupeTtl)
            // 実装差で空になる場合は false を返却
            .defaultIfEmpty(false);
    }
}
