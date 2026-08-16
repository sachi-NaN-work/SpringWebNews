package com.news.recapp.application.port.out;

import reactor.core.publisher.Flux;

/**
 * ロール権限のポート
 */
public interface RolePermissionPort {

    /**
     * ロールに紐づく権限コード一覧を取得
     */
    Flux<String> findPermissionsByRole(String roleCode);
}
