package com.news.recapp.application.service;

import com.news.recapp.application.port.out.UserProfileStorePort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * ユーザープロファイルの業務処理を提供
 */
@Service
public class UserProfileService {

    // ユーザープロフィールを保持
    private final UserProfileStorePort store;

    // コンストラクタ
    public UserProfileService(
        UserProfileStorePort store
    ) {
        this.store = store;
    }

    /**
     * ユーザープロファイルを取得
     */
    public Mono<Map<Object, Object>> getProfile(String userId) {
        return store.getProfile(userId);
    }

    /**
     * カテゴリ別カウントを加算
     */
    public Mono<Long> incrementCategory(String userId, UUID categoryId, long delta) {
        return store.incrementCategory(userId, categoryId, delta);
    }
}
