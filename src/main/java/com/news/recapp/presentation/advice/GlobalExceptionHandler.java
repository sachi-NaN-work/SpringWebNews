package com.news.recapp.presentation.advice;

import com.news.recapp.application.exception.AppMessageException;
import com.news.recapp.util.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 例外発生時の共通エラーハンドリングを提供
 */
@ControllerAdvice
@Order(-2)
@Component
public class GlobalExceptionHandler {

    // ロガー
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // メッセージソース
    private final MessageSource messageSource;

    // コンストラクタ
    public GlobalExceptionHandler(
            MessageSource messageSource
    ) {
        this.messageSource = messageSource;
    }

    /**
     * リクエスト入力エラーをエラーページへリダイレクト
     */
    @ExceptionHandler(ServerWebInputException.class)
    public Mono<Rendering> handleBadRequest(
            ServerWebInputException ex,
            ServerWebExchange exchange
    ) {

        // 入力エラーの理由を警告ログへ記録
        log.warn(Messages.getMsg("log.global.bad_request"), ex.getReason());

        // 入力エラーの表示メッセージを取得
        String message = messageSource.getMessage(
            "common.err.bad_request",
            null,
            exchange.getLocaleContext().getLocale()
        );
        // URL エンコードしたエラーメッセージを生成
        String encoded = url(message);
        // エラーページへのリダイレクト先を生成
        String path = "/error?msg=" + encoded;

        // エラーページへのリダイレクトを返却
        return Mono.just(Rendering.redirectTo(path).build());
    }

    /**
     * メッセージキーを含むアプリケーション例外をエラーページへリダイレクト
     */
    @ExceptionHandler(AppMessageException.class)
    public Mono<Rendering> handleAppMessage(
            AppMessageException ex,
            ServerWebExchange exchange
    ) {

        // アプリケーション例外のメッセージキーを警告ログへ記録
        log.warn(Messages.getMsg("log.global.app_message"), ex.getMessageKey());

        // メッセージキーに対応する表示メッセージを取得
        String message = messageSource.getMessage(
            ex.getMessageKey(),
            ex.getArgs(),
            exchange.getLocaleContext().getLocale()
        );
        // URL エンコードしたエラーメッセージを生成
        String encoded = url(message);
        // エラーページへのリダイレクト先を生成
        String path = "/error?msg=" + encoded;

        // エラーページへのリダイレクトを返却
        return Mono.just(Rendering.redirectTo(path).build());
    }

    /**
     * 引数不正例外をエラーページへリダイレクト
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<Rendering> handleIllegalArgument(IllegalArgumentException ex) {

        // 引数不正例外の内容を警告ログへ記録
        log.warn(Messages.getMsg("log.global.illegal_arg"), ex.getMessage());

        // URL エンコードしたエラーメッセージを生成
        String encoded = url(ex.getMessage());
        // エラーページへのリダイレクト先を生成
        String path = "/error?msg=" + encoded;

        // エラーページへのリダイレクトを返却
        return Mono.just(Rendering.redirectTo(path).build());
    }

    /**
     * 権限不足例外を 403 応答へ変換
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Mono<Void> handleAccessDenied(
            AccessDeniedException ex,
            ServerWebExchange exchange
    ) {

        // 権限不足例外の内容を警告ログへ記録
        log.warn(Messages.getMsg("log.global.access_denied"), ex.getMessage());

        // レスポンスへ 403 ステータスを設定
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);

        // レスポンス完了を返却
        return exchange.getResponse().setComplete();
    }

    /**
     * 想定外例外を詳細メッセージ付きでエラーページへリダイレクト
     */
    @ExceptionHandler(Exception.class)
    public Mono<Rendering> handleGeneral(
            Exception ex,
            ServerWebExchange exchange
    ) {

        // 想定外例外をエラーログへ記録
        log.error(Messages.getMsg("log.global.unexpected"), ex.getMessage(), ex);

        // 例外メッセージを取得
        String rawMessage = ex.getMessage();
        // 表示用の例外メッセージを初期化
        String safeMessage = rawMessage;

        // 表示用の例外メッセージが null の場合
        if (safeMessage == null) {
            // 表示用の例外メッセージを空文字へ置き換え
            safeMessage = "";
        }

        // 例外クラス名（短縮名）を取得
        String simpleName = ex.getClass().getSimpleName();
        // 想定外例外の詳細メッセージを生成
        String composed = messageSource.getMessage(
            "common.err.unexpected.detail",
            new Object[] { simpleName, safeMessage },
            exchange.getLocaleContext().getLocale()
        );
        // URL エンコードしたエラーメッセージを生成
        String encoded = url(composed);

        // エラーページへのリダイレクト先を生成
        String path = "/error?msg=" + encoded;
        // エラーページへのリダイレクト情報を生成
        Rendering rendering = Rendering.redirectTo(path).build();

        // エラーページへのリダイレクトを返却
        return Mono.just(rendering);
    }

    /**
     * 指定文字列を URL エンコードして返却
     */
    private static String url(String uriString) {

        // URL エンコード対象の文字列を設定
        String value = uriString;

        // URL エンコード対象の文字列が null の場合
        if (value == null) {
            // URL エンコード対象の文字列を空文字へ置き換え
            value = "";
        }

        // URL エンコードした文字列を生成
        String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8);

        // URL エンコードした文字列を返却
        return encoded;
    }
}
