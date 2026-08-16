package com.news.recapp.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * お気に入りのポート
 */
public interface FavoriteStorePort {

    /**
     * トグルを切替結果
     */
    record ToggleResult(boolean favorite, int count, boolean limitReached) {}

    /**
     * お気に入りか判定
     */
    Mono<Boolean> isFavorite(String userId, UUID newsId);

    /**
     * お気に入りニュース ID を取得
     */
    Flux<UUID> listFavoriteNewsIds(String userId);

    /**
     * トグルを切替
     */
    Mono<ToggleResult> toggle(String userId, UUID newsId);
}
