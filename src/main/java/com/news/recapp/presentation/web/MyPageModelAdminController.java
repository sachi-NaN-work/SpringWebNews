package com.news.recapp.presentation.web;

import com.news.recapp.application.port.out.ModelRuntimePort;
import com.news.recapp.application.service.ModelManagementService;
import com.news.recapp.application.service.UserAccountService;
import com.news.recapp.persistence.entity.AppUserEntity;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 学習モデル管理（管理画面）のリクエストを処理するコントローラ
 */
@Controller
public class MyPageModelAdminController {

    // ログインユーザーサービスを保持
    private final UserAccountService userAccountService;
    // 学習モデルの管理操作（再読込・アップロード・学習など）サービスを保持
    private final ModelManagementService modelManagementService;
    // 学習モデルの状態（有効/無効、パスなど）を参照するためのポート
    private final ModelRuntimePort modelRuntime;
    // メッセージサービスを保持
    private final MessageSource messageSource;
    // 画面向けメッセージを保持
    private final UiMessageResolver uiMessageResolver;

    // コンストラクタ
    public MyPageModelAdminController(
            UserAccountService userAccountService,
            ModelManagementService modelManagementService,
            ModelRuntimePort modelRuntime,
            MessageSource messageSource,
            UiMessageResolver uiMessageResolver
    ) {
        this.userAccountService = userAccountService;
        this.modelManagementService = modelManagementService;
        this.modelRuntime = modelRuntime;
        this.messageSource = messageSource;
        this.uiMessageResolver = uiMessageResolver;
    }

    /**
     * 学習モデル管理画面を表示
     */
    @PreAuthorize("hasAnyAuthority('MODEL_RELOAD','MODEL_UPLOAD','MODEL_TRAIN')")
    @GetMapping("/mypage/admin/model")
    public Mono<Rendering> adminModelPage(
            @RequestParam(name = "msg", required = false) String msg,
            @RequestParam(name = "warn", required = false) String warn,
            @RequestParam(name = "err", required = false) String err,
            Authentication auth
    ) {

        // ログインユーザー名を取得
        String username = auth.getName();

        // 学習モデル再読込権限（MODEL_RELOAD）を持つかどうかを判定
        boolean canReload = MyPageSupport.hasAuthority(auth, "MODEL_RELOAD");

        // 学習モデルアップロード権限（MODEL_UPLOAD）を持つかどうかを判定
        boolean canUpload = MyPageSupport.hasAuthority(auth, "MODEL_UPLOAD");

        // 学習モデル権限（MODEL_TRAIN）を持つかどうかを判定
        boolean canTrain = MyPageSupport.hasAuthority(auth, "MODEL_TRAIN");

        // 各権限情報を付与して、学習モデル管理を返却
        return userAccountService.getUser(username)

                // 取得できなかった場合は、最低限の情報を持つダミー情報で補完
                .defaultIfEmpty(new AppUserEntity(username, "", UserAccountService.ROLE_USER, true))

                // 各情報を付与して、学習モデル管理画面を返却
                .map(me ->
                        Rendering.view("mypage/admin_model")
                                .modelAttribute("msg", msg)
                                .modelAttribute("warn", warn)
                                .modelAttribute("err", err)
                                .modelAttribute("me", me)
                                .modelAttribute("canReload", canReload)
                                .modelAttribute("canUpload", canUpload)
                                .modelAttribute("canTrain", canTrain)
                                .modelAttribute("modelEnabled", modelRuntime.isEnabled())
                                .modelAttribute("modelPath", modelRuntime.getCurrentModelPath())
                                .modelAttribute("lastModelOpLog", modelManagementService.getLastModelOperationLog())
                                .build()
                );
    }

