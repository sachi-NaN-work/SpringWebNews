"""後処理を提供。

学習に使用した設定や統計を JSON レポートとして保存します。

確定版運用:
- フィードバック 0 件などで学習をスキップする場合でも、レポートは必ず出力します。
  （CI/運用側で "trained" フラグを見て配備可否を判断できるようにする）
"""

import json
import logging
from pathlib import Path
from zoneinfo import ZoneInfo

from training.src.common.MessageCatalog import MessageCatalog
from training.src.process.AppContextDTO import AppContextDTO
from training.src.common.Constants import (
    REPORT_JSON,
    UTF8_ENCODING,
    REPORT_KEY_TS,
    REPORT_KEY_SOURCE_WEIGHT,
)

logger = logging.getLogger(__name__)

# メッセージカタログ（日本語）を保持
MESSAGES = MessageCatalog(locale="ja")


# --------------------------------------
# 後処理クラス
# --------------------------------------
class PostProcessClass:

    # --------------------------------------
    # 後処理を実行
    # --------------------------------------
    def run(self, context: AppContextDTO) -> AppContextDTO:

        # レポートの基準パス（ONNX 未出力でも args.out_path を使う）
        base_path = context.onnx_path if context.onnx_path is not None else context.args.out_path

        # ファイル名を指定してファイルパスの取得
        report_path = base_path.with_name(REPORT_JSON)

        # report を組み立て
        report = {
            REPORT_KEY_TS: context.ts.astimezone(ZoneInfo("Asia/Tokyo")).strftime("%Y-%m-%d %H:%M:%S"),
            REPORT_KEY_SOURCE_WEIGHT: context.source_weights,
            **context.report,
        }

        # レポートを保存
        report_text = json.dumps(report, ensure_ascii=False, indent=2)
        report_path.write_text(report_text, encoding=UTF8_ENCODING)

        # ログを出力
        logger.info(MESSAGES.get("info.report_path"), report_path)

        return context
