package com.news.recapp.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * フィードバック情報を保持
 */
@Table("feedback")
public class FeedbackEntity {

    // フィードバック ID
    @Id
    @Column("feedback_id")
    private Long id;

    // ユーザー ID
    @Column("user_id")
    private String userId;

    // ニュース ID
    @Column("news_id")
    private UUID newsId;

    // カテゴリ ID
    @Column("category_id")
    private UUID categoryId;

    // フィードバック種別
    @Column("feedback_type")
    private String feedbackType;

    // 作成日時
    @Column("created_at")
    private OffsetDateTime createdAt;

    // コンストラクタ
    public FeedbackEntity() {
    }

    // コンストラクタ
    public FeedbackEntity(
        Long id,
        String userId,
        UUID newsId,
        UUID categoryId,
        String feedbackType,
        OffsetDateTime createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.newsId = newsId;
        this.categoryId = categoryId;
        this.feedbackType = feedbackType;
        this.createdAt = createdAt;
    }

    /**
     * フィードバック ID を取得
     */
    public Long getId() {
        // フィードバック ID を返却
        return id;
    }

    /**
     * フィードバック ID を設定
     */
    public void setId(Long id) {
        // フィードバック ID を設定
        this.id = id;
    }

    /**
     * ユーザー ID を取得
     */
    public String getUserId() {
        // ユーザー ID を返却
        return userId;
    }

    /**
     * ユーザー ID を設定
     */
    public void setUserId(String userId) {
        // ユーザー ID を設定
        this.userId = userId;
    }

    /**
     * ニュース ID を取得
     */
    public UUID getNewsId() {
        // ニュース ID を返却
        return newsId;
    }

    /**
     * ニュース ID を設定
     */
    public void setNewsId(UUID newsId) {
        // ニュース ID を設定
        this.newsId = newsId;
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
     * フィードバック種別を取得
     */
    public String getFeedbackType() {
        // フィードバック種別を返却
        return feedbackType;
    }

    /**
     * フィードバック種別を設定
     */
    public void setFeedbackType(String feedbackType) {
        // フィードバック種別を設定
        this.feedbackType = feedbackType;
    }

    /**
     * 作成日時を取得
     */
    public OffsetDateTime getCreatedAt() {
        // 作成日時を返却
        return createdAt;
    }

    /**
     * 作成日時を設定
     */
    public void setCreatedAt(OffsetDateTime createdAt) {
        // 作成日時を設定
        this.createdAt = createdAt;
    }
}
