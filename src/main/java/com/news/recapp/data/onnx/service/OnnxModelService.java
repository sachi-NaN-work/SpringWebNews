package com.news.recapp.data.onnx.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.news.recapp.application.port.out.ModelRuntimePort;
import com.news.recapp.util.Messages;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

/**
 * ONNX Runtime を用いた推論サービス。
 *
 * 設定された単一モデルを読み込み、推薦スコアを算出する。
 * モデルが無い、または読込に失敗した場合は無効状態として扱い、
 * 呼び出し側は rule-based/fixed へフォールバックする。
 */
@Service
public class OnnxModelService implements ModelRuntimePort {

    // ロガーを保持
    private static final Logger log = LoggerFactory.getLogger(OnnxModelService.class);

    // モデルリソース読み込みを保持
    private final ResourceLoader resourceLoader;

    // ONNX ランタイム環境を保持
    private final OrtEnvironment env = OrtEnvironment.getEnvironment();

    // 現在のモデルパスを保持
    private volatile String currentModelPath;

    // 現在の ONNX セッションを保持
    private volatile OrtSession session;

    // 入力名を保持
    private volatile String inputName;

    // モデルが期待する特徴量数（不明な場合は -1）を保持
    private volatile int expectedFeatureSize = -1;

    /**
     * コンストラクタ。
     */
    public OnnxModelService(
            ResourceLoader resourceLoader,
            @Value("${rec.model.path:./model/rec.onnx}") String modelPath
    ) {
        this.resourceLoader = resourceLoader;
        this.currentModelPath = modelPath;
    }

    /**
     * 初期化時にモデルを読込
     */
    @PostConstruct
    public void init() {
        // 起動時モデルを読込
        load(currentModelPath);
    }

    /**
     * 終了時にセッションを解放
     */
    @PreDestroy
    public void destroy() {
        // 現在のセッションを解放
        closeSession(session);
        // セッション参照をクリア
        session = null;
    }

    /**
     * モデルが有効かどうかを返却
     */
    @Override
    public boolean isEnabled() {
        // セッションがあれば有効
        return session != null;
    }

    /**
     * 現在のモデルパスを返却
     */
    @Override
    public String getCurrentModelPath() {
        // 現在のモデルパスを返却
        return currentModelPath;
    }

    /**
     * モデルを再読込
     */
    @Override
    public void reload() {
        // 現在のモデルパスで再読込
        load(currentModelPath);
    }

    /**
     * 指定したモデルパスで再読込
     */
    @Override
    public void reload(String newModelPath) {
        // 新しいモデルパスを保持
        this.currentModelPath = newModelPath;
        // 指定パスで再読込
        load(this.currentModelPath);
    }

