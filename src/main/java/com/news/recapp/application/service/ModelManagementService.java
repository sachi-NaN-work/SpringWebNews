package com.news.recapp.application.service;

import com.news.recapp.application.exception.AppMessageException;
import com.news.recapp.application.port.out.ModelRuntimePort;
import com.news.recapp.util.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * モデル管理の業務処理を提供するサービス。
 *
 * 管理画面の「直近ログ」にそのまま表示しやすい形で、
 * 学習・再読込・アップロードの操作ログを 1 本の文字列として保持
 */
@Service
public class ModelManagementService {

    // ロガーを保持
    private static final Logger log =
            LoggerFactory.getLogger(ModelManagementService.class);

    // モデル格納ディレクトリを保持
    public static final Path MODEL_DIR =
            Paths.get("./model");

    // ONNX モデルファイルを保持
    public static final Path MODEL_FILE =
            MODEL_DIR.resolve("rec.onnx");

    // 学習中の一時 ONNX モデルファイルを保持
    public static final Path MODEL_FILE_TMP =
            MODEL_DIR.resolve("rec.onnx.tmp");

    // 学習結果レポートファイルを保持
    public static final Path MODEL_REPORT_FILE =
            MODEL_DIR.resolve("rec_report.json");

    // 学習中の一時レポートファイルを保持
    public static final Path MODEL_REPORT_FILE_TMP =
            MODEL_DIR.resolve("rec_report.tmp.json");

    // 管理画面の直近実行ログを永続化するファイルを保持
    public static final Path MODEL_OPERATION_LOG_FILE =
            MODEL_DIR.resolve("last_model_operation.log");

    // file: 形式で再読込するためのモデルパスを保持
    private static final String MODEL_FILE_RESOURCE =
            "file:" + MODEL_FILE.toString().replace('\\', '/');

    // モデルランタイムを保持
    private final ModelRuntimePort modelRuntime;

    // 固定係数サービスを保持
    private final FixedParamsService fixedParamsService;

    // 環境設定を保持
    private final Environment env;

    // 管理画面の「直近ログ」に表示する文字列を保持
    private volatile String lastModelOperationLog = "";

    // タイムアウト秒数を保持
    private static final int timeoutSeconds  = 120;

    // コンストラクタ
    public ModelManagementService(
            ModelRuntimePort modelRuntime,
            FixedParamsService fixedParamsService,
            Environment env
    ) {
        this.modelRuntime = modelRuntime;
        this.fixedParamsService = fixedParamsService;
        this.env = env;
        this.lastModelOperationLog = loadLastModelOperationLog();
    }

    /**
     * 直近の学習モデル操作ログを取得
     */
    public String getLastModelOperationLog() {
        // 画面表示用の最新ログ文字列を返却
        return lastModelOperationLog;
    }

    /**
     * 永続化済みの直近ログを読み込む
     */
    private String loadLastModelOperationLog() {
        try {

            // 永続化されたログファイルが存在する場合
            if (Files.exists(MODEL_OPERATION_LOG_FILE)) {

                // ログファイルから読み込んだ情報を返却する
                return Files.readString(
                        MODEL_OPERATION_LOG_FILE,
                        StandardCharsets.UTF_8
                );

            }

        } catch (Exception exception) {

            // ログの復元失敗時は、警告ログを出力して処理を継続する
            log.warn(
                    "直近の学習モデル操作ログを読み込めませんでした: {}",
                    MODEL_OPERATION_LOG_FILE,
                    exception
            );
        }

        // 保存済みログが存在しない、または読み込みに失敗した場合は空文字を返す
        return "";
    }

    /**
     * 直近ログをメモリとファイルの両方へ保存する
     */
    private synchronized void saveLastModelOperationLog(String value) {

        // 管理画面側で扱いやすいよう空文字へ正規化する
        lastModelOperationLog = value == null ? "" : value;

        try {

            // 保存先ディレクトリが存在しない場合は作成する
            Files.createDirectories(MODEL_DIR);

            // 直近ログをUTF-8でファイルへ永続化する
            Files.writeString(
                    MODEL_OPERATION_LOG_FILE,
                    lastModelOperationLog,
                    StandardCharsets.UTF_8
            );

        } catch (Exception exception) {

            // ファイル保存失敗時は、警告ログのみ出力する
            log.warn(
                    "直近の学習モデル操作ログを保存できませんでした: {}",
                    MODEL_OPERATION_LOG_FILE,
                    exception
            );
        }
    }

