"""メッセージカタログの読み込みを提供。

画面やログに出す文言はコードに直書きせず、外部ファイル（JSON）へ分離します。
Java 側の i18n と同じように、キーから文言を取得できるようにします。
"""

import json
from pathlib import Path
from typing import Any, Dict, Optional

from training.src.common.Constants import (
    I18N_DIR_NAME,
    MESSAGE_FILE_BASENAME_TEMPLATE,
    UTF8_ENCODING,
)

# --------------------------------------
# キーから文言を取得クラス（messages_*.json から取得）
# --------------------------------------
class MessageCatalog:

    # --------------------------------------
    # イニシャライザ
    # --------------------------------------
    def __init__(self, locale: str = "ja") -> None:

        # training/src を基準に i18n ディレクトリを探す
        base = Path(__file__).resolve().parents[1]

        # locale に応じたメッセージファイルパスを組み立て
        path = base / I18N_DIR_NAME / MESSAGE_FILE_BASENAME_TEMPLATE.format(locale=locale)

        # メッセージ辞書を初期化
        self._messages: Dict[str, Any] = {}

        try:

            # JSON を読み込んでメッセージ辞書を保持
            self._messages = json.loads(path.read_text(encoding=UTF8_ENCODING))

        except FileNotFoundError:

            # ファイルが無い場合は空のまま継続
            self._messages = {}

        except Exception:

            # ファイルが壊れていても学習自体は継続させる
            self._messages = {}

    # --------------------------------------
    # キーに対応する文言を取得して返却
    # --------------------------------------
    def get(self, key: str, default: Optional[str] = None) -> str:

        # ドット区切りでメッセージを探索
        msg: Any = self._messages
        for part in key.split("."):

            # キーに対応する階層が見つからない場合
            if not isinstance(msg, dict) or part not in msg:
                # default が指定されている場合
                if default is not None:
                    # default を返却
                    return default

                # キー自体を返却
                return key

            # 次の階層へ進む
            msg = msg[part]

        # 取得したメッセージを文字列化して返却
        return str(msg)
