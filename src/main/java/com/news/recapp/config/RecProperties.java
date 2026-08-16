package com.news.recapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * リアルタイム推薦の設定を保持
 */
@Component
@ConfigurationProperties(prefix = "rec")
public class RecProperties {

    // 推薦件数 (K) の既定値
    private int defaultK = 10;

    // リアルタイム推薦パラメータ
    private RtRec rtRec = new RtRec();

    /**
     * 推薦件数 (K) の既定値を取得
     */
    public int getDefaultK() {
        // 推薦件数 (K) の既定値を返却
        return defaultK;
    }

    /**
     * 推薦件数 (K) の既定値を設定
     */
    public void setDefaultK(int defaultK) {

        // 設定値が 0 より大きい場合
        if (defaultK > 0) {
            // 推薦件数 (K) の既定値を更新
            this.defaultK = defaultK;
        }
    }

    /**
     * リアルタイム推薦パラメータを取得
     */
    public RtRec getRtRec() {
        // リアルタイム推薦パラメータを返却
        return rtRec;
    }

    /**
     * リアルタイム推薦パラメータを設定
     */
    public void setRtRec(RtRec rtRec) {

        // リアルタイム推薦パラメータが未設定でない場合
        if (rtRec != null) {
            // リアルタイム推薦パラメータを更新
            this.rtRec = rtRec;
        }
    }

    /**
     * リアルタイム推薦のパラメータ群
     *  - 候補生成（personal/global）
     *  - 品質ゲート（evidence/quality 等）
     *  - 露出(exposure)と重複抑制(repeatPenalty)でスコア調整
     *  - personal/global を一定割合で合成して最終K件を返す
     *  （※）application.yml で上書き可能
     */
    public static class RtRec {

        /**
         * パーソナル推薦の候補サイズ（ユーザーの嗜好に基づく候補集合の上限）。
         *  （※）大きくすると精度向上の余地はあるが、計算量・IOが増えやすい。
         */
        private int personalCandidateSize = 500;

        /**
         * グローバル推薦の候補サイズ（全体傾向/人気などからの候補集合の上限）。
         *  （※）大きくすると多様性が増える可能性があるが、品質ゲートやスキャン負荷が増えやすい。
         */
        private int globalCandidateSize = 500;

        /**
         * personal 側で参照する上位カテゴリ数。
         *  （※）ユーザーの閲覧履歴等から「上位カテゴリ」を抽出して候補を集める場合の上限。
         */
        private int personalTopCategories = 5;

        /**
         * personal 側で参照する上位タグ数。
         */
        private int personalTopTags = 10;

        /**
         * 1カテゴリあたり採用する候補数。
         */
        private int perCategoryTake = 50;

        /**
         * 1タグあたり採用（take）する候補数。
         */
        private int perTagTake = 20;

        /**
         * 探索（explore）枠としてランダムに混ぜる数。
         *  （※）0 で探索無し。大きいほど多様性は増えるが、嗜好一致は下がることがある。
         */
        private int exploreRandom = 50;

        /**
         * グローバル推薦のスキャン上限。
         *  （※）0 なら全件スキャンを許容（データ量が多いと負荷が高い）。
         *  （※）運用で負荷が問題になる場合のみ上限を設ける。ただし上限を付けると新着/一部に偏る可能性がある。
         */
        private int globalScanLimit = 0; // 0 なら全件

        /**
         * グローバル推薦の品質計算（quality）の並列度。
         *  （※）大きくするとスループットは上がる可能性があるが、CPU使用率や外部IOが増える。
         */
        private int globalQualityConcurrency = 32;

        /**
         * A/B テスト用：A パターンの global枠割合。
         *  （※）A=GLOBAL 寄り（例: 0.8）。0〜1 の範囲でのみ有効。
         */
        private double globalSlotRatioA = 0.6;

        /**
         * A/B テスト用：B パターンの global枠割合。
         *  （※）B=PERSONAL 寄り（例: 0.2）。0〜1 の範囲でのみ有効。
         */
        private double globalSlotRatioB = 0.4;

        /**
         * 同一カテゴリの最大採用比率。
         */
        private double maxPerCategoryRatio = 0.25;