    /**
     * 推論スコアを返却
     */
    @Override
    public double score(float[] features) {

        // モデルが無効な場合
        if (session == null) {
            // 呼び出し側フォールバック用に 0 を返却
            return 0.0d;
        }

        // 特徴量が未指定の場合
        if (features == null) {
            // 呼び出し側フォールバック用に 0 を返却
            return 0.0d;
        }

        // 次元不一致の場合
        if (expectedFeatureSize > 0 && features.length != expectedFeatureSize) {
            // 次元不一致は warn を出して 0 を返却
            log.warn(Messages.getMsg("log.onnx.feature_size_mismatch"), expectedFeatureSize, features.length);
            return 0.0d;
        }

        try {
            // ONNX 入力テンソルを作成
            try (OnnxTensor tensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(features),
                    new long[]{1, features.length}
            )) {

                // 入力マップを生成
                Map<String, OnnxTensor> inputs = Collections.singletonMap(inputName, tensor);

                // 推論を実行
                try (OrtSession.Result result = session.run(inputs)) {

                    // 先頭出力を取得
                    Object value = result.get(0).getValue();

                    // float[][] の場合
                    if (value instanceof float[][] values2d
                            && values2d.length > 0
                            && values2d[0].length > 0) {
                        // 先頭要素を返却
                        return values2d[0][0];
                    }

                    // float[] の場合
                    if (value instanceof float[] values1d && values1d.length > 0) {
                        // 先頭要素を返却
                        return values1d[0];
                    }

                    // double[][] の場合
                    if (value instanceof double[][] values2d
                            && values2d.length > 0
                            && values2d[0].length > 0) {
                        // 先頭要素を返却
                        return values2d[0][0];
                    }

                    // double[] の場合
                    if (value instanceof double[] values1d && values1d.length > 0) {
                        // 先頭要素を返却
                        return values1d[0];
                    }

                    // 想定外の出力形式は 0 を返却
                    log.warn(Messages.getMsg("log.onnx.unexpected_output_type"), value == null ? "null" : value.getClass().getName());
                    return 0.0d;
                }
            }
        } catch (Exception exception) {
            // 推論失敗は warn を出して 0 を返却
            log.warn(Messages.getMsg("log.onnx.score_failed"), currentModelPath, exception);
            return 0.0d;
        }
    }

    /**
     * 指定したモデルパスを読込
     */
    private synchronized void load(String modelPath) {

        // 現在セッションを退避
        OrtSession oldSession = this.session;

        try {
            // モデル実体ファイルパスを解決
            Path resolvedPath = resolveModelPath(modelPath);

            // ファイル未存在の場合
            if (resolvedPath == null || !Files.exists(resolvedPath)) {
                // セッションを無効化
                this.session = null;
                this.inputName = null;
                this.expectedFeatureSize = -1;

                // モデル未発見ログを出力
                log.warn(Messages.getMsg("log.onnx.model_not_found"), modelPath);
                return;
            }

            // ONNX セッションオプションを生成
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();

            // セッションを新規生成
            OrtSession newSession = env.createSession(resolvedPath.toString(), options);

            // 入力名を取得
            String newInputName = newSession.getInputNames().iterator().next();

            // 入力次元を推定
            int newExpectedFeatureSize = extractExpectedFeatureSize(newSession, newInputName);

            // 新しいセッションを反映
            this.session = newSession;
            this.inputName = newInputName;
            this.expectedFeatureSize = newExpectedFeatureSize;

            // 旧セッションを解放
            closeSession(oldSession);

            // 読込成功ログを出力
            log.info(Messages.getMsg("log.onnx.model_loaded"),
                    resolvedPath, newInputName, newExpectedFeatureSize);

        } catch (Exception exception) {
            // 新規読込失敗時は無効化
            this.session = null;
            this.inputName = null;
            this.expectedFeatureSize = -1;

            // 旧セッションを解放
            closeSession(oldSession);

            // 読込失敗ログを出力
            log.warn(Messages.getMsg("log.onnx.load_failed"), modelPath, exception.toString());

        }
    }

    /**
     * モデルパス文字列を実体ファイルパスへ解決
     *
     * 対応形式:
     * - classpath:model/rec.onnx
     * - file:/app/model/rec.onnx
     * - file:///app/model/rec.onnx
     * - file:./model/rec.onnx
     * - ./model/rec.onnx
     */
    private Path resolveModelPath(String rawPath) throws IOException {

        // モデルパス未指定の場合
        if (isBlank(rawPath)) {
            // 解決不可として null を返却
            return null;
        }

        // 前後空白を除去
        String path = rawPath.trim();

        // classpath 指定の場合
        if (path.startsWith("classpath:")) {

            // Resource を取得
            Resource resource = resourceLoader.getResource(path);

            // Resource が存在しない場合
            if (!resource.exists()) {
                // 解決不可として null を返却
                return null;
            }

            // ファイルシステム上のパスへ解決して返却
            return resource.getFile().toPath();
        }

        // file:./... のような相対 file URI の場合
        if (path.startsWith("file:./") || path.startsWith("file:../")) {
            // file: を外して通常パスとして解決
            return Paths.get(path.substring("file:".length())).normalize();
        }

        // file:/... または file:///... のような絶対 file URI の場合
        if (path.startsWith("file:/")) {
            // Resource を取得
            Resource resource = resourceLoader.getResource(path);

            // Resource が存在しない場合
            if (!resource.exists()) {
                // 解決不可として null を返却
                return null;
            }

            // ファイルシステム上のパスへ解決して返却
            return resource.getFile().toPath();
        }

        // それ以外は通常のファイルパスとして解決
        return Paths.get(path).normalize();
    }

    /**
     * 入力テンソルの最終次元から想定特徴量数を抽出
     */
    private int extractExpectedFeatureSize(OrtSession session, String inputName) {
        try {
            // 入力情報を取得
            var inputInfo = session.getInputInfo().get(inputName);

            // テンソル形状でない場合
            if (!(inputInfo.getInfo() instanceof ai.onnxruntime.TensorInfo tensorInfo)) {
                // 不明として -1 を返却
                return -1;
            }

            // テンソル形状を取得
            long[] shape = tensorInfo.getShape();

            // 形状未設定の場合
            if (shape == null || shape.length == 0) {
                // 不明として -1 を返却
                return -1;
            }

            // 最終次元を取得
            long lastDim = shape[shape.length - 1];

            // 最終次元が正なら int 化して返却
            if (lastDim > 0 && lastDim <= Integer.MAX_VALUE) {
                return (int) lastDim;
            }

            // 不明として -1 を返却
            return -1;

        } catch (Exception exception) {
            // 取得失敗時は不明扱い
            return -1;
        }
    }

    /**
     * セッションを安全に解放
     */
    private void closeSession(OrtSession target) {

        // セッション未設定の場合
        if (target == null) {
            // 何もしない
            return;
        }

        try {
            // セッションを解放
            target.close();
        } catch (OrtException exception) {
            // 解放失敗は warn のみ
            log.warn(Messages.getMsg("log.onnx.close_session_failed"), exception);
        }
    }

    /**
     * 空白文字列かどうかを判定
     */
    private static boolean isBlank(String value) {
        // null または空白のみなら true
        return value == null || value.trim().isEmpty();
    }
}