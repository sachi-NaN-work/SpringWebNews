package com.news.recapp.application.port.out;

import reactor.core.publisher.Mono;

/**
 * フィードバックのポート
 */
public interface FeedbackEventPublisherPort {

    /**
     * フィードバックをまとめる
     */
    Mono<Void> publishKafka(
        String userId,
        String newsId,
        String categoryId,
        String type,
        String source,
        String eventId,
        String requestId
    );
}
