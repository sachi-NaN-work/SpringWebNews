package com.news.recapp.persistence.repo;

import com.news.recapp.persistence.entity.AppUserEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * アプリケーションユーザー情報の永続化を提供
 */
public interface AppUserRepository extends ReactiveCrudRepository<AppUserEntity, String> {

    /**
     * ユーザー名でユーザー情報を取得
     */
    Mono<AppUserEntity> findByUsername(String username);
}
