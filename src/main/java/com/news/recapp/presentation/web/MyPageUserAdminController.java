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

import java.util.List;

/**
 * ユーザー管理（管理画面）へのリクエストを処理するコントローラ
 */
@Controller
public class MyPageUserAdminController {

    // ログインユーザーサービスを保持
    private final UserAccountService userAccountService;
    // メッセージサービスを保持
    private final MessageSource messageSource;
    // 画面向けメッセージを保持
    private final UiMessageResolver uiMessageResolver;

    // コンストラクタ
    public MyPageUserAdminController(
            UserAccountService userAccountService,
            MessageSource messageSource,
            UiMessageResolver uiMessageResolver
    ) {
        this.userAccountService = userAccountService;
        this.messageSource = messageSource;
        this.uiMessageResolver = uiMessageResolver;
    }

    /**
     * ユーザー作成管理ページを表示
     */
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @GetMapping("/mypage/admin/user/create")
    public Mono<Rendering> adminUserCreatePage(
        @RequestParam(name = "msg", required = false) String msg,
        @RequestParam(name = "err", required = false) String err,
        Authentication auth
    ) {
        // ログインユーザー名を取得
        String username = auth.getName();

        // ログインユーザー情報を取得
        Mono<AppUserEntity> meMono = userAccountService.getUser(username)
                // 取得できなかった場合は、最低限の情報を持つダミー情報で補完
                .defaultIfEmpty(new AppUserEntity(username, "", UserAccountService.ROLE_USER, true));

        // ロール一覧（表示用）を取得
        List<MyPageSupport.RoleOption> roles = MyPageSupport.roleOptions();

        // 各情報を付与して、ユーザー作成管理画面を返却
        return meMono.map(me ->
                Rendering.view("mypage/admin_user_create")
                        .modelAttribute("msg", msg)
                        .modelAttribute("err", err)
                        .modelAttribute("me", me)
                        .modelAttribute("roles", roles)
                        .build());
    }

    /**
     * ユーザー有効フラグ管理ページを表示
     */
    @PreAuthorize("hasAuthority('USER_ENABLE_DISABLE')")
    @GetMapping("/mypage/admin/user/enabled")
    public Mono<Rendering> adminUserEnabledPage(
        @RequestParam(name = "msg", required = false) String msg,
        @RequestParam(name = "err", required = false) String err,
        Authentication auth
    ) {
        // ログインユーザー名を取得
        String username = auth.getName();

        // ログインユーザー情報を取得
        Mono<AppUserEntity> meMono = userAccountService.getUser(username)
                // 取得できなかった場合は、最低限の情報を持つダミー情報で補完
                .defaultIfEmpty(new AppUserEntity(username, "", UserAccountService.ROLE_USER, true));

        // ユーザー一覧を取得
        Mono<List<AppUserEntity>> usersMono = userAccountService.listUsers().collectList();

        // 各情報を付与して、ユーザー有効フラグ管理画面を返却
        return Mono.zip(meMono, usersMono)
            .map(tuple ->
                    Rendering.view("mypage/admin_user_enabled")
                            .modelAttribute("msg", msg)
                            .modelAttribute("err", err)
                            .modelAttribute("me", tuple.getT1())
                            .modelAttribute("users", tuple.getT2())
                            .build()
            );
    }

    /**
     * ユーザーロール管理ページを表示
     */
    @PreAuthorize("hasAuthority('USER_ROLE_CHANGE')")
    @GetMapping("/mypage/admin/user/role")
    public Mono<Rendering> adminUserRolePage(
        @RequestParam(name = "msg", required = false) String msg,
        @RequestParam(name = "err", required = false) String err,
        Authentication auth
    ) {
        // ログインユーザー名を取得
        String username = auth.getName();

        // ログインユーザー情報を取得
        Mono<AppUserEntity> meMono = userAccountService.getUser(username)
                // 取得結果が空の場合は既定値を返す
                .defaultIfEmpty(new AppUserEntity(username, "", UserAccountService.ROLE_USER, true));

        // ユーザー一覧を取得
        Mono<List<AppUserEntity>> usersMono = userAccountService.listUsers().collectList();

        // 各情報を付与して、ユーザーロール管理画面を返却
        return Mono.zip(meMono, usersMono)
            .map(tuple ->
                    Rendering.view("mypage/admin_user_role")
                            .modelAttribute("msg", msg)
                            .modelAttribute("err", err)
                            .modelAttribute("me", tuple.getT1())
                            .modelAttribute("users", tuple.getT2())
                            .modelAttribute("roles", MyPageSupport.roleOptions())
                            .build()
            );
    }

