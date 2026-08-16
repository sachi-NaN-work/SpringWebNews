package com.news.recapp.data.r2dbc.adapter;

import com.news.recapp.application.port.out.CategoryPort;
import com.news.recapp.persistence.entity.CategoryEntity;
import com.news.recapp.persistence.repo.CategoryRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * カテゴリ情報の永続化連携を仲介
 */
@Component
public class R2dbcCategoryAdapter implements CategoryPort {

    // カテゴリリポジトリを保持
    private final CategoryRepository categoryRepository;

    // コンストラクタ
    public R2dbcCategoryAdapter(
            CategoryRepository categoryRepository
    ) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * スラグからカテゴリを取得
     */
    @Override
    public Mono<CategoryEntity> findBySlug(String slug) {
        // スラグ一致のカテゴリを返却
        return categoryRepository.findBySlug(slug);
    }

    /**
     * 名称（大文字小文字無視）からカテゴリを取得
     */
    @Override
    public Mono<CategoryEntity> findByNameIgnoreCase(String name) {
        // 名称一致のカテゴリを返却
        return categoryRepository.findByNameIgnoreCase(name);
    }

    /**
     * 有効なカテゴリ一覧を名称昇順で取得
     */
    @Override
    public Flux<CategoryEntity> findAllByEnabledTrueOrderByNameAsc() {
        // 有効カテゴリ一覧を返却
        return categoryRepository.findAllByEnabledTrueOrderByNameAsc();
    }

    /**
     * カテゴリ一覧を取得
     */
    @Override
    public Flux<CategoryEntity> findAllCategory() {
        // 全カテゴリ一覧を返却
        return categoryRepository.findAll();
    }

    /**
     * カテゴリIDからカテゴリを取得
     */
    @Override
    public Mono<CategoryEntity> findByCategoryId(UUID id) {
        // カテゴリID一致のカテゴリを返却
        return categoryRepository.findById(id);
    }

    /**
     * カテゴリを保存
     */
    @Override
    public Mono<CategoryEntity> save(CategoryEntity entity) {
        // カテゴリを保存して返却
        return categoryRepository.save(entity);
    }
}