        /**
         * 同一カテゴリの最大採用数の最低値。
         */
        private int maxPerCategoryMin = 2;

        /**
         * 品質ゲート：根拠数（evidence）の最低値。
         *  （※）0 以上。大きくしすぎると候補が減って返却件数が不足する可能性がある。
         */
        private long evidenceMin = 5L;

        /**
         * 品質ゲート：quality の最低値（0〜1想定）。
         *  （※）大きくしすぎると候補が減って返却件数が不足する可能性がある。
         */
        private double qualityMin = 0.35;

        /**
         * 品質ゲートの調整係数（アルゴリズム内でのしきい値調整用）。
         *  （※）0〜1 の範囲で想定。
         */
        private double gateMultiplier = 0.2;

        /**
         * exposure（全体×記事閲覧段階）補正：bucket0（全体閲覧数 0 件の記事に適用する段階）。
         *  （※）値が大きいほどスコアが上がりやすい（人気記事を強める）。
         */
        private double exposureBucket0 = 1.00;
        private double exposureBucket1 = 1.10;
        private double exposureBucket2 = 1.20;
        private double exposureBucket3 = 1.30;

        /**
         * repeatPenalty（ユーザー×記事閲覧段階）補正：stage0。
         *  （※）同一記事/類似記事の繰り返し表示を抑える目的の係数。
         *  （※）値が小さいほど繰り返しを強く抑制する。
         */
        private double repeatStage0 = 1.00;
        private double repeatStage1 = 0.95;
        private double repeatStage2 = 0.70;
        private double repeatStage3 = 0.10;

        /**
         * repeatPenalty の共有調整係数（share調整の強さ）。
         */
        private double repeatShareAdjCoeff = 0.5;

        /**
         * repeatPenalty の共有調整上限（cap）。
         */
        private double repeatShareAdjCap = 0.2;

        /**
         * baseScore の exposure 成分の重み。
         *  （※）例：exposureとuserMatchで線形合成する場合の係数。
         */
        private double baseExposureWeight = 0.3;

        /**
         * baseScore の userMatch 成分の重み。
         */
        private double baseUserMatchWeight = 0.7;

        /**
         * クライアントが送る「有効閲覧」の判定時間（ms）。
         *  （※）0 以下は既定値 200ms を維持
         */
        private int viewMs = 200;

        /**
         * A/Bテスト用：ブーストの適用方式。
         *  - add      : score = scoreModel + abBoost
         *  - multiply : score = scoreModel * abBoost
         */
        private String abBoostType = "add";

        /**
         * A/Bテスト用：Aパターンのブースト値。
         *  - add      : 0.0 を想定
         *  - multiply : 1.0 を想定
         */
        private double abBoostA = 0.0;

        /**
         * A/Bテスト用：Bパターンに加算するブースト値。
         *  （※）0以上の微小値を想定。大きくするとランキングがブレやすくなる。
         */
        private double abBoostB = 0.05;

        /**
         * モデル/固定係数が無い場合の既定スコア（A）。
         */
        private double defaultScoreA = 0.0;

        /**
         * モデル/固定係数が無い場合の既定スコア（B）。
         */
        private double defaultScoreB = 0.0;

        /**
         * A/Bテスト用：ユーザーIDから群を安定決定するための固定salt。
         */
        private String abSalt = "";

        /**
         * A/Bテスト用：Bパターンの割当割合（%）。
         *  （※）0〜100 の範囲で丸めて使用する。
         */
        private int abBPercent = 10;

        /**
         * パーソナル推薦の候補サイズを取得
         */
        public int getPersonalCandidateSize() {
            // パーソナル推薦の候補サイズを返却
            return personalCandidateSize;
        }

        /**
         * パーソナル推薦の候補サイズを設定
         */
        public void setPersonalCandidateSize(int personalCandidateSize) {

            // 設定値が 0 より大きい場合
            if (personalCandidateSize > 0) {
                // パーソナル推薦の候補サイズを更新
                this.personalCandidateSize = personalCandidateSize;
            }
        }

        /**
         * グローバル推薦の候補サイズを取得
         */
        public int getGlobalCandidateSize() {
            // グローバル推薦の候補サイズを返却
            return globalCandidateSize;
        }

