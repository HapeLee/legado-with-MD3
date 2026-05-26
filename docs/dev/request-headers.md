# 请求头配置 (Request Headers)

[[toc]]

源的请求头字段用于控制 HTTP 请求行为，包括代理设置、自定义 Headers 和动态参数注入。

## 1. 基本格式

请求头以 JSON 对象形式填写，key **区分大小写**。

| 正确           | 错误           |
|:-------------|:-------------|
| `User-Agent` | `user-agent` |
| `Referer`    | `referer`    |

## 2. 代理配置 (Proxy)

支持 HTTP、SOCKS4、SOCKS5 三种代理协议。

| 协议        | 格式                           | 示例                                                |
|:----------|:-----------------------------|:--------------------------------------------------|
| SOCKS5    | `socks5://host:port`         | `{"proxy": "socks5://127.0.0.1:1080"}`            |
| SOCKS4    | `socks4://host:port`         | `{"proxy": "socks4://127.0.0.1:1080"}`            |
| HTTP      | `http://host:port`           | `{"proxy": "http://127.0.0.1:1080"}`              |
| HTTP（带认证） | `http://host:port@user@pass` | `{"proxy": "http://127.0.0.1:1080@admin@secret"}` |

## 3. URL 附加 JS 参数

在 URL 后附加 JSON 对象，可在解析 URL 时执行 JavaScript 动态处理请求。

**语法：**

```
URL,{"js":"JavaScript 代码"}
```

**示例：**

```
https://www.example.com,{"js":"java.headerMap.put('xxx', 'yyy')"}
https://www.example.com,{"js":"java.url=java.url+'yyyy'"}
```

## 4. 图片链接自定义 Headers

在正文图片链接中附加自定义请求头，适用于需要 Referer 或 Cookie 才能加载图片的场景。

```js
let options = {
  "headers": {
    "User-Agent": "xxxx",
    "Referrer": baseUrl,
    "Cookie": "aaa=vbbb;"
  }
};
'<img src="' + src + "," + JSON.stringify(options) + '">'
```

## 5. 重定向拦截 (Redirect Interception)

通过 `java.get` / `java.post` 方法拦截重定向，获取最终跳转后的 URL。适用于搜索结果会重定向的网站。

```js
// 方法签名
java.get(urlStr: String, headers: Map<String, String>)
java.post(urlStr: String, body: String, headers: Map<String, String>)
```

**示例**：搜索重定向场景下获取真实 URL：

```js
(() => {
  if (page == 1) {
    let url = 'https://www.example.com/search,' + JSON.stringify({
      "method": "POST",
      "body": "show=title&tempid=1&keyboard=" + key
    });
    return source.put('surl', String(java.connect(url).raw().request().url()));
  } else {
    return source.get('surl') + '&page=' + (page - 1)
  }
})()
```
