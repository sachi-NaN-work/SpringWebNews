package com.news.recapp.data.r2dbc.adapter;

import com.news.recapp.application.port.out.HashtagPort;
import com.news.recapp.persistence.entity.HashtagEntity;
import com.news.recapp.persistence.repo.HashtagRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * ハッシュタグ情報の永続化連携を仲介
 */
@Component
public class R2dbcHashtagAdapter implements HashtagPort {

    // ハッシュタグリポジトリを保持
    private final HashtagRepository repo;

    // コンストラクタ
    public R2dbcHashtagAdapter(
            HashtagRepository repo
    ) {
        this.repo = repo;
    }

    /**
     * 有効なハッシュタグ一覧を名称昇順で取得
     */
    @Override
    public Flux<HashtagEntity> findAllEnabledOrderByNameAsc() {
        // 有効ハッシュタグ一覧を返却
        return repo.findAllByEnabledTrueOrderByNameAsc();
    }

    /**
     * 全ハッシュタグ一覧を名称昇順で取得
     */
    @Override
    public Flux<HashtagEntity> findAllOrderByNameAsc() {
        // 全ハッシュタグ一覧を返却
        return repo.findAllByOrderByNameAsc();
    }

    /**
     * ハッシュタグIDからハッシュタグを取得
     */
    @Override
    public Mono<HashtagEntity> findById(UUID id) {
        // ハッシュタグID一致のハッシュタグを返却
        return repo.findById(id);
    }

    /**
     * 名称（大文字小文字無視）からハッシュタグを取得
     */
    @Override
    public Mono<HashtagEntity> findByNameIgnoreCase(String name) {
        // 名称一致のハッシュタグを返却
        return repo.findByNameIgnoreCase(name);
    }

    /**
     * ハッシュタグを保存
     */
    @Override
    public Mono<HashtagEntity> save(HashtagEntity entity) {
        // ハッシュタグを保存して返却
        return repo.save(entity);
    }

    /**
     * ハッシュタグがいずれかの記事に使用されているか判定
     */
    @Override
    public Mono<Boolean> existsInAnyNews(UUID hashtagId) {
        // ハッシュタグの使用有無を返却
        return repo.existsInAnyNews(hashtagId);
    }
}
