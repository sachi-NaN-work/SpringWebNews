package com.news.recapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;


/*アプリケーション起動クラス*/
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.news.recapp")
public class NewsApplication {

    /**アプリケーションを起動*/
    public static void main(String[] args) {

        // アプリケーションを起動
        SpringApplication.run(NewsApplication.class, args);
    }
}
