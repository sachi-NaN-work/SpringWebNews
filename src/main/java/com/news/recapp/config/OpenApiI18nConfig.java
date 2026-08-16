package com.news.recapp.config;

import io.swagger.v3.oas.models.PathItem;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * OpenAPI の summary に設定したメッセージキーを messages.properties から解決
 */
@Configuration
public class OpenApiI18nConfig {

    /**
     * OpenAPI の summary をローカライズするカスタマイザを生成
     */
    @Bean
    public OpenApiCustomizer openApiSummaryCustomizer(MessageSource messageSource) {

        // summary をローカライズするカスタマイザを返却
        return openApi -> {

            // OpenAPI は通常単一言語で生成するため既定ロケールで解決
            Locale locale = Locale.getDefault();

            // OpenAPI のパス定義が未設定の場合
            if (openApi.getPaths() == null) {
                // summary 変換を行わずに終了
                return;
            }

            // 全パス定義を走査
            openApi.getPaths().values().forEach(pathItem -> {

                // パス定義が未設定の場合
                if (pathItem == null) {
                    // パス定義が無いためスキップ
                    return;
                }

                // HTTP メソッド毎の Operation を走査
                for (PathItem.HttpMethod method : PathItem.HttpMethod.values()) {

                    // メソッド毎の Operation を取得
                    var operation = pathItem.readOperationsMap().get(method);

                    // Operation が未定義の場合
                    if (operation == null) {
                        // 操作定義が無いためスキップ
                        continue;
                    }

                    // summary のメッセージキーを取得
                    String summaryKey = operation.getSummary();

                    // summary が対象キーではない場合
                    if (summaryKey == null || !summaryKey.startsWith("openapi.summary.")) {
                        // summary 変換対象外のためスキップ
                        continue;
                    }

                    try {
                        // メッセージキーをローカライズして取得
                        String resolved = messageSource.getMessage(summaryKey, null, locale);
                        // summary に解決結果を設定
                        operation.setSummary(resolved);
                    } catch (Exception ignored) {
                        // メッセージ未定義などの場合は変換せずに継続
                    }
                }
            });
        };
    }
}