    /**
     * 学習モデルを再読込して反映
     */
    @PreAuthorize("hasAuthority('MODEL_RELOAD')")
    @PostMapping("/mypage/admin/model/reload")
    public Mono<Rendering> reloadModel(ServerWebExchange exchange, Authentication auth) {

        // ログインユーザーIDを取得
        String loginUserId = auth.getName();

        // 再読込前に、管理対象の既存モデルがあるかを判定
        boolean existsManagedModelFile = modelManagementService.existsManagedModelFile();

        // 再読込処理を実行
        return modelManagementService.reloadFromDisk(loginUserId)

                // 再読込処理が例外なく完了した場合
                .thenReturn(existsManagedModelFile
                        // 既存モデルが存在する場合、成功メッセージを返却
                        ? MyPageSupport.redirectOk(
                                "/mypage/admin/model",
                                messageSource.getMessage(
                                        "mypage.msg.model.reloaded",
                                        null,
                                        exchange.getLocaleContext().getLocale()
                                )
                        )
                        // 既存モデルが存在しない場合、警告メッセージを返却
                        : MyPageSupport.redirectWarn(
                                "/mypage/admin/model",
                                messageSource.getMessage(
                                        "mypage.warn.model.not_found",
                                        null,
                                        exchange.getLocaleContext().getLocale()
                                )
                        )
                )

                // 例外が発生した場合、エラーメッセージを付与して、学習モデル管理画面へリダイレクトを返却
                .onErrorResume(ex ->
                        Mono.just(MyPageSupport.redirectErr(
                                "/mypage/admin/model",
                                uiMessageResolver.resolve(ex, exchange.getLocaleContext().getLocale())
                        ))
                );
    }

    /**
     * 学習モデルファイルをアップロードし、その後に再読込して反映
     */
    @PreAuthorize("hasAuthority('MODEL_UPLOAD')")
    @PostMapping(
            path = "/mypage/admin/model/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Mono<Rendering> uploadModel(
            @RequestPart("file") FilePart file,
            ServerWebExchange exchange,
            Authentication auth
    ) {

        // ログインユーザーIDを取得
        String loginUserId = auth.getName();

        // アップロード + 再読込処理を実行
        return modelManagementService.uploadAndReload(file, loginUserId)

                // 成功した場合、成功メッセージを付与して、学習モデル管理画面へリダイレクトを返却
                .thenReturn(MyPageSupport.redirectOk(
                        "/mypage/admin/model",
                        messageSource.getMessage(
                                "mypage.msg.model.uploaded_reloaded",
                                null,
                                exchange.getLocaleContext().getLocale())
                        )
                )

                // 例外が発生した場合、エラーメッセージを付与して、学習モデル管理画面へリダイレクトを返却
                .onErrorResume(ex ->
                        Mono.just(MyPageSupport.redirectErr(
                                "/mypage/admin/model",
                                uiMessageResolver.resolve(ex, exchange.getLocaleContext().getLocale())
                        ))
                );
    }

    /**
     * 学習を実行し、完了後にモデルを再読込して反映
     */
    @PreAuthorize("hasAuthority('MODEL_TRAIN')")
    @PostMapping("/mypage/admin/model/train")
    public Mono<Rendering> trainModel(ServerWebExchange exchange, Authentication auth) {

        // ログインユーザーIDを取得
        String loginUserId = auth.getName();

        // 学習・再読込処理を実行
        return modelManagementService.trainAndReload(loginUserId)

                // 学習処理が終了した場合（遅延評価）
                .then(Mono.fromSupplier(() -> {

                    // 直近の学習ログを取得
                    String log = modelManagementService.getLastModelOperationLog();

                    // ログ内容から警告条件に該当するか判定
                    if (MyPageSupport.hasNoFeedbackWarning(log)) {

                        // フィードバック0件の場合、警告メッセージを付与して、学習モデル管理画面へリダイレクトを返却
                        return MyPageSupport.redirectWarn(
                                "/mypage/admin/model",
                                messageSource.getMessage("mypage.warn.model.no_feedback", null, exchange.getLocaleContext().getLocale()
                                )
                        );
                    }

                    // 成功した場合、成功メッセージを付与して、学習モデル管理画面へリダイレクトを返却
                    return MyPageSupport.redirectOk(
                            "/mypage/admin/model",
                            messageSource.getMessage("mypage.msg.model.trained_reloaded", null, exchange.getLocaleContext().getLocale()
                            )
                    );
                }))

                // 例外が発生した場合、エラーメッセージを付与して、学習モデル管理画面へリダイレクトを返却
                .onErrorResume(ex -> Mono.just(MyPageSupport.redirectErr(
                        "/mypage/admin/model",
                        uiMessageResolver.resolve(ex, exchange.getLocaleContext().getLocale()
                        )
                )));
    }
}