    /**
     * 既存の学習モデルの存在有無を取得
     */
    public boolean existsManagedModelFile() {

        // 既存の学習モデルの存在有無を返却
        return Files.exists(MODEL_FILE);

    }

    /**
     * ディスク上のモデルを再読込
     */
    public Mono<Void> reloadFromDisk(String loginUserId) {

        // ブロッキング処理を別スレッドで実行する Mono を組み立てる
        return Mono.fromRunnable(() -> {

                    // 画面へ出すログをためる文字列バッファを作成
                    StringBuilder sb = new StringBuilder();

                    // 操作種別をログへ出力
                    sb.append("[OPERATION] 実行操作=[既存の学習モデルを再読込]\n");

                    // 開始時刻の取得
                    String startedAt = OffsetDateTime.now(ZoneId.of("Asia/Tokyo"))
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    // 開始時刻をログへ出力
                    sb.append("[OPERATION] 開始時刻=").append(startedAt).append("\n");
                    // 実行ユーザーIDをログへ出力
                    sb.append("[OPERATION] 実行ユーザーID=********\n");

                    try {

                        // 再読込前に学習済みファイルが存在するかを判定
                        boolean fileExists = Files.exists(MODEL_FILE);
                        // モデル本体と固定係数を静かに再読込
                        reloadModelSilently();

                        // どこから再読込したかをログへ出力
                        sb.append("[RELOAD] 既存モデル=").append(fileExists ? "あり" : "なし").append("\n");
                        // 再読込後に現在使われている学習モデルパスをログへ出力
                        sb.append("[RELOAD] 学習モデルパス=").append(modelRuntime.getCurrentModelPath()).append("\n");
                        // 再読込後にモデルが有効かどうかをログへ出力
                        sb.append("[RELOAD] 学習モデルが有効/無効=").append(modelRuntime.isEnabled()).append("\n");

                        if (fileExists) {
                            // 既存モデルあり
                            sb.append("[RELOAD] exitCode=0").append("\n");
                        } else {
                            // 既存モデルなし（config 側へフォールバック）
                            sb.append("[RELOAD] exitCode=2").append("\n");
                            sb.append("[RELOAD] 警告=既存モデルなし").append("\n");
                        }

                        // 完成したログを直近ログへ保存
                        saveLastModelOperationLog(sb.toString());
                        // サーバーログにも同じ内容を出力
                        log.info(Messages.getMsg("log.model.reload_from_disk.completed"), lastModelOperationLog);

                    } catch (Exception exception) {

                        // 再読込前に学習済みファイルが存在するかを判定
                        boolean fileExists = Files.exists(MODEL_FILE);

                        // どこから再読込したかをログへ出力
                        sb.append("[RELOAD] 既存モデル=").append(fileExists ? "あり" : "なし").append("\n");
                        // 再読込後に現在使われているモデルパスをログへ出力
                        sb.append("[RELOAD] 学習モデルパス=").append(modelRuntime.getCurrentModelPath()).append("\n");
                        // 再読込後にモデルが有効かどうかをログへ出力
                        sb.append("[RELOAD] 学習モデルが有効/無効=").append(modelRuntime.isEnabled()).append("\n");
                        // 再読込後に終了コードをログへ出力
                        sb.append("[RELOAD] exitCode=1").append("\n");

                        // 失敗時のログも直近ログへ保存
                        saveLastModelOperationLog(sb.toString());
                        // サーバーログへ失敗内容を出力
                        log.warn(Messages.getMsg("log.model.reload_from_disk.failed"), lastModelOperationLog);

                        // RuntimeException はそのまま再送出
                        if (exception instanceof RuntimeException runtimeException) {
                            throw runtimeException;
                        }
                        // それ以外は RuntimeException に包んで再送出
                        throw new RuntimeException(exception);
                    }
                })
                // ブロッキング処理なので boundedElastic へ切替
                .subscribeOn(Schedulers.boundedElastic())
                // 完了シグナルだけを呼び出し元へ返却
                .then();
    }

