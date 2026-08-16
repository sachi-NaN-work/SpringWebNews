package com.news.recapp.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ニュース記事情報を保持
 */
@Table("news")
public class NewsEntity implements Persistable<UUID> {

    // ニュース ID
    @Id
    @Column("news_id")
    private UUID id;

    // カテゴリ ID
    @Column("category_id")
    private UUID categoryId;

    // 正規化後のコンテンツテーブルのカラム名に合わせてタイトルをマッピング
    @Column("news_title")
    private String title;

    // 正規化後のコンテンツテーブルのカラム名に合わせて本文をマッピング
    @Column("news_body")
    private String body;

    // 公開日時
    @Column("published_at")
    private OffsetDateTime publishedAt;

    // 有効フラグ
    private Boolean enabled;

    // 新規作成フラグ
    @Transient
    private boolean newEntity = false;

    // コンストラクタ
    public NewsEntity() {
    }

    // コンストラクタ
    public NewsEntity(
        UUID id,
        UUID categoryId,
        String title,
        String body,
        OffsetDateTime publishedAt,
        Boolean enabled
    ) {
        this.id = id;
        this.categoryId = categoryId;
        this.title = title;
        this.body = body;
        this.publishedAt = publishedAt;
        this.enabled = enabled;
        this.newEntity = true;
    }

    /**
     * ニュース記事情報を新規作成として扱う
     */
    public NewsEntity markNew() {
        // 新規作成フラグを true に設定
        this.newEntity = true;
        // ニュース記事情報を返却
        return this;
    }

    /**
     * Persistable の ID を取得
     */
    @Override
    public UUID getId() {
        // ニュース ID を返却
        return id;
    }

    /**
     * Persistable の新規判定を返却
     */
    @Override
    public boolean isNew() {
        // 新規作成フラグを返却
        return newEntity;
    }

    /**
     * ニュース ID を設定
     */
    public void setId(UUID id) {
        // ニュース ID を設定
        this.id = id;
    }

    /**
     * カテゴリ ID を取得
     */
    public UUID getCategoryId() {
        // カテゴリ ID を返却
        return categoryId;
    }

    /**
     * カテゴリ ID を設定
     */
    public void setCategoryId(UUID categoryId) {
        // カテゴリ ID を設定
        this.categoryId = categoryId;
    }

    /**
     * タイトルを取得
     */
    public String getTitle() {
        // タイトルを返却
        return title;
    }

    /**
     * タイトルを設定
     */
    public void setTitle(String title) {
        // タイトルを設定
        this.title = title;
    }

    /**
     * 本文を取得
     */
    public String getBody() {
        // 本文を返却
        return body;
    }

    /**
     * 本文を設定
     */
    public void setBody(String body) {
        // 本文を設定
        this.body = body;
    }

    /**
     * 公開日時を取得
     */
    public OffsetDateTime getPublishedAt() {
        // 公開日時を返却
        return publishedAt;
    }

    /**
     * 公開日時を設定
     */
    public void setPublishedAt(OffsetDateTime publishedAt) {
        // 公開日時を設定
        this.publishedAt = publishedAt;
    }

    /**
     * 有効フラグを取得
     */
    public Boolean getEnabled() {
        // 有効フラグを返却
        return enabled;
    }

    /**
     * 有効フラグを設定
     */
    public void setEnabled(Boolean enabled) {
        // 有効フラグを設定
        this.enabled = enabled;
    }
}
