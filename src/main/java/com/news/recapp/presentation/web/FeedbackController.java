package com.news.recapp.presentation.web;

import com.news.recapp.presentation.openapi.ApiOperationSummaries;
import com.news.recapp.application.dto.ApiDtos;
import com.news.recapp.application.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.security.Principal;

/**
 * フィードバック内容リクエストのコントローラ
 * - JSON 形式で送られてきたフィードバックを記録
 */
@RestController
public class FeedbackController {

    // フィードバックサービスを保持
    private final FeedbackService feedbackService;
    // メッセージサービスを保持
    private final MessageSource messageSource;

    // コンストラクタ
    public FeedbackController(
            FeedbackService feedbackService,
            MessageSource messageSource
    ) {
        this.feedbackService = feedbackService;
        this.messageSource = messageSource;
    }

    /**
     * フィードバックを記録
     * - 未ログインなら 401 エラーを返す
     * - ログイン済みならサービスに記録を依頼し、その結果を返す
     */
    @Operation(summary = ApiOperationSummaries.FEEDBACK_FEEDBACK_JSON)
    @PostMapping(value = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiDtos.FeedbackResponse> feedbackJson(
            Principal principal,
            @Valid @RequestBody ApiDtos.FeedbackRequest req,
            ServerWebExchange exchange
    ) {
        // ログインユーザーが未設定の場合
        if (principal == null) {

            // エラー（401）を返却
            return Mono.error(new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                messageSource.getMessage(
                    "feedback.ctrl.err.login_required",
                    null,
                    exchange.getLocaleContext().getLocale()
                )
            ));
        }

        // ログインユーザー名で記録
        return feedbackService.recordFeedback(principal.getName(), req);
    }
}
