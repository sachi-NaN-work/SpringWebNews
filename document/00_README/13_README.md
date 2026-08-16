# SpringWebNews - ニュースサイト

[\[ <u>目次へ戻る</u> \]](../../README.md#-readme-ファイル一式-)


---

<br>

## ● 外部設定

[＜ 1 ＞ 設定ファイルの配置](#-1--設定ファイルの配置)


<br>

―――――――――――――――――――――――――――――――

<br>

### ＜ 1 ＞ 設定ファイルの配置

`src/main/resources/application.yml` は、設定ファイルの配置箇所のみを指定している。

```yaml
spring:
  config:
    import: 'optional:file:./config/'
```

> 運用・開発時に触る設定は主に `config/` 配下です。


<br>


---

[\[ <u>▲ ページ上部へ</u> \]](#springwebnews---ニュースサイト)

[\[ <u>目次へ戻る</u> \]](../../README.md#-readme-ファイル一式-)
