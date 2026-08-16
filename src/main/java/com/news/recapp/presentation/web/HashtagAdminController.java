package com.news.recapp.presentation.web;

import com.news.recapp.application.service.HashtagAdminService;
import com.news.recapp.application.service.UserAccountService;
import com.news.recapp.persistence.entity.AppUserEntity;
import com.news.recapp.persistence.entity.HashtagEntity;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ハッシュタグ（マスタ）管理内容の内容リクエストを処理
 */
@Controller
public class HashtagAdminController {

    // ログインユーザーサービスを保持
    private final UserAccountService userAccountService;
    // ハッシュタグサービスを保持
    private final HashtagAdminService hashtagAdminService;
    // 例外メッセージサービスを保持
    private final UiMessageResolver uiMessageResolver;
    // メッセージサービスを保持
    private final MessageSource messageSource;

    // コンストラクタ
    public HashtagAdminController(
        UserAccountService userAccountService,
        HashtagAdminService hashtagAdminService,
        UiMessageResolver uiMessageResolver,
        MessageSource messageSource
    ) {
        this.userAccountService = userAccountService;
        this.hashtagAdminService = hashtagAdminService;
        this.uiMessageResolver = uiMessageResolver;
        this.messageSource = messageSource;
    }

    /**
     * ハッシュタグマスタ画面を表示
     */
    @PreAuthorize("hasAuthority('NEWS_HASHTAG_MASTER')")
    @GetMapping("/mypage/admin/hashtag/master")
    public Mono<Rendering> masterPageHashtag(
            @RequestParam(name = "msg", required = false) String msg,
            @RequestParam(name = "err", required = false) String err,
            Authentication auth
    ) {

        // ログインユーザー名を取得
        String userName = auth.getName();

        // ユーザー情報を取得
        Mono<AppUserEntity> meMono = userAccountService.getUser(userName)
                // 取得できなかった場合は、最低限の情報を持つダミー情報で補完
                .defaultIfEmpty(
                        new AppUserEntity(
                                userName,
                                "",
                                UserAccountService.ROLE_USER,
                                true
                        )
                );

        // ハッシュタグ一覧を取得
        Mono<List<HashtagEntity>> hashtagsMono = hashtagAdminService.listAll().collectList();

        // ユーザー情報・ハッシュタグ一覧を付与して、ハッシュタグマスタ画面を返却
        return Mono.zip(meMono, hashtagsMono)
                .map(tuple ->
                        Rendering.view("mypage/admin_hashtag_master")
                                .modelAttribute("msg", msg)
                                .modelAttribute("err", err)
                                .modelAttribute("me", tuple.getT1())
                                .modelAttribute("hashtags", tuple.getT2())
                                .build()
                );
    }


    /**
     * ハッシュタグを新規登録
     */
    @PreAuthorize("hasAuthority('NEWS_HASHTAG_MASTER')")
    @PostMapping(
            value = "/mypage/admin/hashtag/master/create",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE}
    )
    public Mono<Rendering> createHashtag(ServerWebExchange exchange) {

        // フォームパラメータを読取
        return NewsAdminSupport.readParams(exchange)
                .flatMap(form -> {

                    // ハッシュタグ名を取得
                    String name = NewsAdminSupport.firstNonBlank(form, "name");

                    // ハッシュタグ作成処理を実行
                    return hashtagAdminService.create(name)

                            // 成功した場合、成功メッセージを付与して、一覧画面へリダイレクトを返却
                            .thenReturn(
                                    NewsAdminSupport.redirectOk(
                                            "/mypage/admin/hashtag/master",
                                            messageSource.getMessage(
                                                    "hashtag.master.msg.created",
                                                    null,
                                                    exchange.getLocaleContext().getLocale()
                                            )
                                    )
                            )

                            // 例外が発生した場合、エラーメッセージを付与して、一覧画面へリダイレクトを返却
                            .onErrorResume(ex ->
                                    Mono.just(
                                            NewsAdminSupport.redirectErr(
                                                    "/mypage/admin/hashtag/master",
                                                    uiMessageResolver.resolve(ex, exchange.getLocaleContext().getLocale())
                                            )
                                    )
                            );
                });
    }


    /**
     * ハッシュタグの有効・無効を切り替え
     */
    @PreAuthorize("hasAuthority('NEWS_HASHTAG_MASTER')")
    @PostMapping(
            value = "/mypage/admin/hashtag/master/enabled",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE}
    )
    public Mono<Rendering> setEnabledHashtag(ServerWebExchange exchange) {

        // フォームパラメータを読取
        return NewsAdminSupport.readParams(exchange)
                .flatMap(form -> {

                    // ハッシュタグ ID を取得
                    String idRaw = NewsAdminSupport.firstNonBlank(form, "id");

                    // 有効・無効を取得
                    String enabledRaw = NewsAdminSupport.firstNonBlank(form, "enabled");

                    UUID id;
                    try {
                        // 文字列の UUID を UUID 型に変換
                        id = UUID.fromString(Objects.requireNonNull(idRaw));
                    } catch (Exception exception) {

                        // 例外が発生した場合、エラーメッセージを付与して、一覧画面へリダイレクトを返却
                        return Mono.just(
                                NewsAdminSupport.redirectErr(
                                        "/mypage/admin/hashtag/master",
                                        messageSource.getMessage("hashtag.master.err.invalid_id", null, exchange.getLocaleContext().getLocale())
                                )
                        );
                    }

                    // 文字列を boolean 型に変換
                    boolean enabled = "true".equalsIgnoreCase(enabledRaw);

                    // 指定 ID のハッシュタグについて、有効/無効フラグを更新
                    return hashtagAdminService.setEnabled(id, enabled)

                            // 成功した場合、成功メッセージを付与して、一覧画面へリダイレクトを返却
                            .thenReturn(
                                    NewsAdminSupport.redirectOk(
                                            "/mypage/admin/hashtag/master",
                                            messageSource.getMessage(
                                                    "hashtag.master.msg.updated",
                                                    null,
                                                    exchange.getLocaleContext().getLocale()
                                            )
                                    )
                            )

                            // 例外が発生した場合、エラーメッセージを付与して、一覧画面へリダイレクトを返却
                            .onErrorResume(ex ->
                                    Mono.just(
                                            NewsAdminSupport.redirectErr(
                                                    "/mypage/admin/hashtag/master",
                                                    uiMessageResolver.resolve(ex, exchange.getLocaleContext().getLocale())
                                            )
                                    )
                            );
                });
    }

}
