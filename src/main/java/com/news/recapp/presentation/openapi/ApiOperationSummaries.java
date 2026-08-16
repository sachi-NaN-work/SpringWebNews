package com.news.recapp.presentation.openapi;

/**
 * OpenAPI の概要文キーを定義
 */
public final class ApiOperationSummaries {

    // コンストラクタ
    private ApiOperationSummaries() {}

    // お気に入り登録/解除 API の概要キー
    public static final String FAVORITE_TOGGLE = "openapi.summary.favorite_toggle";

    // フィードバック登録 API（JSON） の概要キー
    public static final String FEEDBACK_FEEDBACK_JSON = "openapi.summary.feedback_json";

    // カテゴリ一覧 API の概要キー
    public static final String NEWS_API_CATEGORIES = "openapi.summary.news_categories";

    // ニュース取得 API の概要キー
    public static final String NEWS_API_GET = "openapi.summary.news_get";

    // 最新ニュース一覧 API の概要キー
    public static final String NEWS_API_LATEST = "openapi.summary.news_latest";

    // 推薦 API の概要キー
    public static final String REC_REC = "openapi.summary.rec_rec";
}
