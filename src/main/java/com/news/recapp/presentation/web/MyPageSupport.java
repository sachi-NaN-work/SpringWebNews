package com.news.recapp.presentation.web;

import com.news.recapp.util.Messages;
import com.news.recapp.application.service.UserAccountService;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ページの共通処理を提供するサポートクラス
 */
public final class MyPageSupport {

    /**
     * ロールを表すレコード
     */
    public record RoleOption(String code, String name) {

        // 表示用文字列を生成
        public String display() {
            return code + "：" + name;
        }
    }

    // コンストラクタ
    private MyPageSupport() {
        
    }

    /**
     * ロール一覧（表示用）を取得
     */
    public static List<RoleOption> roleOptions() {

        return List.of(
                // ロール（一般ユーザー）を追加
                new RoleOption(UserAccountService.ROLE_USER, Messages.getMsg("role.display.user")),
                // ロール（ユーザー管理）を追加
                new RoleOption(UserAccountService.ROLE_USER_MGR, Messages.getMsg("role.display.user_mgr")),
                // ロール（学習モデル管理）を追加
                new RoleOption(UserAccountService.ROLE_MODEL_MGR, Messages.getMsg("role.display.model_mgr")),
                // ロール（ニュース作成管理）を追加
                new RoleOption(UserAccountService.ROLE_NEWS_MGR, Messages.getMsg("role.display.news_mgr"))
        );
    }

    /**
     * 全ロール一覧（表示用）を取得
     */
    public static List<RoleOption> roleOptionsAll() {

        return List.of(
                // ロール（一般ユーザー）を追加
                new RoleOption(UserAccountService.ROLE_USER, Messages.getMsg("role.display.user")),
                // ロール（管理者ユーザー）を追加
                new RoleOption(UserAccountService.ROLE_ADMIN, Messages.getMsg("role.display.admin")),
                // ロール（ユーザー管理）を追加
                new RoleOption(UserAccountService.ROLE_USER_MGR, Messages.getMsg("role.display.user_mgr")),
                // ロール（学習モデル管理）を追加
                new RoleOption(UserAccountService.ROLE_MODEL_MGR, Messages.getMsg("role.display.model_mgr")),
                // ロール（ニュース作成管理）を追加
                new RoleOption(UserAccountService.ROLE_NEWS_MGR, Messages.getMsg("role.display.news_mgr")),
                // ロール（デモユーザー）を追加
                new RoleOption(UserAccountService.ROLE_DEMO, Messages.getMsg("role.display.demo"))
        );
    }

    /**
     * ロール表示文字列を取得
     */
    public static String roleDisplay(String roleStr) {

        // ロール表示文字列
        final String roleStrFinal;

        boolean isRoleNull = roleStr == null;

        // ロール表示文字列が取得できなかった場合
        if (isRoleNull) {
            roleStrFinal = UserAccountService.ROLE_USER;
        // ロール表示文字列が取得できた場合
        } else {
            roleStrFinal = roleStr;
        }

        // 全ロール一覧（表示用）を返却
        return roleOptionsAll()
                .stream()
                .filter(roleOption -> roleOption.code().equalsIgnoreCase(roleStrFinal))
                .findFirst()
                .map(RoleOption::display)
                .orElse(roleStrFinal);
    }

    /**
     * 指定したロールを保持しているかを判定
     */
    public static boolean hasRole(Authentication auth, String role) {

        // ログインユーザーに付与されているロール情報が取得できなかった場合、false を返却
        if (auth == null || auth.getAuthorities() == null) return false;

        // 引数で指定されたロールを保持しているかを判定
        return auth.getAuthorities()
                .stream()
                .anyMatch(authMatch -> role.equals(authMatch.getAuthority()));
    }

    /**
     * 指定した権限を保持しているかを判定
     */
    public static boolean hasAuthority(Authentication auth, String authority) {

        // ログインユーザーに付与されている権限情報が取得できなかった場合、false を返却
        if (auth == null || auth.getAuthorities() == null || authority == null) return false;

        // 引数で指定された権限を保持しているかを判定
        return auth.getAuthorities()
                .stream()
                .anyMatch(authMatch -> authority.equals(authMatch.getAuthority()));
    }

    /**
     * 指定した権限を保持しているかを判定
     */
    public static boolean hasAnyAuthority(Authentication auth, String... authorities) {

        // 権限が取得できなかった場合、false を返却
        if (authorities == null) return false;

        // 権限を全件ループ処理
        for (String authority : authorities) {
            // 引数で指定された権限を保持している場合、true を返却
            if (hasAuthority(auth, authority)) return true;
        }

        // 権限が確認できなかった場合、false を返却
        return false;
    }

    /**
     * UUID 文字列を解析
     */
    public static UUID parseUuid(String textUUID) {

        // 文字列が取得できなかった場合、null を返却
        if (textUUID == null) return null;

        try {
            // UUID 文字列を解析
            return UUID.fromString(textUUID.trim());
        } catch (Exception exception) {
            // 例外が発生した場合、null を返却
            return null;
        }
    }

