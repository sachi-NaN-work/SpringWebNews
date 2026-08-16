package com.news.recapp.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * messages.properties からメッセージを取得するユーティリティ
 */
public final class Messages {

    // プロパティファイル名を保持
    private static final String BASENAME = "messages";

    // コンストラクタ
    private Messages() {}

    /**
     * メッセージを取得
     */
    public static String getMsg(String key, Object... args) {

        // メッセージキーが未設定の場合は、空文字を返却
        if (key == null || key.isBlank()) return "";

        try {
            // 日本語ロケールのメッセージ定義ファイルを読み込む
            ResourceBundle bundle = ResourceBundle.getBundle(BASENAME, Locale.JAPAN);
            // 指定されたメッセージキーに対応するメッセージを取得
            String pattern = bundle.getString(key);

            // パラメータ引数が未設定の場合は、メッセージを返却
            if (args == null || args.length == 0) return pattern;

            // プレースホルダにパラメータ引数を埋め込み、完成したメッセージを返却
            return MessageFormat.format(pattern, args);

        } catch (MissingResourceException missingResourceException) {
            // キー未定義の場合は、キー文字列を返却
            return key;
        }
    }
}
