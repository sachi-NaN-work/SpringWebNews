package com.news.recapp.application.port.out;

import com.news.recapp.persistence.entity.FeedbackEntity;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * フィードバックのポート
 */
public interface FeedbackPort {

    /**
     * フィードバックを保存
     */
    Mono<FeedbackEntity> save(FeedbackEntity entity);

    /**
     * 削除対象ニュース識別子を削除
     */
    Mono<Void> deleteByNewsId(UUID newsId);

    /**
     * 1ユーザー×1ニュースの内容/取得する（未送信は内容）
     */
    Mono<String> findReactionType(String userId, UUID newsId);
}
