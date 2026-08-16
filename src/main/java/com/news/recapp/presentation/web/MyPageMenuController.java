package com.news.recapp.presentation.web;

import com.news.recapp.application.service.UserAccountService;
import com.news.recapp.persistence.entity.AppUserEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;

/**
 * マイページ（メニュー画面）へのリクエストを処理するコントローラ。
 */
@Controller
public class MyPageMenuController {

    // ログインユーザーサービスを保持
    private final UserAccountService userAccountService;

    // コンストラクタ
    public MyPageMenuController(
            UserAccountService userAccountService
    ) {
        this.userAccountService = userAccountService;
    }

    /**
     * マイページ（メニュー画面）を表示する。
     */
    @GetMapping("/mypage")
    public Mono<Rendering> mypageMenu(
            @RequestParam(name = "msg", required = false) String msg,
            @RequestParam(name = "err", required = false) String err,
            Authentication auth
    ) {

        // ログインユーザーが管理者ロール（ROLE_ADMIN）を持つかどうかを判定
        boolean isAdmin = MyPageSupport.hasRole(auth, "ROLE_ADMIN");

        // パスワード変更権限（PASSWORD_CHANGE）を持つかどうかを判定
        boolean canPasswordChange = MyPageSupport.hasAuthority(auth, "PASSWORD_CHANGE");

        // ユーザー作成権限（USER_CREATE）を持つかどうかを判定
        boolean canUserCreate = MyPageSupport.hasAuthority(auth, "USER_CREATE");

        // ユーザーの有効/無効切替権限（USER_ENABLE_DISABLE）を持つかどうかを判定
        boolean canUserEnabled = MyPageSupport.hasAuthority(auth, "USER_ENABLE_DISABLE");

        // ユーザーのロール変更権限（USER_ROLE_CHANGE）を持つかどうかを判定
        boolean canUserRole = MyPageSupport.hasAuthority(auth, "USER_ROLE_CHANGE");

        // モデル関連の権限（MODEL_RELOAD / MODEL_UPLOAD / MODEL_TRAIN）を持つかどうかを判定
        boolean canModelAny = MyPageSupport.hasAnyAuthority(auth, "MODEL_RELOAD", "MODEL_UPLOAD", "MODEL_TRAIN");

        // ニュース作成系のいずれかの権限（NEWS_CATEGORY_CREATE / NEWS_ARTICLE_CREATE）を持つかどうかを判定
        boolean canNewsCreateAny = MyPageSupport.hasAnyAuthority(auth, "NEWS_CATEGORY_CREATE", "NEWS_ARTICLE_CREATE");

        // ニュース無効化系のいずれかの権限（NEWS_CATEGORY_DISABLE / NEWS_ARTICLE_DISABLE）を持つかどうかを判定
        boolean canNewsDisableAny = MyPageSupport.hasAnyAuthority(auth, "NEWS_CATEGORY_DISABLE", "NEWS_ARTICLE_DISABLE");

        // ハッシュタグマスタ操作権限（NEWS_HASHTAG_MASTER）を持つかどうかを判定
        boolean canHashtagMaster = MyPageSupport.hasAuthority(auth, "NEWS_HASHTAG_MASTER");

        // ニュース記事のハッシュタグ編集権限（NEWS_ARTICLE_HASHTAG_EDIT）を持つかどうかを判定
        boolean canNewsHashtagEdit = MyPageSupport.hasAuthority(auth, "NEWS_ARTICLE_HASHTAG_EDIT");

        // ログインユーザー名を取得
        String username = auth.getName();

        // ユーザー情報を取得
        Mono<AppUserEntity> meMono = userAccountService.getUser(username)
                // 取得できなかった場合は、最低限の情報を持つダミー情報で補完
                .defaultIfEmpty(new AppUserEntity(username, "", UserAccountService.ROLE_USER, true));

        // 各権限情報を付与して、マイページ（メニュー画面）を返却
        return meMono.map(me ->
                Rendering.view("mypage/menu")
                        .modelAttribute("msg", msg)
                        .modelAttribute("err", err)
                        .modelAttribute("isAdmin", isAdmin)
                        .modelAttribute("canPasswordChange", canPasswordChange)
                        .modelAttribute("canUserCreate", canUserCreate)
                        .modelAttribute("canUserEnabled", canUserEnabled)
                        .modelAttribute("canUserRole", canUserRole)
                        .modelAttribute("canModelAny", canModelAny)
                        .modelAttribute("canNewsCreateAny", canNewsCreateAny)
                        .modelAttribute("canNewsDisableAny", canNewsDisableAny)
                        .modelAttribute("canHashtagMaster", canHashtagMaster)
                        .modelAttribute("canNewsHashtagEdit", canNewsHashtagEdit)
                        .modelAttribute("roleDispUserMgr", MyPageSupport.roleDisplay(UserAccountService.ROLE_USER_MGR))
                        .modelAttribute("roleDispModelMgr", MyPageSupport.roleDisplay(UserAccountService.ROLE_MODEL_MGR))
                        .modelAttribute("roleDispNewsMgr", MyPageSupport.roleDisplay(UserAccountService.ROLE_NEWS_MGR))
                        .modelAttribute("me", me)
                        .build()
        );
    }
}
