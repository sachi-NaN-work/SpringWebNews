"""DB アクセス処理を提供。

SQLAlchemy で Postgres に接続し、学習に必要なテーブルを DataFrame として読み込みます。

対象テーブル:
  - news           : 記事（id/category/published_at）
  - feedback       : 行動ログ（type/source/created_at）
  - news_hashtags  : 記事とタグの紐付け（userMatch のタグ特徴量に使用）

※ SQL は Python に直書きせず、sql/ 配下の *.sql へ分離します。

学習の DBログ復元（リーク対策）のため、feedback は「created_at + PK(feedback_id)」で
時系列再生できる形で読み込みます（feedback.sql 側で SELECT/ORDER を揃える）。
"""

import logging
from pathlib import Path
from typing import Tuple

import pandas as pd
from sqlalchemy import create_engine, text as sql_text

from training.src.common.MessageCatalog import MessageCatalog
from training.src.db.DbConfigRecordDTO import DbConfigRecordDTO

logger = logging.getLogger(__name__)

# メッセージカタログ（日本語）を保持
MESSAGES = MessageCatalog(locale="ja")


# --------------------------------------
# DB クライアントクラス
# --------------------------------------
class DbClientClass:

    # --------------------------------------
    # イニシャライザ
    # --------------------------------------
    def __init__(self, config: DbConfigRecordDTO):
        self._config = config
        self._engine = create_engine(config.sqlalchemy_url(), pool_pre_ping=True)

    # --------------------------------------
    # DB から学習に必要なテーブル情報を取得
    # --------------------------------------
    def read_tables(self) -> Tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:

        # SQL（news）をファイルから読み込み
        news_sql_text = self._load_sql_file("news.sql")

        # SQL（feedback）をファイルから読み込み（created_at + feedback_id で安定順序にする）
        fb_sql_text = self._load_sql_file("feedback.sql")

        # SQL（news_hashtags）をファイルから読み込み（userMatch のタグ特徴量のため。enabled=true のみ）
        nh_sql_text = self._load_sql_file("news_hashtags.sql")

        # SQLAlchemy 用の text オブジェクトへ変換
        news_sql = sql_text(news_sql_text)
        fb_sql = sql_text(fb_sql_text)
        nh_sql = sql_text(nh_sql_text)

        # DB へ接続してテーブルを読み込み
        with self._engine.connect() as conn:

            # news テーブルを読み込み
            news_df = pd.read_sql(news_sql, conn)

            # feedback テーブルを読み込み
            fb_df = pd.read_sql(fb_sql, conn)

            # news_hashtags テーブルを読み込み
            nh_df = pd.read_sql(nh_sql, conn)

        # ログ出力（デバッグ用）
        logger.debug(MESSAGES.get("info.table_rows.news"), len(news_df))
        logger.debug(MESSAGES.get("info.table_rows.feedback"), len(fb_df))
        logger.debug(MESSAGES.get("info.table_rows.news_hashtags"), len(nh_df))

        # 読み込んだ DataFrame を返却
        return news_df, fb_df, nh_df

    # --------------------------------------
    # SQL ファイルを読み込んで返却
    # --------------------------------------
    def _load_sql_file(self, file_name: str) -> str:

        # このファイルのディレクトリを基準に sql/ 配下を解決
        sql_dir = Path(__file__).resolve().parent / "sql"

        # 読み込む SQL ファイルパスを組み立て
        path = sql_dir / file_name

        # SQL ファイルが存在しない場合は例外
        if not path.exists():
            raise FileNotFoundError(MESSAGES.get("error.sql_missing") % str(path))

        try:
            # SQL テキストを読み込み
            return path.read_text(encoding="utf-8")
        except Exception as ex:
            raise RuntimeError(MESSAGES.get("error.sql_read_failed") % str(path)) from ex