        /**
         * グローバル推薦の候補サイズを設定
         */
        public void setGlobalCandidateSize(int globalCandidateSize) {

            // 設定値が 0 より大きい場合
            if (globalCandidateSize > 0) {
                // グローバル推薦の候補サイズを更新
                this.globalCandidateSize = globalCandidateSize;
            }
        }

        /**
         * personal 側で参照する上位カテゴリ数を取得
         */
        public int getPersonalTopCategories() {
            // personal 側で参照する上位カテゴリ数を返却
            return personalTopCategories;
        }

        /**
         * personal 側で参照する上位カテゴリ数を設定
         */
        public void setPersonalTopCategories(int personalTopCategories) {

            // 設定値が 0 より大きい場合
            if (personalTopCategories > 0) {
                // personal 側で参照する上位カテゴリ数を更新
                this.personalTopCategories = personalTopCategories;
            }
        }

        /**
         * personal 側で参照する上位タグ数を取得
         */
        public int getPersonalTopTags() {
            // personal 側で参照する上位タグ数を返却
            return personalTopTags;
        }

        /**
         * personal 側で参照する上位タグ数を設定
         */
        public void setPersonalTopTags(int personalTopTags) {

            // 設定値が 0 より大きい場合
            if (personalTopTags > 0) {
                // personal 側で参照する上位タグ数を更新
                this.personalTopTags = personalTopTags;
            }
        }

        /**
         * 1カテゴリあたり採用する候補数を取得
         */
        public int getPerCategoryTake() {
            // 1カテゴリあたり採用する候補数を返却
            return perCategoryTake;
        }

        /**
         * 1カテゴリあたり採用する候補数を設定
         */
        public void setPerCategoryTake(int perCategoryTake) {

            // 設定値が 0 より大きい場合
            if (perCategoryTake > 0) {
                // 1カテゴリあたり採用する候補数を更新
                this.perCategoryTake = perCategoryTake;
            }
        }

        /**
         * 1タグあたり採用する候補数を取得
         */
        public int getPerTagTake() {
            // 1タグあたり採用する候補数を返却
            return perTagTake;
        }

        /**
         * 1タグあたり採用する候補数を設定
         */
        public void setPerTagTake(int perTagTake) {

            // 設定値が 0 より大きい場合
            if (perTagTake > 0) {
                // 1タグあたり採用する候補数を更新
                this.perTagTake = perTagTake;
            }
        }

        /**
         * 探索枠としてランダムに混ぜる数を取得
         */
        public int getExploreRandom() {
            // 探索枠としてランダムに混ぜる数を返却
            return exploreRandom;
        }

        /**
         * 探索枠としてランダムに混ぜる数を設定
         */
        public void setExploreRandom(int exploreRandom) {

            // 設定値が 0 以上の場合
            if (exploreRandom >= 0) {
                // 探索枠としてランダムに混ぜる数を更新
                this.exploreRandom = exploreRandom;
            }
        }

        /**
         * グローバル推薦のスキャン上限を取得
         */
        public int getGlobalScanLimit() {
            // グローバル推薦のスキャン上限を返却
            return globalScanLimit;
        }

        /**
         * グローバル推薦のスキャン上限を設定
         */
        public void setGlobalScanLimit(int globalScanLimit) {

            // 設定値が 0 以上の場合
            if (globalScanLimit >= 0) {
                // グローバル推薦のスキャン上限を更新
                this.globalScanLimit = globalScanLimit;
            }
        }

        /**
         * グローバル推薦の品質計算の並列度を取得
         */
        public int getGlobalQualityConcurrency() {
            // グローバル推薦の品質計算の並列度を返却
            return globalQualityConcurrency;
        }

        /**
         * グローバル推薦の品質計算の並列度を設定
         */
        public void setGlobalQualityConcurrency(int globalQualityConcurrency) {

            // 設定値が 0 より大きい場合
            if (globalQualityConcurrency > 0) {
                // グローバル推薦の品質計算の並列度を更新
                this.globalQualityConcurrency = globalQualityConcurrency;
            }
        }