    /**
     * ONNX ファイルをアップロードして再読込
     */
    public Mono<Void> uploadAndReload(FilePart filePart, String loginUserId) {

        // アップロードファイル未指定の場合
        if (filePart == null) {
            // 例外を返却
            return Mono.error(new AppMessageException("model.ctrl.err.file.required"));
        }

        // まずモデル格納ディレクトリを作成する処理を組み立てる
        return Mono.fromRunnable(() -> {
                    try {
                        // モデル格納ディレクトリを作成
                        Files.createDirectories(MODEL_DIR);
                    } catch (Exception exception) {
                        // ディレクトリ作成失敗は RuntimeException に包む
                        throw new RuntimeException(exception);
                    }
                })
                // 受け取ったファイルを rec.onnx として保存
                .then(filePart.transferTo(MODEL_FILE))
                // 保存後に再読込とログ保存を行う
                .then(Mono.fromRunnable(() -> {
                    // 画面へ出すログをためる文字列バッファを作成
                    StringBuilder sb = new StringBuilder();
                    // 操作種別をログへ出力
                    sb.append("[OPERATION] 実行操作=[新規の学習モデルを作成して読込]\n");
                    // 開始時刻の取得
                    String startedAt = OffsetDateTime.now(ZoneId.of("Asia/Tokyo"))
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    // 開始時刻をログへ出力
                    sb.append("[OPERATION] 開始時刻=").append(startedAt).append("\n");
                    // 実行ユーザーIDをログへ出力
                    sb.append("[OPERATION] 実行ユーザーID=********\n");
                    // 保存先パスをログへ出力
                    sb.append("[upload] 保存先パス=").append(MODEL_FILE).append("\n");

                    try {
                        // 保存した ONNX を再読込
                        reloadModelSilently();
                        // 再読込後の実際のモデルパスをログへ出力
                        sb.append("[RELOAD] 学習モデルパス=").append(modelRuntime.getCurrentModelPath()).append("\n");
                        // 再読込後にモデルが有効かどうかをログへ出力
                        sb.append("[RELOAD] 学習モデルが有効/無効=").append(modelRuntime.isEnabled()).append("\n");

                        // 完成したログを直近ログへ保存
                        saveLastModelOperationLog(sb.toString());
                        // サーバーログにも同じ内容を出力
                        log.info(Messages.getMsg("log.model.upload_and_reload.completed"), lastModelOperationLog);

                    } catch (Exception exception) {

                        // 例外内容を画面用ログへ追記
                        sb.append("[OPERATION] エラー=").append(exception).append("\n");
                        // 失敗時のログも直近ログへ保存
                        saveLastModelOperationLog(sb.toString());
                        // サーバーログへ失敗内容を出力
                        log.warn(Messages.getMsg("log.model.upload_and_reload.failed"), lastModelOperationLog);

                        // RuntimeException はそのまま再送出
                        if (exception instanceof RuntimeException runtimeException) {
                            throw runtimeException;
                        }
                        // それ以外は RuntimeException に包んで再送出
                        throw new RuntimeException(exception);
                    }
                    // 再読込もブロッキング処理なので boundedElastic で実行
                }).subscribeOn(Schedulers.boundedElastic()))
                // 完了シグナルだけを呼び出し元へ返却
                .then();
    }

