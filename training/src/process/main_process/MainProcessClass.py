"""メイン処理を提供。

特徴量生成 → 学習 → ONNX 書き出しを順番に実行します。
推論側（Spring）の `RerankService` と一致する 9特徴量で学習します。

仕様（単一モデル）
----------------
- 学習モデルは 1 本（A/B で分けない）。
- A/B ブーストは学習対象外（推論の最後に add/multiply で適用）。
- モデルが無い/無効な場合に備え、学習済みの係数(w,b)を fixed_params として保存する。
"""

import logging

from training.src.common.MessageCatalog import MessageCatalog
from training.src.common.Constants import (
    FIXED_PARAMS_JSON,
    FEATURE_LABELS_JA,
    RIDGE_ATTR_COEF,
    RIDGE_ATTR_INTERCEPT,
    RIDGE_DEFAULT_INTERCEPT,
    REPORT_KEY_TRAINED,
    REPORT_KEY_FEEDBACK_ROWS,
    REPORT_KEY_MODEL_FILE,
    REPORT_KEY_FIXED_PARAMS_FILE,
    REPORT_KEY_TYPE_WEIGHTS,
    REPORT_KEY_DATASET_ROWS,
    REPORT_KEY_NEG_PER_POS,
    REPORT_KEY_RIDGE_ALPHA,
    REPORT_KEY_NEGATIVE_WEIGHT,
    REPORT_KEY_BAD_NEGATIVE_WEIGHT_MIN,
    REPORT_KEY_BAD_NEGATIVE_WEIGHT_MULT,
    LABEL_INTERCEPT,
)
from training.src.model.TrainingCore import (
    build_training_dataset,
    export_fixed_params,
    export_to_onnx,
    train_ridge,
    FEATURE_ORDER,
)
from training.src.process.AppContextDTO import AppContextDTO

logger = logging.getLogger(__name__)

# メッセージカタログ（日本語）を保持
MESSAGES = MessageCatalog(locale="ja")


# --------------------------------------
# メイン処理クラス
# --------------------------------------
class MainProcessClass:

    # --------------------------------------
    # 学習本体を実行
    # --------------------------------------
    def run(self, context: AppContextDTO) -> AppContextDTO:

        # 引数（学習パラメータ/入出力先）を取得
        args = context.args

        # 出力パス（rec.onnx）を取得
        out_path = args.out_path
        fixed_path = out_path.with_name(FIXED_PARAMS_JSON)

        # フィードバック件数（レポート用）
        fb = context.feedback_df
        fb_rows = 0 if fb is None else int(len(fb))

        # 学習データセットを生成（フィードバック0件は内部でダミーを追加し学習を成立させる）
        dataset = build_training_dataset(
            news_df=context.news_df,
            feedback_df=context.feedback_df,
            news_hashtags_df=context.news_hashtags_df,
            rt_rec=context.rt_rec,
            source_weights=context.source_weights,
            type_weights=context.type_weights,
            neg_per_pos=args.neg_per_pos,
            negative_weight=args.negative_weight,
            bad_negative_weight_min=context.bad_negative_weight_min,
            bad_negative_weight_mult=context.bad_negative_weight_mult,
            seed=args.seed,
        )

        # Ridge 回帰モデルを学習
        model = train_ridge(
            dataset,
            ridge_alpha=args.ridge_alpha,
            seed=args.seed,
        )

        # 学習した係数をログへ出力
        self._log_ridge_summary(
            model=model,
            args=args,
            fb_rows=fb_rows,
            dataset_rows=int(dataset.X.shape[0]),
        )

        # 学習済みモデルを ONNX へ変換して保存
        export_to_onnx(model, str(out_path))

        # 学習済み係数を固定値として保存（モデルが無い場合のフォールバック用）
        export_fixed_params(model, str(fixed_path))

        # 出力先をログへ出力
        logger.info(MESSAGES.get("info.exported_onnx"), out_path)

        # コンテキストへ ONNX パスを保持
        context.onnx_path = out_path

        # レポート
        context.report.update(
            {
                REPORT_KEY_TRAINED: fb_rows > 0,
                REPORT_KEY_FEEDBACK_ROWS: fb_rows,
                REPORT_KEY_MODEL_FILE: out_path.name,
                REPORT_KEY_FIXED_PARAMS_FILE: fixed_path.name,
                REPORT_KEY_TYPE_WEIGHTS: dict(context.type_weights),
                REPORT_KEY_DATASET_ROWS: int(dataset.X.shape[0]),
                REPORT_KEY_NEG_PER_POS: int(args.neg_per_pos),
                REPORT_KEY_RIDGE_ALPHA: float(args.ridge_alpha),
                REPORT_KEY_NEGATIVE_WEIGHT: float(args.negative_weight),
                REPORT_KEY_BAD_NEGATIVE_WEIGHT_MIN: float(context.bad_negative_weight_min),
                REPORT_KEY_BAD_NEGATIVE_WEIGHT_MULT: float(context.bad_negative_weight_mult),
            }
        )

        # 学習済みコンテキストを返却
        return context


    # --------------------------------------
    # 学習した係数をログへ出力
    # --------------------------------------
    def _log_ridge_summary(self, model, args, fb_rows: int, dataset_rows: int) -> None:

        # Ridge 学習結果の係数を取得
        coef = getattr(model, RIDGE_ATTR_COEF, None)
        # Ridge 学習結果の切片を取得
        intercept = getattr(model, RIDGE_ATTR_INTERCEPT, RIDGE_DEFAULT_INTERCEPT)

        # 係数一覧を特徴量順に整形
        coef_list = [0.0] * len(FEATURE_ORDER)
        if coef is not None:
            try:
                values = [float(x) for x in list(coef)]
                for i in range(min(len(values), len(coef_list))):
                    coef_list[i] = values[i]
            except Exception:
                pass

        # Ridge 学習サマリを出力
        logger.info(MESSAGES.get("info.ridge.trained"))
        logger.info(
            MESSAGES.get("info.ridge.params"),
            float(args.ridge_alpha),
            int(args.seed),
            int(args.neg_per_pos),
            float(args.negative_weight),
        )
        logger.info(
            MESSAGES.get("info.ridge.dataset"),
            int(fb_rows),
            int(dataset_rows),
        )
        logger.info(MESSAGES.get("info.ridge.coef_header"))

        # 特徴量ごとの係数を出力
        for idx, feature_name in enumerate(FEATURE_ORDER):
            label_ja = FEATURE_LABELS_JA.get(feature_name, "")
            logger.info(
                MESSAGES.get("info.ridge.coef_row"),
                idx + 1,
                feature_name,
                float(coef_list[idx]),
                label_ja,
                )

        # 切片を出力
        logger.info(
            MESSAGES.get("info.ridge.coef_row"),
            len(FEATURE_ORDER) + 1,
            LABEL_INTERCEPT,
            float(intercept),
            MESSAGES.get("label.intercept"),
            )