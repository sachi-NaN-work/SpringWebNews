package com.news.recapp.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * ニュース記事とハッシュタグの紐付け情報を保持
 */
@Table("news_hashtags")
public class NewsHashtagEntity {

    // 紐付け ID
    @Id
    @Column("news_hashtag_id")
    private Long id;

    // ニュース ID
    @Column("news_id")
    private UUID newsId;

    // ハッシュタグ ID
    @Column("hashtag_id")
    private UUID hashtagId;

    // コンストラクタ
    public NewsHashtagEntity() {
    }

    /**
     * 紐付け ID を取得
     */
    public Long getId() {
        // 紐付け ID を返却
        return id;
    }

    /**
     * 紐付け ID を設定
     */
    public void setId(Long id) {
        // 紐付け ID を設定
        this.id = id;
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
     * ハッシュタグ ID を取得
     */
    public UUID getHashtagId() {
        // ハッシュタグ ID を返却
        return hashtagId;
    }

    /**
     * ハッシュタグ ID を設定
     */
    public void setHashtagId(UUID hashtagId) {
        // ハッシュタグ ID を設定
        this.hashtagId = hashtagId;
    }
}
