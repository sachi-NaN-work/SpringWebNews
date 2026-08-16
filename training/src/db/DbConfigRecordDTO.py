"""DB 接続設定 DTO を提供。

Java 側（Spring）の設定と揃えるため、以下の環境変数を読み取ります。

優先順位:
  1) docker-compose 互換の POSTGRES_* 系
  2) Spring 互換の SPRING_R2DBC_* 系
"""

import os
import urllib.parse
from dataclasses import dataclass
from dotenv import load_dotenv

from training.src.common.MessageCatalog import MessageCatalog


# .env ファイルから環境変数をロード（ローカル実行向け）
load_dotenv()

MESSAGES = MessageCatalog("ja")

# --------------------------------------
# URL を解析
# --------------------------------------
def _parse_r2dbc_url(url: str) -> tuple[str | None, int | None, str | None]:

    # URL が取得できなかった場合
    if not url:
        # None を返却
        return None, None, None

    urlStr = url
    # "r2dbc:" から始まる場合
    if urlStr.startswith("r2dbc:"):
        # "r2dbc:" を除去
        urlStr = urlStr[len("r2dbc:"):]

    # URL の変換
    parsed = urllib.parse.urlparse(urlStr)

    # ホスト名の取得
    host = parsed.hostname
    # ポート番号（文字列）の取得
    port = parsed.port
    # DB 名を取得（未指定の場合は None）
    db = parsed.path.lstrip("/") if parsed.path else None

    return host, port, db

# --------------------------------------
# DB 接続の必須設定を格納する DTO クラス
# --------------------------------------
@dataclass(frozen=True)
class DbConfigRecordDTO:

    host: str
    port: int
    db: str
    user: str
    password: str

    # --------------------------------------
    # 環境変数から DB 接続設定を取得
    # --------------------------------------
    @staticmethod
    def from_env() -> "DbConfigRecordDTO":

        # ホスト名を取得（未指定の場合は localhost）
        host = os.getenv("POSTGRES_HOST") or "localhost"

        # ポート番号（文字列）を取得（未指定の場合は 5432）
        port_s = os.getenv("POSTGRES_PORT") or "5432"

        # DB 名を取得（未指定の場合は recdb）
        db = os.getenv("POSTGRES_DB") or "recdb"

        # R2DBC_URL を取得
        r2dbc_url = os.getenv("R2DBC_URL") or os.getenv("SPRING_R2DBC_URL") or ""

        # R2DBC_URL が取得できた場合
        if r2dbc_url:
            # URL を解析
            h, p, d = _parse_r2dbc_url(r2dbc_url)

            # host が取得できた場合に上書き
            if h:
                host = h

            # port が取得できた場合に上書き
            if p:
                port_s = str(p)

            # db が取得できた場合に上書き
            if d:
                db = d

        # ユーザー名を取得（未指定の場合は rec）
        user = (
                os.getenv("POSTGRES_USER")
                or os.getenv("SPRING_R2DBC_USERNAME")
                or "rec"
        )

        # パスワードを取得（必須）
        password = (
                os.getenv("POSTGRES_PASSWORD")
                or os.getenv("SPRING_R2DBC_PASSWORD")
        )

        # パスワードが未設定の場合はエラー
        if password is None or str(password).strip() == "":
            raise RuntimeError(
                MESSAGES.get("error.postgres_password_missing")
            )

        # DB 接続設定 DTO を組み立てて返却
        return DbConfigRecordDTO(
            host=str(host),
            port=int(str(port_s)),
            db=str(db),
            user=str(user),
            password=str(password),
        )

    # --------------------------------------
    # SQLAlchemy 用の接続 URL を生成
    # --------------------------------------
    def sqlalchemy_url(self) -> str:

        # psycopg2-binary を使用する URL 形式で組み立て
        return (
            f"postgresql+psycopg2://{self.user}:{self.password}"
            f"@{self.host}:{self.port}/{self.db}"
        )