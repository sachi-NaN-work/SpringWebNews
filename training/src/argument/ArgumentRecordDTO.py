"""Argument DTO (dataclass).

"DTO" は "Data Transfer Object" の略で、
引数のまとまりを 1 つの型として扱うために使います。
"""

from dataclasses import dataclass
from pathlib import Path
from typing import Optional

# --------------------------------------
# train.py の引数を格納する DTO クラス
# --------------------------------------
@dataclass(frozen=True)
class ArgumentRecordDTO:

    # ONNX の出力先パス
    out_path: Path

    # 正例 1 件あたりの負例サンプル数
    neg_per_pos: int

    # 乱数シード
    seed: int

    # Ridge 回帰の正則化係数
    ridge_alpha: float

    # 負例（y=0）のサンプル重み
    negative_weight: float

    # source 重み設定（application.yml）のパス
    source_weight_yaml: Optional[Path]

    # リアルタイム推薦の係数（rec.rt-rec.*）を読む application.yml のパス
    rt_rec_yaml: Optional[Path]

    # verbose ログ出力を行うか
    verbose: bool
