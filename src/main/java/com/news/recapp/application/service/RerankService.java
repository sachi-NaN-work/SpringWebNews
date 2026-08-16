package com.news.recapp.application.service;

import com.news.recapp.application.dto.ApiDtos;
import com.news.recapp.application.port.out.ModelRuntimePort;
import com.news.recapp.application.port.out.NewsHashtagPort;
import com.news.recapp.application.port.out.RecSignalStorePort;
import com.news.recapp.config.RecProperties;
import com.news.recapp.util.Messages;
import com.news.recapp.persistence.entity.HashtagEntity;
import com.news.recapp.persistence.entity.NewsEntity;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推薦順位の調整処理を提供（今回仕様:時間を使わない）
 */
@Service
public class RerankService {

    // ロガーを保持
    private static final Logger log = LoggerFactory.getLogger(RerankService.class);

    // モデルランタイムを保持
    private final ModelRuntimePort modelRuntime;

    // 学習済み固定係数を保持（モデル無し時のフォールバック）
    private final FixedParamsService fixedParamsService;
    // シグナルストアを保持
    private final RecSignalStorePort signalStore;
    // ニュース記事ハッシュタグを保持
    private final NewsHashtagPort newsHashtagPort;
    // リアルタイム推薦の設定を保持
    private final RecProperties props;

    // コンストラクタ
    public RerankService(
            ModelRuntimePort modelRuntime,
            FixedParamsService fixedParamsService,
            RecSignalStorePort signalStore,
            NewsHashtagPort newsHashtagPort,
            RecProperties props
    ) {
        this.modelRuntime = modelRuntime;
        this.fixedParamsService = fixedParamsService;
        this.signalStore = signalStore;
        this.newsHashtagPort = newsHashtagPort;
        this.props = props;
    }

    /**
     * リアルタイム推薦の設定パラメータを取得
     */
    private RecProperties.RtRec rt() {
        // リアルタイム推薦の設定パラメータを返却
        return props.getRtRec();
    }

    /**
     * スコア算出経路（MODEL/FIXED/DEFAULT）を集計してログ出力
     */
    private void logScoringSummary(String userId, String ab, int topK, List<Scored> scored) {

        // スコア付与済み候補が無い場合は何もしない
        if (scored == null || scored.isEmpty()) {
            return;
        }

        // 集計
        long cModel = scored.stream().filter(scoredItem -> "MODEL".equals(scoredItem.scoringPath())).count();
        long cFixed = scored.stream().filter(scoredItem -> "FIXED".equals(scoredItem.scoringPath())).count();
        long cDefault = scored.size() - cModel - cFixed;

        // A/B ブースト値（学習対象外）
        double boost = ("B".equalsIgnoreCase(ab)
                ? rt().getAbBoostB()
                : rt().getAbBoostA());

        // ユーザー識別子
        String userKey = userKey(userId);

        // ログに出力
        log.info(
                Messages.getMsg("log.rerank.scoring"),
                userKey,
                (ab == null ? "" : ab),
                scored.size(),
                topK,
                cModel,
                cFixed,
                cDefault,
                modelRuntime.isEnabled(),
                rt().getAbBoostType(),
                boost
        );
    }

    /**
     * ログ用のユーザー識別子を生成（PIIを避ける）
     */
    private static String userKey(String userId) {

        // 未ログイン/匿名の場合
        if (userId == null || userId.isBlank() || "anonymous".equalsIgnoreCase(userId)) {
            return "anon";
        }

        // hashCode を 16進で返す（安定・短い）
        return Integer.toHexString(userId.hashCode());
    }

    // 候補とスコアを紐づけたスコアリング結果
    private record Scored(
            CandidateService.Candidate cand,
            double score,
            double modelScore,
            double baseScore,
            double categoryViewShare,
            double categoryFavoriteShare,
            double categoryGoodShare,
            double categoryBadShare,
            double strongestTagViewShare,
            double strongestTagFavoriteShare,
            double strongestTagGoodShare,
            double strongestTagBadShare,
            double exposureScore,
            double categoryPreferenceScore,
            double tagPreferenceScore,
            double userMatchScore,
            double baseExposureWeight,
            double baseUserMatchWeight,
            double boostAb,
            String abBoostType,
            double epsilon,
            double repeatPenalty,
            double scoreBeforeRepeatPenalty,
            double modelBias,
            List<Double> modelWeights,
            String scoringPath,
            boolean imputed
    ) {}

    // 特徴量ベクトル（9特徴量）と補完フラグ
    private record FeatureVector(double[] values, boolean imputed) {}

