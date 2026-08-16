package com.news.recapp.application.service;

import com.news.recapp.application.port.out.HashtagPort;
import com.news.recapp.application.port.out.NewsHashtagPort;
import com.news.recapp.application.port.out.NewsPort;
import com.news.recapp.config.RecProperties;
import com.news.recapp.persistence.entity.HashtagEntity;
import com.news.recapp.persistence.entity.NewsEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推薦候補の生成
 * 方針（時間は使わない前提）
 * -個別候補:上位カテゴリ/タグ由来+探索ランダムを混ぜて合計500件程度
 * -全体候補:全体品質スコア（全体お気に入り/高評価/低評価の合成）上位500件
 */
@Service
public class CandidateService {

    // 個別/全体の推薦種別
    public enum SourceType { PERSONAL, GLOBAL }
    // 個別/全体の推薦候補
    public record Candidate(NewsEntity news, SourceType source, double globalQuality) {}
    // 個別/全体の候推薦補プール
    public record CandidatePools(List<Candidate> personal, List<Candidate> global) {}

    // ニュース記事を保持
    private final NewsPort newsPort;
    // ニュース記事ハッシュタグを保持
    private final NewsHashtagPort newsHashtagPort;
    // ハッシュタグを保持
    private final HashtagPort hashtagPort;
    // リアルタイム推薦設定を保持
    private final RecProperties props;
    // カテゴリ候補に必要な最小件数
    private static final long CATEGORY_MIN_COUNT = 3L;
    // カテゴリ候補の事前母数
    private static final double CATEGORY_PRIOR_TOTAL = 20.0;
    // カテゴリ候補の最小補正比率
    private static final double CATEGORY_MIN_SCORE = 0.20;
    // 全体候補スコアのカテゴリ重み
    private static final double GLOBAL_CATEGORY_WEIGHT = 0.15;
    // 全体候補スコアのハッシュタグ重み
    private static final double GLOBAL_TAG_WEIGHT = 0.85;
    // 全体品質に必要な最小根拠数
    private static final long GLOBAL_QUALITY_MIN_EVIDENCE = 1L;
    // 全体品質の信頼度補正母数
    private static final double GLOBAL_QUALITY_PRIOR_TOTAL = 20.0;

    // コンストラクタ
    public CandidateService(
            NewsPort newsPort,
            NewsHashtagPort newsHashtagPort,
            HashtagPort hashtagPort,
            RecProperties props
    ) {
        this.newsPort = newsPort;
        this.newsHashtagPort = newsHashtagPort;
        this.hashtagPort = hashtagPort;
        this.props = props;
    }

    /**
     * 個別/全体の候補プールを取得
     */
    public Mono<CandidatePools> generatePools(String userId, RecSignalService.RecSignalSnapshot snap) {

        // 個別候補一覧を取得
        Mono<List<Candidate>> personalMono = buildPersonalCandidates(userId, snap);
        // 全体候補一覧を取得
        Mono<List<Candidate>> globalMono = buildGlobalCandidates(snap);

        // 個別/全体候補一覧を返却
        return Mono.zip(personalMono, globalMono)
                // 個別/全体の候推薦補プールへ変換
                .map(tuple -> new CandidatePools(tuple.getT1(), tuple.getT2()));
    }

    /**
     * リアルタイム推薦のパラメータ群
     */
    private RecProperties.RtRec recProps() {
        return props.getRtRec();
    }

