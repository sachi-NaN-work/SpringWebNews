package com.news.recapp.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * カテゴリ情報を保持
 */
@Table("categories")
public class CategoryEntity implements Persistable<UUID> {

    // カテゴリ ID
    @Id
    @Column("category_id")
    private UUID id;

    // カテゴリの短縮識別子
    private String slug;

    // カテゴリ名
    @Column("category_name")
    private String name;

    // 有効フラグ
    private Boolean enabled;

    // 新規作成フラグ
    @Transient
    private boolean newEntity = false;

    // コンストラクタ
    public CategoryEntity() {
    }

    // コンストラクタ
    public CategoryEntity(
        UUID id,
        String slug,
        String name,
        Boolean enabled
    ) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.enabled = enabled;
        this.newEntity = true;
    }

    /**
     * カテゴリ情報を新規作成として扱う
     */
    public CategoryEntity markNew() {
        // 新規作成フラグを true に設定
        this.newEntity = true;
        // カテゴリ情報を返却
        return this;
    }

    /**
     * Persistable の ID を取得
     */
    @Override
    public UUID getId() {
        // カテゴリ ID を返却
        return id;
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
     * カテゴリ ID を設定
     */
    public void setId(UUID id) {
        // カテゴリ ID を設定
        this.id = id;
    }

    /**
     * カテゴリの短縮識別子を取得
     */
    public String getSlug() {
        // カテゴリの短縮識別子を返却
        return slug;
    }

    /**
     * カテゴリの短縮識別子を設定
     */
    public void setSlug(String slug) {
        // カテゴリの短縮識別子を設定
        this.slug = slug;
    }

    /**
     * カテゴリ名を取得
     */
    public String getName() {
        // カテゴリ名を返却
        return name;
    }

    /**
     * カテゴリ名を設定
     */
    public void setName(String name) {
        // カテゴリ名を設定
        this.name = name;
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
