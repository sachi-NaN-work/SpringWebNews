package com.news.recapp.presentation.web;

import com.news.recapp.persistence.entity.CategoryEntity;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ニュース管理（分割画面）向けの共通処理を提供するサポートクラス
 * - フォーム（application/x-www-form-urlencoded）/ multipart の差異を吸収してパラメータを読めるようにする
 * - 画面遷移用の URL クエリ生成や、表示用の整形（日時/ID 等）を補助する
 */
public final class NewsAdminSupport {

    /**
     * 公開日時などを画面表示用の文字列に整形するためのフォーマット
     */
    private static final DateTimeFormatter ARTICLE_DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 日本時間のタイムゾーン
     */
    private static final ZoneId JAPAN_ZONE = ZoneId.of("Asia/Tokyo");

    // コンストラクタ
    private NewsAdminSupport() {
        
    }

    /**
     * フォームパラメータを読取
     */
    public static Mono<MultiValueMap<String, String>> readParams(ServerWebExchange exchange) {

        // フォームデータを取得
        Mono<MultiValueMap<String, String>> formMono = exchange.getFormData();

        // フォームデータが空の場合、multipart にフォールバック
        return formMono.flatMap(form -> {

            // フォームデータが取得できた場合、そのまま返却
            if (form != null && !form.isEmpty()) {
                return Mono.just(form);
            }

            // multipart/form-data の取得
            Mono<MultiValueMap<String, Part>> multipartMono = exchange.getMultipartData();

            // multipart を「文字列パラメータの MultiValueMap」に変換して返却
            return multipartMono.map(multipartMap -> {

                // 返却用（キーに対して複数値を保持できる）
                LinkedMultiValueMap<String, String> outMap = new LinkedMultiValueMap<>();

                // multipart が取得できなかった場合、空を返却
                if (multipartMap == null) return outMap;

                // multipart の全エントリ（キー→Part一覧）を順に処理
                for (Map.Entry<String, List<Part>> item : multipartMap.entrySet()) {

                    // 同じキーに複数の Part がある場合があるため、全 Part を処理
                    for (Part part : item.getValue()) {

                        // フォーム入力（文字列）の Part のみを対象にする（ファイル等は除外）
                        if (part instanceof FormFieldPart formFieldPart) {

                            // キーに対応する文字列値を追加（複数値があり得るため add）
                            outMap.add(item.getKey(), formFieldPart.value());
                        }
                    }
                }

                // 文字列パラメータに変換した結果を返却
                return outMap;
            });
        });
    }

    /**
     * ログ出力用に、フォームの値を最大60文字に短縮して「キー→値一覧」の Map に変換
     */
    public static Map<String, Object> safeKeys(MultiValueMap<String, String> form) {

        // 返却用の Map（追加順を保持してログの見やすさを保つ）
        Map<String, Object> safeFormMap = new LinkedHashMap<>();

        // 入力が null の場合、空 Map を返却
        if (form == null) return safeFormMap;

        // フォームキーを全ループ処理
        for (String key : form.keySet()) {

            // 指定キーに対する値一覧を取得（MultiValueMap なので複数値があり得る）
            List<String> values = form.get(key);

            // 値一覧が取得できなかった場合、スキップ
            if (values == null) continue;

            // 各値を最大60文字に短縮し、長い場合、末尾に "..." を付ける
            List<String> shortValues = values.stream()
                    .map(value -> value == null ? null : (value.length() > 60 ? (value.substring(0, 60) + "...") : value))
                    .collect(Collectors.toList());

            // キーに対して短縮済み値一覧を Map に格納
            safeFormMap.put(key, shortValues);
        }

        // 短縮済みの Map を返却
        return safeFormMap;
    }

    /**
     * 指定したキー候補の順に探索し、最初に見つかった空でない文字列を返却
     */
    public static String firstNonBlank(MultiValueMap<String, String> form, String... keys) {

        // 優先順位順にキー候補を確認
        for (String key : keys) {

            // そのキーの値一覧を取得
            List<String> vals = form.get(key);

            // キーが存在しない場合、スキップ
            if (vals == null) continue;

            // 値を全ループ処理
            for (String value : vals) {

                // 値が null の場合、スキップ
                if (value == null) continue;

                // 前後の空白を除去して有効判定
                String trimmedText = value.trim();

                // 空文字でない場合、最初の有効値として返却
                if (!trimmedText.isEmpty()) return trimmedText;
            }
        }

        // どれも見つからなければ null を返却
        return null;
    }

    /**
     * 正常メッセージを付与して、リダイレクトを生成
     */
    public static Rendering redirectOk(String path, String msg) {
        return Rendering.redirectTo(path + "?msg=" + urlEncode(msg)).build();
    }

    /**
     * エラーメッセージを付与して、リダイレクトを生成
     */
    public static Rendering redirectErr(String path, String err) {
        return Rendering.redirectTo(path + "?err=" + urlEncode(err)).build();
    }

    /**
     * UTF-8 で URL エンコード
     */
    public static String urlEncode(String uriString) {

        // 文字列が取得できなかった場合、空文字として扱う
        String value = (uriString == null) ? "" : uriString;

        // UTF-8 で URL エンコードして返却
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 公開日時を日本時間の表示用（yyyy-MM-dd HH:mm:ss）に整形
     */
    public static String fmtDateTime(OffsetDateTime offsetDateTime) {

        // 日時が取得できなかった場合、"-" を返却
        if (offsetDateTime == null) {
            return "-";
        }

        try {
            // 日本時間へ変換して指定フォーマットで整形
            return offsetDateTime
                    .atZoneSameInstant(JAPAN_ZONE)
                    .format(ARTICLE_DTF);
        } catch (Exception exception) {
            // 例外時は最低限の表現として toString() を返却
            return offsetDateTime.toString();
        }
    }

    /**
     * カテゴリ一覧から「カテゴリ ID：カテゴリ名」の Map を生成
     */
    public static Map<UUID, String> toCategoryNameMap(List<CategoryEntity> categories) {

        // カテゴリ一覧が取得できなかった場合、空の Map を返却
        if (categories == null) {
            return Map.of();
        }

        // カテゴリ一覧を「カテゴリ ID：カテゴリ名」の Map に変換して返却
        return categories.stream()
                .filter(Objects::nonNull)
                .filter(category -> category.getId() != null)
                .collect(Collectors.toMap(
                        CategoryEntity::getId,
                        CategoryEntity::getName,
                        (existing, ignore) -> existing
                ));
    }
}