    // カテゴリ割合の補正母数
    private static final double CATEGORY_SHARE_PRIOR_TOTAL = 20.0;

    /**
     * リアルタイム推薦の調整
     */
    @CircuitBreaker(name = "rerank", fallbackMethod = "fallback")
    public Mono<List<ApiDtos.RecItem>> rerank(
            String userId,
            String ab,
            int topK,
            CandidateService.CandidatePools pools,
            RecSignalService.RecSignalSnapshot snap
    ) {

        // 個別候補と全体候補を統合する候補一覧を初期化
        List<CandidateService.Candidate> all = new ArrayList<>();

        // 候補プールが取得できた場合
        if (pools != null) {

            // 個別候補一覧が取得できた場合
            if (pools.personal() != null) {
                // 個別候補一覧を統合候補へ追加
                all.addAll(pools.personal());
            }

            // 全体候補一覧が取得できた場合
            if (pools.global() != null) {
                // 全体候補一覧を統合候補へ追加
                all.addAll(pools.global());
            }
        }

        // 統合候補一覧が空の場合
        if (all.isEmpty()) {
            // 推薦アイテム一覧の空配列を返却
            return Mono.just(List.of());
        }

        // ニュース ID 単位で重複排除した候補マップを初期化
        LinkedHashMap<UUID, CandidateService.Candidate> uniq = new LinkedHashMap<>();

        // 統合候補一覧を全ループ処理
        for (CandidateService.Candidate candidate : all) {

            // 候補またはニュース ID が取得できなかった場合
            if (candidate == null || candidate.news() == null || candidate.news().getId() == null) {
                // ニュース ID が取得できなかった候補をスキップ
                continue;
            }

            // 未登録のニュース ID の場合のみ候補を登録
            uniq.putIfAbsent(candidate.news().getId(), candidate);
        }

        // 重複排除後のニュース ID 一覧を取得
        List<UUID> ids = new ArrayList<>(uniq.keySet());

        // ユーザー別ニュース閲覧数一覧取得処理
        Mono<List<Long>> uNewsViewsMono;
        // ログイン済みユーザーか判定
        boolean loggedIn = userId != null && !userId.isBlank() && !"anonymous".equalsIgnoreCase(userId);

        // ログイン済みユーザーの場合
        if (loggedIn) {
            // ユーザー別ニュース閲覧数一覧の取得処理を設定
            uNewsViewsMono = signalStore.getUserNewsViews(userId, ids);
            // ログイン済みユーザーでない場合
        } else {
            // ユーザー別ニュース閲覧数一覧へ 0 を詰めたデフォルト値を設定
            uNewsViewsMono = Mono.just(Collections.nCopies(ids.size(), 0L));
        }

        // 全体ニュース閲覧数一覧（上限 11）を取得（A/B グループ別）
        Mono<List<Long>> gNewsViewsMono = signalStore.getGlobalNewsViewsCapped11(ab, ids);

        // ユーザー別/全体の閲覧数一覧をまとめて取得してスコア算出を実行
        return Mono.zip(uNewsViewsMono, gNewsViewsMono)
                // 閲覧数一覧をマップ化してスコア算出を実行
                .flatMap(tuple -> {

                    // ユーザー別ニュース閲覧数マップを生成
                    Map<UUID, Long> uNewsViewMap = toMap(ids, tuple.getT1());
                    // 全体ニュース閲覧数マップを生成
                    Map<UUID, Long> gNewsViewMap = toMap(ids, tuple.getT2());

                    // A/B テスト用の乱数シードを生成
                    Random rnd = new Random(
                            (userId == null
                                    ? 0
                                    : userId.hashCode())
                                    ^ (ab == null
                                    ? 0
                                    : ab.hashCode())
                    );

                    // 重複排除後の候補一覧へスコアを付与して推薦アイテム一覧へ変換
                    return Flux.fromIterable(uniq.values())
                            // 候補ごとにスコアを算出
                            .flatMap(cand -> scoreOne(cand, ab, rnd, snap, uNewsViewMap, gNewsViewMap), 32)
                            // スコア付与済み候補を一覧へ集約
                            .collectList()
                            // 上位件数へ絞り込んだ推薦アイテム一覧へ変換
                            .map(scored -> {

                                // スコア算出経路を集計してログ出力
                                logScoringSummary(userId, ab, topK, scored);

                                return selectTopK(scored, topK, ab);
                            });
                });
    }

