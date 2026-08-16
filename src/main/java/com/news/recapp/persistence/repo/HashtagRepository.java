package com.news.recapp.persistence.repo;

import com.news.recapp.persistence.entity.HashtagEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * ハッシュタグ情報の永続化を提供
 */
public interface HashtagRepository extends ReactiveCrudRepository<HashtagEntity, UUID> {

    /**
     * 有効なハッシュタグ一覧をハッシュタグ名の昇順で取得
     */
    Flux<HashtagEntity> findAllByEnabledTrueOrderByNameAsc();

    /**
     * 全ハッシュタグ一覧をハッシュタグ名の昇順で取得
     */
    Flux<HashtagEntity> findAllByOrderByNameAsc();

    /**
     * ハッシュタグ名（大文字小文字無視）でハッシュタグ情報を取得
     */
    @Query("SELECT * FROM hashtags WHERE LOWER(hashtag_name) = LOWER(:name) LIMIT 1")
    Mono<HashtagEntity> findByNameIgnoreCase(String name);

    /**
     * 指定ハッシュタグ ID の紐付けが存在するか判定
     */
    @Query("SELECT EXISTS(SELECT 1 FROM news_hashtags WHERE hashtag_id = :hashtagId)")
    Mono<Boolean> existsInAnyNews(UUID hashtagId);

}
