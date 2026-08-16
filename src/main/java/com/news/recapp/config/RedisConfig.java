package com.news.recapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Redis 利用のアプリケーション設定を定義
 */
@Configuration
public class RedisConfig {

    /**
     * 文字列用の Redis テンプレートを生成
     */
    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory connectionFactory) {

        // Redis 接続ファクトリを指定してテンプレートを生成して返却
        return new ReactiveStringRedisTemplate(connectionFactory);
    }
}