    /**
     * 1件分のスコアを算出
     */
    private Mono<Scored> scoreOne(
            CandidateService.Candidate cand,
            String ab,
            Random rnd,
            RecSignalService.RecSignalSnapshot snap,
            Map<UUID, Long> uNewsViewMap,
            Map<UUID, Long> gNewsViewMap
    ) {

        // 候補のニュース記事を取得
        NewsEntity news = cand.news();
        // ニュース ID を取得
        UUID newsId = news.getId();

        // 有効なハッシュタグ一覧を取得して候補スコアを算出
        return newsHashtagPort.findEnabledHashtags(newsId)
                // 有効なハッシュタグ ID を抽出
                .map(HashtagEntity::getId)
                // ハッシュタグ ID 一覧へ集約
                .collectList()
                // ハッシュタグが取得できない場合、空一覧で補完
                .defaultIfEmpty(List.of())
                // ハッシュタグ ID 一覧を使って候補スコアを算出
                .map(tagIds -> {
                    // 全体閲覧数から露出スコアを算出
                    double exposure = exposureScore(gNewsViewMap.getOrDefault(newsId, 0L));

                    // 9特徴量を生成（A/B ブーストは特徴量に含めない）
                    FeatureVector fvObj = featureVector9(news, tagIds, snap, exposure);
                    double[] fvValues = fvObj.values();
                    boolean imputed = fvObj.imputed();

                    // カテゴリ嗜好スコアを算出
                    double categoryPreferenceScore = prefFromShares(fvValues[1], fvValues[2], fvValues[3], fvValues[4]);
                    // タグ嗜好スコアを算出
                    double tagPreferenceScore = prefFromShares(fvValues[5], fvValues[6], fvValues[7], fvValues[8]);
                    // ユーザー嗜好一致度を算出
                    double match = userMatch(news, tagIds, snap);
                    // 露出スコアとユーザー嗜好一致度から基本スコアを算出
                    double baseScore = baseScore(exposure, match);

                    // 固定パラメータを取得
                    FixedParamsService.FixedParams fixed = fixedParamsService.get();
                    // 表示用の固定パラメータ重みを取得
                    List<Double> modelWeights = hasLearnedWeights(fixed) ? toDoubleList(fixed.weights()) : List.of();
                    // 表示用の固定パラメータ切片を取得
                    double modelBias = hasLearnedWeights(fixed) ? fixed.bias() : 0.0;

                    // A/B テストのブースト値を取得
                    double abBoost = ("B".equalsIgnoreCase(ab)
                            ? rt().getAbBoostB()
                            : rt().getAbBoostA());
                    // 微小乱数を算出
                    double epsilon = rnd.nextDouble() * 0.01;

                    // 候補スコア
                    double score;

                    // 1) 学習モデルが使える場合はモデルスコア
                    String scoringPath = "DEFAULT";
                    double modelScore = Double.NaN;

                    // 学習モデルの有効フラグ判定
                    if (modelRuntime.isEnabled()) {
                        try {
                            // 特徴量に対する学習モデルのスコアを取得
                            modelScore = modelRuntime.score(toFloatArray(fvValues));
                            scoringPath = "MODEL";
                        } catch (Exception ignored) {
                            // 推論失敗時はフォールバックへ
                        }
                    }

                    // 2) モデルが無い/失敗した場合は固定パラメータ（学習された値）
                    if (Double.isNaN(modelScore)) {

                        // 学習済み固定パラメータが取得できた場合
                        if (hasLearnedWeights(fixed)) {
                            // 学習済み固定パラメータを計算
                            modelScore = fixed.score(fvValues);
                            scoringPath = "FIXED";

                        } else {
                            // 3) 学習済み重みが無い場合はモデルスコアなしとして扱う
                            modelScore = 0.0;
                        }
                    }

                    // 最終計算前のモデル/固定係数スコアを初期化
                    double scoreBeforeBoost;

                    // モデル/固定係数のスコアに基本スコアを加算
                    if ("MODEL".equals(scoringPath) || "FIXED".equals(scoringPath)) {
                        // モデル/固定係数が同点でも嗜好一致度で順位差を付ける
                        scoreBeforeBoost = modelScore + baseScore;
                    } else {
                        // 学習値が無い場合は基本スコアのみを使用
                        scoreBeforeBoost = baseScore;
                    }

                    // ブーストは学習対象にせず、最後に適用
                    double scoreBeforeRepeatPenalty = applyBoost(scoreBeforeBoost, abBoost, rt().getAbBoostType()) + epsilon;
                    // ペナルティ適用前スコアを候補スコアへ設定
                    score = scoreBeforeRepeatPenalty;

                    // ユーザー別ニュース閲覧回数を取得
                    long rawRepeat = uNewsViewMap.getOrDefault(newsId, 0L);
                    // リピート閲覧率に応じたペナルティ係数を算出
                    double repeatPenalty = repeatPenalty(rawRepeat, snap.totalViews());
                    // リピート閲覧ペナルティをスコアへ反映
                    score *= repeatPenalty;

                    // 候補とスコアの組を返却
                    return new Scored(
                            cand,
                            score,
                            modelScore,
                            baseScore,
                            fvValues[1],
                            fvValues[2],
                            fvValues[3],
                            fvValues[4],
                            fvValues[5],
                            fvValues[6],
                            fvValues[7],
                            fvValues[8],
                            exposure,
                            categoryPreferenceScore,
                            tagPreferenceScore,
                            match,
                            rt().getBaseExposureWeight(),
                            rt().getBaseUserMatchWeight(),
                            abBoost,
                            rt().getAbBoostType(),
                            epsilon,
                            repeatPenalty,
                            scoreBeforeRepeatPenalty,
                            modelBias,
                            modelWeights,
                            scoringPath,
                            imputed
                    );
                });
    }

