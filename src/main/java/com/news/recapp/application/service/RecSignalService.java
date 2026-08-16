package com.news.recapp.application.service;

import com.news.recapp.application.port.out.RecSignalStorePort;
import com.news.recapp.persistence.entity.NewsEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * リアルタイム推薦用のシグナル更新/スナップショット取得をまとめたサービス
 *
 * -有効閲覧(view): 0.5秒閲覧（500ms）を「1回のview」として扱う
 *   -ユーザー×記事/ユーザー×カテゴリ・タグ/全体×記事を更新
 *   -（※）view の閾値は RecProperties#rtRec.viewMs で 500ms 固定（推論/学習の定義一致のため）
 * -お気に入り登録/お気に入り解除:ユーザー×カテゴリ・タグ+合計/全体×カテゴリ・タグ+合計を更新
 * -高評価/低評価:直前状態との差分で更新（高評価->低評価などを想定）
 */
@Service
public class RecSignalService {

    // 個別/全体のシグナルスナップショット
    public record RecSignalSnapshot(
        long totalViews,
        long totalFav,
        long totalGood,
        long totalBad,
        Map<UUID, Long> uCatView,
        Map<UUID, Long> uCatFav,
        Map<UUID, Long> uCatGood,
        Map<UUID, Long> uCatBad,
        Map<UUID, Long> uTagView,
        Map<UUID, Long> uTagFav,
        Map<UUID, Long> uTagGood,
        Map<UUID, Long> uTagBad,
        long gTotalFav,
        long gTotalGood,
        long gTotalBad,
        Map<UUID, Long> gCatFav,
        Map<UUID, Long> gCatGood,
        Map<UUID, Long> gCatBad,
        Map<UUID, Long> gTagFav,
        Map<UUID, Long> gTagGood,
        Map<UUID, Long> gTagBad
    ) {}

    // 集計ストアを保持
    private final RecSignalStorePort store;

    // A/B テスト判定を保持
    private final AbTestService abTestService;

    // コンストラクタ
    public RecSignalService(
        RecSignalStorePort store,
        AbTestService abTestService
    ) {
        this.store = store;
        this.abTestService = abTestService;
    }

