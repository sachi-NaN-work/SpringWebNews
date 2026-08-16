package com.news.recapp.application.service;

import com.news.recapp.application.exception.AppMessageException;
import com.news.recapp.application.port.out.HashtagPort;
import com.news.recapp.persistence.entity.HashtagEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ハッシュタグ（マスタ）および選択内容の検証を提供
 * ※内容のみで検証すると、将来別経路で紐付け処理が追加された際に
 * 「未登録タグの混入」や「無効タグの混入」が起きやすいため、業務層で吸収する。
 */
@Service
public class HashtagAdminService {

    // ハッシュタグを保持
    private final HashtagPort hashtagPort;

    // コンストラクタ
    public HashtagAdminService(
        HashtagPort hashtagPort
    ) {
        this.hashtagPort = hashtagPort;
    }

    /**
     * 全ハッシュタグ一覧（名前順）
     */
    public Flux<HashtagEntity> listAll() {
        return hashtagPort.findAllOrderByNameAsc();
    }

    /**
     * 有効なハッシュタグ一覧（名前順）
     */
    public Flux<HashtagEntity> listEnabled() {
        return hashtagPort.findAllEnabledOrderByNameAsc();
    }

    /**
     * ハッシュタグを新規登録（重複はエラー）
     */
    public Mono<HashtagEntity> create(String name) {

        // ハッシュタグ名を正規化
        String normalizeName = getNormalizeName(name);

        // ハッシュタグ名が取得できなかった場合
        if (normalizeName.isBlank()) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("hashtag.master.err.required"));
        }

        // ハッシュタグ名が最大文字数を超える場合
        if (normalizeName.length() > 10) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException(
                    "hashtag.master.err.too_long",
                    10
            ));
        }

        // ハッシュタグ名（大文字小文字無視）を取得して返却
        return hashtagPort.findByNameIgnoreCase(normalizeName)
                // ハッシュタグ名が重複する場合、エラーメッセージを付与して返却
                .flatMap(existing ->
                        Mono.<HashtagEntity>error(new AppMessageException("hashtag.master.err.duplicate")))
                // 値が取得できなかった場合、エラーメッセージを付与して返却
                .switchIfEmpty(Mono.defer(() ->
                        hashtagPort.save(new HashtagEntity(UUID.randomUUID(), normalizeName, Boolean.TRUE))
                ));
    }

    /**
     * ハッシュタグの有効/無効を切替
     */
    public Mono<Void> setEnabled(UUID hashtagId, boolean enabled) {

        // ハッシュタグ ID が取得できなかった場合
        if (hashtagId == null) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("hashtag.master.err.invalid_id"));
        }

        // ハッシュタグ有効フラグを取得
        Mono<Void> guard = enabled
                // 空の Mono を作成して返却
                ? Mono.empty()
                // ハッシュタグ使用有無判定
                : hashtagPort.existsInAnyNews(hashtagId)
                // ハッシュタグ名が重複する場合、エラーメッセージを付与して返却
                .flatMap(exists -> exists
                        ? Mono.error(new AppMessageException("hashtag.master.err.disable_in_use"))
                        : Mono.empty()
                );

        // ハッシュタグ情報を取得して返却
        return guard.then(Mono.defer(() -> hashtagPort.findById(hashtagId)
                // ハッシュタグ情報が取得できなかった場合、エラーメッセージを付与して返却
                .switchIfEmpty(Mono.error(new AppMessageException("hashtag.master.err.not_found")))
                // ハッシュタグ
                .flatMap(hashtagEntity -> {
                    // ハッシュタグの有効フラグを設定
                    hashtagEntity.setEnabled(enabled);
                    // ハッシュタグ情報を保存して返却
                    return hashtagPort.save(hashtagEntity).then();
                })
        ));
    }

    /**
     * 選択されたハッシュタグ識別子が「全て、登録済みかつ有効」であることを検証
     */
    public Mono<List<UUID>> validateEnabledHashtagIds(List<UUID> hashtagIds) {

        // ハッシュタグ ID 一覧が取得できなかった場合
        if (hashtagIds == null || hashtagIds.isEmpty()) {
            // 空のリスト Mono を返却
            return Mono.just(List.of());
        }

        // ハッシュタグ ID 一覧を UUID 型に変換
        Set<UUID> deduped = new HashSet<>(hashtagIds);

        // ハッシュタグ ID 一覧
        return listEnabled()
                // ハッシュタグ ID を取得
                .map(HashtagEntity::getId)
                // リストにまとめる
                .collectList()
                // ハッシュタグ ID 有効一覧
                .flatMap(enabledIds -> {

                    // ハッシュタグ有効一覧を UUID 型に変換
                    Set<UUID> enabledSet = new HashSet<>(enabledIds);

                    // 全ハッシュタグが有効か判定
                    boolean allEnabled = enabledSet.containsAll(deduped);

                    // 全ハッシュタグが有効でない場合
                    if (!allEnabled) {
                        // エラーメッセージを付与して返却
                        return Mono.error(new AppMessageException("hashtag.master.err.invalid_selection"));
                    }

                    // ハッシュタグ ID 一覧のリストを付与して返却
                    return Mono.just(deduped.stream().toList());
                });
    }

    /**
     * ハッシュタグ名を正規化（先頭 # を除去・前後空白を除去）
     *
     * ※DB 側は文字列をそのまま保存するため、seed/初期データも同じルール（#なし・前後空白なし）で統一する。
     */
    private String getNormalizeName(String name) {

        // ハッシュタグ名が取得できなかった場合
        if (name == null) {
            // 空文字の返却
            return "";
        }

        // ハッシュタグ名をトリムして取得
        String trimName = name.trim();

        // ハッシュタグ名が # から始まる場合
        if (trimName.startsWith("#")) {
            // ハッシュタグ名から # を除去
            trimName = trimName.substring(1).trim();
        }

        // ハッシュタグ名の返却
        return trimName;
    }
}
