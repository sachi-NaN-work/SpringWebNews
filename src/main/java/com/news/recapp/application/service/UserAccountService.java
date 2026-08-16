package com.news.recapp.application.service;

import com.news.recapp.persistence.entity.AppUserEntity;
import com.news.recapp.application.exception.AppMessageException;
import com.news.recapp.application.port.out.AppUserPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * ユーザー内容の業務処理を提供
 */
@Service
public class UserAccountService {

    // 一般ユーザー権限を表すロール定数
    public static final String ROLE_USER = "USER";
    // 管理者ユーザー権限を表すロール定数
    public static final String ROLE_ADMIN = "ADMIN";
    // ユーザー管理者権限を表すロール定数
    public static final String ROLE_USER_MGR = "USER_MGR";
    // モデル管理者権限を表すロール定数
    public static final String ROLE_MODEL_MGR = "MODEL_MGR";
    // ニュース作成者権限を表すロール定数
    public static final String ROLE_NEWS_MGR = "NEWS_MGR";
    // デモユーザー権限を表すロール定数
    public static final String ROLE_DEMO = "DEMO";

    // 管理者ユーザー ID を表す定数
    private static final String ADMIN_USER_ID = "admin";
    // デモユーザー ID を表す定数
    private static final String DEMO_USER_ID = "demo";

    // 英大文字を判定する正規表現
    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    // 英小文字を判定する正規表現
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    // 数字を判定する正規表現
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    // ユーザー情報ポートを保持
    private final AppUserPort userPort;
    // パスワードエンコーダを保持
    private final PasswordEncoder encoder;

    /**
     * 管理者ユーザー判定（ロールベース）
     */
    private static boolean isFixedAdminUserByRole(AppUserEntity user) {
        return user != null && ROLE_ADMIN.equalsIgnoreCase(user.getRole());
    }

    /**
     * 管理者ユーザー判定（ユーザー ID ベース）
     */
    private static boolean isFixedAdminUserByUserId(String username) {
        // admin（大文字小文字を無視）を含むか判定
        return username != null && username.toLowerCase(Locale.ROOT).contains(ADMIN_USER_ID);
    }

    /**
     * デモユーザー判定（ユーザー ID ベース）
     */
    private static boolean isFixedDemoUserByUserId(String username) {
        // demo（大文字小文字を無視）を含むか判定
        return username != null && username.toLowerCase(Locale.ROOT).contains(DEMO_USER_ID);
    }

    // コンストラクタ
    public UserAccountService(
        AppUserPort userPort,
        PasswordEncoder encoder
    ) {
        this.userPort = userPort;
        this.encoder = encoder;
    }

    /**
     * ユーザー一覧を取得
     */
    public Flux<AppUserEntity> listUsers() {
        return userPort.findAll()
                // ユーザー名で並び替え
                .sort((leftUser, rightUser) -> leftUser.getUsername()
                // 大文字小文字を無視して比較
                .compareToIgnoreCase(rightUser.getUsername()));
    }

    /**
     * ユーザーを取得
     */
    public Mono<AppUserEntity> getUser(String username) {
        return userPort.findById(username);
    }

