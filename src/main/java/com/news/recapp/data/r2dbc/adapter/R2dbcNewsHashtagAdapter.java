package com.news.recapp.data.r2dbc.adapter;

import com.news.recapp.application.port.out.NewsHashtagPort;
import com.news.recapp.persistence.entity.HashtagEntity;
import com.news.recapp.persistence.entity.NewsEntity;
import com.news.recapp.persistence.repo.NewsHashtagRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * ニュース-ハッシュタグ紐付けの永続化連携を仲介
 */
@Component
public class R2dbcNewsHashtagAdapter implements NewsHashtagPort {

    // ニュース-ハッシュタグリポジトリを保持
    private final NewsHashtagRepository repo;
    // トランザクション演算子のプロバイダを保持
    private final ObjectProvider<TransactionalOperator> txProvider;

    // コンストラクタ
    public R2dbcNewsHashtagAdapter(
        NewsHashtagRepository repo,
        ObjectProvider<TransactionalOperator> txProvider
    ) {
        this.repo = repo;
        this.txProvider = txProvider;
    }

    /**
     * ニュース ID に紐づく有効なハッシュタグ一覧を取得
     */
    @Override
    public Flux<HashtagEntity> findEnabledHashtags(UUID newsId) {
        // 有効なハッシュタグ一覧を返却
        return repo.findEnabledHashtags(newsId);
    }

    /**
     * ハッシュタグ名に紐づくニュース一覧を取得
     */
    @Override
    public Flux<NewsEntity> findAllVisibleNews(String hashtagName) {
        // ハッシュタグ名に紐づくニュース一覧を返却
        return repo.findAllVisibleNews(hashtagName);
    }

    /**
     * ハッシュタグ名に紐づくニュースをランダムに取得
     */
    @Override
    public Flux<NewsEntity> findVisibleRandomNews(String hashtagName, long limit) {
        // ハッシュタグ名に紐づくニュースをランダムに返却
        return repo.findVisibleRandomNews(hashtagName, limit);
    }

    /**
     * ニュースのハッシュタグ紐付けを置換（削除 → 追加）
     */
    @Override
    public Mono<Void> replaceNewsHashtags(UUID newsId, List<UUID> hashtagIds) {

        // 既存紐付けを削除して新しい紐付けを登録
        Mono<Void> action = repo.deleteByNewsId(newsId)
            // 登録対象のハッシュタグ ID 一覧を Flux へ変換
            .thenMany(hashtagIds == null
                ? Flux.empty()
                : Flux.fromIterable(hashtagIds))
            // ハッシュタグ ID を紐付けとして追加
            .flatMap(hashtagId -> repo.insert(newsId, hashtagId))
            // 追加完了後に完了へ変換
            .then();

        // トランザクション演算子を取得
        TransactionalOperator tx = txProvider.getIfAvailable();

        // トランザクション演算子が利用できない場合
        if (tx == null) {
            // 非トランザクションで実行して返却
            return action;
        }

        // トランザクション内で実行して返却
        return tx.transactional(action);
    }
}
