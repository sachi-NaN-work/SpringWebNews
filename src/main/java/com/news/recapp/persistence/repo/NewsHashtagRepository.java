package com.news.recapp.persistence.repo;

import com.news.recapp.persistence.entity.HashtagEntity;
import com.news.recapp.persistence.entity.NewsEntity;
import com.news.recapp.persistence.entity.NewsHashtagEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * ニュース記事とハッシュタグの紐付け情報の永続化を提供
 */
public interface NewsHashtagRepository extends ReactiveCrudRepository<NewsHashtagEntity, Long> {

    /**
     * ニュース ID に紐づく有効なハッシュタグ一覧を取得
     */
    @Query("SELECT h.* FROM hashtags h " +
        "JOIN news_hashtags nh ON nh.hashtag_id = h.hashtag_id " +
        "WHERE nh.news_id = :newsId AND h.enabled = TRUE " +
        "ORDER BY h.hashtag_name ASC")
    Flux<HashtagEntity> findEnabledHashtags(@Param("newsId") UUID newsId);

    /**
     * 指定ハッシュタグ名に紐づく表示可能ニュース一覧を新着順で取得
     */
    @Query("SELECT n.*, nc.news_title, nc.news_body FROM news n " +
        "JOIN news_content nc ON nc.news_id = n.news_id " +
        "JOIN categories c ON n.category_id = c.category_id " +
        "JOIN news_hashtags nh ON nh.news_id = n.news_id " +
        "JOIN hashtags h ON h.hashtag_id = nh.hashtag_id " +
        "WHERE n.enabled = TRUE AND c.enabled = TRUE AND h.enabled = TRUE " +
        "AND LOWER(h.hashtag_name) = LOWER(:hashtagName) " +
        "ORDER BY n.published_at DESC")
    Flux<NewsEntity> findAllVisibleNews(@Param("hashtagName") String hashtagName);

    /**
     * 指定ハッシュタグ名に紐づく表示可能ニュース一覧をランダムに取得
     */
    @Query("SELECT n.*, nc.news_title, nc.news_body FROM news n " +
        "JOIN news_content nc ON nc.news_id = n.news_id " +
        "JOIN categories c ON n.category_id = c.category_id " +
        "JOIN news_hashtags nh ON nh.news_id = n.news_id " +
        "JOIN hashtags h ON h.hashtag_id = nh.hashtag_id " +
        "WHERE n.enabled = TRUE AND c.enabled = TRUE AND h.enabled = TRUE " +
        "AND LOWER(h.hashtag_name) = LOWER(:hashtagName) " +
        "ORDER BY random() LIMIT :limit")
    Flux<NewsEntity> findVisibleRandomNews(@Param("hashtagName") String hashtagName, @Param("limit") long limit);

    /**
     * 指定ニュース ID の紐付けを全削除
     */
    @Query("DELETE FROM news_hashtags WHERE news_id = :newsId")
    Mono<Integer> deleteByNewsId(@Param("newsId") UUID newsId);

    /**
     * ニュース ID とハッシュタグ ID の紐付けを 1 件追加
     */
    @Query("INSERT INTO news_hashtags(news_id, hashtag_id) VALUES (:newsId, :hashtagId)")
    Mono<Integer> insert(@Param("newsId") UUID newsId, @Param("hashtagId") UUID hashtagId);
}