        /**
         * A/B テスト用：A パターンの global枠割合を取得
         */
        public double getGlobalSlotRatioA() {
            // A/B テスト用：A パターンの global枠割合を返却
            return globalSlotRatioA;
        }

        /**
         * A/B テスト用：A パターンの global枠割合を設定
         */
        public void setGlobalSlotRatioA(double globalSlotRatioA) {

            // 設定値が 0 より大きく 1 未満の場合
            if (globalSlotRatioA > 0 && globalSlotRatioA < 1) {
                // A/B テスト用：A パターンの global枠割合を更新
                this.globalSlotRatioA = globalSlotRatioA;
            }
        }

        /**
         * A/B テスト用：B パターンの global枠割合を取得
         */
        public double getGlobalSlotRatioB() {
            // A/B テスト用：B パターンの global枠割合を返却
            return globalSlotRatioB;
        }

        /**
         * A/B テスト用：B パターンの global枠割合を設定
         */
        public void setGlobalSlotRatioB(double globalSlotRatioB) {

            // 設定値が 0 より大きく 1 未満の場合
            if (globalSlotRatioB > 0 && globalSlotRatioB < 1) {
                // A/B テスト用：B パターンの global枠割合を更新
                this.globalSlotRatioB = globalSlotRatioB;
            }
        }

        /**
         * 同一カテゴリの最大採用比率を取得
         */
        public double getMaxPerCategoryRatio() {
            // 同一カテゴリの最大採用比率を返却
            return maxPerCategoryRatio;
        }

        /**
         * 同一カテゴリの最大採用比率を設定
         */
        public void setMaxPerCategoryRatio(double maxPerCategoryRatio) {

            // 設定値が 0 より大きく 1 以下の場合
            if (maxPerCategoryRatio > 0 && maxPerCategoryRatio <= 1) {
                // 同一カテゴリの最大採用比率を更新
                this.maxPerCategoryRatio = maxPerCategoryRatio;
            }
        }

        /**
         * 同一カテゴリの最大採用数の最低値を取得
         */
        public int getMaxPerCategoryMin() {
            // 同一カテゴリの最大採用数の最低値を返却
            return maxPerCategoryMin;
        }

        /**
         * 同一カテゴリの最大採用数の最低値を設定
         */
        public void setMaxPerCategoryMin(int maxPerCategoryMin) {

            // 設定値が 0 より大きい場合
            if (maxPerCategoryMin > 0) {
                // 同一カテゴリの最大採用数の最低値を更新
                this.maxPerCategoryMin = maxPerCategoryMin;
            }
        }

        /**
         * 品質ゲート：根拠数の最低値を取得
         */
        public long getEvidenceMin() {
            // 品質ゲート：根拠数の最低値を返却
            return evidenceMin;
        }

        /**
         * 品質ゲート：根拠数の最低値を設定
         */
        public void setEvidenceMin(long evidenceMin) {

            // 設定値が 0 以上の場合
            if (evidenceMin >= 0) {
                // 品質ゲート：根拠数の最低値を更新
                this.evidenceMin = evidenceMin;
            }
        }

        /**
         * 品質ゲート：quality の最低値を取得
         */
        public double getQualityMin() {
            // 品質ゲート：quality の最低値を返却
            return qualityMin;
        }

        /**
         * 品質ゲート：quality の最低値を設定
         */
        public void setQualityMin(double qualityMin) {

            // 設定値が 0 以上 1 以下の場合
            if (qualityMin >= 0 && qualityMin <= 1) {
                // 品質ゲート：quality の最低値を更新
                this.qualityMin = qualityMin;
            }
        }

        /**
         * 品質ゲートの調整係数を取得
         */
        public double getGateMultiplier() {
            // 品質ゲートの調整係数を返却
            return gateMultiplier;
        }

        /**
         * 品質ゲートの調整係数を設定
         */
        public void setGateMultiplier(double gateMultiplier) {

            // 設定値が 0 以上 1 以下の場合
            if (gateMultiplier >= 0 && gateMultiplier <= 1) {
                // 品質ゲートの調整係数を更新
                this.gateMultiplier = gateMultiplier;
            }
        }

        /**
         * exposure補正：bucket0を取得
         */
        public double getExposureBucket0() {
            // exposure補正：bucket0を返却
            return exposureBucket0;
        }

