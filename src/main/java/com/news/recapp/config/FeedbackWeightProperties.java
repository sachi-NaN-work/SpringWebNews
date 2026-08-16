package com.news.recapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * フィードバックの重み設定を保持
 */
@Component
@ConfigurationProperties(prefix = "rec.feedback-weight")
public class FeedbackWeightProperties {

    // 出典キー毎の重み設定を保持
    private Map<String, Double> weights = new HashMap<>();

    /**
     * 出典キー毎の重み設定を取得
     */
    public Map<String, Double> getWeights() {
        // 出典キー毎の重み設定を返却
        return weights;
    }

    /**
     * 出典キー毎の重み設定を更新
     */
    public void setWeights(Map<String, Double> weights) {
        // 出典キー毎の重み設定を保持
        this.weights = weights;
    }

    /**
     * 出典キーに対応する重みを取得
     */
    public double weightOf(String source) {

        // 出典キーを初期化
        String src;
        // 出典キーが未指定または空か判定
        boolean isSourceBlank = (source == null || source.isBlank());

        // 出典キーが未指定または空の場合
        if (isSourceBlank) {
            // 出典キーに unknown を設定
            src = "unknown";
        } else {
            // 出典キーを小文字化して設定
            src = source.toLowerCase();
        }

        // 設定済みの重みを取得
        Double warning = weights.get(src);

        // 設定済みの重みが無い場合は既定重みを返却
        return Objects.requireNonNullElseGet(
            warning,
            () -> {

                // 出典キー毎の既定重みを返却
                return switch (src) {
                    // レコメンド由来の既定重みを返却
                    case "rec" -> 1.0;
                    // カテゴリ由来の既定重みを返却
                    case "category" -> 0.6;
                    // 新着由来の既定重みを返却
                    case "latest" -> 0.4;
                    // 外部ソース由来の既定重みを返却
                    case "external" -> 0.2;
                    // 未定義出典の既定重みを返却
                    default -> 0.3;
                };
            }
        );
    }
}