    /**
     * 個別候補:上位カテゴリ/タグ+探索ランダム
     */
    private Mono<List<Candidate>> buildPersonalCandidates(String userId, RecSignalService.RecSignalSnapshot snap) {

        // 個別推薦の候補サイズを取得
        int target = recProps().getPersonalCandidateSize();

        // ユーザー ID が取得できなかった場合
        if (userId == null || userId.isBlank() || "anonymous".equalsIgnoreCase(userId) || snap.totalViews() <= 0L) {

            // 表示可能ニュースをランダムで返却
            return newsPort.findVisibleRandom(target)
                    // ニュースオブジェクトへ変換
                    .map(newsEntity -> new Candidate(newsEntity, SourceType.PERSONAL, 0.0))
                    // リストにまとめる
                    .collectList();
        }

        // 上位カテゴリを取得
        List<UUID> topCats = topKeysByShare(snap.uCatView(), snap.totalViews(), recProps().getPersonalTopCategories(), CATEGORY_MIN_COUNT, CATEGORY_PRIOR_TOTAL, CATEGORY_MIN_SCORE);
        // 上位カテゴリ
        Mono<List<NewsEntity>> catsMono = Flux.fromIterable(topCats)
                // カテゴリ別にランダムニュース一覧を取得
                .concatMap(catId -> newsPort.findVisibleRandomByCategory(catId, recProps().getPerCategoryTake()).collectList())
                // カテゴリ別のランダムニュース一覧を平坦化
                .flatMapIterable(list -> list)
                // リストにまとめる
                .collectList();

        // 上位ハッシュタグを取得
        List<UUID> topTags = topKeysByShare(snap.uTagView(), snap.totalViews(), recProps().getPersonalTopTags());
        // 上位ハッシュタグ名
        Mono<List<String>> tagNamesMono =
                Flux.fromIterable(topTags)
                        // ハッシュタグ情報を取得
                        .concatMap(hashtagPort::findById)
                        // ハッシュタグ名を取得
                        .map(HashtagEntity::getName)
                        // 空でない名称のみ抽出
                        .filter(name -> name != null && !name.isBlank())
                        // リストにまとめる
                        .collectList();

        // ハッシュタグ
        Mono<List<NewsEntity>> tagsMono =
                tagNamesMono
                        // ハッシュタグ名一覧を、ハッシュタグ別のニュース一覧の Flux に展開
                        .flatMapMany(names -> Flux.fromIterable(names)
                                // ハッシュタグ名別にランダムニュース一覧を取得
                                .concatMap(name -> newsHashtagPort.findVisibleRandomNews(
                                        name, recProps().getPerTagTake()).collectList()
                                ))
                        // ハッシュタグ名別のランダムニュース一覧を平坦化（Flux<List<NewsEntity>> を Flux<NewsEntity> に変換）
                        .flatMapIterable(list -> list)
                        // リストにまとめる
                        .collectList();

        // 表示可能ニュースをランダムに取得
        Mono<List<NewsEntity>> exploreMono =
                newsPort.findVisibleRandom(recProps().getExploreRandom())
                        // リストにまとめる
                        .collectList();

        // 各ニュース情報をまとめる
        return Mono.zip(catsMono, tagsMono, exploreMono)
                .flatMap(tuple -> {

                    // 重複しないニュースを収集し、順序を保ちながらニュースを格納
                    LinkedHashMap<UUID, NewsEntity> uniq = new LinkedHashMap<>();
                    // ハッシュタグ/探索/カテゴリの順に均衡投入
                    putBalanced(uniq, target, tuple.getT1(), tuple.getT2(), tuple.getT3());

                    // 不足分を取得
                    int missing = Math.max(0, target - uniq.size());
                    // 不足分が 0 の場合
                    if (missing == 0) {
                        // ニュース一覧を個別候補一覧へ変換して返却
                        return Mono.just(toCandidates(uniq.values(), SourceType.PERSONAL));
                    }

                    // 表示可能ニュースをランダムに取得
                    return newsPort.findVisibleRandom(missing)
                            // 有効なニュースを重複排除マップへ追加
                            .doOnNext(newsEntity -> putIfValid(uniq, newsEntity))
                            // ニュース一覧を個別候補一覧へ変換して返却
                            .then(Mono.fromSupplier(() ->
                                    toCandidates(uniq.values(), SourceType.PERSONAL)
                            ));
                });
    }

    /**
     * 全体候補:全体品質スコア上位
     */
    private Mono<List<Candidate>> buildGlobalCandidates(RecSignalService.RecSignalSnapshot snap) {

        // グローバル推薦の候補サイズを取得
        int target = recProps().getGlobalCandidateSize();
        // グローバル推薦のスキャン上限を取得
        int scanLimit = recProps().getGlobalScanLimit();

        // 全ニュース一覧を新しい順に取得
        Flux<NewsEntity> base = newsPort.findAllVisible();
        // グローバル推薦のスキャン上限が 0 以上の場合、グローバル推薦のスキャン上限を設定
        if (scanLimit > 0) base = base.take(scanLimit);

        // 全ニュース一覧
        return base
                // 全体品質スコアを算出
                .flatMap(news -> computeGlobalQuality(news, snap)
                        // ニュースを候補へ変換
                        .map(globalQualityScore -> new Candidate(news, SourceType.GLOBAL, globalQualityScore)), recProps().getGlobalQualityConcurrency())
                // リストにまとめる
                .collectList()
                // 候補一覧を並び替えて上位件数へ制限
                .map(list -> list.stream()
                        // スコア降順で並び替え
                        .sorted(Comparator.comparingDouble(Candidate::globalQuality).reversed())
                        // 上位件数へ制限
                        .limit(target)
                        // リストにまとめる
                        .collect(Collectors.toList()));
    }

