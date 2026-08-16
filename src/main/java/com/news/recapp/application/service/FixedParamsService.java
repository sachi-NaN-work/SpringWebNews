package com.news.recapp.application.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.news.recapp.util.Messages;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 学習済みの固定パラメータを読み込み、モデル無し時のスコア計算に利用する。
 *
 * - モデル(ONNX)が無い/無効な場合は、最後に学習された係数(weights,bias)を固定値として利用する。
 * - 学習側（training）では rec.onnx と同階層へ rec_fixed_params.json を出力する。
 */
@Service
public class FixedParamsService {

    // ロガーを保持
    private static final Logger log = LoggerFactory.getLogger(FixedParamsService.class);

    // モデルディレクトリ
    private static final Path MODEL_DIR = Paths.get("./model");

    // 固定パラメータファイル（単一モデル）
    private static final Path FIXED = MODEL_DIR.resolve("rec_fixed_params.json");

    // フィードバックイベントをJSONへ変換するためのObjectMapper
    private final ObjectMapper objectMapper;

    // 読み込んだ固定パラメータ
    private volatile FixedParams params;

    // コンストラクタ
    public FixedParamsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 初期化時に固定パラメータ設定をファイルから読み込む
     */
    @PostConstruct
    public void init() {
        reloadFromDisk();
    }

    /**
     * ディスク上の固定パラメータを再読み込み
     */
    public synchronized void reloadFromDisk() {
        this.params = loadOne();
    }

    /**
     * 固定パラメータを取得
     */
    public FixedParams get() {
        return params;
    }

    /**
     * ディスク上の固定パラメータを再読込
     */
    private FixedParams loadOne() {

        try {

            // 読み込み対象のファイルが存在しない場合
            if (!Files.exists(FixedParamsService.FIXED)) {
                // null を返却
                return null;
            }

            // 固定係数 JSON を読み込む
            byte[] bytes = Files.readAllBytes(FixedParamsService.FIXED);

            // JSON から固定パラメータを復元
            FixedParams fixedParams = objectMapper.readValue(bytes, FixedParams.class);

            // ログに出力
            log.info(Messages.getMsg("log.fixed_params.loaded"), FixedParamsService.FIXED);

            // 固定パラメータを返却
            return fixedParams;

        } catch (Exception ex) {

            // ログに出力
            log.warn(Messages.getMsg("log.fixed_params.load_failed"), FixedParamsService.FIXED, ex.toString());

            // null を返却
            return null;
        }
    }

    /**
     * 学習済み固定パラメータを計算
     */
    public record FixedParams(
            @JsonProperty("feature_order") List<String> featureOrder,
            @JsonProperty("weights") double[] weights,
            @JsonProperty("bias") double bias,
            @JsonProperty("default_score") double defaultScore
    ) {
        /**
         * dot(weights, x) + bias を計算
         */
        public double score(double[] features) {

            // 特徴量または重みが未設定の場合はデフォルト値を返す
            if (features == null || features.length == 0 ||
                    weights == null || weights.length == 0) {
                return defaultScore;
            }

            // 利用可能な要素数の範囲で内積を計算
            int featureCount = Math.min(features.length, weights.length);

            // 切片からスコア計算を開始
            double score = bias;
            for (int index = 0; index < featureCount; index++) {
                score += weights[index] * features[index];
            }

            // スコアを返却
            return score;
        }
    }
}
