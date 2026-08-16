package com.news.recapp.persistence.repo;

import com.news.recapp.persistence.entity.FeedbackEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * フィードバック情報の永続化を提供
 */
public interface FeedbackRepository extends ReactiveCrudRepository<FeedbackEntity, Long> {

    /**
     * 指定ニュース ID のフィードバックを削除
     */
    @Query("DELETE FROM feedback WHERE news_id = :newsId")
    Mono<Void> deleteByNewsId(@Param("newsId") UUID newsId);

    /**
     * 指定ユーザー・ニュースの最新リアクション種別を取得
     *
     * - good / bad: リアクションあり
     * - reaction_clear: リアクション解除
     *
     * created_at が同一の行がある場合でも prev を安定化するため、
     * ORDER BY に PK(feedback_id) を追加して決定性を担保する。
     *  （※）学習側（Python）の DBログ復元も created_at + feedback_id で安定化しており、オンライン/学習の一致を担保する。
     */
    @Query("SELECT feedback_type FROM feedback " +
        "WHERE user_id = :userId AND news_id = :newsId " +
        "AND feedback_type IN ('good','bad','reaction_clear') " +
        "ORDER BY created_at DESC, feedback_id DESC LIMIT 1")
    Mono<String> findReactionType(@Param("userId") String userId, @Param("newsId") UUID newsId);

}