    /**
     * 固定パラメータに学習済み重みがあるか判定
     */
    private static boolean hasLearnedWeights(FixedParamsService.FixedParams fixed) {

        // 固定パラメータが取得できなかった場合
        if (fixed == null || fixed.weights() == null) {
            // 学習済み重みなしとして返却
            return false;
        }

        // 重み一覧を全ループ処理
        for (double weight : fixed.weights()) {
            // 0 以外の重みがある場合
            if (Math.abs(weight) > 0.0000001) {
                // 学習済み重みありとして返却
                return true;
            }
        }

        // すべて 0 の場合は学習済み重みなしとして返却
        return false;
    }

    /**
     * 露出スコアとユーザー嗜好一致度から基本スコアを算出
     */
    private double baseScore(double exposure, double match) {

        // 設定値の重みに従って基本スコアを算出して返却
        return (rt().getBaseExposureWeight() * exposure) +
                (rt().getBaseUserMatchWeight() * match);
    }

    /**
     * 9特徴量を生成
     */
    private FeatureVector featureVector9(
            NewsEntity news,
            List<UUID> tagIds,
            RecSignalService.RecSignalSnapshot snap,
            double exposure
    ) {

        // 欠損補完の有無を表すフラグ
        boolean imputed = false;

        // ニュースのカテゴリ ID を取得
        UUID catId = news.getCategoryId();

        // カテゴリ ID が取得できない場合
        if (catId == null) {
            // 欠損フラグ
            imputed = true;
        }

        // カテゴリごとの share を算出
        double catViewShare = (catId == null) ? 0.0 : categoryShare(snap.uCatView().getOrDefault(catId, 0L), snap.totalViews());
        double catFavShare = (catId == null) ? 0.0 : categoryShare(snap.uCatFav().getOrDefault(catId, 0L), snap.totalFav());
        double catGoodShare = (catId == null) ? 0.0 : categoryShare(snap.uCatGood().getOrDefault(catId, 0L), snap.totalGood());
        double catBadShare = (catId == null) ? 0.0 : categoryShare(snap.uCatBad().getOrDefault(catId, 0L), snap.totalBad());

        // 最も強いタグの share を初期化
        double strongestTagViewShare = 0.0;
        double strongestTagFavoriteShare = 0.0;
        double strongestTagGoodShare = 0.0;
        double strongestTagBadShare = 0.0;
        // 最も強いタグの嗜好スコアを初期化
        double bestTagPreferenceScore = -1.0;

        // タグ一覧が無い場合
        if (tagIds == null) {
            // 欠損フラグ
            imputed = true;
        }

        // タグ一覧がある場合
        if (tagIds != null && !tagIds.isEmpty()) {

            // タグ一覧をループ処理
            for (UUID tid : tagIds) {
                // タグ ID が無い要素は欠損扱いとしてスキップ
                if (tid == null) {
                    imputed = true;
                    continue;
                }

                // タグごとの閲覧 share を算出
                double tagViewShare = share(snap.uTagView().getOrDefault(tid, 0L), snap.totalViews());
                // タグごとのお気に入り share を算出
                double tagFavShare = share(snap.uTagFav().getOrDefault(tid, 0L), snap.totalFav());
                // タグごとの Good share を算出
                double tagGoodShare = share(snap.uTagGood().getOrDefault(tid, 0L), snap.totalGood());
                // タグごとの Bad share を算出
                double tagBadShare = share(snap.uTagBad().getOrDefault(tid, 0L), snap.totalBad());
                // タグごとの嗜好スコアを算出
                double tagPreferenceScore = prefFromShares(tagViewShare, tagFavShare, tagGoodShare, tagBadShare);

                // 最も強いタグの場合
                if (tagPreferenceScore > bestTagPreferenceScore) {
                    // 最も強いタグの嗜好スコアを更新
                    bestTagPreferenceScore = tagPreferenceScore;
                    // 最も強いタグの閲覧 share を設定
                    strongestTagViewShare = tagViewShare;
                    // 最も強いタグのお気に入り share を設定
                    strongestTagFavoriteShare = tagFavShare;
                    // 最も強いタグの Good share を設定
                    strongestTagGoodShare = tagGoodShare;
                    // 最も強いタグの Bad share を設定
                    strongestTagBadShare = tagBadShare;
                }
            }
        }

        // 9特徴量と欠損補完フラグを FeatureVector にまとめて返す
        return new FeatureVector(new double[]
                {
                        exposure,
                        catViewShare, catFavShare, catGoodShare, catBadShare,
                        strongestTagViewShare, strongestTagFavoriteShare, strongestTagGoodShare, strongestTagBadShare
                },
                imputed
        );
    }


