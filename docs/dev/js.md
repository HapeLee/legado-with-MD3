# JS 变量和函数

[[toc]]

Legado 使用 [Rhino v1.8.0](https://github.com/mozilla/rhino) 作为 JavaScript 引擎，支持在源规则中调用
Java 类和方法。本文档列出所有可用的内置变量、对象属性和扩展函数。

## 1. Rhino 引擎概览

| 构造函数           | 函数                            | 调用类                                                                                                                                       | 说明                        |
|:---------------|:------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------|:--------------------------|
| `JavaImporter` | `importClass` `importPackage` | [ImporterTopLevel](https://github.com/mozilla/rhino/blob/master/rhino/src/main/java/org/mozilla/javascript/ImporterTopLevel.java)         | 导入 Java 类到 JavaScript     |
| —              | `getClass`                    | [NativeJavaTopPackage](https://github.com/mozilla/rhino/blob/master/rhino/src/main/java/org/mozilla/javascript/NativeJavaTopPackage.java) | 默认导入 JavaScript 中的 Java 类 |
| `JavaAdapter`  | —                             | [JavaAdapter](https://github.com/mozilla/rhino/blob/master/rhino/src/main/java/org/mozilla/javascript/JavaAdapter.java)                   | 继承 Java 类                 |

- [Rhino 运行时](https://github.com/mozilla/rhino/blob/master/rhino/src/main/java/org/mozilla/javascript/ScriptRuntime.java)
  懒加载导入的 Java 类和方法
- [ECMAScript 兼容性表格](https://mozilla.github.io/rhino/compat/engines.html)

::: warning 注意事项

- `java` 变量已被 Legado 修改，调用 `java.*` 下的包请使用 `Packages.java.*`
- 在源规则中使用 `@js`、`<js>`、<code v-pre>{{}}</code> 可调用 Legado 内置的类和方法
- 为安全起见，部分 Java
  类调用被屏蔽，见 [RhinoClassShutter](https://github.com/HapeLee/legado-with-MD3/blob/master/modules/rhino/src/main/java/com/script/rhino/RhinoClassShutter.kt)
- 不同源规则中支持调用的 Java 类和方法可能不同
- `const` 声明的变量不支持块级作用域，循环中使用会出现值不变的问题，请改用 `var`
  :::

## 2. 内置变量 (Built-in Variables)

以下变量在源规则的 JS 执行环境中自动可用。

| 变量               | 类型             | 作用域 | 说明                                                                                                                             |
|:-----------------|:---------------|:----|:-------------------------------------------------------------------------------------------------------------------------------|
| `java`           | `Object`       | 全局  | 扩展工具对象，提供网络请求、加解密、文件操作等方法                                                                                                      |
| `baseUrl`        | `String`       | 全局  | 当前请求的 URL                                                                                                                      |
| `result`         | `Any`          | 全局  | 上一步规则的执行结果                                                                                                                     |
| `book`           | `Book`         | 全局  | [书籍对象](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/data/entities/Book.kt)           |
| `rssArticle`     | `RssArticle`   | 全局  | [RSS 文章对象](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/data/entities/RssArticle.kt) |
| `chapter`        | `BookChapter`  | 全局  | [章节对象](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/data/entities/BookChapter.kt)    |
| `source`         | `BaseSource`   | 全局  | [源对象](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/data/entities/BaseSource.kt)      |
| `cookie`         | `CookieStore`  | 全局  | [Cookie 操作对象](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/help/http/CookieStore.kt) |
| `cache`          | `CacheManager` | 全局  | [缓存操作对象](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/help/CacheManager.kt)          |
| `title`          | `String`       | 全局  | 当前章节标题                                                                                                                         |
| `src`            | `String`       | 全局  | 请求返回的源码                                                                                                                        |
| `nextChapterUrl` | `String`       | 全局  | 下一章节 URL                                                                                                                       |

## 3. java 对象方法

`java` 对象是 Legado 暴露给 JS 环境的核心工具对象，聚合了多个扩展类的方法。

### 3.1 RSS 扩展 ([RssJsExtensions](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/ui/rss/read/RssJsExtensions.kt))

::: warning 作用域限制
只能在订阅源 `shouldOverrideUrlLoading` 规则中使用。URL 跳转拦截规则不能执行耗时操作。
:::

```js
java.searchBook(bookName: String)  // 调用 Legado 搜索
java.addBook(bookUrl: String)      // 添加书架
```

### 3.2 URL 解析 ([AnalyzeUrl](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeUrl.kt))

通过 `java.` 调用，仅在 `登录检查 JS` 规则中有效。

| 方法                                                 | 返回值           | 说明                  |
|:---------------------------------------------------|:--------------|:--------------------|
| `initUrl()`                                        | —             | 重新解析 URL            |
| `getHeaderMap().putAll(source.getHeaderMap(true))` | —             | 重新设置登录头             |
| `getStrResponse(jsStr, sourceRegex)`               | `StrResponse` | 返回文本类型的访问结果         |
| `getResponse()`                                    | `Response`    | 返回 Response 类型的访问结果 |

### 3.3 规则解析 ([AnalyzeRule](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeRule.kt))

```js
// 获取文本/文本列表
java.getString(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false)
java.getStringList(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false)

// 设置解析内容
java.setContent(content: Any?, baseUrl: String? = null)

// 获取 Element/Element 列表
java.getElement(ruleStr: String)
java.getElements(ruleStr: String)

// 重新搜索书籍/重新获取目录 url（只能在刷新目录之前使用）
java.reGetBook()
java.refreshTocUrl()

// 变量存取
java.get(key)
java.put(key, value)
```

### 3.4 扩展工具 ([JsExtensions](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/help/JsExtensions.kt))

#### 链接解析

| 方法                         | 返回值      | 说明                    |
|:---------------------------|:---------|:----------------------|
| `java.toURL(url)`          | `JsURL`  | 将字符串解析为 URL 对象        |
| `java.toURL(url, baseUrl)` | `JsURL`  | 基于 baseUrl 解析相对 URL   |
| `java.getWebViewUA()`      | `String` | 获取 WebView User-Agent |

#### 网络请求

| 方法                                                            | 返回值                   | 说明                                                                |
|:--------------------------------------------------------------|:----------------------|:------------------------------------------------------------------|
| `java.ajax(urlStr)`                                           | `String`              | GET 请求，返回响应体                                                      |
| `java.ajaxAll(urlList)`                                       | `Array<StrResponse>`  | 批量请求                                                              |
| `java.connect(urlStr)`                                        | `StrResponse`         | 返回 `body()` `code()` `message()` `headers()` `raw()` `toString()` |
| `java.post(url, body, headerMap)`                             | `Connection.Response` | POST 请求                                                           |
| `java.get(url, headerMap)`                                    | `Connection.Response` | GET 请求                                                            |
| `java.head(url, headerMap)`                                   | `Connection.Response` | HEAD 请求                                                           |
| `java.webView(html, url, js)`                                 | `String?`             | 使用 WebView 访问网络                                                   |
| `java.webViewGetOverrideUrl(html, url, js, overrideUrlRegex)` | `String?`             | 使用 WebView 获取跳转 URL                                               |
| `java.webViewGetSource(html, url, js, sourceRegex)`           | `String?`             | 使用 WebView 获取资源 URL                                               |
| `java.startBrowser(url, title)`                               | —                     | 使用内置浏览器打开链接                                                       |
| `java.startBrowserAwait(url, title, refetchAfterSuccess)`     | `StrResponse`         | 使用内置浏览器打开链接，等待结果                                                  |

#### 调试与提示

| 方法                                   | 说明       |
|:-------------------------------------|:---------|
| `java.log(msg)`                      | 输出日志     |
| `java.logType(var)`                  | 输出变量类型   |
| `java.getVerificationCode(imageUrl)` | 弹出验证码输入框 |
| `java.longToast(msg)`                | 长时间提示    |
| `java.toast(msg)`                    | 短时间提示    |

#### 脚本导入

| 方法                                | 说明                                         |
|:----------------------------------|:-------------------------------------------|
| `java.importScript(url)`          | 从网络加载脚本                                    |
| `java.importScript(relativePath)` | 从相对路径加载（支持 `android/data/{package}/cache`） |
| `java.importScript(absolutePath)` | 从绝对路径加载                                    |

#### 文件缓存

```js
java.cacheFile(url)              // 缓存网络文件
java.cacheFile(url, saveTime)    // 缓存并指定保存时间
eval(String(java.cacheFile(url)))  // 缓存并执行
cache.delete(java.md5Encode16(url))  // 使缓存失效
```

#### 编码与转换

| 分类        | 方法                                         | 说明                   |
|:----------|:-------------------------------------------|:---------------------|
| URI       | `java.encodeURI(str, enc?)`                | URI 编码，默认 UTF-8      |
| Base64    | `java.base64Decode(str, charset?)`         | Base64 解码为字符串        |
| Base64    | `java.base64DecodeToByteArray(str, flags)` | Base64 解码为 ByteArray |
| Base64    | `java.base64Encode(str, flags)`            | Base64 编码            |
| ByteArray | `java.strToBytes(str, charset?)`           | 字符串转 ByteArray       |
| ByteArray | `java.bytesToStr(bytes, charset?)`         | ByteArray 转字符串       |
| Hex       | `java.hexDecodeToByteArray(hex)`           | Hex 解码为 ByteArray    |
| Hex       | `java.hexDecodeToString(hex)`              | Hex 解码为字符串           |
| Hex       | `java.hexEncodeToString(utf8)`             | 字符串转 Hex             |

#### 标识与格式化

| 方法                                     | 返回值       | 说明            |
|:---------------------------------------|:----------|:--------------|
| `java.randomUUID()`                    | `String`  | 生成 UUID       |
| `java.androidId()`                     | `String`  | 获取 Android ID |
| `java.t2s(text)`                       | `String`  | 繁体转简体         |
| `java.s2t(text)`                       | `String`  | 简体转繁体         |
| `java.timeFormatUTC(time, format, sh)` | `String?` | UTC 时间格式化     |
| `java.timeFormat(time)`                | `String`  | 时间格式化         |
| `java.htmlFormat(str)`                 | `String`  | HTML 格式化      |

#### 文件操作

::: tip 路径限制
所有文件读写删除操作均使用相对路径，只能操作阅读缓存目录 `android/data/{package}/cache/` 内的文件。
:::

| 方法                          | 返回值      | 说明          |
|:----------------------------|:---------|:------------|
| `downloadFile(url)`         | `String` | 文件下载，返回文件路径 |
| `unArchiveFile(zipPath)`    | `String` | 文件解压，返回解压路径 |
| `unzipFile(zipPath)`        | `String` | ZIP 解压      |
| `unrarFile(zipPath)`        | `String` | RAR 解压      |
| `un7zFile(zipPath)`         | `String` | 7Z 解压       |
| `getTxtInFolder(unzipPath)` | `String` | 读取文件夹内所有文件  |
| `readTxtFile(path)`         | `String` | 读取文本文件      |
| `deleteFile(path)`          | —        | 删除文件        |

#### 外部链接跳转

| 方法                            | 说明                       |
|:------------------------------|:-------------------------|
| `java.openUrl(url)`           | 跳转外部链接（HTTP 或 scheme）    |
| `java.openUrl(url, mimeType)` | 指定 MIME 类型跳转，如 `video/*` |

### 3.5 加解密 ([JsEncodeUtils](https://github.com/HapeLee/legado-with-MD3/blob/master/app/src/main/java/io/legado/app/help/JsEncodeUtils.kt))

提供在 JavaScript 环境中快捷调用 crypto
算法的函数，由 [hutool-crypto](https://www.hutool.cn/docs/#/crypto/概述) 实现（当前版本 5.8.22）。

::: warning 输入类型
如果输入参数不是 Utf8String，可先调用 `java.hexDecodeToByteArray` 或 `java.base64DecodeToByteArray`
转成 ByteArray。
:::

#### 对称加密

```js
// 创建 Cipher，key/iv 支持 ByteArray | Utf8String
java.createSymmetricCrypto(transformation, key, iv)

// data 支持 ByteArray | Base64String | HexString | InputStream
cipher.decrypt(data)          // 解密为 ByteArray
cipher.decryptStr(data)       // 解密为字符串
cipher.encrypt(data)          // 加密为 ByteArray
cipher.encryptBase64(data)    // 加密为 Base64
cipher.encryptHex(data)       // 加密为 Hex
```

#### 非对称加密

```js
java.createAsymmetricCrypto(transformation)
  .setPublicKey(key)
  .setPrivateKey(key)

cipher.decrypt(data, usePublicKey: Boolean? = true)
cipher.decryptStr(data, usePublicKey: Boolean? = true)
cipher.encrypt(data, usePublicKey: Boolean? = true)
cipher.encryptBase64(data, usePublicKey: Boolean? = true)
cipher.encryptHex(data, usePublicKey: Boolean? = true)
```

#### 签名

```js
java.createSign(algorithm)
  .setPublicKey(key)
  .setPrivateKey(key)

sign.sign(data)
sign.signHex(data)
```

#### 摘要与 HMAC

| 方法                                      | 返回值       | 说明           |
|:----------------------------------------|:----------|:-------------|
| `java.digestHex(data, algorithm)`       | `String?` | 摘要（Hex）      |
| `java.digestBase64Str(data, algorithm)` | `String?` | 摘要（Base64）   |
| `java.md5Encode(str)`                   | `String`  | MD5（32 位）    |
| `java.md5Encode16(str)`                 | `String`  | MD5（16 位）    |
| `java.HMacHex(data, algorithm, key)`    | `String`  | HMAC（Hex）    |
| `java.HMacBase64(data, algorithm, key)` | `String`  | HMAC（Base64） |

## 4. book 对象

在 JS 中或 <code v-pre>{{}}</code> 中使用 `book.属性` 的方式获取。

| 属性                   | 类型        | 说明                  |
|:---------------------|:----------|:--------------------|
| `bookUrl`            | `String`  | 详情页 URL（本地源为完整文件路径） |
| `tocUrl`             | `String`  | 目录页 URL             |
| `origin`             | `String`  | 源 URL               |
| `originName`         | `String`  | 源名称或本地书籍文件名         |
| `name`               | `String`  | 书籍名称                |
| `author`             | `String`  | 作者名称                |
| `kind`               | `String`  | 分类信息                |
| `customTag`          | `String`  | 分类信息（用户修改）          |
| `coverUrl`           | `String`  | 封面 URL              |
| `customCoverUrl`     | `String`  | 封面 URL（用户修改）        |
| `intro`              | `String`  | 简介内容                |
| `customIntro`        | `String`  | 简介内容（用户修改）          |
| `charset`            | `String`  | 自定义字符集名称（仅本地书籍）     |
| `type`               | `Int`     | 0: text, 1: audio   |
| `group`              | `Int`     | 自定义分组索引号            |
| `latestChapterTitle` | `String`  | 最新章节标题              |
| `latestChapterTime`  | `Long`    | 最新章节更新时间            |
| `lastCheckTime`      | `Long`    | 最近一次更新书籍信息的时间       |
| `lastCheckCount`     | `Int`     | 最近一次发现新章节数量         |
| `totalChapterNum`    | `Int`     | 书籍目录总数              |
| `durChapterTitle`    | `String`  | 当前章节名称              |
| `durChapterIndex`    | `Int`     | 当前章节索引              |
| `durChapterPos`      | `Long`    | 当前阅读进度              |
| `durChapterTime`     | `Long`    | 最近一次阅读时间            |
| `canUpdate`          | `Boolean` | 刷新书架时是否更新           |
| `order`              | `Int`     | 手动排序                |
| `originOrder`        | `Int`     | 源排序                 |
| `variable`           | `String?` | 自定义书籍变量             |

## 5. chapter 对象

| 属性            | 类型        | 说明         |
|:--------------|:----------|:-----------|
| `url`         | `String`  | 章节地址       |
| `title`       | `String`  | 章节标题       |
| `baseUrl`     | `String`  | 用于拼接相对 URL |
| `bookUrl`     | `String`  | 书籍地址       |
| `index`       | `Int`     | 章节序号       |
| `resourceUrl` | `String`  | 音频真实 URL   |
| `tag`         | `String`  | 标签         |
| `start`       | `Long`    | 章节起始位置     |
| `end`         | `Long`    | 章节终止位置     |
| `variable`    | `String?` | 变量         |

## 6. source 对象

| 方法                                    | 返回值       | 说明        |
|:--------------------------------------|:----------|:----------|
| `source.getKey()`                     | `String`  | 获取源 URL   |
| `source.setVariable(variable)`        | —         | 设置源变量     |
| `source.getVariable()`                | `String?` | 获取源变量     |
| `source.getLoginHeader()`             | `String?` | 获取登录头     |
| `source.getLoginHeaderMap().get(key)` | `String?` | 获取登录头某一键值 |
| `source.putLoginHeader(header)`       | —         | 保存登录头     |
| `source.removeLoginHeader()`          | —         | 清除登录头     |
| `source.getLoginInfo()`               | `String?` | 获取登录信息    |
| `source.getLoginInfoMap().get(key)`   | `String?` | 获取登录信息键值  |
| `source.removeLoginInfo()`            | —         | 清除登录信息    |

## 7. cookie 对象

| 方法                                  | 返回值       | 说明          |
|:------------------------------------|:----------|:------------|
| `cookie.getCookie(url)`             | `String`  | 获取全部 cookie |
| `cookie.getKey(url, key)`           | `String?` | 获取某一键值      |
| `cookie.setCookie(url, cookie)`     | —         | 设置 cookie   |
| `cookie.replaceCookie(url, cookie)` | —         | 替换 cookie   |
| `cookie.removeCookie(url)`          | —         | 删除 cookie   |

## 8. cache 对象

saveTime 单位：秒，可省略。保存至数据库和缓存文件（50MB），内容较大时请使用 `getFile` / `putFile`。

| 方法                                     | 返回值       | 说明      |
|:---------------------------------------|:----------|:--------|
| `cache.put(key, value, saveTime?)`     | —         | 保存到数据库  |
| `cache.get(key)`                       | `String?` | 从数据库读取  |
| `cache.delete(key)`                    | —         | 删除数据库缓存 |
| `cache.putFile(key, value, saveTime?)` | —         | 缓存文件内容  |
| `cache.getFile(key)`                   | `String?` | 读取文件缓存  |
| `cache.putMemory(key, value)`          | —         | 保存到内存   |
| `cache.getFromMemory(key)`             | `Any?`    | 从内存读取   |
| `cache.deleteMemory(key)`              | —         | 删除内存缓存  |
