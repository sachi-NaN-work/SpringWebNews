package com.news.recapp.persistence.repo;

import com.news.recapp.persistence.entity.CategoryEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * カテゴリ情報の永続化を提供
 */
public interface CategoryRepository extends ReactiveCrudRepository<CategoryEntity, UUID> {

    /**
     * カテゴリの短縮識別子でカテゴリ情報を取得
     */
    Mono<CategoryEntity> findBySlug(String slug);

    /**
     * カテゴリ名（大文字小文字無視）でカテゴリ情報を取得
     */
    Mono<CategoryEntity> findByNameIgnoreCase(String name);

    /**
     * 有効なカテゴリ一覧をカテゴリ名の昇順で取得
     */
    Flux<CategoryEntity> findAllByEnabledTrueOrderByNameAsc();

}