    /**
     * Python で学習を実行し、その後にモデルを再読込
     *
     * 管理画面には学習ログを見せたいので、
     * 余計な begin/end や reload 詳細は直近ログへ出さない。
     */
    public Mono<Void> trainAndReload(String loginUserId) {

        // ブロッキング処理を別スレッドで実行する Mono を組み立てる
        return Mono.fromRunnable(() -> {
                    // 画面へ出すログをためる文字列バッファを作成
                    StringBuilder sb = new StringBuilder();
                    // 操作種別をログへ出力
                    sb.append("[OPERATION] 実行操作=[新規の学習モデルを作成して読込]\n");
                    // 開始時刻の取得
                    String startedAt = OffsetDateTime.now(ZoneId.of("Asia/Tokyo"))
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    // 開始時刻をログへ出力
                    sb.append("[OPERATION] 開始時刻=").append(startedAt).append("\n");
                    // 実行ユーザーIDをログへ出力
                    sb.append("[OPERATION] 実行ユーザーID=********\n");

                    try {
                        // モデル格納ディレクトリを事前に作成
                        Files.createDirectories(MODEL_DIR);

                        // 学習側へ渡す application.yml の候補を探索
                        Path appYaml = findAppYaml();
                        // 実際に使う設定ファイルパスをログへ出力
                        sb.append("[TRAIN] 設定ファイル（appYaml）パス=").append(appYaml).append("\n");
                        // 出力先 ONNX ファイルパスをログへ出力
                        sb.append("[TRAIN] ONNX ファイルパス=").append(MODEL_FILE_TMP).append("\n");

                        // Python 学習を実行し、標準出力をそのままログへ追記
                        runTraining(appYaml, sb);

                        // 学習成功した生成物を本番ファイル名へ置換
                        renameTrainingOutputs(sb);

                        try {
                            // 学習成功後にモデル本体と固定係数を読込
                            reloadModelSilently();
                            // 読込成功
                            sb.append("[LOAD] exitCode=0").append("\n");
                        } catch (Exception reloadException) {
                            // 読込失敗
                            sb.append("[LOAD] exitCode=1").append("\n");
                            throw reloadException;
                        }

                        // 完成したログを直近ログへ保存
                        saveLastModelOperationLog(sb.toString());

                        // サーバーログにも同じ内容を出力
                        log.info(Messages.getMsg("log.model.train_and_reload.completed"), lastModelOperationLog);

                    } catch (InterruptedException interruptedException) {

                        // 実行が中断されたことを画面用ログへ追記
                        sb.append("[OPERATION] status=キャンセル\n");
                        sb.append("[OPERATION] reason=処理の中断\n");

                        // 中断時は一時ファイルを削除しておく
                        deleteTempTrainingOutputs();

                        // 中断時のログも直近ログへ保存
                        saveLastModelOperationLog(sb.toString());

                        // サーバーログへ中断内容を出力
                        log.info(Messages.getMsg("log.model.train_and_reload.cancelled"), lastModelOperationLog);

                        // 割り込み状態を復元して呼び出し元へ伝える
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(interruptedException);

                    } catch (Exception exception) {

                        // 失敗時も一時ファイルを削除しておく
                        deleteTempTrainingOutputs();

                        // 例外内容を画面用ログへ追記
                        sb.append("[OPERATION] エラー=").append(exception).append("\n");

                        // 失敗時のログも直近ログへ保存
                        saveLastModelOperationLog(sb.toString());

                        // サーバーログへ失敗内容を出力
                        log.warn(Messages.getMsg("log.model.train_and_reload.failed"), lastModelOperationLog);

                        // 実行例外はそのまま再送出
                        if (exception instanceof RuntimeException runtimeException) {
                            throw runtimeException;
                        }

                        // それ以外は実行例外に包んで再送出
                        throw new RuntimeException(exception);
                    }
                })
                // ブロッキング処理なので boundedElastic へ切替
                .subscribeOn(Schedulers.boundedElastic())
                // 完了シグナルだけを呼び出し元へ返却
                .then();
    }

