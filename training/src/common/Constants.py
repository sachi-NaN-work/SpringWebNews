"""定数定義"""

# i18n ディレクトリ名
I18N_DIR_NAME = "i18n"
# メッセージファイル名テンプレート
MESSAGE_FILE_BASENAME_TEMPLATE = "messages_{locale}.json"
# 文字コード
UTF8_ENCODING = "utf-8"

# JSONファイル
FIXED_PARAMS_JSON = "rec_fixed_params.json"
REPORT_JSON = "rec_report.tmp.json"

# Ridge モデル属性名定義
RIDGE_ATTR_COEF = "coef_"
RIDGE_ATTR_INTERCEPT = "intercept_"
RIDGE_DEFAULT_INTERCEPT = 0.0

# レポートキー定義
REPORT_KEY_TRAINED = "trained"
REPORT_KEY_FEEDBACK_ROWS = "feedback_rows"
REPORT_KEY_MODEL_FILE = "model_file"
REPORT_KEY_FIXED_PARAMS_FILE = "fixed_params_file"
REPORT_KEY_TYPE_WEIGHTS = "type_weights"
REPORT_KEY_DATASET_ROWS = "dataset_rows"
REPORT_KEY_NEG_PER_POS = "neg_per_pos"
REPORT_KEY_RIDGE_ALPHA = "ridge_alpha"
REPORT_KEY_NEGATIVE_WEIGHT = "negative_weight"
REPORT_KEY_BAD_NEGATIVE_WEIGHT_MIN = "bad_negative_weight_min"
REPORT_KEY_BAD_NEGATIVE_WEIGHT_MULT = "bad_negative_weight_mult"

# ラベルキー
LABEL_INTERCEPT = "intercept"

# 特徴量ラベル定義（日本語）
FEATURE_LABELS_JA = {
    "exposure": "記事の露出度（表示された度合い）",
    "cat_viewShare": "カテゴリ別の閲覧割合",
    "cat_favShare": "カテゴリ別のお気に入り割合",
    "cat_goodShare": "カテゴリ別の高評価割合",
    "cat_badShare": "カテゴリ別の低評価割合",
    "strongest_tag_viewShare": "最も強いタグの閲覧割合",
    "strongest_tag_favShare": "最も強いタグのお気に入り割合",
    "strongest_tag_goodShare": "最も強いタグの高評価割合",
    "strongest_tag_badShare": "最も強いタグの低評価割合",
}

REPORT_KEY_TS = "ts"
REPORT_KEY_SOURCE_WEIGHT = "source_weights"