    /**
     * ブースト適用（学習対象にしない）
     */
    private static double applyBoost(
            double scoreModel,
            double boostAb,
            String boostType
    ) {

        // boostType が multiply の場合
        if ("multiply".equalsIgnoreCase(boostType)) {
            // boostAb を乗算して返却
            return scoreModel * boostAb;
        }

        // (既定) boostAb を足して返却
        return scoreModel + boostAb;
    }

    /**
     * double[] -> List<Double> 変換（表示用）
     */
    private static List<Double> toDoubleList(double[] values) {

        // 値が取得できていない場合は空一覧を返却
        if (values == null || values.length == 0) {
            return List.of();
        }

        // 表示用の一覧を初期化
        List<Double> out = new ArrayList<>();

        // 値を順に追加
        for (double value : values) {
            // 値を一覧へ追加
            out.add(value);
        }

        // 表示用の一覧を返却
        return out;
    }

    /**
     * double[] -> float[] 変換（ONNX 入力用）
     */
    private static float[] toFloatArray(double[] values) {
        if (values == null) {
            return new float[0];
        }
        float[] out = new float[values.length];
        for (int index = 0; index < values.length; index++) {
            out[index] = (float) values[index];
        }
        return out;
    }

    /**
     * ユーザー嗜好一致度を算出（カテゴリとタグの嗜好を加味）
     */
    private double userMatch(
            NewsEntity news,
            List<UUID> tagIds,
            RecSignalService.RecSignalSnapshot snap
    ) {

        // カテゴリ ID を取得
        UUID catId = news.getCategoryId();

        // カテゴリ嗜好スコアを算出
        double prefCat = prefFromShares(
                catId == null ? 0.0 : categoryShare(snap.uCatView().getOrDefault(catId, 0L), snap.totalViews()),
                catId == null ? 0.0 : categoryShare(snap.uCatFav().getOrDefault(catId, 0L), snap.totalFav()),
                catId == null ? 0.0 : categoryShare(snap.uCatGood().getOrDefault(catId, 0L), snap.totalGood()),
                catId == null ? 0.0 : categoryShare(snap.uCatBad().getOrDefault(catId, 0L), snap.totalBad())
        );

        // ハッシュタグ嗜好スコアの最大値を初期化
        double prefTagMax = 0.0;

        // ハッシュタグ ID 一覧が取得できた場合
        if (tagIds != null && !tagIds.isEmpty()) {

            // ハッシュタグ ID 一覧を全ループ処理
            for (UUID tid : tagIds) {

                // ハッシュタグ ID が取得できなかった場合
                if (tid == null) {
                    // ハッシュタグ ID が取得できなかった場合、スキップ
                    continue;
                }

                // ハッシュタグ嗜好スコアを算出
                double tagPreferenceScore = prefFromShares(
                        share(snap.uTagView().getOrDefault(tid, 0L), snap.totalViews()),
                        share(snap.uTagFav().getOrDefault(tid, 0L), snap.totalFav()),
                        share(snap.uTagGood().getOrDefault(tid, 0L), snap.totalGood()),
                        share(snap.uTagBad().getOrDefault(tid, 0L), snap.totalBad())
                );

                // ハッシュタグ嗜好スコアの最大値を更新
                prefTagMax = Math.max(prefTagMax, tagPreferenceScore);
            }
        }

        // カテゴリ嗜好とハッシュタグ嗜好を合成して 0〜1 に正規化して返却
        return clamp01((0.50 * prefCat) + (0.50 * prefTagMax));
    }