    /**
     * ユーザー有効フラグを設定
     */
    @PreAuthorize("hasAuthority('USER_ENABLE_DISABLE')")
    @PostMapping("/mypage/admin/user/enabled")
    public Mono<Rendering> setUserEnabled(Authentication auth, ServerWebExchange exchange) {

        // フォームから入力値を取り出す
        return MyPageSupport.readFormValues(exchange)
            .flatMap(data -> {

                // ログインユーザー名を取得
                String username = MyPageSupport.firstNonBlank(data, "username", "userId", "user_id");
                // 有効フラグを取得
                String enabledStr = data.get("enabled");

                // 有効フラグ
                boolean enabled;

                // 有効フラグが取得できなかった場合
                if (enabledStr == null) {
                    enabled = true;
                // 有効フラグが取得できた場合
                } else {
                    enabled = Boolean.parseBoolean(enabledStr);
                }

                // ユーザー名が取得できなかった場合
                if (username == null || username.isBlank()) {
                    // エラーメッセージを付与して、ユーザー有効フラグ管理画面へリダイレクトを返却
                    return Mono.just(MyPageSupport.redirectErr(
                            "/mypage/admin/user/enabled",
                            messageSource.getMessage(
                                    "user.ctrl.err.username.required",
                                    null,
                                    exchange.getLocaleContext().getLocale()
                            ))
                    );
                }

                // 認証情報が取得できなかった場合
                if (auth != null &&
                    auth.getName() != null &&
                    auth.getName().equalsIgnoreCase(username) &&
                    !enabled
                ) {
                    // エラーメッセージを付与して、ユーザー有効フラグ管理画面へリダイレクトを返却
                    return Mono.just(MyPageSupport.redirectErr(
                            "/mypage/admin/user/enabled",
                            messageSource.getMessage(
                                    "user.ctrl.err.disable_self",
                                    null,
                                    exchange.getLocaleContext().getLocale()
                            ))
                    );
                }

                // ユーザー有効フラグを設定する
                return userAccountService.setUserEnabled(username, enabled)

                        // 成功した場合、成功メッセージを付与して、ユーザー有効フラグ管理画面へリダイレクトを返却
                        .thenReturn(MyPageSupport.redirectOk(
                                "/mypage/admin/user/enabled",
                                messageSource.getMessage(
                                        "mypage.msg.user.enabled_updated",
                                        null,
                                        exchange.getLocaleContext().getLocale()
                                ))
                        )

                        // 例外が発生した場合、エラーメッセージを付与して、ユーザー有効フラグ管理画面へリダイレクトを返却
                        .onErrorResume(ex ->
                                Mono.just(MyPageSupport.redirectErr(
                                        "/mypage/admin/user/enabled",
                                        uiMessageResolver.resolve(ex, exchange.getLocaleContext().getLocale()
                                        )
                                ))
                        );
            });
    }

