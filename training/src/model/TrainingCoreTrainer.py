"""TrainingCore の学習とモデル出力。

学習（Ridge 回帰）と、ONNX 形式への変換・保存を切り出します。
"""

from typing import TYPE_CHECKING

import json

from training.src.model.TrainingCoreDataset import TrainingDataset
from training.src.model.TrainingCoreConstants import FEATURE_ORDER

if TYPE_CHECKING:
    # 実行時 import を避けつつ型ヒントだけ提供
    from sklearn.linear_model import Ridge


# --------------------------------------
# リッジ 回帰モデルを学習
# --------------------------------------
def train_ridge(dataset: TrainingDataset, ridge_alpha: float, seed: int):

    # 実行時 import（依存の遅延読み込み）
    from sklearn.linear_model import Ridge

    # リッジ 回帰モデルを作成（エルツー正則化つき線形モデル）
    model = Ridge(alpha=float(ridge_alpha), random_state=int(seed))
    # 特徴量/ラベル/重みで学習を実行
    model.fit(dataset.X, dataset.y, sample_weight=dataset.sample_weight)
    # リッジ 回帰モデルを返却
    return model


# --------------------------------------
# 学習済みモデルを モデル形式 に変換して保存
# --------------------------------------
def export_to_onnx(model, out_path: str, feature_size: int = 9) -> None:

    # 実行時 import（依存の遅延読み込み）
    from skl2onnx import convert_sklearn
    from skl2onnx.common.data_types import FloatTensorType

    # モデル形式 変換用の入力テンソル形状（9特徴量）を定義
    initial_type = [("input", FloatTensorType([None, int(feature_size)]))]
    # 機械学習ライブラリモデルを変換
    onnx_model = convert_sklearn(model, initial_types=initial_type)
    # モデル形式をバイナリで書き出す
    with open(out_path, "wb") as f:
        # モデル形式 バイト列をファイルへ保存
        f.write(onnx_model.SerializeToString())


# --------------------------------------
# 学習済み係数を固定値として保存
# --------------------------------------
def export_fixed_params(model, out_path: str) -> None:

    # 学習済みモデルから係数を取得
    coef = getattr(model, "coef_", None)
    # 学習済みモデルから切片を取得
    intercept = getattr(model, "intercept_", 0.0)

    # 特徴量数に合わせて固定長の重み配列を先に用意する
    weights = [0.0] * len(FEATURE_ORDER)
    # 係数が取得できた場合
    if coef is not None:
        try:
            # 係数を float の配列へ変換
            weights = [float(x) for x in list(coef)]
        except Exception:
            # 係数変換に失敗した場合は 0 埋めの既定値を維持
            pass

    # 切片の初期値を用意
    bias = 0.0
    try:
        # 切片を float へ変換
        bias = float(intercept)
    except Exception:
        # 切片変換に失敗した場合は 0.0 を使う
        bias = 0.0

    # 固定パラメータとして保存する JSON ペイロードを構築
    payload = {
        "feature_order": list(FEATURE_ORDER),
        "weights": weights,
        "bias": bias,
        "default_score": 0.0,
    }

    # JSON ファイルとして保存
    with open(out_path, "w", encoding="utf-8") as f:
        # JSON形式に整形して書き込む
        json.dump(payload, f, ensure_ascii=False, indent=2)
