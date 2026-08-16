package com.news.recapp.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Kafka 接続の必須設定を保持
 */
@Validated
@ConfigurationProperties(prefix = "spring.kafka")
public class RequiredKafkaProperties {

    // Kafka ブローカーの接続先（bootstrap.servers）
    @NotBlank(message = "{config.kafka.bootstrap.required}")
    private String bootstrapServers;

    /**
     * Kafka ブローカーの接続先を取得
     */
    public String getBootstrapServers() {
        // Kafka ブローカーの接続先を返却
        return bootstrapServers;
    }

    /**
     * Kafka ブローカーの接続先を設定
     */
    public void setBootstrapServers(String bootstrapServers) {
        // Kafka ブローカーの接続先を保持
        this.bootstrapServers = bootstrapServers;
    }
}
