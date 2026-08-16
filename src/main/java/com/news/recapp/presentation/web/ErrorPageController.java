package com.news.recapp.presentation.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;

/**
 * エラーページへのリクエストを処理するコントローラ。
 */
@Controller
public class ErrorPageController {

    /**
     * エラーページを表示する。
     */
    @GetMapping("/error")
    public Mono<Rendering> errorPage(
            @RequestParam(name = "msg", required = false) String msg
    ) {

        // エラーメッセージを付与して、エラーページを返却
        return Mono.just(
                Rendering.view("error")
                        .modelAttribute("msg", msg).build()
        );
    }
}