    /**
     * 閲覧/お気に入り/高評価/低評価の比率から嗜好スコアを算出
     */
    private static double prefFromShares(
            double viewShare,
            double favShare,
            double goodShare,
            double badShare
    ) {
        // 比率を重み付けして嗜好スコアを算出して返却
        return clamp01((0.20 * viewShare) + (0.30 * favShare) + (0.50 * goodShare) - (0.60 * badShare));
    }

    /**
     * カテゴリ割合を算出
     */
    private static double categoryShare(long part, long total) {
        // 分子を下限 0 で補正
        long adjustedPart = Math.max(0L, part);
        // 分母を補正母数込みで算出
        double denominator = Math.max(1.0, (double) Math.max(0L, total) + CATEGORY_SHARE_PRIOR_TOTAL);

        // 補正後のカテゴリ割合を 0〜1 に収めて返却
        return clamp01((double) adjustedPart / denominator);
    }

    /**
     * 合計値に対する比率を算出
     */
    private static double share(long part, long total) {
        // 最小分母を 1 として比率を算出して返却
        return (double) part / (double) Math.max(total, 1L);
    }

    /**
     * 数値を 0〜1 に収める
     */
    private static double clamp01(double value) {

        // 嗜好スコアが 0 より小さい場合
        if (value < 0) {
            // 下限値として 0.0 を返却
            return 0.0;
        }

        // 嗜好スコアが 1 より大きい場合
        if (value > 1) {
            // 上限値として 1.0 を返却
            return 1.0;
        }

        // 0〜1 の範囲内の嗜好スコアを返却
        return value;
    }

    /**
     * 記事閲覧段階に応じた露出スコアを算出
     */
    private double exposureScore(long gNewsViewCapped11) {

        // 全体閲覧数を 0〜11 に丸めた値を算出
        long clampedViewCount = Math.clamp(gNewsViewCapped11, 0L, 11L);

        // 全体閲覧数が 0 の場合
        if (clampedViewCount == 0) {
            // 露出スコアのバケット 0 を返却
            return rt().getExposureBucket0();
        }

        // 全体閲覧数が 1〜5 の場合
        if (clampedViewCount <= 5) {
            // 露出スコアのバケット 1 を返却
            return rt().getExposureBucket1();
        }

        // 全体閲覧数が 6〜10 の場合
        if (clampedViewCount <= 10) {
            // 露出スコアのバケット 2 を返却
            return rt().getExposureBucket2();
        }

        // 全体閲覧数が 11 の場合、露出スコアのバケット 3 を返却
        return rt().getExposureBucket3();
    }

    /**
     * リピート閲覧率に応じたペナルティを算出
     */
    private double repeatPenalty(long rawRepeat, long totalViews) {

        // リピート閲覧数の下限補正を反映
        long rv = Math.max(0L, rawRepeat);

        // リピート閲覧段階係数
        double stage;

        // リピート閲覧数が 0 の場合
        if (rv == 0) {
            // 段階係数にステージ 0 を設定
            stage = rt().getRepeatStage0();
            // リピート閲覧数が 1〜5 の場合
        } else if (rv <= 5) {
            // 段階係数にステージ 1 を設定
            stage = rt().getRepeatStage1();
            // リピート閲覧数が 6〜10 の場合
        } else if (rv <= 10) {
            // 段階係数にステージ 2 を設定
            stage = rt().getRepeatStage2();
            // リピート閲覧数が 11 以上の場合
        } else {
            // 段階係数にステージ 3 を設定
            stage = rt().getRepeatStage3();
        }

        // 合計閲覧数に対するリピート閲覧比率を算出
        double shareRepeat = (double) rv / (double) Math.max(totalViews, 1L);
        // リピート比率に応じた補正係数を算出
        double adj = 1.0 - (rt().getRepeatShareAdjCoeff() * Math.min(shareRepeat, rt().getRepeatShareAdjCap()));
        // 補正係数を 0〜1 に収める
        adj = clamp01(adj);

        // 段階係数と補正係数を掛けたペナルティ係数を返却
        return stage * adj;
    }

    /**
     * 同一カテゴリの最大採用数を算出
     */
    private int maxPerCategory(int topKLimit) {

        // 上位件数と設定比率から同一カテゴリの上限を算出
        return Math.max(
                rt().getMaxPerCategoryMin(),
                (int) Math.ceil((double) topKLimit * rt().getMaxPerCategoryRatio())
        );
    }