    /**
     * ユーザーロールを設定
     */
    @PreAuthorize("hasAuthority('USER_ROLE_CHANGE')")
    @PostMapping("/mypage/admin/user/role")
    public Mono<Rendering> setUserRole(Authentication auth, ServerWebExchange exchange) {

        // フォームから入力値を取り出す
        return MyPageSupport.readFormValues(exchange)
            .flatMap(data -> {

                // ログインユーザー名を取得
                String username = MyPageSupport.firstNonBlank(data, "username", "userId", "user_id");
                // ログインユーザーロールを取得
                String role = data.get("role");

                // ユーザー名が取得できなかった場合
                if (username == null || username.isBlank()) {
                    // エラーメッセージを付与して、ユーザーロール管理画面へリダイレクトを返却
                    return Mono.just(MyPageSupport.redirectErr(
                            "/mypage/admin/user/role",
                            messageSource.getMessage(
                                    "user.ctrl.err.username.required",
                                    null,
                                    exchange.getLocaleContext().getLocale()
                            ))
                    );
                }

                // ログインユーザーロールが取得できなかった場合
                if (role == null || role.isBlank()) {
                    // エラーメッセージを付与して、ユーザーロール管理画面へリダイレクトを返却
                    return Mono.just(MyPageSupport.redirectErr(
                            "/mypage/admin/user/role",
                            messageSource.getMessage(
                                    "user.ctrl.err.role.required",
                                    null,
                                    exchange.getLocaleContext().getLocale()
                            ))
                    );
                }

                // 一般ユーザーロールの場合
                if (auth != null &&
                    auth.getName() != null &&
                    auth.getName().equalsIgnoreCase(username) &&
                    UserAccountService.ROLE_USER.equalsIgnoreCase(role)
                ) {
                    // エラーメッセージを付与して、ユーザーロール管理画面へリダイレクトを返却
                    return Mono.just(MyPageSupport.redirectErr(
                            "/mypage/admin/user/role",
                            messageSource.getMessage(
                                    "user.ctrl.err.cannot_demote_self",
                                    null,
                                    exchange.getLocaleContext().getLocale()
                            ))
                    );
                }

                // ユーザーロールを設定
                return userAccountService.setUserRole(username, role)

                        // 成功した場合、成功メッセージを付与して、ユーザーロール管理画面へリダイレクトを返却
                        .thenReturn(MyPageSupport.redirectOk(
                                "/mypage/admin/user/role",
                                messageSource.getMessage(
                                        "mypage.msg.user.role_updated",
                                        null,
                                        exchange.getLocaleContext().getLocale()
                                ))
                        )

                        // 例外が発生した場合、エラーメッセージを付与して、ユーザーロール管理画面へリダイレクトを返却
                        .onErrorResume(ex ->
                                Mono.just(MyPageSupport.redirectErr(
                                        "/mypage/admin/user/role",
                                        uiMessageResolver.resolve(ex, exchange.getLocaleContext().getLocale())
                                ))
                        );
            });
    }

    /**
     * ユーザーを作成
     */
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @PostMapping("/mypage/admin/user/create")
    public Mono<Rendering> createUser(ServerWebExchange exchange) {

        // フォームから入力値を取り出す
        return MyPageSupport.readFormValues(exchange)
            .flatMap(data -> {

                // ログインユーザー情報を取得
                String username = MyPageSupport.firstNonBlank(data, "username", "userId", "user_id");
                String password = data.get("password");
                String confirmPassword = data.get("confirmPassword");
                String role = data.get("role");
                String enabledStr = MyPageSupport.firstNonBlank(data, "enabled");

                // 有効フラグ
                boolean enabled;

                // 有効フラグが取得できなかった場合
                if (enabledStr == null) {
                    enabled = true;
                // 有効フラグが取得できた場合
                } else {
                    enabled = Boolean.parseBoolean(enabledStr);
                }

                // ユーザー名が半角英数字以外の場合
                if (!username.matches("^[A-Za-z0-9]+$")) {
                    // エラーメッセージを付与して、ユーザー作成管理画面へリダイレクトを返却
                    return Mono.just(MyPageSupport.redirectErr(
                            "/mypage/admin/user/create",
                            messageSource.getMessage(
                                    "user.ctrl.err.username.invalid",
                                    null,
                                    exchange.getLocaleContext().getLocale()
                            ))
                    );
                }
                // ロールが取得できなかった場合
                if (role == null || role.isBlank()) {
                    // エラーメッセージを付与して、ユーザー作成管理画面へリダイレクトを返却
                    return Mono.just(MyPageSupport.redirectErr(
                            "/mypage/admin/user/create",
                            messageSource.getMessage(
                                    "user.ctrl.err.role.required",
                                    null,
                                    exchange.getLocaleContext().getLocale()
                            ))
                    );
                }

                // ユーザーを作成
                return userAccountService.createUser(username, password, confirmPassword, role, enabled)

                        // 成功した場合、成功メッセージを付与して、ユーザー作成管理画面へリダイレクトを返却
                        .thenReturn(MyPageSupport.redirectOk(
                                "/mypage/admin/user/create",
                                messageSource.getMessage(
                                        "mypage.msg.user.created",
                                        null,
                                        exchange.getLocaleContext().getLocale()
                                ))
                        )

                        // 例外が発生した場合、エラーメッセージを付与して、ユーザー作成管理画面へリダイレクトを返却
                        .onErrorResume(ex ->
                                Mono.just(MyPageSupport.redirectErr(
                                        "/mypage/admin/user/create",
                                        uiMessageResolver.resolve(ex, exchange.getLocaleContext().getLocale())
                                ))
                        );
            });
    }
}
