"""ログ出力設定を提供。

学習バッチのログレベル/フォーマットを統一するため、
初期化処理を 1 箇所へ集約します。

※ Java 側の実装と同じ粒度で、
  "何を設定しているか" をステップごとにコメントします。
"""

import logging
import sys

# --------------------------------------
# ロガークラス
# --------------------------------------
class LoggerClass:

    # --------------------------------------
    # ログ出力の初期化を実行
    # --------------------------------------
    def configure(self, verbose: bool = False) -> None:

        # verbose 指定の有無でログレベルを決定
        level = logging.DEBUG if verbose else logging.INFO

        # ルートロガーを取得
        root = logging.getLogger()

        # ルートロガーのログレベルを設定
        root.setLevel(level)

        # 標準出力へ出力するハンドラを生成
        handler = logging.StreamHandler(sys.stdout)

        # ハンドラのログレベルを設定
        handler.setLevel(level)

        # ログフォーマットを設定
        formatter = logging.Formatter("[%(levelname)s] %(message)s")

        # ハンドラへフォーマッタを設定
        handler.setFormatter(formatter)

        # 既存ハンドラを置き換えて二重出力を防止
        root.handlers[:] = [handler]
