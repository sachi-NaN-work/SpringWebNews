package com.news.recapp.presentation.web;

import com.news.recapp.application.exception.AppMessageException;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 例外を画面表示用のメッセージへ変換するサポートクラス
 */
@Component
public class UiMessageResolver {

    // メッセージサービスを保持
    private final MessageSource messageSource;

    // コンストラクタ
    public UiMessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 例外を表示用メッセージへ変換
     */
    public String resolve(Throwable ex, Locale locale) {

        // ロケール（未指定なら日本語）
        Locale loc = (locale == null) ? Locale.JAPAN : locale;

        // 業務例外（メッセージキー付き）を検索
        AppMessageException ame = findAppMessageException(ex);
        if (ame != null) {

            // メッセージキーを解決して返却
            return messageSource.getMessage(ame.getMessageKey(), ame.getArgs(), loc);
        }

        // DB 関連の例外の場合、DB エラーを返却
        if (isDataAccess(ex)) {
            return messageSource.getMessage("common.err.db", null, loc);
        }

        // 想定外のエラーを返却
        return messageSource.getMessage("common.err.unexpected", null, loc);
    }

    /**
     * 例外の原因チェーンに {@link DataAccessException} が含まれるかを判定
     */
    private static boolean isDataAccess(Throwable ex) {
        Throwable cur = ex;

        // Throwable のループ処理
        while (cur != null) {

            // DataAccessException が見つかった場合、true を返す
            if (cur instanceof DataAccessException) return true;
            // チェーンを一段深く辿る
            cur = cur.getCause();
        }

        // 見つからなければ false を返す
        return false;
    }

    /**
     * 例外の原因チェーンから {@link AppMessageException} を探索
     */
    private static AppMessageException findAppMessageException(Throwable ex) {
        Throwable cur = ex;

        // Throwable のループ処理
        while (cur != null) {

            // AppMessageException が見つかった場合、AppMessageException を返す
            if (cur instanceof AppMessageException ame) return ame;
            // チェーンを一段深く辿る
            cur = cur.getCause();
        }

        // 見つからなければ null を返す
        return null;
    }
}
