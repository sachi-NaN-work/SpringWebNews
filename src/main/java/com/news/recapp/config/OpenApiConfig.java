package com.news.recapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI の仕様情報を設定
 */
@Configuration
public class OpenApiConfig {

    /**
     * OpenAPI の基本情報を生成
     */
    @Bean
    public OpenAPI openAPI() {
        // OpenAPI 定義を生成して返却
        return new OpenAPI()
                // API 情報を設定
                .info(new Info()
                        // 表示タイトルを設定
                        .title("Spring Web News")
                        // API バージョンを設定
                        .version("0.1.0")
                        // API 概要説明を設定
                        .description("Spring Boot 3 + WebFlux minimal real-time recommendation (candidate + rerank + feedback)."));
    }
}
