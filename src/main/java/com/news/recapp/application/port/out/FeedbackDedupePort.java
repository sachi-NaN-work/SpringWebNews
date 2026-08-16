package com.news.recapp.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * フィードバックのポート
 */
public interface FeedbackDedupePort {

    /**
     * フィードバック重複判定
     */
    Mono<Boolean> shouldProcess(String userId, UUID newsId, String feedbackType, String source, String eventId);
}
