package com.news.recapp.presentation.web;

import com.news.recapp.presentation.openapi.ApiOperationSummaries;
import com.news.recapp.application.dto.ApiDtos;
import com.news.recapp.application.service.FavoriteService;
import com.news.recapp.application.service.FeedbackService;
import com.news.recapp.config.FavoritesProperties;
import com.news.recapp.util.Messages;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.UUID;

/**
 * お気に入りの API コントローラ
 * - ベースパス: /api/favorites
 * - お気に入りのON/OFFを切り替え、結果（状態・件数）を返す
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    // ロガーを保持
    private static final Logger log =
            LoggerFactory.getLogger(FavoriteController.class);

    /**
     * お気に入り切り替えのリクエスト
     * - source: どの画面/機能からの操作か（例: "news", "mypage" など）
     * - eventId: 追跡用のイベントID
     */
    public record FavToggleRequest(String source, String eventId) {}

    /**
     * お気に入り切り替えのレスポンス
     * - newsId: 対象ニュースID
     * - favorite: 切り替え後にお気に入り状態か
     * - count: 現在のお気に入り件数
     * - message: エラーやメッセージ
     */
    public record FavToggleResponse(UUID newsId, boolean favorite, int count, String message) {}

    // お気に入り切り替えサービスを保持
    private final FavoriteService favoriteService;
    // フィードバックを記録するサービスを保持
    private final FeedbackService feedbackService;
    // お気に入り上限などの設定値
    private final FavoritesProperties favProps;
    // メッセージサービスを保持
    private final MessageSource messageSource;

    // コンストラクタ
    public FavoriteController(
        FavoriteService favoriteService,
        FeedbackService feedbackService,
        FavoritesProperties favProps,
        MessageSource messageSource
    ) {
        this.favoriteService = favoriteService;
        this.feedbackService = feedbackService;
        this.favProps = favProps;
        this.messageSource = messageSource;
    }

    /**
     * お気に入りをトグル（ON/OFF切り替え）する
     * POST /api/favorites/{newsId}/favToggle
     * - 未ログインは "anonymous" として扱う
     * - 上限超過なら 409 を返す
     * - 成功時は 200 を返す（フィードバック記録に失敗しても 200 のまま）
     */
    @Operation(summary = ApiOperationSummaries.FAVORITE_TOGGLE)
    @PostMapping("/{newsId}/favToggle")
    public Mono<ResponseEntity<FavToggleResponse>> favToggle(
            @PathVariable UUID newsId,
            @RequestBody(required = false) FavToggleRequest body,
            Principal principal,
            ServerWebExchange exchange
    ) {

        // ログインユーザー名を取得（未指定なら "anonymous"）
        String userName = (principal == null) ? "anonymous" : principal.getName();

        // リクエスト元の情報（未指定なら "unknown"）
        String source =(body != null && body.source() != null && !body.source().isBlank()) ? body.source(): "unknown";

        // イベント ID（未指定なら空文字）
        String eventId = (body != null) ? body.eventId() : "";

        // お気に入りを切り替えた結果を取得（状態/件数/上限超過など）
        Mono<FavoriteService.ToggleResult> toggleResultMono = favoriteService.toggle(userName, newsId);

        // 切り替え結果に応じたレスポンスを付与して返却
        return toggleResultMono.flatMap(favToggle -> {

            // お気に入りの最大件数に達している場合
            if (favToggle.limitReached()) {

                // お気に入りの最大件数を取得
                int maxFav = favProps.getMaxFav();
                // お気に入り最大件数メッセージを格納する変数
                String msg = messageSource.getMessage(
                    "favorite.ctrl.err.limit_reached",
                    new Object[] { maxFav },
                    exchange.getLocaleContext().getLocale()
                );

                // レスポンス内容を格納
                FavToggleResponse responseBody = new FavToggleResponse(newsId, false, favToggle.count(), msg);
                ResponseEntity.BodyBuilder builder = ResponseEntity.status(409);
                ResponseEntity<FavToggleResponse> responseEntity = builder.body(responseBody);

                // 結果（失敗）を返却
                return Mono.just(responseEntity);

            // お気に入りの最大件数に達していない場合
            } else {

                // 切り替え後の状態に応じてお気に入りの種別を決める（favorite / unfavorite）
                String fabType = favToggle.favorite() ? "favorite" : "unfavorite";

                // フィードバック記録用のリクエスト DTO を作成
                ApiDtos.FeedbackRequest feedBack = new ApiDtos.FeedbackRequest(newsId, fabType, source, eventId, "");

                // フィードバックの記録処理
                Mono<Void> recordMono = feedbackService.recordFeedback(userName, feedBack)
                        // 値なしで返却
                        .then();

                // フィードバック記録が失敗してもお気に入り操作は成功扱いにする
                Mono<Void> safeRecordMono = recordMono
                        // 例外が発生した場合、警告ログを出力
                        .doOnError(ex ->
                                log.warn(
                                        Messages.getMsg("log.favorite.feedback_record_failed"),
                                        ex.toString()
                                )
                        )
                        // 例外が発生した場合、スキップ
                        .onErrorResume(ex -> Mono.empty());

                // レスポンス内容を格納
                FavToggleResponse responseBody = new FavToggleResponse(newsId, favToggle.favorite(), favToggle.count(), "");
                ResponseEntity<FavToggleResponse> okResponseEntity = ResponseEntity.ok(responseBody);

                // 結果（成功）を返却
                return safeRecordMono.thenReturn(okResponseEntity);
            }
        });

    }
}