    /**
     * Python 学習を 1 回実行
     *
     * 画面に見せたいログへ寄せるため、
     * コマンド行や begin/end 区切りではなく、Python の標準出力本文をそのまま追記
     */
    private void runTraining(Path appYaml, StringBuilder sb) throws Exception {

        // Python 実行コマンドの配列を用意
        var cmd = new java.util.ArrayList<String>();
        // Python 本体を実行
        cmd.add("python");
        // モジュール実行オプションを付与
        cmd.add("-m");
        // training モジュールを実行対象に
        cmd.add("training");
        // 出力先指定オプションを付与
        cmd.add("--out");
        // 出力先 ONNX パスを付与
        cmd.add(ModelManagementService.MODEL_FILE_TMP.toString());

        // application.yml が見つかったときだけ追加引数を付与
        if (appYaml != null) {
            // 学習側の重み設定読み込みオプションを付与
            cmd.add("--source-weight-yaml");
            // 学習側へ application.yml パスを付与
            cmd.add(appYaml.toString());
            // ランタイム側の設定読み込みオプションを付与
            cmd.add("--rt-rec-yaml");
            // ランタイム側へも同じ application.yml パスを付与
            cmd.add(appYaml.toString());
        }

        // 外部プロセス起動設定を作成
        ProcessBuilder pb = new ProcessBuilder(cmd);
        // 実行ディレクトリをアプリ起動位置へ合わせる
        pb.directory(Paths.get(".").toFile());
        // 標準エラーを標準出力へまとめる
        pb.redirectErrorStream(true);

        // Python 全体を UTF-8 モード
        pb.environment().put("PYTHONUTF8", "1");
        // Python 標準入出力の文字コードを UTF-8 に固定
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        // 有効な Spring プロファイル設定を取得
        String[] activeProfiles = env.getActiveProfiles();
        // 有効な Spring プロファイル設定がある場合
        if (activeProfiles.length > 0) {
            // 学習プロセスへ渡す
            pb.environment().put("SPRING_PROFILES_ACTIVE", String.join(",", activeProfiles));
        }

        // 学習プロセスへ DB 接続情報を流し込む。
        applyDbEnv(pb);

        // Python 学習プロセスを起動
        Process process = pb.start();
        // Python 側の出力を最後まで読取
        String out = readAll(process.getInputStream());


        boolean okProcess;
        try {
            // 最大 120 秒だけ終了を待機
            okProcess = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException interruptedException) {
            // 中断時は Python プロセスを強制終了
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            throw interruptedException;
        }

        // 制限時間内に終わらなかった場合の分岐
        if (!okProcess) {
            // タイムアウトしたプロセスを強制終了
            process.destroyForcibly();
            // タイムアウト秒数をログへ出力
            sb.append("[TRAIN] タイムアウト=").append(timeoutSeconds).append("秒\n");
            // 途中までの標準出力もログへ追記
            appendProcessOutput(sb, out);
            // 画面表示用の業務例外を送出
            throw new AppMessageException("model.train.err.timeout");
        }

        // プロセスの終了コードを取得
        int code = process.exitValue();
        // 終了コードをログへ出力
        sb.append("[TRAIN] exitCode=").append(code).append("\n");
        // Python の標準出力本文をそのままログへ追記
        appendProcessOutput(sb, out);

        // 異常終了時の分岐へ入る。
        if (code != 0) {
            // 依存ライブラリ不足らしい文字列が含まれているかを判定
            if (out.contains("ModuleNotFoundError") || out.contains("No module named")) {
                // 依存不足用の業務例外を送出
                throw new AppMessageException("model.train.err.deps_missing", code);
            }
            // それ以外は一般的なコマンド失敗として業務例外を送出
            throw new AppMessageException("model.train.err.command_failed", code);
        }
    }

    /**
     * モデル本体と固定係数を再読込
     *
     * trainAndReload の直近ログをすっきりさせたいので、
     * このメソッドではログ文字列へ何も追記しない。
     */
    private void reloadModelSilently() throws Exception {
        // 学習済み ONNX があるときは file: パスから再読込
        if (Files.exists(MODEL_FILE)) {
            // 明示的に学習済み ONNX を再読込
            modelRuntime.reload(MODEL_FILE_RESOURCE);
        } else {
            // 学習済み ONNX がなければ現在保持しているモデルパスで再読込
            modelRuntime.reload();
        }
        // 固定係数 JSON もディスクから再読込
        fixedParamsService.reloadFromDisk();
    }

