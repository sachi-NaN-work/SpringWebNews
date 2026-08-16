package com.news.recapp.presentation.web;

import com.news.recapp.application.service.UserAccountService;
import com.news.recapp.persistence.entity.AppUserEntity;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * パスワード管理画面へのリクエストを処理するコントローラ
 */
@Controller
public class MyPagePasswordController {

    // ログインユーザーサービスを保持
    private final UserAccountService userAccountService;
    // メッセージサービスを保持
    private final MessageSource messageSource;
    // 画面向けメッセージを保持
    private final UiMessageResolver uiMessageResolver;

    // コンストラクタ
    public MyPagePasswordController(
            UserAccountService userAccountService,
            MessageSource messageSource,
            UiMessageResolver uiMessageResolver
    ) {
        this.userAccountService = userAccountService;
        this.messageSource = messageSource;
        this.uiMessageResolver = uiMessageResolver;
    }

    /**
     * パスワードページを表示する。
     */
    @GetMapping("/mypage/password")
    @PreAuthorize("hasAuthority('PASSWORD_CHANGE')")
    public Mono<Rendering> passwordPage(
            @RequestParam(name = "msg", required = false) String msg,
            @RequestParam(name = "err", required = false) String err,
            Authentication auth
    ) {
        // ログインユーザー名を取得
        String username = auth.getName();

        // ログインユーザー名を返却
        return userAccountService.getUser(username)
            // 取得できなかった場合は、最低限の情報を持つダミー情報で補完
            .defaultIfEmpty(new AppUserEntity(username, "", UserAccountService.ROLE_USER, true))
                .map(me -> Rendering.view("mypage/password")
                        .modelAttribute("msg", msg)
                        .modelAttribute("err", err)
                        .modelAttribute("me", me)
                        .build());
    }

    /**
     * パスワード変更を実行
     */
    @PostMapping("/mypage/password")
    @PreAuthorize("hasAuthority('PASSWORD_CHANGE')")
    public Mono<Rendering> changePassword(
            Authentication auth,
            ServerWebExchange exchange
    ) {

        // フォーム値を読取
        return MyPageSupport.readFormValues(exchange)
                .flatMap(data -> {

                    // 現在のパスワード
                    String currentPassword = data.get("currentPassword");
                    // 新しいパスワード
                    String newPassword = data.get("newPassword");
                    // 新しいパスワード（確認）
                    String confirmPassword = data.get("confirmPassword");

                    // 各フォーム値が取得できなかった場合
                    if (currentPassword == null || currentPassword.isBlank()
                            || newPassword == null || newPassword.isBlank()
                            || confirmPassword == null || confirmPassword.isBlank()) {

                        // エラーメッセージを設定
                        String msg = messageSource.getMessage(
                                "common.err.bad_request",
                                null,
                                exchange.getLocaleContext().getLocale()
                        );
                        // エラーメッセージを付与して、パスワード管理画面へリダイレクトを返却
                        return Mono.just(MyPageSupport.redirectErr("/mypage/password", msg));
                    }

                    // パスワード変更
                    return userAccountService.changePassword(auth.getName(), currentPassword, newPassword, confirmPassword)

                            // 成功した場合、成功メッセージを付与して、パスワード管理画面へリダイレクトを返却
                            .thenReturn(MyPageSupport.redirectOk("/mypage/password",
                                    messageSource.getMessage(
                                            "mypage.msg.password.changed",
                                            null,
                                            exchange.getLocaleContext().getLocale()
                                    )
                            ))

                            // 例外が発生した場合、エラーメッセージを付与して、パスワード管理画面へリダイレクトを返却
                            .onErrorResume(ex ->
                                    Mono.just(MyPageSupport.redirectErr(
                                            "/mypage/password",
                                            uiMessageResolver.resolve(ex, exchange.getLocaleContext().getLocale())
                                    ))
                            );
                });
    }
}
