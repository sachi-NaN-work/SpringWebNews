package com.news.recapp.application.port.out;

import com.news.recapp.persistence.entity.HashtagEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * ハッシュタグのポート
 */
public interface HashtagPort {

    /**
     * 有効なハッシュタグ一覧（名前順）を取得
     */
    Flux<HashtagEntity> findAllEnabledOrderByNameAsc();

    /**
     * 全ハッシュタグ一覧（名前順）を取得
     */
    Flux<HashtagEntity> findAllOrderByNameAsc();

    /**
     * ハッシュタグ ID で取得
     */
    Mono<HashtagEntity> findById(UUID id);

    /**
     * ハッシュタグ名（大文字小文字無視）で取得
     */
    Mono<HashtagEntity> findByNameIgnoreCase(String name);

    /**
     * ハッシュタグ情報を保存
     */
    Mono<HashtagEntity> save(HashtagEntity entity);

    /**
     * ハッシュタグ使用有無判定
     */
    Mono<Boolean> existsInAnyNews(UUID hashtagId);
}