    /**
     * パスワードを変更
     */
    public Mono<Void> changePassword(String username, String currentPassword, String newPassword, String confirmPassword) {

        // デモユーザーの場合
        if (isFixedDemoUserByUserId(username)) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("user.account.err.demo_password_change_forbidden"));
        }
        
        // 新しいパスワード
        String newPwd;
        // 新しいパスワードが未指定か判定
        boolean isNewPasswordNull = (newPassword == null);
        
        // 新しいパスワードが取得できなかった場合
        if (isNewPasswordNull) {
            // 新しいパスワードを空文字として扱う
            newPwd = "";
        // 新しいパスワードが取得できた場合
        } else {
            // 新しいパスワードを設定
            newPwd = newPassword;
        }
        
        // 確認パスワード
        String changePwd;
        // 確認パスワードが未指定か判定
        boolean isConfirmPasswordNull = (confirmPassword == null);
        
        // 確認パスワードが取得できなかった場合
        if (isConfirmPasswordNull) {
            // 確認パスワードを空文字として扱う
            changePwd = "";
        // 確認パスワードが取得できた場合
        } else {
            // 確認パスワードを設定
            changePwd = confirmPassword;
        }

        // 新しいパスワードと確認パスワードが一致しない場合
        if (!newPwd.equals(changePwd)) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("user.account.err.new_password_mismatch"));
        }
        
        try {
            // パスワードポリシーを検証
            validatePasswordPolicy(newPwd);
        } catch (AppMessageException appMessageException) {
            // エラーメッセージを付与して返却
            return Mono.error(appMessageException);
        }

        final String finalNewPassword = newPwd;

        // ユーザー情報を取得
        return userPort.findById(username)
                // ユーザー情報が取得できなかった場合、エラーメッセージを付与して返却
                .switchIfEmpty(Mono.error(new AppMessageException("user.account.err.user_not_found")))

                // ユーザー情報
                .flatMap(userEntity -> {
                    // ユーザーの有効フラグが取得できなかった場合
                    if (userEntity.getEnabled() != null && !userEntity.getEnabled()) {
                        // エラーメッセージを付与して返却
                        return Mono.error(new AppMessageException("user.account.err.user_disabled"));
                    }

                    // 現在パスワード
                    String currentPwd;
                    // 現在パスワードが未指定か判定
                    boolean isCurrentPasswordNull = (currentPassword == null);
                    // 現在パスワードが取得できなかった場合
                    if (isCurrentPasswordNull) {
                        // 現在パスワードを空文字として扱う
                        currentPwd = "";
                        // 現在パスワードが取得できた場合
                    } else {
                        // 現在パスワードを設定
                        currentPwd = currentPassword;
                    }

                    // 現在パスワードが保存されているエンコード済みパスワードと一致しない場合
                    if (!encoder.matches(currentPwd, userEntity.getPassword())) {
                        // エラーメッセージを付与して返却
                        return Mono.error(new AppMessageException("user.account.err.current_password_incorrect"));
                    }
                    // パスワードを設定（エンコード済みパスワード）
                    userEntity.setPassword(encoder.encode(finalNewPassword));
                    // ユーザーを保存して返却
                    return userPort.save(userEntity)
                        // 処理完了後、値なしで返却
                        .then();
                });
    }

    /**
     * ユーザー有効フラグを設定
     */
    public Mono<Void> setUserEnabled(String targetUsername, boolean enabled) {
        // デモユーザーの場合
        if (isFixedDemoUserByUserId(targetUsername)) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("user.account.err.demo_enabled_change_forbidden"));
        }

        // ユーザー情報ポートを取得
        return userPort.findById(targetUsername)
                // ユーザー情報ポートが取得できなかった場合、エラーメッセージを付与して返却
                .switchIfEmpty(Mono.error(new AppMessageException("user.account.err.user_not_found")))
                // ユーザー情報ポートに対して処理を実行
                .flatMap(userEntity -> {

                    // ADMIN ロールのユーザーは DB 側でも更新禁止のため、アプリ側で明示的に禁止
                    if (isFixedAdminUserByRole(userEntity)) {
                        return Mono.error(new AppMessageException("user.account.err.admin_enabled_change_forbidden"));
                    }

                    // ユーザーの有効フラグを設定
                    userEntity.setEnabled(enabled);
                    // ユーザーを保存して返却
                    return userPort.save(userEntity)
                        // 処理完了後、値なしで返却
                        .then();
                });
    }

    /**
     * ユーザーロールを設定
     */
    public Mono<Void> setUserRole(String targetUsername, String role) {
        // ロールを正規化して取得
        String normalized = normalizeRole(role);
        // デモユーザー名の場合
        if (isFixedDemoUserByUserId(targetUsername)) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("user.account.err.demo_role_change_forbidden"));
        }

        // 管理者ユーザーロールの場合
        if (ROLE_ADMIN.equalsIgnoreCase(normalized)) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("user.account.err.admin_role_not_assignable"));
        }
        // デモユーザーロールの場合
        if (ROLE_DEMO.equalsIgnoreCase(normalized)) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("user.account.err.demo_role_only_for_demo"));
        }

        // ユーザー情報を取得
        return userPort.findById(targetUsername)
                // ユーザー情報が取得できなかった場合、エラーメッセージを付与して返却
                .switchIfEmpty(Mono.error(new AppMessageException("user.account.err.user_not_found")))
                // ユーザー情報に対して処理を実行
                .flatMap(userEntity -> {

                    // ADMIN ロールのユーザーは DB 側でも role 更新禁止のため、アプリ側で明示的に禁止
                    if (isFixedAdminUserByRole(userEntity)) {
                        return Mono.error(new AppMessageException("user.account.err.admin_role_change_forbidden"));
                    }

                    // ロールを更新
                    userEntity.setRole(normalized);
                    // ユーザーを保存して返却
                    return userPort.save(userEntity)
                        // 処理完了後、値なしで返却
                        .then();
                });
    }

    /**
     * ユーザーを作成
     */
    public Mono<Void> createUser(String username, String password, String confirmPassword, String role, boolean enabled) {
        
        // 新しいユーザー名を取得
        String newUsername = (username == null) ? "" : username.trim();

        // 新しいユーザー名が空の場合
        if (newUsername.isBlank()) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("user.ctrl.err.username.required"));
        }

        // 新しいユーザー名が最大文字数を超える場合
        if (newUsername.length() > 128) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("user.account.err.username_too_long"));
        }

        // 新しいユーザー名が admin を含む場合
        if (isFixedAdminUserByUserId(newUsername)) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("user.account.err.admin_user_cannot_create"));
        }
        // 新しいユーザー名が demo を含む場合
        if (isFixedDemoUserByUserId(newUsername)) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("user.account.err.demo_user_cannot_create"));
        }

        // パスワード
        String pw;
        // パスワードが未指定か判定
        boolean isPasswordNull = (password == null);
        // パスワードが取得できなかった場合
        if (isPasswordNull) {
            // パスワードを空文字として扱う
            pw = "";
        // パスワードが取得できた場合
        } else {
            // パスワードを更新
            pw = password;
        }

        // 確認パスワード
        String changePwd;
        // 確認パスワードが未指定か判定
        boolean isConfirmPasswordNull = (confirmPassword == null);
        // 確認パスワードが取得できなかった場合
        if (isConfirmPasswordNull) {
            // 確認パスワードを空文字として扱う
            changePwd = "";
        // 確認パスワードが取得できた場合
        } else {
            // 確認パスワードを設定
            changePwd = confirmPassword;
        }

        // パスワードと確認パスワードが一致しない場合
        if (!pw.equals(changePwd)) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("user.account.err.password_confirm_mismatch"));
        }

        try {
            // パスワードポリシーを検証
            validatePasswordPolicy(pw);
        } catch (AppMessageException appMessageException) {
            // エラーメッセージを付与して返却
            return Mono.error(appMessageException);
        }

        // ロールを正規化して取得
        String normalizedRole = normalizeRole(role);

        // 管理者ユーザーロールの場合
        if (ROLE_ADMIN.equalsIgnoreCase(normalizedRole)) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("user.account.err.admin_role_user_cannot_create"));
        }
        // デモユーザーロールの場合
        if (ROLE_DEMO.equalsIgnoreCase(normalizedRole)) {
            // エラーメッセージを付与して返却
            return Mono.error(new AppMessageException("user.account.err.demo_role_only_for_demo"));
        }

        // ユーザー名を設定
        final String finalUsername = newUsername;
        // ユーザーオブジェクト
        AppUserEntity entity = new AppUserEntity();
        // ユーザーオブジェクトにユーザー名を設定
        entity.setUsername(finalUsername);
        // ユーザーオブジェクトにパスワードを設定
        entity.setPassword(encoder.encode(pw));
        // ユーザーオブジェクトにロールを設定
        entity.setRole(normalizedRole);
        // ユーザーオブジェクトに有効フラグを設定
        entity.setEnabled(enabled);
        // ユーザーオブジェクトに新規作成フラグを設定
        entity.markNew();

        // ユーザー情報を取得
        return userPort.findById(finalUsername)
                // ユーザー情報に対して処理を実行
                .flatMap(exists -> Mono.<Void>error(new AppMessageException("user.account.err.user_id_exists")))
                // ユーザー情報が取得できなかった場合、エラーメッセージを付与して返却
                .switchIfEmpty(Mono.defer(() ->
                        // ユーザー情報を保存
                        userPort.save(entity)
                                // ユーザー情報を取得
                                .then(userPort.findById(finalUsername)
                                // ユーザー情報が取得できなかった場合、エラーメッセージを付与して返却
                                .switchIfEmpty(Mono.error(new AppMessageException("user.account.err.create_failed_db_not_reflected"))))
                                // 処理完了後、値なしで返却
                                .then()
                ));
    }

    /**
     * パスワードポリシーを検証
     */
    public static void validatePasswordPolicy(String password) {

        // 検証パスワード
        String valPassword;
        // パスワードが未指定か判定
        boolean isPwNull = (password == null);
        // パスワードが取得できなかった場合
        if (isPwNull) {
            // 検証パスワードを空文字として扱う
            valPassword = "";
        // パスワードが取得できた場合
        } else {
            // 検証パスワードを更新
            valPassword = password;
        }

        // 検証パスワードが最小文字数を超えていない場合
        if (valPassword.length() < 8) {
            // エラーメッセージを付与して、例外をスロー
            throw new AppMessageException("user.account.err.password_min_length", 8);
        }

        // 検証パスワードに大文字がない場合
        if (!UPPER.matcher(valPassword).find()) {
            // エラーメッセージを付与して、例外をスロー
            throw new AppMessageException("user.account.err.password_require_upper");
        }

        // 検証パスワードに小文字がない場合
        if (!LOWER.matcher(valPassword).find()) {
            // エラーメッセージを付与して、例外をスロー
            throw new AppMessageException("user.account.err.password_require_lower");
        }

        // 検証パスワードに数字がない場合
        if (!DIGIT.matcher(valPassword).find()) {
            // エラーメッセージを付与して、例外をスロー
            throw new AppMessageException("user.account.err.password_require_digit");
        }
    }

    /**
     * ユーザーロールを取得
     */
    public static String normalizeRole(String role) {

        // ロールが取得できなかった場合
        if (role == null) {
            // 一般ユーザーロールを返却
            return ROLE_USER;
        }

        // ロール名の取得（トリム・大文字へ変換）
        String trimmedRole = role.trim().toUpperCase(Locale.ROOT);

        // 各ロール名の返却
        return switch (trimmedRole) {
            case ROLE_ADMIN -> ROLE_ADMIN;
            case ROLE_USER_MGR -> ROLE_USER_MGR;
            case ROLE_MODEL_MGR -> ROLE_MODEL_MGR;
            case ROLE_NEWS_MGR -> ROLE_NEWS_MGR;
            case ROLE_DEMO -> ROLE_DEMO;
            case ROLE_USER -> ROLE_USER;
            default -> ROLE_USER;
        };
    }
}
