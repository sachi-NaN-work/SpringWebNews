package com.news.recapp.data.r2dbc.adapter;

import com.news.recapp.application.port.out.AppUserPort;
import com.news.recapp.persistence.entity.AppUserEntity;
import com.news.recapp.persistence.repo.AppUserRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * ユーザー情報の永続化連携を仲介
 */
@Component
public class R2dbcAppUserAdapter implements AppUserPort {

    // ユーザーリポジトリを保持
    private final AppUserRepository userRepository;

    // コンストラクタ
    public R2dbcAppUserAdapter(
            AppUserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    /**
     * ユーザー一覧を取得
     */
    @Override
    public Flux<AppUserEntity> findAll() {
        // 全ユーザー一覧を返却
        return userRepository.findAll();
    }

    /**
     * ユーザーIDでユーザーを取得
     */
    @Override
    public Mono<AppUserEntity> findById(String username) {
        // ユーザーIDでユーザーを返却
        return userRepository.findById(username);
    }

    /**
     * ユーザー名でユーザーを取得
     */
    @Override
    public Mono<AppUserEntity> findByUsername(String username) {
        // ユーザー名でユーザーを返却
        return userRepository.findByUsername(username);
    }

    /**
     * ユーザーを保存
     */
    @Override
    public Mono<AppUserEntity> save(AppUserEntity entity) {
        // ユーザー情報を保存して返却
        return userRepository.save(entity);
    }
}
