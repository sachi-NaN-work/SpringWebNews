package com.news.recapp.application.port.out;

import com.news.recapp.persistence.entity.CategoryEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * カテゴリのポート
 */
public interface CategoryPort {

    /**
     * カテゴリスラグを取得
     */
    Mono<CategoryEntity> findBySlug(String slug);

    /**
     * カテゴリ名を取得
     */
    Mono<CategoryEntity> findByNameIgnoreCase(String name);

    /**
     * カテゴリ一覧の有効フラグのみを取得
     */
    Flux<CategoryEntity> findAllByEnabledTrueOrderByNameAsc();

    /**
     * カテゴリ一覧を取得
     */
    Flux<CategoryEntity> findAllCategory();

    /**
     * カテゴリ ID から特定カテゴリを取得
     */
    Mono<CategoryEntity> findByCategoryId(UUID id);

    /**
     * カテゴリ情報を保存
     */
    Mono<CategoryEntity> save(CategoryEntity entity);
}