        /**
         * exposure補正：bucket0を設定
         */
        public void setExposureBucket0(double exposureBucket0) {

            // exposure補正：bucket0を更新
            this.exposureBucket0 = exposureBucket0;
        }

        /**
         * exposure補正：bucket1を取得
         */
        public double getExposureBucket1() {
            // exposure補正：bucket1を返却
            return exposureBucket1;
        }

        /**
         * exposure補正：bucket1を設定
         */
        public void setExposureBucket1(double exposureBucket1) {

            // exposure補正：bucket1を更新
            this.exposureBucket1 = exposureBucket1;
        }

        /**
         * exposure補正：bucket2を取得
         */
        public double getExposureBucket2() {
            // exposure補正：bucket2を返却
            return exposureBucket2;
        }

        /**
         * exposure補正：bucket2を設定
         */
        public void setExposureBucket2(double exposureBucket2) {

            // exposure補正：bucket2を更新
            this.exposureBucket2 = exposureBucket2;
        }

        /**
         * exposure補正：bucket3を取得
         */
        public double getExposureBucket3() {
            // exposure補正：bucket3を返却
            return exposureBucket3;
        }

        /**
         * exposure補正：bucket3を設定
         */
        public void setExposureBucket3(double exposureBucket3) {

            // exposure補正：bucket3を更新
            this.exposureBucket3 = exposureBucket3;
        }

        /**
         * repeatPenalty補正：stage0を取得
         */
        public double getRepeatStage0() {
            // repeatPenalty補正：stage0を返却
            return repeatStage0;
        }

        /**
         * repeatPenalty補正：stage0を設定
         */
        public void setRepeatStage0(double repeatStage0) {

            // repeatPenalty補正：stage0を更新
            this.repeatStage0 = repeatStage0;
        }

        /**
         * repeatPenalty補正：stage1を取得
         */
        public double getRepeatStage1() {
            // repeatPenalty補正：stage1を返却
            return repeatStage1;
        }

        /**
         * repeatPenalty補正：stage1を設定
         */
        public void setRepeatStage1(double repeatStage1) {

            // repeatPenalty補正：stage1を更新
            this.repeatStage1 = repeatStage1;
        }

        /**
         * repeatPenalty補正：stage2を取得
         */
        public double getRepeatStage2() {
            // repeatPenalty補正：stage2を返却
            return repeatStage2;
        }

        /**
         * repeatPenalty補正：stage2を設定
         */
        public void setRepeatStage2(double repeatStage2) {

            // repeatPenalty補正：stage2を更新
            this.repeatStage2 = repeatStage2;
        }

        /**
         * repeatPenalty補正：stage3を取得
         */
        public double getRepeatStage3() {
            // repeatPenalty補正：stage3を返却
            return repeatStage3;
        }

        /**
         * repeatPenalty補正：stage3を設定
         */
        public void setRepeatStage3(double repeatStage3) {

            // repeatPenalty補正：stage3を更新
            this.repeatStage3 = repeatStage3;
        }

        /**
         * repeatPenalty の共有調整係数を取得
         */
        public double getRepeatShareAdjCoeff() {
            // repeatPenalty の共有調整係数を返却
            return repeatShareAdjCoeff;
        }

        /**
         * repeatPenalty の共有調整係数を設定
         */
        public void setRepeatShareAdjCoeff(double repeatShareAdjCoeff) {

            // repeatPenalty の共有調整係数を更新
            this.repeatShareAdjCoeff = repeatShareAdjCoeff;
        }

        /**
         * repeatPenalty の共有調整上限を取得
         */
        public double getRepeatShareAdjCap() {
            // repeatPenalty の共有調整上限を返却
            return repeatShareAdjCap;
        }

        /**
         * repeatPenalty の共有調整上限を設定
         */
        public void setRepeatShareAdjCap(double repeatShareAdjCap) {

            // repeatPenalty の共有調整上限を更新
            this.repeatShareAdjCap = repeatShareAdjCap;
        }

        /**
         * baseScore の exposure 成分の重みを取得
         */
        public double getBaseExposureWeight() {
            // baseScore の exposure 成分の重みを返却
            return baseExposureWeight;
        }

