"""前処理を提供。

役割:
  - DB 接続情報を環境変数から取得
  - source 重み（Spring の application.yml）を読み込み
  - Postgres から news/feedback/news_hashtags テーブルを読み込み（タグ特徴量を含む）
  - AppContextDTO を組み立てて返却
"""

import logging

from training.src.argument.ArgumentRecordDTO import ArgumentRecordDTO
from training.src.common.MessageCatalog import MessageCatalog
from training.src.config.RtRecLoaderClass import RtRecLoaderClass
from training.src.config.WeightLoaderClass import WeightLoaderClass
from training.src.db.DbClientClass import DbClientClass
from training.src.db.DbConfigRecordDTO import DbConfigRecordDTO
from training.src.process.AppContextDTO import AppContextDTO

logger = logging.getLogger(__name__)

# メッセージカタログ（日本語）を保持
MESSAGES = MessageCatalog(locale="ja")


# --------------------------------------
# 前処理クラス
# --------------------------------------
class PreProcessClass:

    # --------------------------------------
    # 前処理を実行
    # --------------------------------------
    def run(self, args: ArgumentRecordDTO) -> AppContextDTO:

        # DB 接続設定を環境変数から取得
        db_cfg = DbConfigRecordDTO.from_env()

        # ログ出力
        logger.info(
            MESSAGES.get("info.db_connect"),
            db_cfg.host,
            db_cfg.db,
            db_cfg.user,
        )

        # 重み設定の読込クラスを用意
        weight_loader = WeightLoaderClass()

        # source 重み設定を読み込み（未指定/失敗時はデフォルトを使用）
        weights = weight_loader.load_source_weights(args.source_weight_yaml)

        # 正例のイベント種別重みを読み込み（未指定/失敗時はデフォルトを使用）
        type_weights = weight_loader.load_type_weights(args.source_weight_yaml)

        # 「bad」負例ルールを読み込み（未指定/失敗時はデフォルトを使用）
        bad_negative_rule = weight_loader.load_bad_negative_rule(args.source_weight_yaml)

        # rt-rec 係数を読み込み（未指定の場合は profile 用 YAML または config/application.yml を探索）
        rt_rec = RtRecLoaderClass().load_rt_rec(args.rt_rec_yaml)

        # DB から学習に必要なテーブル情報を取得（news/feedback/news_hashtags）
        news_df, fb_df, nh_df = DbClientClass(db_cfg).read_tables()

        # 出力先ディレクトリを作成（存在していてもエラーにしない）
        args.out_path.parent.mkdir(parents=True, exist_ok=True)

        # 共有コンテキストを組み立てて返却
        return AppContextDTO(
            args=args,
            db_config=db_cfg,
            source_weights=weights,
            type_weights=type_weights,
            bad_negative_weight_min=float(bad_negative_rule["min"]),
            bad_negative_weight_mult=float(bad_negative_rule["mult"]),
            rt_rec=rt_rec,
            news_df=news_df,
            feedback_df=fb_df,
            news_hashtags_df=nh_df,
        )
