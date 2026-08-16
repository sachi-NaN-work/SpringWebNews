# SpringWebNews - ニュースサイト

[\[ <u>目次へ戻る</u> \]](../../README.md#-readme-ファイル一式-)


---

<br>

## ● リポジトリ構成

[**＜ 1 ＞ リポジトリの主要ディレクトリ（簡易版）**](#-1--リポジトリの主要ディレクトリ簡易版)

[**＜ 2 ＞ リポジトリの主要ディレクトリ（詳細版）**](#-2--リポジトリの主要ディレクトリ詳細版)


<br>

―――――――――――――――――――――――――――――――

<br>


#### ＜ 1 ＞ リポジトリの主要ディレクトリ（簡易版）

```text
.
├─ config/                         # 外部設定
├─ infra/docker/postgres/init/     # Postgres 初期化 SQL / seed
├─ model/                          # 学習済み ONNX と固定係数の配置先
├─ src/main/java/                  # Spring アプリ本体
├─ src/main/resources/             # テンプレート / static / messages
├─ training/                       # Python 学習バッチ
├─ .env                            # 環境設定ファイル
├─ .env.sample                     # 環境設定ファイル（サンプル）
├─ Dockerfile                      # アプリ実行用コンテナのビルド定義
└─ docker-compose.yml              # Docker用設定ファイル
```


<br>

[\[ <u>▲ ページ上部へ</u> \]](#springwebnews---ニュースサイト)

―――――――――――――――――――――――――――――――

<br>


#### ＜ 2 ＞ リポジトリの主要ディレクトリ（詳細版）

```text
.
├─ config/                                  # 外部設定
│  ├─ application.yml                       # 共通アプリケーション設定
│  ├─ application-dev.yml                   # 開発環境用設定
│  └─ application-prod.yml                  # 本番環境用設定
│
├─ dummy/                                   # ダミーデータ
│
├─ infra/                                   # インフラ関連ファイル
│  └─ docker/
│     └─ postgres/
│        └─ init/                           # Postgres 初期化 SQL / 初期データ投入 SQL
│
├─ model/                                   # 学習済み ONNX と固定係数の配置先
│  ├─ rec.onnx                              # 推薦モデル本体
│  └─ rec_fixed_params.json                 # 推薦用固定パラメータ
│
├─ src/                                     # アプリケーション本体
│  └─ main/
│     ├─ java/                              # Spring アプリケーション本体
│     │  └─ com/news/recapp/
│     │     │
│     │     ├─ NewsApplication.java         # Spring Boot 起動クラス
│     │     │
│     │     ├─ application/                 # アプリケーション層
│     │     │  ├─ dto/                      # API / 画面表示用 DTO
│     │     │  ├─ exception/                # アプリ共通例外
│     │     │  ├─ metrics/                  # 推薦・処理メトリクス
│     │     │  ├─ port/                     # ポート定義
│     │     │  └─ service/                  # ユースケース / 業務ロジック
│     │     │
│     │     ├─ config/                      # 設定層
│     │     │
│     │     ├─ data/                        # データアクセス層
│     │     │  ├─ kafka/                    # Kafka 関連
│     │     │  ├─ onnx/                     # ONNX 推論関連
│     │     │  ├─ r2dbc/                    # R2DBC / PostgreSQL 接続関連
│     │     │  ├─ redis/                    # Redis 関連
│     │     │  └─ security/                 # DB認証 / ログインロック制御
│     │     │
│     │     ├─ persistence/                 # DB アクセス層
│     │     │  ├─ entity/                   # DB エンティティ
│     │     │  └─ repo/                     # Spring Data Repository
│     │     │
│     │     ├─ presentation/                # プレゼンテーション層
│     │     │  ├─ advice/                   # 例外ハンドリング / ControllerAdvice
│     │     │  ├─ openapi/                  # OpenAPI 表示用定義
│     │     │  ├─ security/                 # Web認証補助
│     │     │  └─ web/                      # Controller / Web API
│     │     │
│     │     └─ util/                        # 共通ユーティリティ
│     │
│     └─ resources/                         # テンプレート / static / messages
│        ├─ application.yml                 # Spring リソース側設定
│        ├─ messages.properties             # 画面表示メッセージ
│        ├─ ValidationMessages.properties   # バリデーションメッセージ
│        ├─ static/                         # CSS / JS / 画像などの静的ファイル
│        └─ templates/                      # Thymeleaf テンプレート
│
├─ training/                                # Python 学習バッチ
│  ├─ src/                                  # 学習処理本体
│  │  ├─ argument/                          # コマンドライン引数処理
│  │  ├─ common/                            # 共通定数 / ログ / メッセージ
│  │  ├─ config/                            # 学習用設定ローダ
│  │  ├─ db/                                # DB 接続 / 学習データ取得 SQL
│  │  ├─ i18n/                              # 学習バッチ用メッセージ
│  │  ├─ model/                             # 特徴量生成 / 学習 / ONNX 出力処理
│  │  ├─ process/                           # 前処理 / メイン処理 / 後処理
│  │  └─ main.py                            # 学習バッチ起動処理
│  └─ __main__.py                           # python -m training 用エントリ
│
├─ .env                                     # 環境設定ファイル（ローカル作成）
├─ .env.sample                              # 環境設定ファイル（サンプル）
├─ build.gradle                             # Gradle ビルド定義
├─ settings.gradle                          # Gradle プロジェクト設定
├─ gradle.properties                        # Gradle プロパティ
├─ gradlew.bat                              # Gradle Wrapper（Windows）
├─ Dockerfile                               # アプリ実行用コンテナのビルド定義
├─ docker-compose.yml                       # Docker Compose 構成
├─ requirements.txt                         # Python 学習バッチ用依存ライブラリ
├─ README.md                                # プロジェクト説明
└─ README_TRAINING.md                       # 学習バッチ説明
```


<br>


---

[\[ <u>▲ ページ上部へ</u> \]](#springwebnews---ニュースサイト)

[\[ <u>目次へ戻る</u> \]](../../README.md#-readme-ファイル一式-)
