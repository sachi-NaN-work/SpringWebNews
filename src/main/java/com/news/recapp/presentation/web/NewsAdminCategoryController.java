package com.news.recapp.presentation.web;

import com.news.recapp.application.service.AdminNewsService;
import com.news.recapp.application.service.UserAccountService;
import com.news.recapp.application.exception.AppMessageException;
import com.news.recapp.persistence.entity.AppUserEntity;
import com.news.recapp.persistence.entity.CategoryEntity;
import com.news.recapp.util.Messages;
import org.springframework.context.MessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * ニュースカテゴリ管理（管理画面）へのリクエストを処理するコントローラ
 */
@Controller
public class NewsAdminCategoryController {

    private static final Logger log = LoggerFactory.getLogger(NewsAdminCategoryController.class);

    // ログインユーザーサービスを保持
    private final UserAccountService userAccountService;
    // ニュース管理サービスを保持
    private final AdminNewsService adminNewsService;
    // メッセージサービスを保持
    private final MessageSource messageSource;
    // 画面向けメッセージを保持
    private final UiMessageResolver uiMessageResolver;

    // コンストラクタ
    public NewsAdminCategoryController(
        UserAccountService userAccountService,
        AdminNewsService adminNewsService,
        MessageSource messageSource,
        UiMessageResolver uiMessageResolver
    ) {
        this.userAccountService = userAccountService;
        this.adminNewsService = adminNewsService;
        this.messageSource = messageSource;
        this.uiMessageResolver = uiMessageResolver;
    }

    /**
     * ニュースカテゴリ作成ページを表示
     */
    @PreAuthorize("hasAuthority('NEWS_CATEGORY_CREATE')")
    @GetMapping("/mypage/admin/news/category/create")
    public Mono<Rendering> newsCategoryCreatePage(
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

        // 各情報を付与して、ニュースカテゴリ作成管理画面を返却
        return meMono.map(me ->
                Rendering.view("mypage/admin_news_category_create")
                        .modelAttribute("msg", msg)
                        .modelAttribute("err", err)
                        .modelAttribute("me", me)
                        .build()
        );
    }

    /**
     * カテゴリを作成
     */
    @PreAuthorize("hasAuthority('NEWS_CATEGORY_CREATE')")
    @PostMapping(
        value = "/mypage/admin/news/category/create",
        consumes = { MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE }
    )
    public Mono<Rendering> createCategory(ServerWebExchange exchange) {

        // パラメータを読取
        return NewsAdminSupport.readParams(exchange)
            .flatMap(form -> {

                // ログに出力
                log.info(Messages.getMsg("log.news.category.create"),
                        // レスポンスヘッダーを取得
                        exchange.getRequest().getHeaders().getContentType(),
                        // キー一覧を出力
                        NewsAdminSupport.safeKeys(form)
                );

                // カテゴリスラグを取得
                String slugRaw = NewsAdminSupport.firstNonBlank(form, "slug");
                // カテゴリ名を取得
                String nameRaw = NewsAdminSupport.firstNonBlank(form, "name");

                // カテゴリを作成
                return adminNewsService.createCategory(slugRaw, nameRaw)

                        // 成功した場合、成功メッセージを付与して、カテゴリ作成管理画面へリダイレクトを返却
                        .thenReturn(NewsAdminSupport.redirectOk(
                                "/mypage/admin/news/category/create",
                                messageSource.getMessage(
                                        "news.category.create.msg.created",
                                        null,
                                        exchange.getLocaleContext().getLocale()
                                )
                            )
                        )

                        // 例外が発生した場合、エラーメッセージを付与して、カテゴリ作成管理画面へリダイレクトを返却
                        .onErrorResume(ex ->
                                Mono.just(NewsAdminSupport.redirectErr(
                                        "/mypage/admin/news/category/create",
                                        uiMessageResolver.resolve(ex, exchange.getLocaleContext().getLocale()
                                        )
                                ))
                        );
            });
    }

    /**
     * ニュースカテゴリ一覧ページを表示
     */
    @PreAuthorize("hasAuthority('NEWS_CATEGORY_DISABLE')")
    @GetMapping("/mypage/admin/news/category/disable")
    public Mono<Rendering> newsCategoryDisablePage(
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

        // カテゴリ一覧を取得
        Mono<List<CategoryEntity>> categoriesMono = adminNewsService.listCategoriesForAdmin().collectList();

        // 各情報を付与
        return Mono.zip(meMono, categoriesMono)

                // ニュースカテゴリ一覧画面を取得
                .map(tuple ->
                                Rendering.view("mypage/admin_news_category_disable")
                                        .modelAttribute("msg", msg)
                                        .modelAttribute("err", err)
                                        .modelAttribute("me", tuple.getT1())
                                        .modelAttribute("categories", tuple.getT2())
                                        .build()
                )

                // 例外が起きた場合
                .onErrorResume(ex -> {
    
                    // ログに出力
                    log.error(Messages.getMsg("log.news.category.disable.unexpected"), ex);

                    // エラーメッセージを用意
                    String errMsg = (err != null) ? err : uiMessageResolver.resolve(ex, java.util.Locale.JAPAN);

                    // 各情報を付与して、ニュースカテゴリ一覧画面を返却
                    return meMono.map(me ->
                            Rendering.view("mypage/admin_news_category_disable")
                                    .modelAttribute("msg", msg)
                                    .modelAttribute("err", errMsg)
                                    .modelAttribute("me", me)
                                    .modelAttribute("categories", java.util.List.of())
                                    .build()
                    );
                });
    }

    /**
     * ニュースカテゴリ有効フラグを切替
     */
    @PreAuthorize("hasAuthority('NEWS_CATEGORY_DISABLE')")
    @PostMapping("/mypage/admin/news/category/disable/{id}")
    public Mono<Rendering> toggleCategoryEnabled(@PathVariable("id") UUID categoryId) {

        // カテゴリ一覧を取得
        return adminNewsService.listCategoriesForAdmin()

                // カテゴリ ID が一致する場合
                .filter(categoryEntity -> categoryId.equals(categoryEntity.getId()))

                // 要素が1件ならその要素で返却。0件なら空で返却。2件以上ならエラーで返却。
                .singleOrEmpty()

                // カテゴリ情報が取得できなかった場合、指定メッセージコードで例外発生
                .switchIfEmpty(Mono.error(new AppMessageException("news.category.disable.err.not_found")))

                // カテゴリ情報を使って、有効フラグを設定
                .flatMap(categoryEntity ->
                        adminNewsService.setCategoryEnabled(
                                categoryId,
                                !Boolean.TRUE.equals(categoryEntity.getEnabled()
                                )
                        )
                )

                // 成功した場合、成功メッセージを付与して、ニュースカテゴリ有効フラグ管理画面へリダイレクトを返却
                .thenReturn(NewsAdminSupport.redirectOk(
                        "/mypage/admin/news/category/disable",
                        messageSource.getMessage(
                            "news.category.disable.msg.updated",
                            null,
                            java.util.Locale.JAPAN
                        ))
                )

                // 例外が発生した場合、エラーメッセージを付与して、ニュースカテゴリ有効フラグ管理画面へリダイレクトを返却
                .onErrorResume(ex ->
                        Mono.just(NewsAdminSupport.redirectErr(
                                "/mypage/admin/news/category/disable",
                                uiMessageResolver.resolve(ex, java.util.Locale.JAPAN)
                                )
                        )
                );
    }
}
