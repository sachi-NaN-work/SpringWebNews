package com.news.recapp.application.port.out;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * リアルタイム推薦で利用するシグナル（閲覧/お気に入り/高評価/低評価等）を
 * インメモリデータストア等へ保存・取得するためのポート
 */
public interface RecSignalStorePort {

    /**
     * ユーザー×記事の閲覧回数（生値）を加算
     */
    Mono<Long> incrUserNewsView(String userId, UUID newsId, long delta);

    /**
     * ユーザー×記事の閲覧回数（生値）を一括取得（未設定は0）
     */
    Mono<List<Long>> getUserNewsViews(String userId, List<UUID> newsIds);

    /**
     * ユーザー×カテゴリのカウントを加算（種別=閲覧/お気に入り/高評価/低評価）
     */
    Mono<Long> incrUserCategory(String userId, UUID categoryId, String metric, long delta);

    /**
     * ユーザー×タグのカウントを加算（種別=閲覧/お気に入り/高評価/低評価）
     */
    Mono<Long> incrUserTag(String userId, UUID tagId, String metric, long delta);

    /**
     * ユーザー合計カウントを加算（種別=閲覧/お気に入り/高評価/低評価）
     */
    Mono<Long> incrUserTotal(String userId, String metric, long delta);

    /**
     * ユーザー×カテゴリのカウント一覧を取得
     */
    Mono<Map<UUID, Long>> getUserCategoryCounts(String userId, String metric);

    /**
     * ユーザー×タグのカウント一覧を取得
     */
    Mono<Map<UUID, Long>> getUserTagCounts(String userId, String metric);

    /**
     * ユーザー合計カウントを取得
     */
    Mono<Long> getUserTotal(String userId, String metric);
    
    /**
     * 全体×記事閲覧（A/B グループ別、内部で11に飽和）を加算
     *
     * A/B で露出（exposure）定義を揃えるため、推論/学習で同じグループ内カウントを使う。
     */
    Mono<Long> incrGlobalNewsViewCapped11(String abGroup, UUID newsId, long delta);

    /**
     * 全体×記事閲覧（A/B グループ別、内部で11に飽和）を一括取得
     */
    Mono<List<Long>> getGlobalNewsViewsCapped11(String abGroup, List<UUID> newsIds);

    /**
     * 全体×カテゴリのカウントを加算（種別=お気に入り/高評価/低評価）
     */
    Mono<Long> incrGlobalCategory(String metric, UUID categoryId, long delta);

    /**
     * 全体×タグのカウントを加算（種別=お気に入り/高評価/低評価）
     */
    Mono<Long> incrGlobalTag(String metric, UUID tagId, long delta);

    /**
     * 全体合計カウントを加算（種別=お気に入り/高評価/低評価）
     */
    Mono<Long> incrGlobalTotal(String metric, long delta);

    /**
     * 全体×カテゴリのカウント一覧を取得
     */
    Mono<Map<UUID, Long>> getGlobalCategoryCounts(String metric);

    /**
     * 全体×タグのカウント一覧を取得
     */
    Mono<Map<UUID, Long>> getGlobalTagCounts(String metric);

    /**
     * 全体合計カウントを取得
     */
    Mono<Long> getGlobalTotal(String metric);
}
