package com.news.recapp.presentation.security;

import com.news.recapp.application.port.out.AppUserPort;
import com.news.recapp.application.port.out.RolePermissionPort;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ユーザー情報と権限情報を取得して UserDetails を構築
 */
@Service
public class SpringSecurityUserDetailsAdapter implements ReactiveUserDetailsService {

    // 一般ユーザーのロールコードを保持
    private static final String ROLE_USER = "USER";
    // 管理者のロールコードを保持
    private static final String ROLE_ADMIN = "ADMIN";
    // ユーザー管理者のロールコードを保持
    private static final String ROLE_USER_MGR = "USER_MGR";
    // モデル管理者のロールコードを保持
    private static final String ROLE_MODEL_MGR = "MODEL_MGR";
    // ニュース作成者のロールコードを保持
    private static final String ROLE_NEWS_MGR = "NEWS_MGR";
    // デモ用のロールコードを保持
    private static final String ROLE_DEMO = "DEMO";

    // ユーザーポートを保持
    private final AppUserPort userPort;
    // ロール権限ポートを保持
    private final RolePermissionPort rolePermissionPort;

    // コンストラクタ
    public SpringSecurityUserDetailsAdapter(
            AppUserPort userPort,
            RolePermissionPort rolePermissionPort
    ) {
        this.userPort = userPort;
        this.rolePermissionPort = rolePermissionPort;
    }

    /**
     * ユーザー名から UserDetails を取得
     */
    @Override
    public Mono<UserDetails> findByUsername(String username) {

        // ユーザー情報を取得して権限を付与した UserDetails を返却
        return userPort.findById(username)
            // 未存在の場合は空を返却
            .switchIfEmpty(Mono.empty())
            // ユーザー情報から権限を解決して UserDetails を構築
            .flatMap(appUser -> {

                // ロールを正規化して取得
                String role = normalizeRole(appUser.getRole());

                // ロール権限一覧を取得して UserDetails を構築
                return permissionsForRole(role)
                    // 権限一覧をリストへ収集
                    .collectList()
                    // 収集した権限から UserDetails を生成
                    .map(perms -> {

                        // 付与する権限一覧を生成
                        List<GrantedAuthority> auths = new ArrayList<>();

                        // ロール権限を追加
                        auths.add(new SimpleGrantedAuthority("ROLE_" + role));

                        // 追加権限を順に付与
                        for (String permission : perms) {

                            // 空の権限コードは除外
                            if (permission == null || permission.isBlank()) {
                                // 除外して次へ進む
                                continue;
                            }

                            // 権限コードを追加
                            auths.add(new SimpleGrantedAuthority(permission));
                        }

                        // UserDetails を構築して返却
                        return User.withUsername(appUser.getUsername())
                            // パスワードハッシュを設定
                            .password(appUser.getPassword())
                            // 付与権限を設定
                            .authorities(auths)
                            // 無効ユーザーは disabled を設定
                            .disabled(appUser.getEnabled() != null && !appUser.getEnabled())
                            // 設定内容から生成
                            .build();
                    });
            });
    }

    /**
     * ロールコードから権限コード一覧を取得
     */
    private Flux<String> permissionsForRole(String role) {

        // 未指定ロールの既定値を解決
        String resolvedRole;

        // ロールが未指定の場合
        if (role == null) {
            // 既定ロールとして USER を設定
            resolvedRole = ROLE_USER;
        // ロールが指定されている場合
        } else {
            // 指定ロールをそのまま設定
            resolvedRole = role;
        }

        // ロールに紐づく権限コード一覧を返却
        return rolePermissionPort.findPermissionsByRole(resolvedRole);
    }

    /**
     * ロール名を正規化（既定ロール適用）
     */
    private static String normalizeRole(String role) {

        // ロールが未設定の場合
        if (role == null) {
            // 既定ロールを返却
            return ROLE_USER;
        }

        // 空白除去して大文字化
        String trimmedRole = role.trim().toUpperCase(Locale.ROOT);

        // 正規化後ロールを既知ロールに丸めて返却
        return switch (trimmedRole) {
            case ROLE_ADMIN -> ROLE_ADMIN;
            case ROLE_USER_MGR -> ROLE_USER_MGR;
            case ROLE_MODEL_MGR -> ROLE_MODEL_MGR;
            case ROLE_NEWS_MGR -> ROLE_NEWS_MGR;
            case ROLE_DEMO -> ROLE_DEMO;
            case ROLE_USER -> ROLE_USER;
            default -> ROLE_USER;
        };
    }

}