    /**
     * カテゴリ上限を考慮して候補を追加
     */
    private static boolean addIfCategoryAllowed(
            LinkedHashMap<UUID, Scored> chosen,
            Map<UUID, Integer> categoryCounts,
            Scored scoredItem,
            int maxPerCategory,
            boolean enforceCategoryCap
    ) {

        // スコア付与済み候補が取得できなかった場合
        if (scoredItem == null || scoredItem.cand() == null || scoredItem.cand().news() == null) {
            // 追加失敗として返却
            return false;
        }

        // ニュース ID を取得
        UUID newsId = scoredItem.cand().news().getId();

        // ニュース ID が取得できなかった場合
        if (newsId == null) {
            // 追加失敗として返却
            return false;
        }

        // 既に選択済みのニュース ID の場合
        if (chosen.containsKey(newsId)) {
            // 追加失敗として返却
            return false;
        }

        // カテゴリ ID を取得
        UUID categoryId = scoredItem.cand().news().getCategoryId();

        // カテゴリ上限を適用する場合
        if (enforceCategoryCap && categoryId != null) {

            // 現在のカテゴリ採用数を取得
            int currentCount = categoryCounts.getOrDefault(categoryId, 0);

            // 同一カテゴリの上限に達している場合
            if (currentCount >= maxPerCategory) {
                // 追加失敗として返却
                return false;
            }
        }

        // 選択済み候補へ追加
        chosen.put(newsId, scoredItem);

        // カテゴリ ID が取得できた場合
        if (categoryId != null) {
            // カテゴリ採用数を加算
            categoryCounts.merge(categoryId, 1, Integer::sum);
        }

        // 追加成功として返却
        return true;
    }

