package com.news.recapp.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 画面/外部連携で使うデータ定義
 */
public class ApiDtos {

    /**
     * カテゴリ
     */
    public record Category(UUID id, String slug, String name) {}

    /**
     * ニュース一覧の要約
     */
    public record NewsSummary(UUID id, UUID categoryId, String title, OffsetDateTime publishedAt) {}

    /**
     * ニュース詳細（本文下部でハッシュタグを表示するためハッシュタグを含む）
     */
    public record NewsDetail(
        UUID id,
        UUID categoryId,
        String title,
        String body,
        OffsetDateTime publishedAt,
        List<Hashtag> hashtags
    ) {}

    /**
     * ハッシュタグ（マスタ/表示用）
     */
    public record Hashtag(UUID id, String name) {}

    /**
     * 推薦結果の1件
     */
    public record RecItem(
            UUID newsId,
            UUID categoryId,
            String title,
            double score,
            String scoringPath,
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
            double abBoost,
            String abBoostType,
            double epsilon,
            double repeatPenalty,
            double scoreBeforeRepeatPenalty,
            double modelBias,
            List<Double> modelWeights
    ) {

        // 閲覧傾向の嗜好重み
        private static final double PREFERENCE_VIEW_WEIGHT = 0.20;
        // お気に入り傾向の嗜好重み
        private static final double PREFERENCE_FAVORITE_WEIGHT = 0.30;
        // Good 傾向の嗜好重み
        private static final double PREFERENCE_GOOD_WEIGHT = 0.50;
        // Bad 傾向の嗜好重み
        private static final double PREFERENCE_BAD_WEIGHT = 0.60;

        // カテゴリ嗜好の合成重み
        private static final double CATEGORY_MATCH_WEIGHT = 0.50;
        // タグ嗜好の合成重み
        private static final double TAG_MATCH_WEIGHT = 0.50;

        // 学習モデルの特徴量表示名
        private static final List<String> MODEL_FEATURE_LABELS = List.of(
                "各記事の基礎評価",
                "同一カテゴリ記事の閲覧割合",
                "同一カテゴリ記事のFav割合",
                "同一カテゴリ記事のGood割合",
                "同一カテゴリ記事のBad割合",
                "同一タグ記事の閲覧割合",
                "同一タグ記事のFav割合",
                "同一タグ記事のGood割合",
                "同一タグ記事のBad割合"
        );

        // 学習モデルの重み表示名
        private static final List<String> MODEL_FEATURE_WEIGHT_LABELS = List.of(
                "各記事の基礎評価重み",
                "カテゴリ閲覧重み",
                "カテゴリFav重み",
                "カテゴリGood重み",
                "カテゴリBad重み",
                "タグ閲覧重み",
                "タグFav重み",
                "タグGood重み",
                "タグBad重み"
        );

        // 正規化処理
        public RecItem {
            // 学習モデル重みが取得できない場合は空リストに補正
            if (modelWeights == null) {
                modelWeights = List.of();
            }
            // A/B ブースト方式が取得できない場合は加算方式に補正
            if (abBoostType == null || abBoostType.isBlank()) {
                abBoostType = "add";
            }
        }

        /**
         * スコア算出経路の表示名を返却
         */
        public String scoringMethodLabel() {

            // 学習モデルを使った場合
            if ("MODEL".equalsIgnoreCase(scoringPath)) {
                // 学習モデルの表示名を返却
                return "学習モデル";
            }

            // 固定係数を使った場合
            if ("FIXED".equalsIgnoreCase(scoringPath)) {
                // 固定係数の表示名を返却
                return "固定係数";
            }

            // 既定の計算方法として基本点を返却
            return "基本点";
        }

        /**
         * 推薦値の表示文字列を返却
         */
        public String scoreText() {
            // 推薦値を小数第4位で返却
            return fmt4(score);
        }

        /**
         * 推薦値の詳細計算を返却
         */
        public String recommendationDetailText() {

            // 推薦値の計算式を返却
            return "▼ 推薦値の計算\n\n" +
                    "● 推薦値\n" +
                    "= 基本評価 + 学習モデル評価 + 調整値\n" +
                    "= " + fmt4(baseScore) + " + " + fmt4(modelScore) + " " + formulaTerm(adjustmentValue()) + "\n" +
                    "= " + fmt4(score);
        }

        /**
         * 基本評価の詳細計算を返却
         */
        public String baseScoreDetailText() {

            // カテゴリ嗜好の各加算値を算出
            double categoryViewContribution = categoryViewShare * PREFERENCE_VIEW_WEIGHT;
            double categoryFavoriteContribution = categoryFavoriteShare * PREFERENCE_FAVORITE_WEIGHT;
            double categoryGoodContribution = categoryGoodShare * PREFERENCE_GOOD_WEIGHT;
            double categoryBadContribution = categoryBadShare * PREFERENCE_BAD_WEIGHT;

            // タグ嗜好の各加算値を算出
            double tagViewContribution = strongestTagViewShare * PREFERENCE_VIEW_WEIGHT;
            double tagFavoriteContribution = strongestTagFavoriteShare * PREFERENCE_FAVORITE_WEIGHT;
            double tagGoodContribution = strongestTagGoodShare * PREFERENCE_GOOD_WEIGHT;
            double tagBadContribution = strongestTagBadShare * PREFERENCE_BAD_WEIGHT;

            // ユーザー傾向一致度の各加算値を算出
            double categoryMatchContribution = categoryPreferenceScore * CATEGORY_MATCH_WEIGHT;
            double tagMatchContribution = tagPreferenceScore * TAG_MATCH_WEIGHT;

            // 基本評価の各加算値を算出
            double exposureContribution = exposureScore * baseExposureWeight;
            double userMatchContribution = userMatchScore * baseUserMatchWeight;

            // 基本評価の計算式を返却
            return "▼ 基本評価の計算\n\n" +
                    "基本評価は、各記事の基礎評価と、カテゴリ・タグのユーザー傾向から計算しています。\n" +
                    "（タグ嗜好は、記事に付いているタグのうち、ユーザー傾向が最も強いタグを使っています。）\n\n" +
                    "● カテゴリ嗜好\n" +
                    "= 同一カテゴリ記事の閲覧割合 × 閲覧重み\n" +
                    "+ 同一カテゴリ記事のFav割合 × Fav重み\n" +
                    "+ 同一カテゴリ記事のGood割合 × Good重み\n" +
                    "- 同一カテゴリ記事のBad割合 × Bad重み\n\n" +
                    "= " + fmt4(categoryViewShare) + " × " + fmt2(PREFERENCE_VIEW_WEIGHT) + "\n" +
                    "+ " + fmt4(categoryFavoriteShare) + " × " + fmt2(PREFERENCE_FAVORITE_WEIGHT) + "\n" +
                    "+ " + fmt4(categoryGoodShare) + " × " + fmt2(PREFERENCE_GOOD_WEIGHT) + "\n" +
                    "- " + fmt4(categoryBadShare) + " × " + fmt2(PREFERENCE_BAD_WEIGHT) + "\n\n" +
                    "= " + fmt4(categoryViewContribution) + " + " + fmt4(categoryFavoriteContribution) + " + " + fmt4(categoryGoodContribution) + " - " + fmt4(categoryBadContribution) + "\n" +
                    "= " + fmt4(categoryPreferenceScore) + "\n\n" +
                    "● タグ嗜好\n" +
                    "= 同一タグ記事の閲覧割合 × 閲覧重み\n" +
                    "+ 同一タグ記事のFav割合 × Fav重み\n" +
                    "+ 同一タグ記事のGood割合 × Good重み\n" +
                    "- 同一タグ記事のBad割合 × Bad重み\n\n" +
                    "= " + fmt4(strongestTagViewShare) + " × " + fmt2(PREFERENCE_VIEW_WEIGHT) + "\n" +
                    "+ " + fmt4(strongestTagFavoriteShare) + " × " + fmt2(PREFERENCE_FAVORITE_WEIGHT) + "\n" +
                    "+ " + fmt4(strongestTagGoodShare) + " × " + fmt2(PREFERENCE_GOOD_WEIGHT) + "\n" +
                    "- " + fmt4(strongestTagBadShare) + " × " + fmt2(PREFERENCE_BAD_WEIGHT) + "\n\n" +
                    "= " + fmt4(tagViewContribution) + " + " + fmt4(tagFavoriteContribution) + " + " + fmt4(tagGoodContribution) + " - " + fmt4(tagBadContribution) + "\n" +
                    "= " + fmt4(tagPreferenceScore) + "\n\n" +
                    "● ユーザー傾向一致度\n" +
                    "= カテゴリ嗜好 × カテゴリ重み\n" +
                    "+ タグ嗜好 × タグ重み\n\n" +
                    "= " + fmt4(categoryPreferenceScore) + " × " + fmt2(CATEGORY_MATCH_WEIGHT) + "\n" +
                    "+ " + fmt4(tagPreferenceScore) + " × " + fmt2(TAG_MATCH_WEIGHT) + "\n\n" +
                    "= " + fmt4(categoryMatchContribution) + " + " + fmt4(tagMatchContribution) + "\n" +
                    "= " + fmt4(userMatchScore) + "\n\n" +
                    "● 基本評価\n" +
                    "= 各記事の基礎評価 × 基礎評価重み\n" +
                    "+ ユーザー傾向一致度 × ユーザー傾向重み\n\n" +
                    "= " + fmt4(exposureScore) + " × " + fmt2(baseExposureWeight) + "\n" +
                    "+ " + fmt4(userMatchScore) + " × " + fmt2(baseUserMatchWeight) + "\n\n" +
                    "= " + fmt4(exposureContribution) + " + " + fmt4(userMatchContribution) + "\n" +
                    "= " + fmt4(baseScore);
        }

        /**
         * 学習モデル評価の詳細計算を返却
         */
        public String modelScoreDetailText() {

            // 学習モデルの説明を初期化
            StringBuilder builder = new StringBuilder();
            builder.append("▼ 学習モデル評価の計算\n\n");
            builder.append("学習モデル評価は、各記事の基礎評価・同一カテゴリ記事の割合・同一タグ記事の割合を学習済みモデルに入力して算出しています。\n");
            builder.append("（学習モデルの重みは、学習データから自動的に決まります。）\n\n");

            // 学習モデルの線形係数が取得できていない場合
            if (modelWeights == null || modelWeights.isEmpty()) {

                // 表示値のみを表示
                builder.append("● 表示値：").append(fmt4(modelScore));

                // 表示値のみを返却
                return builder.toString();
            }

            // 特徴量一覧を取得
            List<Double> featureValues = modelFeatureValues();

            // 見出しを表示
            builder.append("● 学習モデル評価\n");

            // 表示対象件数を取得
            int indexCount = Math.min(
                    Math.min(
                            // 学習モデルの重み件数と特徴量件数の小さい方を取得
                            Math.min(
                                    modelWeights.size(),
                                    featureValues.size()
                            ),
                            // 表示ラベルの件数を取得
                            MODEL_FEATURE_LABELS.size()
                    ),
                    // 重みラベルの件数を取得
                    MODEL_FEATURE_WEIGHT_LABELS.size()
            );

            // 文字式の切片を表示
            builder.append("= 学習モデルの基準点（切片）\n");

            // 表示対象件数分の文字式を表示
            for (int index = 0; index < indexCount; index++) {
                // 特徴量名と重み名から文字式行を追加
                builder.append(
                        modelFormulaLabelLine(
                                MODEL_FEATURE_LABELS.get(index),
                                MODEL_FEATURE_WEIGHT_LABELS.get(index)
                        )
                );
            }

            // 文字式と数値式の間に空行を追加
            builder.append("\n");

            // 数値式の切片を表示
            builder.append("= ").append(fmt7(modelBias)).append("\n");

            // 表示対象件数分の数値式を表示
            for (int index = 0; index < indexCount; index++) {
                // 特徴量値と重みから数値式行を追加
                builder.append(
                        modelFormulaValueLine(
                                featureValues.get(index),
                                modelWeights.get(index)
                        )
                );
            }

            // 線形計算値を表示
            builder.append("\n= ").append(fmt4(modelLinearScore())).append("\n\n");

            // 計算値を返却
            return builder.toString();
        }

        /**
         * 調整値の詳細計算を返却
         */
        public String adjustmentDetailText() {

            // 調整前スコアを算出
            double rawScore = modelScore + baseScore;

            // 調整値の計算式を初期化
            StringBuilder builder = new StringBuilder();
            builder.append("▼ 調整値の計算\n\n");
            builder.append("調整値は、A/Bテスト補正、微小調整、既読ペナルティなどを反映した値です。\n\n");
            builder.append("① 調整前スコア\n");
            builder.append("= 学習モデル評価 + 基本評価\n");
            builder.append("= ").append(fmt4(modelScore)).append(" + ").append(fmt4(baseScore)).append("\n");
            builder.append("= ").append(fmt4(rawScore)).append("\n\n");

            // A/B ブースト方式が乗算の場合
            if ("multiply".equalsIgnoreCase(abBoostType)) {
                // 乗算方式の調整計算を表示
                builder.append("② A/B・微小調整後\n");
                builder.append("= 調整前スコア × A/Bテスト係数 + 微小調整\n");
                builder.append("= ").append(fmt4(rawScore)).append(" × ").append(fmt4(abBoost)).append(" + ").append(fmt4(epsilon)).append("\n");
                builder.append("= ").append(fmt4(scoreBeforeRepeatPenalty)).append("\n\n");
            } else {
                // 加算方式の調整計算を表示
                builder.append("② A/B・微小調整後\n");
                builder.append("= 調整前スコア + A/Bテスト補正 + 微小調整\n");
                builder.append("= ").append(fmt4(rawScore)).append(" + ").append(fmt4(abBoost)).append(" + ").append(fmt4(epsilon)).append("\n");
                builder.append("= ").append(fmt4(scoreBeforeRepeatPenalty)).append("\n\n");
            }

            // 既読ペナルティ反映後の計算を表示
            builder.append("③ 既読ペナルティ反映後\n");
            builder.append("= A/B・微小調整後 × 既読ペナルティ係数\n");
            builder.append("= ").append(fmt4(scoreBeforeRepeatPenalty)).append(" × ").append(fmt4(repeatPenalty)).append("\n");
            builder.append("= ").append(fmt4(score)).append("\n\n");

            builder.append("● 調整値\n");
            builder.append("= 推薦値 - 学習モデル評価 - 基本評価\n");
            builder.append("= ").append(fmt4(score)).append(" - ").append(fmt4(modelScore)).append(" - ").append(fmt4(baseScore)).append("\n");
            builder.append("= ").append(signedFmt4(adjustmentValue())).append("\n\n");
            return builder.toString();
        }

        /**
         * 調整値を算出
         */
        public double adjustmentValue() {
            // 推薦値から学習モデル評価と基本評価を差し引いた値を返却
            return score - modelScore - baseScore;
        }

        /**
         * 計算式用の符号付き項を返却
         */
        private static String formulaTerm(double value) {

            // 負数の場合
            if (value < 0.0) {
                // マイナス記号付きの項を返却
                return "- " + fmt4(Math.abs(value));
            }

            // プラス記号付きの項を返却
            return "+ " + fmt4(value);
        }

        /**
         * 学習モデルの特徴量一覧を返却
         */
        private List<Double> modelFeatureValues() {
            // 学習モデルへ入力した順番で特徴量を返却
            return List.of(
                    exposureScore,
                    categoryViewShare,
                    categoryFavoriteShare,
                    categoryGoodShare,
                    categoryBadShare,
                    strongestTagViewShare,
                    strongestTagFavoriteShare,
                    strongestTagGoodShare,
                    strongestTagBadShare
            );
        }

        /**
         * 学習モデルの線形計算値を算出
         */
        private double modelLinearScore() {

            // 特徴量一覧を取得
            List<Double> featureValues = modelFeatureValues();
            // 切片から計算を開始
            double calculatedScore = modelBias;
            // 利用可能な範囲で内積を計算
            int count = Math.min(modelWeights.size(), featureValues.size());

            // 特徴量を順に加算
            for (int index = 0; index < count; index++) {
                // 重み付き特徴量を加算
                calculatedScore += modelWeights.get(index) * featureValues.get(index);
            }

            // 線形計算値を返却
            return calculatedScore;
        }

        /**
         * 学習モデル計算の文字式行を返却
         */
        private static String modelFormulaLabelLine(String featureLabel, String weightLabel) {

            // 文字式行を返却
            return "+ " + featureLabel + " × " + weightLabel + "\n";
        }

        /**
         * 学習モデル計算の数値式行を返却
         */
        private static String modelFormulaValueLine(double featureValue, double weight) {

            // 数値式行を返却
            return "+ " + fmt4(featureValue) + " × " + fmt7(weight) + "\n";
        }

        /**
         * 小数第2位で数値を整形
         */
        private static String fmt2(double value) {
            // 小数第2位の文字列を返却
            return String.format(Locale.ROOT, "%.2f", finite(value));
        }

        /**
         * 小数第4位で数値を整形
         */
        private static String fmt4(double value) {
            // 小数第4位の文字列を返却
            return String.format(Locale.ROOT, "%.4f", finite(value));
        }

        /**
         * 小数第4位で符号付き数値を整形
         */
        private static String signedFmt4(double value) {
            // 小数第4位の符号付き文字列を返却
            return String.format(Locale.ROOT, "%+.4f", finite(value));
        }

        /**
         * 小数第7位で数値を整形
         */
        private static String fmt7(double value) {
            // 小数第7位の文字列を返却
            return String.format(Locale.ROOT, "%.7f", finite(value));
        }

        /**
         * 非有限値を 0 に補正
         */
        private static double finite(double value) {

            // NaN/Infinity の場合
            if (!Double.isFinite(value)) {
                // 表示用に 0 を返却
                return 0.0;
            }

            // 有限値を返却
            return value;
        }
    }

    /**
     * 推薦結果
     */
    public record RecResponse(
            String userId,
            String ab,
            int topK,
            long tookMs,
            List<RecItem> items
    ) {}

    /**
     * フィードバック送信リクエスト
     */
    public record FeedbackRequest(
            UUID newsId,
            String feedbackType,
            String source,
            String eventId,
            String requestId
    ) {}

    /**
     * フィードバック送信レスポンス
     */
    public record FeedbackResponse(
            String status
    ) {}

}
