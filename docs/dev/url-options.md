# URL 参数详解 (URL Options)

[[toc]]

Legado 源中的 URL 支持通过 JSON 对象附加请求参数，控制请求方法、编码、Headers、WebView 等行为。

## 1. 基本语法

URL 和参数之间用逗号 `,` 连接：

```
URL,{JSON 参数}
```

**简单 GET 请求：**

```
https://www.example.com/api/list
```

**带参数的请求：**

```
https://www.example.com/api/list,{
    "charset": "gbk",
    "headers": {"User-Agent": "Mozilla/5.0 ..."}
}
```

## 2. UrlOption 完整字段

| 字段        | 类型        | 必须 | 说明                                |
|:----------|:----------|:---|:----------------------------------|
| `method`  | `String`  | 否  | 请求方法，`GET`（默认）或 `POST`            |
| `charset` | `String`  | 否  | 响应编码，默认 `utf-8`                   |
| `headers` | `Object`  | 否  | 自定义请求头                            |
| `body`    | `String`  | 否  | POST 请求体                          |
| `webView` | `Boolean` | 否  | 是否使用 WebView 加载                   |
| `js`      | `String`  | 否  | 解析 URL 时执行的 JS                    |
| `type`    | `String`  | 否  | 文件类型（用于文件类源）                      |
| `retry`   | `Int`     | 否  | 重试次数，默认 0                         |
| `proxy`   | `String`  | 否  | 代理地址，见 [请求头配置](./request-headers) |

## 3. GET 请求

### 简单形式

```
https://www.example.com/api/list
```

### 带 Headers

```
https://www.example.com/api/list,{
    "headers": {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept-Language": "zh-CN,zh;q=0.9"
    }
}
```

### 指定编码

```
https://www.example.com/list,{
    "charset": "gbk"
}
```

### 使用 WebView

```
https://www.example.com/book/123,{
    "webView": true
}
```

### JS 动态构建

```javascript
var ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
var headers = {"User-Agent": ua};
var option = {
    "charset": "gbk",
    "headers": headers,
    "webView": true
};
"https://www.example.com," + JSON.stringify(option)
```

## 4. POST 请求

### 简单形式

```
https://www.example.com/search,{
    "method": "POST",
    "body": "keyword=系统&page=1"
}
```

### 带 Headers 和编码

```
https://www.example.com/search,{
    "charset": "gbk",
    "method": "POST",
    "body": "searchkey={{key}}&page={{page}}",
    "headers": {
        "User-Agent": "Mozilla/5.0 ..."
    }
}
```

### JS 动态构建

```javascript
var body = "key=" + key + "&page=" + page;
var option = {
    "method": "POST",
    "body": String(body),
    "headers": {"User-Agent": "Mozilla/5.0 ..."}
};
"https://www.example.com/search," + JSON.stringify(option)
```

::: warning body 类型
`body` 必须保证是 JavaScript 的 String 类型。变量是计算得到的尽量都用 `String()` 强转。
:::

## 5. WebView 模式

设置 `"webView": true` 后，Legado 会使用内置 WebView 加载页面，适用于需要 JavaScript 渲染的网站。

### WebView 加载

```
https://www.example.com/book/123,{
    "webView": true
}
```

### WebView + 正文嗅探

章节链接加 `{"webView": true}`，配合正文的 `sourceRegex` 嗅探媒体资源：

```json
{
    "ruleToc": {
        "chapterUrl": "href##$##{\"webView\":true}"
    },
    "ruleContent": {
        "content": "<js>result</js>",
        "sourceRegex": ".*\\.(mp3|mp4).*"
    }
}
```

**嗅探步骤：**

1. 章节链接后面加 `,{"webView":true}`
2. 在有嗅探功能的浏览器中输入章节链接（不带 webView 参数）
3. 媒体开始播放后使用浏览器的嗅探功能查看资源链接
4. 在资源正则里填写资源链接的正则，如 `.*\.(mp3|mp4).*`
5. 正文填写 `<js>result</js>`

## 6. 模板变量

URL 中可使用以下模板变量：

| 变量                              | 说明         | 适用位置          |
|:--------------------------------|:-----------|:--------------|
| `{{key}}`                       | 搜索关键字      | 搜索 URL        |
| `{{page}}`                      | 页码（从 1 开始） | 搜索 URL、发现 URL |
| `{{page - 1 == 0 ? "" : page}}` | 第一页无页码     | 搜索 URL、发现 URL |
| `<,{{page}}>`                   | 第一页无页码（简写） | 搜索 URL、发现 URL |

### 页码计算示例

```
/search?key={{key}}&start={{(page-1)*20}}&limit=20
/search?key={{key}}&page={{page - 1 == 0 ? "" : page}}
```

## 7. 相对 URL

URL 支持相对路径，会自动基于源 URL 拼接：

```
/search?key={{key}}     // 相对于 bookSourceUrl
/api/list               // 相对于 bookSourceUrl
```
