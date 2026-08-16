package com.news.recapp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 開発環境と本番環境で必須設定の検証を有効化
 */
@Configuration
@Profile({"dev", "prod"})
@EnableConfigurationProperties({
    RequiredServerProperties.class,
    RequiredR2dbcProperties.class,
    RequiredRedisProperties.class,
    RequiredKafkaProperties.class
})
public class DevProdRequiredEnvConfig {
}
