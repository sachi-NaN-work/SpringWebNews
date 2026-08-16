"""学習ロジック（コア）。

本モジュールは、学習用データセットの構築、モデル学習、モデル形式ファイル出力までを担当します。

目的
----
推論側がモデルへ渡す特徴量と、学習側が作る特徴量を完全に一致させます。
特徴量は「9特徴量（露出 + カテゴリ/タグの share）」です。
（A/B ブーストはモデル外で適用するため、学習特徴量には含めません。）

"""

# ---- 定数 ----
from training.src.model.TrainingCoreConstants import (
    BAD_NEG_WEIGHT_MIN,
    BAD_NEG_WEIGHT_MULT,
    DEFAULT_LOCAL_TZ,
    EXPOSURE_BUCKET0,
    EXPOSURE_BUCKET1,
    EXPOSURE_BUCKET2,
    EXPOSURE_BUCKET3,
    REPEAT_SHARE_ADJ_CAP,
    REPEAT_SHARE_ADJ_COEFF,
    REPEAT_STAGE0,
    REPEAT_STAGE1,
    REPEAT_STAGE2,
    REPEAT_STAGE3,
    TYPE_WEIGHT,
    WARN_NO_FEEDBACK_MARKER,
    FEATURE_ORDER,
)

# ---- 状態（シグナル） ----
from training.src.model.TrainingCoreSignals import GlobalSignals, UserSignals

# ---- 特徴量 ----
from training.src.model.TrainingCoreFeatures import (
    clamp01,
    exposure_score,
    feature_vector,
    pref_from_shares,
    repeat_penalty,
    share,
    user_match,
)

# ---- データセット ----
from training.src.model.TrainingCoreDataset import TrainingDataset, build_training_dataset, parse_feedback_type

# ---- 学習/出力 ----
from training.src.model.TrainingCoreTrainer import export_fixed_params, export_to_onnx, train_ridge


__all__ = [
    # 主要 API
    "build_training_dataset",
    "TrainingDataset",
    "train_ridge",
    "export_to_onnx",
    "export_fixed_params",
    # 互換のために公開（既存コード/将来拡張用）
    "exposure_score",
    "repeat_penalty",
    "user_match",
    "feature_vector",
    "FEATURE_ORDER",
    "parse_feedback_type",
    "UserSignals",
    "GlobalSignals",
    # ユーティリティ
    "share",
    "clamp01",
    "pref_from_shares",
    # 定数
    "TYPE_WEIGHT",
    "BAD_NEG_WEIGHT_MIN",
    "BAD_NEG_WEIGHT_MULT",
    "WARN_NO_FEEDBACK_MARKER",
    "DEFAULT_LOCAL_TZ",
    "EXPOSURE_BUCKET0",
    "EXPOSURE_BUCKET1",
    "EXPOSURE_BUCKET2",
    "EXPOSURE_BUCKET3",
    "REPEAT_STAGE0",
    "REPEAT_STAGE1",
    "REPEAT_STAGE2",
    "REPEAT_STAGE3",
    "REPEAT_SHARE_ADJ_COEFF",
    "REPEAT_SHARE_ADJ_CAP",
]
