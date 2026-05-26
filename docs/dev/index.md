# 开发文档

欢迎来到 Legado 开发文档。本文档为源开发者和贡献者提供参考。

::: tip 快速上手
如果你是第一次接触 Legado 源开发，建议按以下顺序阅读：

1. [规则语法详解](./syntax) — 了解所有支持的规则语法
2. [URL 参数详解](./url-options) — 掌握请求参数配置
3. [源字段速查](./source-fields) — 查看各部分可用字段
4. [源示例](./examples) — 参考完整源 JSON
   :::

## 入门

从零开始学习源开发。

| 文档                        | 说明                                                           |
|:--------------------------|:-------------------------------------------------------------|
| [规则语法详解](./syntax)        | JSOUP Default/CSS、JSONPath、XPath、正则 AllInOne/OnlyOne/净化、连接符号 |
| [URL 参数详解](./url-options) | GET/POST 请求、WebView 模式、模板变量、完整 UrlOption 字段                  |
| [源字段速查](./source-fields)  | 搜索、发现、详情页、目录、正文各部分的所有字段说明                                    |
| [源示例](./examples)         | CSS+正则、XPath+正则、JSONPath 三种完整源 JSON                          |

## 参考

核心 API 和语法参考。

| 文档                     | 说明                                    |
|:-----------------------|:--------------------------------------|
| [源规则帮助](./rule)        | 规则标志、jsLib、并发率、源类型、字体解析、图片解密          |
| [JS 变量和函数](./js)       | Rhino 引擎内置变量、加解密、网络请求、Java 互操作等完整 API |
| [XPath 路径表达式](./xpath) | 13 种轴、谓语条件、通配符、内置函数                   |
| [正则表达式](./regex)       | 元字符、字符集、断言、常用正则示例                     |

## 配置规范

源 JSON 字段的详细规范。

| 文档                                 | 说明                                      |
|:-----------------------------------|:----------------------------------------|
| [请求头配置](./request-headers)         | 代理设置、自定义 Headers、URL 动态参数、重定向拦截         |
| [认证与登录](./authentication)          | CookieJar、登录 UI 表单、登录 URL 脚本、登录检查       |
| [发现 URL 配置](./discovery-url)       | 发现页入口 JSON 格式、分页变量、与首页模块的关系             |
| [首页模块配置](../spec/homepage-modules) | `homepageModules` 字段规范：列表、轮播、排行榜等模块类型   |
| [关联书籍配置](../spec/related-books)    | `ruleBookInfo.relatedBooks` 字段规范：相关书籍推荐 |

## 扩展功能

| 文档                              | 说明                     |
|:--------------------------------|:-----------------------|
| [源调试](./debug)                  | 搜索、发现、详情页、目录页、正文页的调试方法 |
| [字典规则](./dict-rule)             | 正文选中菜单的字典/翻译规则配置       |
| [在线朗读规则](./tts-rule)            | 自定义在线 TTS 接口（支持语速控制）   |
| [TXT 目录正则](./txt-toc)           | 自定义 TXT 书籍的章节识别规则      |
| [MIME 类型参考](../spec/mime-types) | 支持的文件扩展名和 MIME 类型对照表   |

## 外部资源

- [Legado GitHub](https://github.com/HapeLee/legado-with-MD3) — 项目源码和 Issue 跟踪
- [legado-with-MD3 Wiki](https://github.com/HapeLee/legado-with-MD3/wiki) — 社区维护的文档
