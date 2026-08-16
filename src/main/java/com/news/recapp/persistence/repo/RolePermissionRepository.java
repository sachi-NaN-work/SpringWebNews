package com.news.recapp.persistence.repo;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * ロール権限情報の DB 操作を提供
 */
@Repository
public class RolePermissionRepository {

    // SQL 実行用クライアントを保持
    private final DatabaseClient db;

    // コンストラクタ
    public RolePermissionRepository(
            DatabaseClient db
    ) {
        this.db = db;
    }

    /**
     * ロールに紐づく権限コード一覧を取得
     */
    public Flux<String> findPermissionsByRole(String roleCode) {

        // ロールに紐づく権限コード一覧を取得
        return db.sql(
                        "SELECT permission_code FROM role_permissions WHERE role_code = $1"
                )
                // ロールコードをバインド
                .bind(0, roleCode)
                // 権限コードを取得
                .map((row, meta) ->
                        row.get("permission_code", String.class)
                )
                // 結果を Flux として返却
                .all();
    }
}
