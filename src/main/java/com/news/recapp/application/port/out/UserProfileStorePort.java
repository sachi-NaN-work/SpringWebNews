package com.news.recapp.application.port.out;

import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * ユーザープロファイルのポート
 */
public interface UserProfileStorePort {

    /**
     * ユーザープロフィールを取得
     */
    Mono<Map<Object, Object>> getProfile(String userId);

    /**
     * カテゴリ別カウントを加算
     */
    Mono<Long> incrementCategory(String userId, UUID categoryId, long delta);
}
