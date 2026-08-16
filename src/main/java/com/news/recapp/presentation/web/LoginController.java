package com.news.recapp.presentation.web;

import com.news.recapp.config.AppLinksProperties;
import com.news.recapp.presentation.security.LockoutProperties;
import java.time.Duration;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ログイン画面を表示する
 */
@Controller
public class LoginController {

    // ロックアウト情報を保持
    private final LockoutProperties lockoutProps;
    // リンク情報を保持
    private final AppLinksProperties linksProps;
    // メッセージサービスを保持
    private final MessageSource messageSource;

    // コンストラクタ
    public LoginController(LockoutProperties lockoutProps, AppLinksProperties linksProps, MessageSource messageSource) {
        this.lockoutProps = lockoutProps;
        this.linksProps = linksProps;
        this.messageSource = messageSource;
    }

    /**
     * ログイン処理
     */
    @GetMapping("/login")
    public String login(Model model, Locale locale) {

        // 時間の差を格納する変数
        Duration duration = lockoutProps.getDuration();
        // ロックアウトメッセージの設定
        model.addAttribute(
            "lockoutMessage",
            messageSource.getMessage(
                "login.lockout.message",
                new Object[] { getTimeMsg(duration, locale) },
                locale
            )
        );

        // リンクの設定
        model.addAttribute("linkUrl", linksProps.getLinkUrl());
        // メッセージの設定
        model.addAttribute("textComment", linksProps.getTextComment());

        // ログイン画面を表示
        return "login";
    }

    /**
     * 時間表示の取得
     */
    private String getTimeMsg(Duration duration, Locale locale) {
        // 秒数の取得
        long seconds = duration.getSeconds();

        // １時間以上の場合
        if (seconds % 3600 == 0) {
            // 時間の返却
            return messageSource.getMessage(
                "login.lockout.time.hours",
                new Object[] { (seconds / 3600) },
                locale
            );
        }

        // １分間以上の場合
        if (seconds % 60 == 0) {
            // 分数の返却
            return messageSource.getMessage(
                "login.lockout.time.minutes",
                new Object[] { (seconds / 60) },
                locale
            );
        }

        // 秒数の返却
        return messageSource.getMessage(
            "login.lockout.time.seconds",
            new Object[] { seconds },
            locale
        );
    }
}
