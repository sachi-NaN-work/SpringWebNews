"""共有コンテキスト DTO。

前処理で集めた入力（引数・設定・データ）を、
メイン処理/後処理に渡すための "箱" です。
"""

from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Optional
from zoneinfo import ZoneInfo

import pandas as pd

from training.src.argument.ArgumentRecordDTO import ArgumentRecordDTO
from training.src.db.DbConfigRecordDTO import DbConfigRecordDTO


# --------------------------------------
# 学習パイプライン全体の共有コンテキスト
# --------------------------------------
@dataclass
class AppContextDTO:

    # コマンドライン引数を保持
    args: ArgumentRecordDTO

    # DB 接続設定を保持
    db_config: DbConfigRecordDTO

    # feedback のソース別重みを保持
    source_weights: Dict[str, float]

    # 正例のイベント種別重みを保持
    type_weights: Dict[str, float]

    # 「bad」負例の最小重みを保持
    bad_negative_weight_min: float

    # 「bad」負例の重み倍率を保持
    bad_negative_weight_mult: float

    # rec.rt-rec.* の係数を保持（露出/繰り返し抑制を推論側と一致させる）
    rt_rec: Dict[str, float]

    # news テーブル（学習用）を保持
    news_df: pd.DataFrame

    # feedback テーブル（学習用）を保持
    feedback_df: pd.DataFrame

    # news_hashtags テーブル（学習用）を保持（userMatch のタグ特徴量。enabled=true のみを取得）
    news_hashtags_df: pd.DataFrame

    # 実行時刻（レポート用）を保持
    ts: datetime = field(default_factory=lambda: datetime.now(ZoneInfo("Asia/Tokyo")))

    # ONNX 出力パスを保持（未出力の場合は None）
    onnx_path: Optional[Path] = None

    # 後処理で出力するレポート情報を保持
    report: Dict[str, object] = field(default_factory=dict)