    /**
     * 上位件数を選び、最後にスコア降順で返す
     */
    private List<ApiDtos.RecItem> selectTopK(List<Scored> scored, int topK, String ab) {

        // スコア付与済み候補が取得できなかった場合
        if (scored == null || scored.isEmpty()) {
            // 推薦アイテム一覧の空配列を返却
            return List.of();
        }

        // 上位件数を算出
        int topKLimit = Math.max(1, topK);

        // 同一カテゴリの最大採用数を算出
        int maxPerCategory = maxPerCategory(topKLimit);

        // A/B に応じた global枠割合を決定
        double ratio = ("B".equalsIgnoreCase(ab)
                ? rt().getGlobalSlotRatioB()
                : rt().getGlobalSlotRatioA());

        // 比率が未設定/不正の場合
        if (!(ratio > 0 && ratio < 1)) {
            // 設定不備として例外
            throw new IllegalStateException("global-slot-ratio-a/b must be between 0 and 1");
        }

        // 全体候補の枠数を算出
        int globalSlots = (int) Math.ceil(topKLimit * ratio);

        // 全体候補の枠数が上位件数を超える場合
        if (globalSlots > topKLimit) {
            // 全体候補の枠数を上位件数へ丸める
            globalSlots = topKLimit;
        }

        // 個別候補の枠数を算出
        int personalSlots = topKLimit - globalSlots;

        // 個別候補（PERSONAL）のスコア降順一覧を生成
        List<Scored> personal = scored.stream()
                // 候補の取得元が PERSONAL の要素のみ抽出
                .filter(scoredItem -> scoredItem.cand().source() == CandidateService.SourceType.PERSONAL)
                // スコア降順で並び替え
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                // 一覧へ集約
                .toList();

        // 全体候補（GLOBAL）のスコア降順一覧を生成
        List<Scored> global = scored.stream()
                // 候補の取得元が GLOBAL の要素のみ抽出
                .filter(scoredItem -> scoredItem.cand().source() == CandidateService.SourceType.GLOBAL)
                // スコア降順で並び替え
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                // 一覧へ集約
                .toList();

        // 選択済み候補を保持するマップを初期化（重複排除用）
        LinkedHashMap<UUID, Scored> chosen = new LinkedHashMap<>();

        // カテゴリ別採用数を保持するマップを初期化
        Map<UUID, Integer> categoryCounts = new HashMap<>();

        // 個別候補の採用件数を初期化
        int pCount = 0;

        // 個別候補一覧を順に処理
        for (Scored scoredItem : personal) {

            // 個別候補の採用件数が個別候補枠数に達した場合
            if (pCount >= personalSlots) {
                // 個別候補枠数に達したため候補選択を中断
                break;
            }

            // カテゴリ上限を考慮して候補を追加
            boolean added = addIfCategoryAllowed(
                    chosen,
                    categoryCounts,
                    scoredItem,
                    maxPerCategory,
                    true
            );

            // 候補を追加できた場合
            if (added) {
                // 個別候補の採用件数を加算
                pCount++;
            }
        }

        // 全体候補の採用件数を初期化
        int gCount = 0;

        // 全体候補一覧を順に処理
        for (Scored scoredItem : global) {

            // 全体候補の採用件数が全体候補枠数に達した場合
            if (gCount >= globalSlots) {
                // 全体候補枠数に達したため候補選択を中断
                break;
            }

            // カテゴリ上限を考慮して候補を追加
            boolean added = addIfCategoryAllowed(
                    chosen,
                    categoryCounts,
                    scoredItem,
                    maxPerCategory,
                    true
            );

            // 候補を追加できた場合
            if (added) {
                // 全体候補の採用件数を加算
                gCount++;
            }
        }

        // 残り候補一覧を生成
        List<Scored> rest = new ArrayList<>();
        // 個別候補一覧を追加
        rest.addAll(personal);
        // 全体候補一覧を追加
        rest.addAll(global);
        // スコア降順で並び替え
        rest.sort(Comparator.comparingDouble(Scored::score).reversed());

        // 選択済み件数が上位件数に達していない場合
        if (chosen.size() < topKLimit) {

            // 残り候補一覧を順に処理
            for (Scored scoredItem : rest) {

                // 選択済み件数が上位件数に達した場合
                if (chosen.size() >= topKLimit) {
                    // 上位件数に達したため候補選択を中断
                    break;
                }

                // カテゴリ上限を考慮して候補を追加
                addIfCategoryAllowed(
                        chosen,
                        categoryCounts,
                        scoredItem,
                        maxPerCategory,
                        true
                );
            }
        }

        // カテゴリ上限ありで上位件数に達しなかった場合
        if (chosen.size() < topKLimit) {

            // 残り候補一覧を順に処理
            for (Scored scoredItem : rest) {

                // 選択済み件数が上位件数に達した場合
                if (chosen.size() >= topKLimit) {
                    // 上位件数に達したため候補選択を中断
                    break;
                }

                // 候補不足時のみカテゴリ上限を解除して候補を追加
                addIfCategoryAllowed(
                        chosen,
                        categoryCounts,
                        scoredItem,
                        maxPerCategory,
                        false
                );
            }
        }

        // 選択済み候補をスコア降順に並べて上位件数分の推薦アイテム一覧へ変換して返却
        return chosen.values().stream()
                // スコア降順で並び替え
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                // 上位件数へ制限
                .limit(topKLimit)
                // レスポンス用の推薦アイテムへ変換
                .map(scoredItem -> new ApiDtos.RecItem(
                        scoredItem.cand().news().getId(),
                        scoredItem.cand().news().getCategoryId(),
                        scoredItem.cand().news().getTitle(),
                        scoredItem.score(),
                        scoredItem.scoringPath(),
                        scoredItem.modelScore(),
                        scoredItem.baseScore(),
                        scoredItem.categoryViewShare(),
                        scoredItem.categoryFavoriteShare(),
                        scoredItem.categoryGoodShare(),
                        scoredItem.categoryBadShare(),
                        scoredItem.strongestTagViewShare(),
                        scoredItem.strongestTagFavoriteShare(),
                        scoredItem.strongestTagGoodShare(),
                        scoredItem.strongestTagBadShare(),
                        scoredItem.exposureScore(),
                        scoredItem.categoryPreferenceScore(),
                        scoredItem.tagPreferenceScore(),
                        scoredItem.userMatchScore(),
                        scoredItem.baseExposureWeight(),
                        scoredItem.baseUserMatchWeight(),
                        scoredItem.boostAb(),
                        scoredItem.abBoostType(),
                        scoredItem.epsilon(),
                        scoredItem.repeatPenalty(),
                        scoredItem.scoreBeforeRepeatPenalty(),
                        scoredItem.modelBias(),
                        scoredItem.modelWeights()
                ))
                // 一覧へ集約
                .collect(Collectors.toList());
    }

    /**
     * UUID と件数を紐づけたマップへ変換
     */
    private static Map<UUID, Long> toMap(List<UUID> ids, List<Long> vals) {

        // UUID と件数を紐づけるマップを初期化
        Map<UUID, Long> countMap = new HashMap<>();

        // UUID 一覧を全ループ処理
        for (int index = 0; index < ids.size(); index++) {

            // 値一覧から対応する件数を取り出す（値がない場合は 0）
            Long countValue = (vals != null && index < vals.size())
                    ? vals.get(index)
                    : 0L;

            // UUID と件数をマップへ格納
            countMap.put(ids.get(index), countValue);
        }

        // UUID と件数のマップを返却
        return countMap;
    }
}