    /**
     * 全体品質スコアを算出
     */
    private Mono<Double> computeGlobalQuality(NewsEntity news, RecSignalService.RecSignalSnapshot snap) {

        // ニュースが取得できなかった場合
        if (news == null || news.getId() == null) {
            // 0.0 の Mono を返却
            return Mono.just(0.0);
        }

        // カテゴリ ID を取得
        UUID catId = news.getCategoryId();
        // クエリカテゴリを取得
        double qCat = qualityOfCategory(catId, snap);

        // ニュース ID に紐づく有効なハッシュタグ一覧を取得
        return newsHashtagPort.findEnabledHashtags(news.getId())
                // ハッシュタグ ID を取得
                .map(HashtagEntity::getId)
                // リストにまとめる
                .collectList()
                // ハッシュタグ品質の平均を算出
                .map(tagIds -> {
                    // クエリハッシュタグ平均を取得
                    double qTagAvg = 0.0;
                    // ハッシュタグ ID 一覧が取得できた場合
                    if (tagIds != null && !tagIds.isEmpty()) {
                        // 合計を取得
                        double sum = 0.0;
                        // 件数を取得
                        int cnt = 0;
                        // ハッシュタグ ID 一覧を順に処理
                        for (UUID tid : tagIds) {
                            // ハッシュタグ ID が取得できなかった場合
                            if (tid == null) {
                                continue;
                            }
                            // 合計を更新
                            sum += qualityOfTag(tid, snap);
                            // 件数を更新
                            cnt++;
                        }
                        // 件数が取得できた場合、クエリハッシュタグ平均を更新
                        if (cnt > 0) qTagAvg = sum / cnt;
                    }

                    // 全体品質スコアを返却
                    return (GLOBAL_CATEGORY_WEIGHT * qCat) + (GLOBAL_TAG_WEIGHT * qTagAvg);
                });
    }
    /**
     * カテゴリ品質スコアを算出
     */
    private double qualityOfCategory(UUID catId, RecSignalService.RecSignalSnapshot snap) {

        // カテゴリ ID が取得できなかった場合
        if (catId == null) {
            // 0.0 を返却
            return 0.0;
        }

        // お気に入りを取得
        long fav = snap.gCatFav().getOrDefault(catId, 0L);
        // 高評価 件数を取得
        long good = snap.gCatGood().getOrDefault(catId, 0L);
        // 低評価 件数を取得
        long bad = snap.gCatBad().getOrDefault(catId, 0L);

        // カテゴリ品質スコアを返却
        return qualityScore(fav, good, bad, snap.gTotalFav());
    }
    /**
     * ハッシュタグ品質スコアを算出
     */
    private double qualityOfTag(UUID tagId, RecSignalService.RecSignalSnapshot snap) {

        // ハッシュタグ ID が取得できなかった場合
        if (tagId == null) {
            // 0.0 を返却
            return 0.0;
        }

        // お気に入りを取得
        long fav = snap.gTagFav().getOrDefault(tagId, 0L);
        // 高評価の件数を取得
        long good = snap.gTagGood().getOrDefault(tagId, 0L);
        // 低評価の件数を取得
        long bad = snap.gTagBad().getOrDefault(tagId, 0L);

        // ハッシュタグ品質スコアを返却
        return qualityScore(fav, good, bad, snap.gTotalFav());
    }

    /**
     * 全体品質スコアを算出
     */
    private double qualityScore(long fav, long good, long bad, long totalFav) {

        // 根拠数を取得
        long evidence = Math.max(0L, fav) + Math.max(0L, good) + Math.max(0L, bad);
        // 根拠数が不足している場合
        if (evidence < GLOBAL_QUALITY_MIN_EVIDENCE) {
            // 0.0 を返却
            return 0.0;
        }

        // お気に入り比率を取得
        double favShare = (double) Math.max(0L, fav) / (double) Math.max(totalFav, 1L);
        // 高評価の比率を取得
        double goodRatio = (double) (Math.max(0L, good) + 1L) / (double) (Math.max(0L, good) + Math.max(0L, bad) + 2L);
        // 根拠数に応じた信頼度を取得
        double confidence = (double) evidence / ((double) evidence + GLOBAL_QUALITY_PRIOR_TOTAL);

        // 全体品質スコアを返却
        return ((0.4 * favShare) + (0.6 * goodRatio)) * confidence;
    }

