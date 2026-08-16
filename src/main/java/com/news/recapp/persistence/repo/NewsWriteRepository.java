package com.news.recapp.persistence.repo;

import com.news.recapp.persistence.entity.NewsEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * ニュース記事情報の保存処理を提供
 */
@Repository
public class NewsWriteRepository {

    // SQL 実行用クライアントを保持
    private final DatabaseClient db;

    // トランザクション演算子のプロバイダを保持
    private final ObjectProvider<TransactionalOperator> txProvider;

    // コンストラクタ
    public NewsWriteRepository(
        DatabaseClient db,
        ObjectProvider<TransactionalOperator> txProvider
    ) {
        this.db = db;
        this.txProvider = txProvider;
    }

    /**
     * ニュース本体とニュース内容を保存
     */
    public Mono<Void> upsert(NewsEntity entity) {

        // ニュース本体の保存処理を生成
        Mono<Void> write = upsertNews(entity)
            // ニュース本体の保存後にニュース内容を保存
            .then(upsertNewsContent(entity));

        // トランザクション演算子を取得
        TransactionalOperator tx = txProvider.getIfAvailable();

        // トランザクション演算子が利用可能な場合
        if (tx != null) {
            // 保存処理をトランザクション内で実行
            return tx.transactional(write);
        }

        // 保存処理を返却
        return write;
    }

    /**
     * ニュース本体を保存
     */
    private Mono<Void> upsertNews(NewsEntity entity) {

        // ニュース ID を取得
        UUID newsId = entity.getId();

        // カテゴリ ID を取得
        UUID categoryId = entity.getCategoryId();

        // 公開日時を取得
        OffsetDateTime publishedAt = entity.getPublishedAt();

        // 有効フラグを true として補完して取得
        boolean enabled = entity.getEnabled() == null
            || Boolean.TRUE.equals(entity.getEnabled());

        // ニュース本体の保存 SQL を生成
        DatabaseClient.GenericExecuteSpec spec = db.sql(
            "INSERT INTO news (news_id, category_id, published_at, enabled) " +
                "VALUES ($1, $2, COALESCE($3, now()), $4) " +
                "ON CONFLICT (news_id) DO UPDATE SET " +
                "category_id = EXCLUDED.category_id, " +
                "published_at = EXCLUDED.published_at, " +
                "enabled = EXCLUDED.enabled"
        )
            // ニュース ID をバインド
            .bind(0, Objects.requireNonNull(newsId))
            // カテゴリ ID をバインド
            .bind(1, categoryId);

        // 公開日時が未指定の場合
        if (publishedAt == null) {
            // 公開日時を null としてバインド
            spec = spec.bindNull(2, OffsetDateTime.class);
        } else {
            // 公開日時をバインド
            spec = spec.bind(2, publishedAt);
        }

        // 有効フラグをバインド
        spec = spec.bind(3, enabled);

        // ニュース本体を保存
        return spec
            // 更新件数を取得
            .fetch()
            // 更新件数の Mono を取得
            .rowsUpdated()
            // 更新結果を破棄して完了へ変換
            .then();
    }

    /**
     * ニュース内容を保存
     */
    private Mono<Void> upsertNewsContent(NewsEntity entity) {

        // ニュース ID を取得
        UUID newsId = entity.getId();

        // タイトルを空文字補完して取得
        String title = entity.getTitle() == null
            ? ""
            : entity.getTitle();

        // 本文を空文字補完して取得
        String body = entity.getBody() == null
            ? ""
            : entity.getBody();

        // ニュース内容を保存
        return db.sql(
            "INSERT INTO news_content (news_id, news_title, news_body) " +
                "VALUES ($1, $2, $3) " +
                "ON CONFLICT (news_id) DO UPDATE SET " +
                "news_title = EXCLUDED.news_title, " +
                "news_body = EXCLUDED.news_body"
        )
            // ニュース ID をバインド
            .bind(0, Objects.requireNonNull(newsId))
            // タイトルをバインド
            .bind(1, title)
            // 本文をバインド
            .bind(2, body)
            // 更新件数を取得
            .fetch()
            // 更新件数の Mono を取得
            .rowsUpdated()
            // 更新結果を破棄して完了へ変換
            .then();
    }
}