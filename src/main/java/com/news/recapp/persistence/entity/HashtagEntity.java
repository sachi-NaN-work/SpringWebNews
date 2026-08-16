package com.news.recapp.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * ハッシュタグ情報を保持
 */
@Table("hashtags")
public class HashtagEntity implements Persistable<UUID> {

    // ハッシュタグ ID
    @Id
    @Column("hashtag_id")
    private UUID id;

    // ハッシュタグ名
    @Column("hashtag_name")
    private String name;

    // 有効フラグ
    private Boolean enabled;

    // 新規作成フラグ
    @Transient
    private boolean newEntity = false;

    // コンストラクタ
    public HashtagEntity() {
    }

    // コンストラクタ
    public HashtagEntity(
        UUID id,
        String name,
        Boolean enabled
    ) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
        this.newEntity = true;
    }

    /**
     * ハッシュタグ情報を新規作成として扱う
     */
    public HashtagEntity markNew() {
        // 新規作成フラグを true に設定
        this.newEntity = true;
        // ハッシュタグ情報を返却
        return this;
    }

    /**
     * Persistable の ID を取得
     */
    @Override
    public UUID getId() {
        // ハッシュタグ ID を返却
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
     * ハッシュタグ ID を設定
     */
    public void setId(UUID id) {
        // ハッシュタグ ID を設定
        this.id = id;
    }

    /**
     * ハッシュタグ名を取得
     */
    public String getName() {
        // ハッシュタグ名を返却
        return name;
    }

    /**
     * ハッシュタグ名を設定
     */
    public void setName(String name) {
        // ハッシュタグ名を設定
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