    /**
     * Python の標準出力をそのままログ文字列へ追記
     */
    private void appendProcessOutput(StringBuilder sb, String out) {
        // 出力文字列が空白だけでないときだけ追記
        if (!isBlank(out)) {
            // Python 側の出力本文をそのまま付与
            sb.append(out);
            // 末尾改行がないときだけ改行を補う。
            if (!out.endsWith("\n")) {
                // ログが次行とつながらないよう改行を追加
                sb.append("\n");
            }
        }
    }

    /**
     * 学習プロセスへ DB 接続情報の環境変数を付与
     */
    private void applyDbEnv(ProcessBuilder pb) {

        // Spring プロファイル設定または OS 環境変数から R2DBC URL を設定
        String url = firstNonBlank(env.getProperty("spring.r2dbc.url"), System.getenv("R2DBC_URL"));
        // Spring プロファイル設定または OS 環境変数から DB ユーザーを設定
        String user = firstNonBlank(env.getProperty("spring.r2dbc.username"), System.getenv("POSTGRES_USER"));
        // Spring プロファイル設定または OS 環境変数から DB パスワードを設定
        String pass = firstNonBlank(env.getProperty("spring.r2dbc.password"), System.getenv("POSTGRES_PASSWORD"));

        // URL が存在する場合
        if (!isBlank(url)) {
            // Python 側へ R2DBC URL そのものも付与
            pb.environment().put("R2DBC_URL", url);

            try {
                // URI へ変換するための文字列変数を用意
                String uriString;
                // r2dbc: で始まるときの分岐へ入る
                if (url.startsWith("r2dbc:")) {
                    // Java URI で解釈できるよう r2dbc: プレフィックスを外す
                    uriString = url.substring("r2dbc:".length());
                } else {
                    // すでに URI 形式ならそのまま使う
                    uriString = url;
                }

                // URI オブジェクトへ変換
                URI uri = URI.create(uriString);
                // ホスト名が取れたときだけ環境変数へ詰める
                if (!isBlank(uri.getHost())) {
                    // Python 側へ PostgreSQL ホスト名を付与
                    pb.environment().put("POSTGRES_HOST", uri.getHost());
                }

                // ポート番号を取得
                int port = uri.getPort();
                // 有効なポート番号のときだけ環境変数へ詰める
                if (port > 0) {
                    // Python 側へ PostgreSQL ポート番号を付与
                    pb.environment().put("POSTGRES_PORT", String.valueOf(port));
                }

                // パス部分を取得
                String path = uri.getPath();
                // パスが取れたときだけ DB 名へ分解
                if (!isBlank(path)) {
                    // DB 名格納用の変数を用意
                    String db;
                    // 先頭が / なら 1 文字外す。
                    if (path.startsWith("/")) {
                        // 先頭の / を除いた文字列を DB 名
                        db = path.substring(1);
                    } else {
                        // / がなければそのまま DB 名と
                        db = path;
                    }
                    // DB 名が空白でないときだけ環境変数へ詰める
                    if (!isBlank(db)) {
                        // Python 側へ PostgreSQL DB 名を付与
                        pb.environment().put("POSTGRES_DB", db);
                    }
                }
            } catch (Exception exception) {
                // URI 解析に失敗したらサーバーログへ警告を出力
                log.warn(Messages.getMsg("log.model.r2dbc_uri_parse_failed"), url, exception);
            }
        }

        // ユーザー名があるときだけ環境変数へ詰める
        if (!isBlank(user)) {
            // PostgreSQL 用の POSTGRES_USER に入れる
            pb.environment().put("POSTGRES_USER", user);
        }

        // パスワードがあるときだけ環境変数へ詰める
        if (!isBlank(pass)) {
            // PostgreSQL 用の POSTGRES_PASSWORD に入れる
            pb.environment().put("POSTGRES_PASSWORD", pass);
        }
    }

