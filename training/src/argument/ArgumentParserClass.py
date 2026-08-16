"""コマンドライン引数の解析を提供。

引数解釈の責務を 1 クラスへ分離し、
呼び出し側（main）では "何を実行するか" のみに集中できるようにします。
"""

import argparse
import json
from pathlib import Path
from typing import List, Optional

from training.src.argument.ArgumentRecordDTO import ArgumentRecordDTO
from training.src.common.MessageCatalog import MessageCatalog

# メッセージカタログ（日本語）を保持
MESSAGES = MessageCatalog(locale="ja")


# --------------------------------------
# コマンドライン引数の解析クラス
# --------------------------------------
class ArgumentParserClass:

    # --------------------------------------
    # コマンドライン引数を解析して DTO を返却
    # --------------------------------------
    def parse(self, argv: List[str]) -> ArgumentRecordDTO:

        # parser を設定
        parser = argparse.ArgumentParser()

        # ONNX 出力パスのデフォルト設定を設定ファイルから取得
        default_out_path = self._load_default_out_path()

        # out 引数を追加
        parser.add_argument(
            "--out",
            default=str(default_out_path),
            help=MESSAGES.get("help.out"),
        )


        # neg-per-pos 引数を追加
        parser.add_argument(
            "--neg-per-pos",
            type=int,
            default=0,
            help=MESSAGES.get("help.neg_per_pos"),
        )

        # seed 引数を追加
        parser.add_argument(
            "--seed",
            type=int,
            default=7,
            help=MESSAGES.get("help.seed"),
        )

        # ridge-alpha 引数を追加
        parser.add_argument(
            "--ridge-alpha",
            type=float,
            default=1.0,
            help=MESSAGES.get("help.ridge_alpha"),
        )

        # negative-weight 引数を追加
        parser.add_argument(
            "--negative-weight",
            type=float,
            default=0.10,
            help=MESSAGES.get("help.negative_weight"),
        )

        # source-weight-yaml 引数を追加
        parser.add_argument(
            "--source-weight-yaml",
            default=None,
            help=MESSAGES.get("help.source_weight_yaml"),
        )

        # rt-rec-yaml 引数を追加
        parser.add_argument(
            "--rt-rec-yaml",
            default=None,
            help=MESSAGES.get("help.rt_rec_yaml"),
        )

        # verbose 引数を追加
        parser.add_argument(
            "--verbose",
            action="store_true",
            help=MESSAGES.get("help.verbose"),
        )

        # 引数一覧を設定する
        args = parser.parse_args(argv)

        # コマンドライン引数 DTO を組み立てて返却
        return ArgumentRecordDTO(
            out_path=Path(args.out),
            neg_per_pos=int(args.neg_per_pos),
            seed=int(args.seed),
            ridge_alpha=float(args.ridge_alpha),
            negative_weight=float(args.negative_weight),
            source_weight_yaml=Path(args.source_weight_yaml) if args.source_weight_yaml else None,
            rt_rec_yaml=Path(args.rt_rec_yaml) if args.rt_rec_yaml else None,
            verbose=bool(args.verbose),
        )

    # --------------------------------------
    # ONNX 出力パスのデフォルト設定を設定ファイルから取得
    # --------------------------------------
    def _load_default_out_path(self) -> Path:

        # 設定ファイルのパスを取得
        config_path = self._find_model_paths_config()

        # 設定ファイルが見つからない場合
        if config_path is None:
            # デフォルト出力パスを返却
            return Path("model/rec.onnx")

        try:
            # 設定ファイルを読み込み
            config_text = config_path.read_text(encoding="utf-8")
        except Exception:
            # デフォルト出力パスを返却
            return Path("model/rec.onnx")

        try:
            # JSON で読み込み
            config = json.loads(config_text)
        except Exception:
            # デフォルト出力パスを返却
            return Path("model/rec.onnx")

        # ONNX 出力先を取得
        out_value = config.get("out")

        # ONNX 出力先が取得できなかった場合
        if not out_value:
            # デフォルト出力パスを返却
            return Path("model/rec.onnx")

        # 設定された ONNX 出力パスを返却
        return Path(str(out_value))

    # --------------------------------------
    # 設定ファイルのパスを取得
    # --------------------------------------
    def _find_model_paths_config(self) -> Optional[Path]:

        # 設定ファイルのデフォルトパスを取得
        candidate = Path("model/model-paths.json")

        # 設定ファイルが見つからない場合
        if candidate.exists():
            # 設定ファイルのデフォルトパスを返却
            return candidate

        # 実行ファイルパスを取得
        here = Path(__file__).resolve()

        # 親ディレクトリを探索
        for parent in here.parents:

            # 推定ファイルパスを設定
            guess = parent / "model" / "model-paths.json"

            # 推定ファイルが存在する場合
            if guess.exists():
                # 推定ファイルパスを返却
                return guess

        # None を返却
        return None
