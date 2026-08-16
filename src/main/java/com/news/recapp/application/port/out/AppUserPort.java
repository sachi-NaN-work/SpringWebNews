package com.news.recapp.application.port.out;

import com.news.recapp.persistence.entity.AppUserEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * ユーザーのポート
 */
public interface AppUserPort {

    /**
     * ユーザー一覧を取得
     */
    Flux<AppUserEntity> findAll();

    /**
     * ユーザー名を検索
     */
    Mono<AppUserEntity> findById(String username);

    /**
     * ユーザー名を検索
     */
    Mono<AppUserEntity> findByUsername(String username);

    /**
     * ユーザー情報を保存
     */
    Mono<AppUserEntity> save(AppUserEntity entity);
}