    /**
     * 比率上位キーを抽出
     */
    private static List<UUID> topKeysByShare(Map<UUID, Long> counts, long total, int topN) {

        // 比率上位キーを抽出して返却
        return topKeysByShare(counts, total, topN, 1L, 0.0, 0.0);
    }

    /**
     * 比率上位キーを抽出
     */
    private static List<UUID> topKeysByShare(Map<UUID, Long> counts, long total, int topN, long minCount, double priorTotal, double minScore) {

        // 件数マップが取得できなかった場合
        if (counts == null || counts.isEmpty() || total <= 0 || topN <= 0) {
            // 空のリストを返却
            return List.of();
        }

        // 件数マップを全件ループ処理
        return counts.entrySet().stream()
                // キーと件数が有効な要素のみ抽出
                .filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() >= minCount)
                // 補正比率が下限以上の要素のみ抽出
                .filter(entry -> topKeyScore(entry.getValue(), total, priorTotal) >= minScore)
                // スコア降順で並び替え
                .sorted((leftEntry, rightEntry) -> Double.compare(topKeyScore(rightEntry.getValue(), total, priorTotal), topKeyScore(leftEntry.getValue(), total, priorTotal)))
                // 上位件数へ制限
                .limit(topN)
                // キーを取得
                .map(Map.Entry::getKey)
                // リストにまとめる
                .collect(Collectors.toList());
    }

    /**
     * 上位キー抽出用の補正比率を算出
     */
    private static double topKeyScore(long count, long total, double priorTotal) {

        // 母数を補正
        double denominator = Math.max(1.0, (double) total + Math.max(0.0, priorTotal));

        // 補正比率を返却
        return (double) count / denominator;
    }

    /**
     * ハッシュタグ/探索/カテゴリの順に候補を均衡投入
     */
    private static void putBalanced(LinkedHashMap<UUID, NewsEntity> map, int target, List<NewsEntity> categoryNews, List<NewsEntity> tagNews, List<NewsEntity> exploreNews) {

        // 目標件数を取得
        int maxSize = Math.max(0, target);
        // 目標件数が 0 の場合、そのまま返却
        if (maxSize == 0) return;

        // 最大ループ件数を取得
        int maxLoop = Math.max(sizeOf(categoryNews), Math.max(sizeOf(tagNews), sizeOf(exploreNews)));
        // 候補一覧をインデックス順に全ループ処理
        for (int index = 0; index < maxLoop; index++) {
            // ハッシュタグ候補を追加
            putByIndexIfValid(map, maxSize, tagNews, index);
            // 探索候補を追加
            putByIndexIfValid(map, maxSize, exploreNews, index);
            // カテゴリ候補を追加
            putByIndexIfValid(map, maxSize, categoryNews, index);
        }
    }

    /**
     * 指定インデックスの有効なニュース情報を追加
     */
    private static void putByIndexIfValid(LinkedHashMap<UUID, NewsEntity> map, int target, List<NewsEntity> newsList, int index) {

        // 目標件数に到達している場合、そのまま返却
        if (map.size() >= target) return;
        // ニュース一覧が取得できなかった場合、そのまま返却
        if (newsList == null) return;
        // インデックスが範囲外の場合、そのまま返却
        if (index < 0 || index >= newsList.size()) return;

        // 指定インデックスのニュース情報を追加
        putIfValid(map, newsList.get(index));
    }

    /**
     * ニュース一覧の件数を取得
     */
    private static int sizeOf(List<NewsEntity> newsList) {

        // ニュース一覧が取得できなかった場合は 0 を返却
        if (newsList == null) return 0;

        // ニュース一覧の件数を返却
        return newsList.size();
    }

    /**
     * 有効なニュース情報を追加
     */
    private static void putIfValid(LinkedHashMap<UUID, NewsEntity> map, NewsEntity newsEntity) {

        // ニュース情報が取得できなかった場合、そのまま返却
        if (newsEntity == null || newsEntity.getId() == null) return;

        // ニュース情報が未登録の場合、ニュース情報を追加
        map.putIfAbsent(newsEntity.getId(), newsEntity);
    }

    /**
     * ニュース一覧を候補一覧へ変換
     */
    private static List<Candidate> toCandidates(java.util.Collection<NewsEntity> news, SourceType srcType) {

        // 候補一覧を作成
        List<Candidate> out = new ArrayList<>();

        // ニュース一覧を全ループ処理
        for (NewsEntity newsEntity : news) {
            // ニュース一覧を候補一覧に変換
            out.add(new Candidate(newsEntity, srcType, 0.0));
        }

        // 候補一覧を返却
        return out;
    }
}