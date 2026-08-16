package com.news.recapp.presentation.web;

import com.news.recapp.config.IdleTimeoutProperties;
import com.news.recapp.util.Messages;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 無操作タイムアウト時の画面遷移を処理するコントローラ。
 */
@Controller
public class TimeoutController {

    // 無操作タイムアウト設定を保持
    private final IdleTimeoutProperties idleTimeoutProperties;

    /**
     * 無操作タイムアウト設定を受け取る
     */
    public TimeoutController(IdleTimeoutProperties idleTimeoutProperties) {
        // 無操作タイムアウト設定を保持
        this.idleTimeoutProperties = idleTimeoutProperties;
    }

    /**
     * セッションを破棄して共通エラー画面へリダイレクトする。
     */
    @GetMapping("/timeout")
    public Mono<Void> timeout(ServerWebExchange exchange) {

        // タイムアウトメッセージを取得
        String message = Messages.getMsg("common.err.timeout", idleTimeoutProperties.getDurationText());
        // タイムアウトメッセージを URL エンコード
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
        // 共通エラー画面へのリダイレクト先を生成
        URI location = URI.create("/error?msg=" + encodedMessage);

        // セッションを破棄してから共通エラー画面へリダイレクト
        return exchange.getSession()
                .flatMap(session -> session.invalidate())
                .then(Mono.defer(() -> {
                    // レスポンスを取得
                    var response = exchange.getResponse();
                    // リダイレクトステータスを設定
                    response.setStatusCode(HttpStatus.FOUND);
                    // Location ヘッダに遷移先 URI を設定
                    response.getHeaders().setLocation(location);
                    // レスポンス完了を返却
                    return response.setComplete();
                }));
    }
}
