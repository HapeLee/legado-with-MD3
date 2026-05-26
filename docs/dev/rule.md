# 源规则 (Source Rule) 帮助

[[toc]]

本文档介绍 Legado 源规则的核心配置项和语法。源通过声明式的 JSON 配置和可选的 JavaScript 逻辑，从网站抓取书籍内容。

::: tip 相关文档

- [JS 变量和函数](./js) — 内置变量和扩展 API
- [XPath 路径表达式](./xpath) — XPath 语法
- [正则表达式](./regex) — 正则语法
- [请求头配置](./request-headers) — 代理、Headers、重定向拦截
- [认证与登录](./authentication) — CookieJar、登录 UI、登录 URL
- [发现 URL 配置](./discovery-url) — 发现页入口 JSON 格式
  :::

辅助键盘 ❓ 中可插入 URL 参数模板，打开帮助，js 教程，正则教程，选择文件。

## 2. 规则标志 (Rule Flags)

在 <code v-pre>{{......}}</code> 内使用规则时，必须包含明确的规则标志。没有规则标志的内容将作为
JavaScript 执行。

| 标志        | 语法       | 可省略条件         | 适用范围       |
|:----------|:---------|:--------------|:-----------|
| `@@`      | 默认规则     | 直接写时可省略       | 所有字段       |
| `@XPath:` | XPath 规则 | 以 `//` 开头时可省略 | 所有字段       |
| `@Json:`  | JSON 规则  | 以 `$.` 开头时可省略 | 所有字段       |
| `:`       | 正则规则     | 不可省略          | 仅书籍列表和目录列表 |

## 3. JS 库注入 (jsLib)

注入 JavaScript 到 Rhino 引擎中，支持两种格式，可实现函数共用。

| 格式                | 说明                     | 示例                                         |
|:------------------|:-----------------------|:-------------------------------------------|
| `JavaScript Code` | 直接填写 JavaScript 片段     | `function myUtil(s) { return s.trim() }`   |
| JSON Map          | URL 映射表，自动复用已下载的 js 文件 | `{"example":"https://example.com/lib.js"}` |

::: warning 线程安全
此处定义的函数可能被多个线程同时调用，函数内的全局变量内容将被共享。对其进行修改可能导致竞争问题。

- 函数内**不可**声明全局变量
- 函数外的全局变量**不可**再赋值，否则会抛出 `无法修改密封对象的属性` 异常
  :::

## 4. 并发率 (Request Rate Limit)

控制对目标网站的请求频率，支持两种格式：

| 格式    | 说明         | 示例                          |
|:------|:-----------|:----------------------------|
| `N`   | 访问间隔（毫秒）   | `1000` — 每次请求间隔 1 秒         |
| `N/M` | 时间窗口内最大请求数 | `20/60000` — 60 秒内最多 20 次请求 |

## 5. 源类型：文件 (File Source)

适用于提供文件下载的网站（如知轩藏书）。在源详情的「下载 URL」规则中获取文件链接。

**工作原理：**

1. 通过截取下载链接或文件响应头获取文件信息
2. 获取失败时自动拼接 `书名`、`作者` 和下载链接 `UrlOption` 的 `type` 字段
3. 压缩文件解压缓存在下次启动后自动清理，不占用额外空间

## 6. 字体解析 (Font Parsing)

在正文替换规则中使用，根据源字体的字形数据到目标字体中查找对应编码。

```js
(function(){
  var b64 = String(src).match(/ttf;base64,([^\)]+)/);
  if (b64) {
    var f1 = java.queryTTF(b64[1]);
    var f2 = java.queryTTF("https://example.com/font/SourceHanSansCN.ttf");
    return java.replaceFont(result, f1, f2, true);
  }
  return result;
})()
```

## 7. 购买操作 (Purchase Action)

可直接填写链接或 JavaScript。

| 返回值           | 行为          |
|:--------------|:------------|
| 网络链接          | 自动打开浏览器     |
| `true`（JS 返回） | 自动刷新目录和当前章节 |

## 8. 图片解密 (Image Decryption)

适用于图片需要二次解密的情况。直接填写 JavaScript，返回解密后的 `ByteArray`。

**可用变量：**

| 变量       | 说明                                                                                                                                   |
|:---------|:-------------------------------------------------------------------------------------------------------------------------------------|
| `java`   | 仅支持 [JsExtensions](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/help/JsExtensions.kt) 中的方法 |
| `result` | 待解密图片的 `ByteArray`                                                                                                                   |
| `src`    | 图片链接                                                                                                                                 |

**示例 — AES 解密：**

```js
java.createSymmetricCrypto("AES/CBC/PKCS5Padding", key, iv).decrypt(result)
```

**示例 — XOR 解密：**

```js
function decodeImage(data, key) {
  var input = new Packages.java.io.ByteArrayInputStream(data)
  var out = new Packages.java.io.ByteArrayOutputStream()
  var byte
  while ((byte = input.read()) != -1) {
    out.write(byte ^ key)
  }
  return out.toByteArray()
}

decodeImage(result, key)
```

### 封面解密

与图片解密类似，其中 `result` 为待解密封面的 `InputStream`。

```js
java.createSymmetricCrypto("AES/CBC/PKCS5Padding", key, iv).decrypt(result)
```
