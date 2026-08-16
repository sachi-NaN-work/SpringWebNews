package com.news.recapp.persistence.repo;

import com.news.recapp.persistence.entity.NewsEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * ニュース記事情報の永続化を提供
 */
public interface NewsRepository extends ReactiveCrudRepository<NewsEntity, UUID> {

    /**
     * 表示可能な最新ニュース一覧を取得
     */
    @Query("SELECT n.*, nc.news_title, nc.news_body FROM news n " +
        "JOIN news_content nc ON nc.news_id = n.news_id " +
        "JOIN categories c ON n.category_id = c.category_id " +
        "WHERE n.enabled = TRUE AND c.enabled = TRUE " +
        "ORDER BY n.published_at DESC LIMIT :limit")
    Flux<NewsEntity> findLatestVisible(@Param("limit") long limit);

    /**
     * 表示可能ニュースをカテゴリ指定で全件取得（新着順）
     */
    @Query("SELECT n.*, nc.news_title, nc.news_body FROM news n " +
        "JOIN news_content nc ON nc.news_id = n.news_id " +
        "JOIN categories c ON n.category_id = c.category_id " +
        "WHERE n.category_id = :categoryId AND n.enabled = TRUE AND c.enabled = TRUE " +
        "ORDER BY n.published_at DESC")
    Flux<NewsEntity> findAllVisibleByCategory(@Param("categoryId") UUID categoryId);

    /**
     * 表示可能ニュースを全件取得（新着順）
     */
    @Query("SELECT n.*, nc.news_title, nc.news_body FROM news n " +
        "JOIN news_content nc ON nc.news_id = n.news_id " +
        "JOIN categories c ON n.category_id = c.category_id " +
        "WHERE n.enabled = TRUE AND c.enabled = TRUE " +
        "ORDER BY n.published_at DESC")
    Flux<NewsEntity> findAllVisible();

    /**
     * 表示可能ニュースをランダムに取得（候補生成向け）
     */
    @Query("SELECT n.*, nc.news_title, nc.news_body FROM news n " +
        "JOIN news_content nc ON nc.news_id = n.news_id " +
        "JOIN categories c ON n.category_id = c.category_id " +
        "WHERE n.enabled = TRUE AND c.enabled = TRUE " +
        "ORDER BY random() LIMIT :limit")
    Flux<NewsEntity> findVisibleRandom(@Param("limit") long limit);

    /**
     * 表示可能ニュースをカテゴリ指定でランダムに取得（候補生成向け）
     */
    @Query("SELECT n.*, nc.news_title, nc.news_body FROM news n " +
        "JOIN news_content nc ON nc.news_id = n.news_id " +
        "JOIN categories c ON n.category_id = c.category_id " +
        "WHERE n.enabled = TRUE AND c.enabled = TRUE AND n.category_id = :categoryId " +
        "ORDER BY random() LIMIT :limit")
    Flux<NewsEntity> findVisibleRandomByCategory(@Param("categoryId") UUID categoryId, @Param("limit") long limit);

    /**
     * 全ニュースを取得（新着順）
     */
    @Query("SELECT n.*, nc.news_title, nc.news_body FROM news n " +
        "JOIN news_content nc ON nc.news_id = n.news_id " +
        "ORDER BY n.published_at DESC")
    Flux<NewsEntity> findAllNews();

    /**
     * ニュース ID でニュース記事情報を取得（内容結合）
     */
    @Query("SELECT n.*, nc.news_title, nc.news_body FROM news n " +
        "JOIN news_content nc ON nc.news_id = n.news_id " +
        "WHERE n.news_id = :id")
    Mono<NewsEntity> findById(@Param("id") UUID id);

}