    /**
     * 個別/全体のシグナルをまとめて取得（推薦1リクエスト内で再利用する想定）
     */
    public Mono<RecSignalSnapshot> loadSnapshot(String userId) {

        // ユーザー ID が取得できなかった場合
        if (userId == null || userId.isBlank() || "anonymous".equalsIgnoreCase(userId)) {
            // 初期値のシグナルスナップショットを返却
            return Mono.just(new RecSignalSnapshot(
                0L, 0L, 0L, 0L,
                Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(),
                0L, 0L, 0L,
                Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of()
            ));
        }

        // 合計閲覧数を取得
        Mono<Long> totalViews = store.getUserTotal(userId, "view");
        // 合計お気に入り数を取得
        Mono<Long> totalFav = store.getUserTotal(userId, "fav");
        // 合計高評価数を取得
        Mono<Long> totalGood = store.getUserTotal(userId, "good");
        // 合計低評価数を取得
        Mono<Long> totalBad = store.getUserTotal(userId, "bad");

        // ユーザーのカテゴリ別閲覧数を取得
        Mono<Map<UUID, Long>> uCatView = store.getUserCategoryCounts(userId, "view");
        // ユーザーのカテゴリ別お気に入り数を取得
        Mono<Map<UUID, Long>> uCatFav = store.getUserCategoryCounts(userId, "fav");
        // ユーザーのカテゴリ別高評価数を取得
        Mono<Map<UUID, Long>> uCatGood = store.getUserCategoryCounts(userId, "good");
        // ユーザーのカテゴリ別低評価数を取得
        Mono<Map<UUID, Long>> uCatBad = store.getUserCategoryCounts(userId, "bad");

        // ユーザーのハッシュタグ別閲覧数を取得
        Mono<Map<UUID, Long>> uTagView = store.getUserTagCounts(userId, "view");
        // ユーザーのハッシュタグ別お気に入り数を取得
        Mono<Map<UUID, Long>> uTagFav = store.getUserTagCounts(userId, "fav");
        // ユーザーのハッシュタグ別高評価数を取得
        Mono<Map<UUID, Long>> uTagGood = store.getUserTagCounts(userId, "good");
        // ユーザーのハッシュタグ別低評価数を取得
        Mono<Map<UUID, Long>> uTagBad = store.getUserTagCounts(userId, "bad");

        // 全体合計お気に入り数を取得
        Mono<Long> gTotalFav = store.getGlobalTotal("fav");
        // 全体合計高評価数を取得
        Mono<Long> gTotalGood = store.getGlobalTotal("good");
        // 全体合計低評価数を取得
        Mono<Long> gTotalBad = store.getGlobalTotal("bad");

        // 全体カテゴリ別お気に入り数を取得
        Mono<Map<UUID, Long>> gCatFav = store.getGlobalCategoryCounts("fav");
        // 全体カテゴリ別高評価数を取得
        Mono<Map<UUID, Long>> gCatGood = store.getGlobalCategoryCounts("good");
        // 全体カテゴリ別低評価数を取得
        Mono<Map<UUID, Long>> gCatBad = store.getGlobalCategoryCounts("bad");

        // 全体ハッシュタグ別お気に入り数を取得
        Mono<Map<UUID, Long>> gTagFav = store.getGlobalTagCounts("fav");
        // 全体ハッシュタグ別高評価数を取得
        Mono<Map<UUID, Long>> gTagGood = store.getGlobalTagCounts("good");
        // 全体ハッシュタグ別低評価数を取得
        Mono<Map<UUID, Long>> gTagBad = store.getGlobalTagCounts("bad");

        // シグナル集計結果をスナップショットへまとめて返却
        return Mono.zip(values -> new RecSignalSnapshot(
                (Long) values[0],
                (Long) values[1],
                (Long) values[2],
                (Long) values[3],
                castMap(values[4]),
                castMap(values[5]),
                castMap(values[6]),
                castMap(values[7]),
                castMap(values[8]),
                castMap(values[9]),
                castMap(values[10]),
                castMap(values[11]),
                (Long) values[12],
                (Long) values[13],
                (Long) values[14],
                castMap(values[15]),
                castMap(values[16]),
                castMap(values[17]),
                castMap(values[18]),
                castMap(values[19]),
                castMap(values[20])
            ),
            totalViews, totalFav, totalGood, totalBad,
            uCatView, uCatFav, uCatGood, uCatBad,
            uTagView, uTagFav, uTagGood, uTagBad,
            gTotalFav, gTotalGood, gTotalBad,
            gCatFav, gCatGood, gCatBad,
            gTagFav, gTagGood, gTagBad
        );
    }

    /**
     * マップ型へ変換
     */
    private static Map<UUID, Long> castMap(Object value) {

        // Map 以外の場合は空 Map を返却
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }

        // UUID と件数のマップへ変換する。
        Map<UUID, Long> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() instanceof UUID key && entry.getValue() instanceof Number count) {
                result.put(key, count.longValue());
            }
        }

        // 変換結果を返却
        return Map.copyOf(result);
    }

    /**
     * 記事閲覧（有効閲覧）を記録。
     *
     * 閾値はクライアント側の「rec.rt-rec.view-ms」で判定済みである前提。
     */
    public Mono<Void> recordView(String userId, NewsEntity news, List<UUID> tagIds) {

        // ユーザー ID が取得できなかった場合
        if (userId == null || userId.isBlank() || "anonymous".equalsIgnoreCase(userId)) {
            // 値なしで返却
            return Mono.empty();
        }

        // ニュース記事 ID が取得できなかった場合
        if (news == null || news.getId() == null) {
            // 値なしで返却
            return Mono.empty();
        }

        // ニュース ID を取得
        UUID newsId = news.getId();
        // カテゴリ ID を取得
        UUID catId = news.getCategoryId();

        // ユーザー別ニュース閲覧数を加算
        Mono<Void> viewMono = store.incrUserNewsView(userId, newsId, 1).then();
        // ユーザー合計閲覧数を加算
        viewMono = viewMono.then(store.incrUserTotal(userId, "view", 1)).then();

        // カテゴリ ID が取得できた場合
        if (catId != null) {
            // ユーザーのカテゴリ別閲覧数を加算
            viewMono = viewMono.then(store.incrUserCategory(userId, catId, "view", 1)).then();
        }

        // ハッシュタグ ID 一覧が取得できた場合
        if (tagIds != null) {
            // ハッシュタグ ID 一覧を順に処理
            for (UUID tagId : tagIds) {

                // ハッシュタグ ID が取得できなかった場合
                if (tagId == null) {
                    // ハッシュタグ ID が取得できなかった場合、スキップ
                    continue;
                }

                // ユーザーのハッシュタグ別閲覧数を加算
                viewMono = viewMono.then(store.incrUserTag(userId, tagId, "view", 1)).then();
            }
        }

        // 全体ニュース閲覧数を加算（上限 11）
        // A/B 別の全体閲覧数で exposure を揃える
        String resolvedAb = abTestService.resolveAb(userId, null);
        viewMono = viewMono.then(store.incrGlobalNewsViewCapped11(resolvedAb, newsId, 1)).then();

        // 閲覧記録処理を返却
        return viewMono;
    }

    /**
     * お気に入りの登録/解除を記録
     */
    public Mono<Void> recordFavoriteToggle(String userId, NewsEntity news, List<UUID> tagIds, boolean isFavorite) {

        // ユーザー ID が取得できなかった場合
        if (userId == null || userId.isBlank() || "anonymous".equalsIgnoreCase(userId)) {
            // 値なしで返却
            return Mono.empty();
        }

        // ニュース記事が取得できなかった場合
        if (news == null || news.getId() == null) {
            // 値なしで返却
            return Mono.empty();
        }

        // お気に入り登録/解除の増減量を取得
        long delta = isFavorite
                ? 1L
                : -1L;
        // カテゴリ ID を取得
        UUID catId = news.getCategoryId();

        // お気に入り数の合計を更新（ログインユーザー）
        Mono<Void> favMono = store.incrUserTotal(userId, "fav", delta).then();
        // お気に入り数の合計を更新（全ユーザー）
        favMono = favMono.then(store.incrGlobalTotal("fav", delta)).then();

        // カテゴリ ID が取得できた場合
        if (catId != null) {
            // カテゴリ毎のお気に入り数の合計を更新（ログインユーザー）
            favMono = favMono.then(store.incrUserCategory(userId, catId, "fav", delta)).then();
            // カテゴリ毎のお気に入り数の合計を更新（全ユーザー）
            favMono = favMono.then(store.incrGlobalCategory("fav", catId, delta)).then();
        }

        // ハッシュタグ ID 一覧が取得できた場合
        if (tagIds != null) {
            // ハッシュタグ ID 一覧を順に処理
            for (UUID tagId : tagIds) {

                // ハッシュタグ ID が取得できなかった場合
                if (tagId == null) {
                    // ハッシュタグ ID が取得できなかった場合、スキップ
                    continue;
                }

                // ハッシュタグ毎のお気に入り数の合計を更新（ログインユーザー）
                favMono = favMono.then(store.incrUserTag(userId, tagId, "fav", delta)).then();
                // ハッシュタグ毎のお気に入り数の合計を更新（全ユーザー）
                favMono = favMono.then(store.incrGlobalTag("fav", tagId, delta)).then();
            }
        }

        // お気に入り集計更新処理を返却
        return favMono;
    }

    /**
     * リアクション変更（高評価/低評価）を記録
     */
    public Mono<Void> recordReactionChange(String userId, NewsEntity news, List<UUID> tagIds, String prev, String next) {

        // ユーザー ID が取得できなかった場合
        if (userId == null || userId.isBlank() || "anonymous".equalsIgnoreCase(userId)) {
            // 値なしで返却
            return Mono.empty();
        }

        // ニュース記事 ID が取得できなかった場合
        if (news == null || news.getId() == null) {
            // 値なしで返却
            return Mono.empty();
        }

        // 変更前リアクション種別を小文字化して取得
        String previousReaction = (prev == null)
                ? ""
                : prev.toLowerCase();
        // 変更後リアクション種別を小文字化して取得
        String nextReaction = (next == null)
                ? ""
                : next.toLowerCase();

        // 変更後リアクション種別が空でなく、かつ高評価/低評価以外の場合
        if (!"good".equals(nextReaction) && !"bad".equals(nextReaction) && !nextReaction.isEmpty()) {
            // 値なしで返却
            return Mono.empty();
        }

        // 変更前後のリアクション種別が同一の場合
        if (previousReaction.equals(nextReaction)) {
            // 値なしで返却
            return Mono.empty();
        }

        // 高評価の増減量を初期化
        long goodDelta = 0L;
        // 低評価の増減量を初期化
        long badDelta = 0L;

        // 変更前リアクション種別が高評価の場合
        if ("good".equals(previousReaction)) {
            // 高評価数を 1 減算
            goodDelta -= 1L;
        }
        // 変更前リアクション種別が低評価の場合
        if ("bad".equals(previousReaction)) {
            // 低評価数を 1 減算
            badDelta -= 1L;
        }
        // 変更後リアクション種別が高評価の場合
        if ("good".equals(nextReaction)) {
            // 高評価数を 1 加算
            goodDelta += 1L;
        }
        // 変更後リアクション種別が低評価の場合
        if ("bad".equals(nextReaction)) {
            // 低評価数を 1 加算
            badDelta += 1L;
        }

        // カテゴリ ID を取得
        UUID catId = news.getCategoryId();

        // リアクション集計更新処理を初期化
        Mono<Void> reactionMono = Mono.empty();

        // 高評価の増減量が 0 でない場合
        if (goodDelta != 0L) {
            // ユーザー合計高評価数を更新
            reactionMono = reactionMono.then(store.incrUserTotal(userId, "good", goodDelta)).then();
            // 全体合計高評価数を更新
            reactionMono = reactionMono.then(store.incrGlobalTotal("good", goodDelta)).then();
        }

        // 低評価の増減量が 0 でない場合
        if (badDelta != 0L) {
            // ユーザー合計低評価数を更新
            reactionMono = reactionMono.then(store.incrUserTotal(userId, "bad", badDelta)).then();
            // 全体合計低評価数を更新
            reactionMono = reactionMono.then(store.incrGlobalTotal("bad", badDelta)).then();
        }

        // カテゴリ ID が取得できた場合
        if (catId != null) {

            // 高評価の増減量が 0 でない場合
            if (goodDelta != 0L) {
                // ユーザーのカテゴリ別高評価数を更新
                reactionMono = reactionMono.then(store.incrUserCategory(userId, catId, "good", goodDelta)).then();
                // 全体カテゴリ別高評価数を更新
                reactionMono = reactionMono.then(store.incrGlobalCategory("good", catId, goodDelta)).then();
            }

            // 低評価の増減量が 0 でない場合
            if (badDelta != 0L) {
                // ユーザーのカテゴリ別低評価数を更新
                reactionMono = reactionMono.then(store.incrUserCategory(userId, catId, "bad", badDelta)).then();
                // 全体カテゴリ別低評価数を更新
                reactionMono = reactionMono.then(store.incrGlobalCategory("bad", catId, badDelta)).then();
            }
        }

        // ハッシュタグ ID 一覧が取得できた場合
        if (tagIds != null) {
            // ハッシュタグ ID 一覧を順に処理
            for (UUID tagId : tagIds) {

                // ハッシュタグ ID が取得できなかった場合
                if (tagId == null) {
                    // ハッシュタグ ID が取得できなかった場合、スキップ
                    continue;
                }

                // 高評価の増減量が 0 でない場合
                if (goodDelta != 0L) {
                    // ユーザーのハッシュタグ別高評価数を更新
                    reactionMono = reactionMono.then(store.incrUserTag(userId, tagId, "good", goodDelta)).then();
                    // 全体ハッシュタグ別高評価数を更新
                    reactionMono = reactionMono.then(store.incrGlobalTag("good", tagId, goodDelta)).then();
                }

                // 低評価の増減量が 0 でない場合
                if (badDelta != 0L) {
                    // ユーザーのハッシュタグ別低評価数を更新
                    reactionMono = reactionMono.then(store.incrUserTag(userId, tagId, "bad", badDelta)).then();
                    // 全体ハッシュタグ別低評価数を更新
                    reactionMono = reactionMono.then(store.incrGlobalTag("bad", tagId, badDelta)).then();
                }
            }
        }

        // リアクション集計更新処理を返却
        return reactionMono;
    }
}
