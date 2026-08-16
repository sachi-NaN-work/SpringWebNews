package com.news.recapp.application.service;

import com.news.recapp.application.port.out.FeedbackDedupePort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * フィードバックの業務処理を提供
 */
@Service
public class FeedbackDedupeService {

    // 重複判定ポートを保持
    private final FeedbackDedupePort dedupePort;

    // コンストラクタ
    public FeedbackDedupeService(
        FeedbackDedupePort dedupePort
    ) {
        this.dedupePort = dedupePort;
    }

    /**
     * フィードバックのキー重複判定
     */
    public Mono<Boolean> shouldProcess(String userId, UUID newsId, String feedbackType, String source, String eventId) {
        // フィードバックのキー重複判定を返却
        return dedupePort.shouldProcess(userId, newsId, feedbackType, source, eventId);
    }
}
