package com.news.recapp.application.exception;

/**
 * 表示用のメッセージキーを保持する例外。
 *   - 画面に表示する文言はメッセージ定義から解決する。
 *   - 例外メッセージ（内容）はキー名となるため、直接表示しないこと。
 */
public class AppMessageException extends RuntimeException {

    // メッセージキーを保持
    private final String messageKey;
    // 引数一覧を保持
    private final Object[] args;

    // コンストラクタ
    public AppMessageException(
        String messageKey,
        Object... args
    ) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = (args == null) ? new Object[0] : args;
    }

    /**
     * メッセージキーを取得
     */
    public String getMessageKey() {
        return messageKey;
    }

    /**
     * 引数を取得
     */
    public Object[] getArgs() {
        return args;
    }
}