    /**
     * 最初の文字列を取得
     */
    public static String firstNonBlank(Map<String, String> textData, String... keys) {

        // 文字列・キー情報が取得できなかった場合、null を返却
        if (textData == null || keys == null) return null;

        // キー情報を全件ループ処理
        for (String key : keys) {
            // キー情報が取得できなかった場合、スキップ
            if (key == null) continue;

            String value = textData.get(key);
            // 文字列が取得できた場合
            if (value != null && !value.trim().isBlank()) {
                // 空白を取り除いた文字列を返却
                return value.trim();
            }
        }

        // 文字列が確認できなかった場合、null を返却
        return null;
    }

    /**
     * フォームから入力値を取り出す
     */
    public static Mono<Map<String, String>> readFormValues(ServerWebExchange exchange) {

        // フォームデータを Map に変換
        Mono<Map<String, String>> formMapMono = getMapMono(exchange);

        return formMapMono.flatMap(formMap -> {

            // フォーム値が取得できた場合
            if (!formMap.isEmpty()) {

                // フォームからの入力値を返す
                return Mono.just(formMap);

            // フォーム値が取得できなかった場合
            } else {

                // フォーム項目を抽出
                Mono<MultiValueMap<String, Part>> multipartMono = exchange.getMultipartData();

                return multipartMono.map(multipart -> {

                    // フォーム入力項目
                    Map<String, String> outFormFieldMap = new HashMap<>();
                    
                    // multipart を全件ループ処理
                    multipart.forEach((partKey, partList) -> {

                        // リストが取得できなかった場合、対象外
                        if (partList == null || partList.isEmpty()) {
                            return;
                        }

                        // リストの先頭を取得
                        Part firstPart = partList.getFirst();

                        // フォームの入力項目でない場合、対象外
                        if (!(firstPart instanceof FormFieldPart formFieldPart)) {
                            return;
                        }

                        // フォーム入力項目を登録
                        outFormFieldMap.put(partKey, formFieldPart.value());
                    });

                    // 登録されたフォーム入力項目を返却
                    return outFormFieldMap;
                });
            }
        });

    }

    /**
     * フォームデータを Map に変換
     */
    private static Mono<Map<String, String>> getMapMono(ServerWebExchange exchange) {

        // フォームデータを取得
        Mono<MultiValueMap<String, String>> formMono = exchange.getFormData();

        return formMono.map(form -> {

            // フォームデータ
            Map<String, String> formMap = new HashMap<>();

            // フォームデータを全件ループ処理
            form.forEach((formKey, formList) -> {

                // リストが取得できなかった場合、null として扱う
                String firstValue = (formList != null && !formList.isEmpty())
                        ? formList.getFirst()
                        : null;

                // フォームデータを登録
                formMap.put(formKey, firstValue);
            });

            // 登録されたフォームデータを返却
            return formMap;
        });
    }

    /**
     * リダイレクト URL を生成
     */
    public static Rendering redirectOk(String path, String msg) {

        // リダイレクト URL を設定（未指定の場合、マイページを設定）
        String redirectPath = (path == null || path.isBlank()) ? "/mypage" : path;

        // メッセージを付与して、リダイレクト画面を返却
        return Rendering.redirectTo(redirectPath + "?msg=" + urlEncode(msg)).build();
    }

    /**
     * 警告リダイレクト URL を生成
     */
    public static Rendering redirectWarn(String path, String warn) {

        // リダイレクト URL を設定（未指定の場合、マイページを設定）
        String redirectPath = (path == null || path.isBlank()) ? "/mypage" : path;

        // 警告メッセージが未指定の場合、共通警告メッセージを設定
        String warningMessage = (warn == null || warn.isBlank()) ? Messages.getMsg("common.err.generic") : warn;

        // 警告メッセージを付与して、リダイレクト画面を返却
        return Rendering.redirectTo(redirectPath + "?warn=" + urlEncode(warningMessage)).build();
    }

    /**
     * エラーリダイレクト URL を生成
     */
    public static Rendering redirectErr(String path, String err) {

        // リダイレクト URL を設定（未指定の場合、マイページを設定）
        String redirectPath = (path == null || path.isBlank()) ? "/mypage" : path;

        // エラーメッセージが未指定の場合、共通エラーメッセージを設定
        String errorMessage = (err == null || err.isBlank()) ? Messages.getMsg("common.err.generic") : err;

        // エラーメッセージを付与して、リダイレクト画面を返却
        return Rendering.redirectTo(redirectPath + "?err=" + urlEncode(errorMessage)).build();
    }

    /**
     * ログに警告が出ているかを判定
     */
    public static boolean hasNoFeedbackWarning(String logStr) {

        // ログが取得できなかった場合、false を返却
        if (logStr == null) return false;

        // ログに警告が出ているかを判定
        return logStr.contains("[WARN_NO_FEEDBACK]");
    }

    /**
     * リンク先 URL をエンコード
     */
    public static String urlEncode(String uriString) {
        // URL エンコード
        return URLEncoder.encode(uriString, StandardCharsets.UTF_8);
    }
}