    /**
     * 学習側へ渡す application.yml を探索
     */
    private Path findAppYaml() {

        // 有効な Spring プロファイル設定の一覧を取得
        String[] activeProfiles = env.getActiveProfiles();

        // 有効な Spring プロファイル設定のループ処理
        for (String activeProfile : activeProfiles) {
            // 空白でないプロファイル名だけを対象にする
            if (!isBlank(activeProfile)) {
                // application-{profile}.yml を組み立てる
                Path profileYamlPath = Paths.get("./config/application-" + activeProfile + ".yml");
                // 対応する profile 用 YAML が存在すればそれを返却
                if (Files.exists(profileYamlPath)) {
                    return profileYamlPath;
                }
            }
        }

        // 共通設定の application.yml を組み立てる
        Path applicationYamlPath = Paths.get("./config/application.yml");
        // application.yml が存在すればそれを返却
        if (Files.exists(applicationYamlPath)) {
            return applicationYamlPath;
        }

        // どれもなければ null を返却
        return null;
    }

    /**
     * 最初の空白でない文字列を返却
     */
    private static String firstNonBlank(String... values) {
        // 配列自体が null なら null を返却
        if (values == null) {
            return null;
        }

        // 配列を先頭から順に調べる。
        for (String value : values) {
            // 空白でない値を見つけたらそれを返却
            if (!isBlank(value)) {
                return value;
            }
        }

        // 有効な値が 1 つもなければ null を返却
        return null;
    }

    /**
     * 空白文字列かどうかを判定
     */
    private static boolean isBlank(String text) {
        // null または trim 後に空なら true を返却
        return text == null || text.trim().isEmpty();
    }

    /**
     * 学習成功したモデルを本番ファイル名へ置換し、リネームログを追記
     */
    private void renameTrainingOutputs(StringBuilder sb) throws Exception {

        // 学習成功した一時 ONNX ファイルを本番ファイルへ置換
        Files.move(MODEL_FILE_TMP, MODEL_FILE, StandardCopyOption.REPLACE_EXISTING);
        // ONNX のリネーム内容をログへ出力
        sb.append("[RENAME] ファイル名の置換（.tmpの削除）: ").append(MODEL_FILE).append("\n");

        // 学習レポートが存在する場合は本番レポート名へ置換
        if (Files.exists(MODEL_REPORT_FILE_TMP)) {
            Files.move(MODEL_REPORT_FILE_TMP, MODEL_REPORT_FILE, StandardCopyOption.REPLACE_EXISTING);
            // レポートのリネーム内容をログへ出力
            sb.append("[RENAME] ファイル名の置換（.tmpの削除）: ").append(MODEL_REPORT_FILE).append("\n");
        } else {
            // レポートが生成されなかった場合はログへ出力
            sb.append("[RENAME] ファイル名の置換失敗（.tmpの削除）: ").append(MODEL_REPORT_FILE_TMP).append("\n");
        }
    }

    /**
     * 一時学習モデルを削除
     */
    private void deleteTempTrainingOutputs() {
        try {
            // 一時 ONNX ファイルが残っていれば削除
            Files.deleteIfExists(MODEL_FILE_TMP);
        } catch (Exception exception) {
            // 後始末失敗は警告だけ出して処理は継続
            log.warn(Messages.getMsg("log.model.tmp_model_delete_failed"), MODEL_FILE_TMP, exception);
        }

        try {
            // 一時レポートファイルが残っていれば削除
            Files.deleteIfExists(MODEL_REPORT_FILE_TMP);
        } catch (Exception exception) {
            // 後始末失敗は警告だけ出して処理は継続
            log.warn(Messages.getMsg("log.model.tmp_report_delete_failed"), MODEL_REPORT_FILE_TMP, exception);
        }
    }

    /**
     * InputStream を UTF-8 文字列へ変換
     */
    private static String readAll(InputStream in) throws Exception {
        // InputStream と出力バッファを try-with-resources で開く
        try (in; ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // 受け取った全バイト列をバッファへ転送
            in.transferTo(baos);
            // UTF-8 文字列へ変換して返却
            return baos.toString(StandardCharsets.UTF_8);
        }
    }
}