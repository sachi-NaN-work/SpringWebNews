package com.news.recapp.data.r2dbc.adapter;

import com.news.recapp.application.port.out.RolePermissionPort;
import com.news.recapp.persistence.repo.RolePermissionRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * ロール権限の永続化連携を仲介
 */
@Component
public class R2dbcRolePermissionAdapter implements RolePermissionPort {

    // ロール権限リポジトリを保持
    private final RolePermissionRepository repo;

    // コンストラクタ
    public R2dbcRolePermissionAdapter(
            RolePermissionRepository repo
    ) {
        this.repo = repo;
    }

    /**
     * ロールに紐づく権限コード一覧を取得
     */
    @Override
    public Flux<String> findPermissionsByRole(String roleCode) {

        // ロール未指定時の既定値を設定
        String resolvedRoleCode;

        // ロール未指定時は USER を設定
        resolvedRoleCode = Objects.requireNonNullElse(roleCode, "USER");

        // ロールに紐づく権限コード一覧を取得
        return repo.findPermissionsByRole(resolvedRoleCode);
    }
}