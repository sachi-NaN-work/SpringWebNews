package com.news.recapp.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

/**
 * ユーザー情報を保持
 */
@Table("app_users")
public class AppUserEntity implements Persistable<String> {

    // ユーザー名
    @Id
    private String username;

    // パスワード（ハッシュ化済み）
    private String password;

    // ロール
    private String role;

    // 有効フラグ
    private Boolean enabled;

    // 新規作成フラグ
    @Transient
    private boolean newEntity = false;

    // コンストラクタ
    public AppUserEntity() {
    }

    // コンストラクタ
    public AppUserEntity(
        String username,
        String password,
        String role,
        Boolean enabled
    ) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.enabled = enabled;
    }

    /**
     * ユーザー情報を新規作成として扱う
     */
    public void markNew() {
        // 新規作成フラグを true に設定
        this.newEntity = true;
    }

    /**
     * Persistable の新規判定を返却
     */
    @Override
    public boolean isNew() {
        // 新規作成フラグを返却
        return newEntity;
    }

    /**
     * Persistable の ID としてユーザー名を返却
     */
    @Override
    public String getId() {
        // Persistable の ID としてユーザー名を返却
        return getUsername();
    }

    /**
     * ユーザー名を取得
     */
    public String getUsername() {
        // ユーザー名を返却
        return username;
    }

    /**
     * ユーザー名を設定
     */
    public void setUsername(String username) {
        // ユーザー名を設定
        this.username = username;
    }

    /**
     * パスワード（ハッシュ化済み）を取得
     */
    public String getPassword() {
        // パスワード（ハッシュ化済み）を返却
        return password;
    }

    /**
     * パスワード（ハッシュ化済み）を設定
     */
    public void setPassword(String password) {
        // パスワード（ハッシュ化済み）を設定
        this.password = password;
    }

    /**
     * ロールを取得
     */
    public String getRole() {
        // ロールを返却
        return role;
    }

    /**
     * ロールを設定
     */
    public void setRole(String role) {
        // ロールを設定
        this.role = role;
    }

    /**
     * 有効フラグを取得
     */
    public Boolean getEnabled() {
        // 有効フラグを返却
        return enabled;
    }

    /**
     * 有効フラグを設定
     */
    public void setEnabled(Boolean enabled) {
        // 有効フラグを設定
        this.enabled = enabled;
    }

}
