package com.news.recapp.application.service;

import com.news.recapp.application.port.out.FavoriteStorePort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * お気に入りの業務処理を提供
 */
@Service
public class FavoriteService {

    // トグル結果を保持
    public record ToggleResult(boolean favorite, int count, boolean limitReached) {}

    // お気に入りを保持
    private final FavoriteStorePort favoriteStore;

    // コンストラクタ
    public FavoriteService(
        FavoriteStorePort favoriteStore
    ) {
        this.favoriteStore = favoriteStore;
    }

    /**
     * お気に入りか判定
     */
    public Mono<Boolean> isFavorite(String userId, UUID newsId) {
        return favoriteStore.isFavorite(userId, newsId);
    }

    /**
     * お気に入りニュース記事 ID 一覧を取得
     */
    public Flux<UUID> listFavoriteNewsIds(String userId) {
        return favoriteStore.listFavoriteNewsIds(userId);
    }

    /**
     * お気に入り状態を切り替え
     */
    public Mono<ToggleResult> toggle(String userId, UUID newsId) {
        return favoriteStore.toggle(userId, newsId)
                // 取得したデータを整形
                .map(toggleResult -> new ToggleResult(toggleResult.favorite(), toggleResult.count(), toggleResult.limitReached()));
    }
}