        /**
         * baseScore の exposure 成分の重みを設定
         */
        public void setBaseExposureWeight(double baseExposureWeight) {

            // baseScore の exposure 成分の重みを更新
            this.baseExposureWeight = baseExposureWeight;
        }

        /**
         * baseScore の userMatch 成分の重みを取得
         */
        public double getBaseUserMatchWeight() {
            // baseScore の userMatch 成分の重みを返却
            return baseUserMatchWeight;
        }

        /**
         * baseScore の userMatch 成分の重みを設定
         */
        public void setBaseUserMatchWeight(double baseUserMatchWeight) {

            // baseScore の userMatch 成分の重みを更新
            this.baseUserMatchWeight = baseUserMatchWeight;
        }

        /**
         * A/Bテスト用：Bパターンに加算するブースト値を取得
         */
        public double getAbBoostB() {
            // A/Bテスト用：Bパターンに加算するブースト値を返却
            return abBoostB;
        }

        /**
         * A/Bテスト用：Bパターンに加算するブースト値を設定
         */
        public void setAbBoostB(double abBoostB) {

            // A/Bテスト用：Bパターンに加算するブースト値を更新
            this.abBoostB = abBoostB;
        }

        /**
         * A/Bテスト用：ブースト適用方式を取得
         */
        public String getAbBoostType() {
            return abBoostType;
        }

        /**
         * A/Bテスト用：ブースト適用方式を設定
         */
        public void setAbBoostType(String abBoostType) {

            // 未設定の場合は既定のまま
            if (abBoostType == null || abBoostType.isBlank()) {
                return;
            }

            this.abBoostType = abBoostType.trim();
        }

        /**
         * A/Bテスト用：Aパターンのブースト値を取得
         */
        public double getAbBoostA() {
            return abBoostA;
        }

        /**
         * A/Bテスト用：Aパターンのブースト値を設定
         */
        public void setAbBoostA(double abBoostA) {
            this.abBoostA = abBoostA;
        }

        /**
         * モデル/固定係数が無い場合の既定スコア（A）を取得
         */
        public double getDefaultScoreA() {
            return defaultScoreA;
        }

        /**
         * モデル/固定係数が無い場合の既定スコア（A）を設定
         */
        public void setDefaultScoreA(double defaultScoreA) {
            this.defaultScoreA = defaultScoreA;
        }

        /**
         * モデル/固定係数が無い場合の既定スコア（B）を取得
         */
        public double getDefaultScoreB() {
            return defaultScoreB;
        }

        /**
         * モデル/固定係数が無い場合の既定スコア（B）を設定
         */
        public void setDefaultScoreB(double defaultScoreB) {
            this.defaultScoreB = defaultScoreB;
        }

        /**
         * A/Bテスト用：固定saltを取得
         */
        public String getAbSalt() {
            // A/Bテスト用：固定saltを返却
            return abSalt;
        }

        /**
         * A/Bテスト用：固定saltを設定
         */
        public void setAbSalt(String abSalt) {

            // 設定値が null の場合
            if (abSalt == null) {
                // 固定saltを空にして保持
                this.abSalt = "";
                return;
            }

            // 前後空白除去して保持
            this.abSalt = abSalt.trim();
        }

        /**
         * A/Bテスト用：Bパターンの割当割合（%）を取得
         */
        public int getAbBPercent() {
            // A/Bテスト用：Bパターンの割当割合（%）を返却
            return abBPercent;
        }

        /**
         * A/Bテスト用：Bパターンの割当割合（%）を設定
         */
        public void setAbBPercent(int abBPercent) {

            // 設定値を 0〜100 に丸めて保持
            this.abBPercent = Math.clamp(abBPercent, 0, 100);
        }

        /**
         * クライアントが送る「有効閲覧」の判定時間（ms）を取得
         */
        public int getViewMs() {
            return viewMs;
        }

        /**
         * クライアントが送る「有効閲覧」の判定時間（ms）を設定
         */
        public void setViewMs(int viewMs) {

            // 設定値が 0 より大きい場合のみ更新
            if (viewMs > 0) {
                this.viewMs = viewMs;
            }
        }

    }
}
