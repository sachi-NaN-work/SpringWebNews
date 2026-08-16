package com.news.recapp.data.r2dbc.adapter;

import com.news.recapp.application.port.out.FeedbackPort;
import com.news.recapp.persistence.entity.FeedbackEntity;
import com.news.recapp.persistence.repo.FeedbackRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * フィードバック情報の永続化連携を仲介
 */
@Component
public class R2dbcFeedbackAdapter implements FeedbackPort {

    // フィードバックリポジトリを保持
    private final FeedbackRepository repo;

    // コンストラクタ
    public R2dbcFeedbackAdapter(
            FeedbackRepository repo
    ) {
        this.repo = repo;
    }

    /**
     * フィードバックを保存
     */
    @Override
    public Mono<FeedbackEntity> save(FeedbackEntity entity) {
        // フィードバックを保存して返却
        return repo.save(entity);
    }

    /**
     * ニュースIDに紐づくフィードバックを削除
     */
    @Override
    public Mono<Void> deleteByNewsId(UUID newsId) {
        // ニュースID一致のフィードバックを削除して返却
        return repo.deleteByNewsId(newsId);
    }

    /**
     * 1ユーザー×1ニュースのリアクション種別を取得
     */
    @Override
    public Mono<String> findReactionType(String userId, UUID newsId) {
        // 取得したリアクション種別を整形して返却
        return repo.findReactionType(userId, newsId)
            // クリア操作は空文字として扱う
            .map(reactionType -> "reaction_clear".equals(reactionType)
                ? ""
                : reactionType);
    }
}
