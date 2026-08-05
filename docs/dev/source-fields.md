# 源字段速查 (Source Fields)

[[toc]]

本文档列出源 JSON 中各部分（搜索、发现、详情页、目录、正文）的所有可用字段。

## 1. 基本字段

| 字段                  | 必须 | 说明                                        |
|:--------------------|:---|:------------------------------------------|
| `bookSourceUrl`     | 是  | 源 URL，唯一标识，不可重复。与其他源相同会覆盖                 |
| `bookSourceName`    | 是  | 源名称，可重复                                   |
| `bookSourceGroup`   | 否  | 源分组，用于整理                                  |
| `bookSourceType`    | 否  | 源类型：`0`（文本，默认）、`1`（音频）                    |
| `bookUrlPattern`    | 否  | 书籍 URL 正则，添加网址时用于识别源                      |
| `header`            | 否  | 请求头 JSON 字符串，见 [请求头配置](./request-headers) |
| `loginUrl`          | 否  | 登录 URL，见 [认证与登录](./authentication)        |
| `exploreUrl`        | 否  | 发现地址，见 [发现 URL 配置](./discovery-url)       |
| `searchUrl`         | 否  | 搜索地址                                      |
| `weight`            | 否  | 源权重，搜索排序时使用                               |
| `enabled`           | 否  | 是否启用                                      |
| `enabledExplore`    | 否  | 是否启用发现                                    |
| `customOrder`       | 否  | 自定义排序                                     |
| `lastUpdateTime`    | 否  | 最后更新时间                                    |
| `bookSourceComment` | 否  | 源备注                                       |

## 2. 搜索字段 (`ruleSearch`)

| 字段            | 说明                                 |
|:--------------|:-----------------------------------|
| `url`         | 搜索地址。`{{key}}` 为关键字，`{{page}}` 为页码 |
| `bookList`    | 书籍列表规则                             |
| `name`        | 书名规则                               |
| `author`      | 作者规则                               |
| `kind`        | 分类规则                               |
| `wordCount`   | 字数规则                               |
| `lastChapter` | 最新章节规则                             |
| `intro`       | 简介规则                               |
| `coverUrl`    | 封面规则                               |
| `bookUrl`     | 详情页 URL 规则                         |

## 3. 发现字段 (`ruleExplore`)

| 字段            | 说明                               |
|:--------------|:---------------------------------|
| `url`         | 发现地址。`{{page}}` 为页码，支持 JSON 数组格式 |
| `bookList`    | 书籍列表规则                           |
| `name`        | 书名规则                             |
| `author`      | 作者规则                             |
| `kind`        | 分类规则                             |
| `wordCount`   | 字数规则                             |
| `lastChapter` | 最新章节规则                           |
| `intro`       | 简介规则                             |
| `coverUrl`    | 封面规则                             |
| `bookUrl`     | 详情页 URL 规则                       |

## 4. 详情页字段 (`ruleBookInfo`)

| 字段             | 说明                                       |
|:---------------|:-----------------------------------------|
| `bookInfoInit` | 预处理规则（仅支持 AllInOne 正则或 JS）               |
| `name`         | 书名规则                                     |
| `author`       | 作者规则                                     |
| `kind`         | 分类规则                                     |
| `wordCount`    | 字数规则                                     |
| `lastChapter`  | 最新章节规则                                   |
| `intro`        | 简介规则                                     |
| `coverUrl`     | 封面规则                                     |
| `tocUrl`       | 目录 URL 规则（仅支持单个 URL）                     |
| `canReName`    | 允许修改书名作者                                 |
| `relatedBooks` | 关联书籍配置，见 [关联书籍配置](../spec/related-books) |

### 预处理规则 (`bookInfoInit`)

只能使用 AllInOne 正则（以 `:` 开头）或 JS。JS 返回值需为 JSON 对象：

```javascript
(function(){
    return {
        a: "书名",
        b: "作者",
        c: "分类",
        d: "字数",
        e: "最新章节",
        f: "简介",
        g: "封面URL",
        h: "目录URL"
    };
})()
```

此时各规则字段填对应的 key：`name` → `a`，`author` → `b`，以此类推。

### `canReName` 逻辑

| 条件               | 行为      |
|:-----------------|:--------|
| 规则不为空 且 详情页书名不为空 | 使用详情页书名 |
| 否则               | 使用搜索页书名 |
| 规则不为空 且 详情页作者不为空 | 使用详情页作者 |
| 否则               | 使用搜索页作者 |

## 5. 目录字段 (`ruleToc`)

| 字段            | 说明                                                 |
|:--------------|:---------------------------------------------------|
| `chapterList` | 目录列表规则。首字符 `-` 可使列表反序                              |
| `chapterName` | 章节名称规则                                             |
| `chapterUrl`  | 章节 URL 规则                                          |
| `isVip`       | VIP 标识。结果为 `null`、`false`、`0`、`""` 时为非 VIP         |
| `updateTime`  | 章节信息（可用 `java.timeFormat(timestamp)` 转换时间戳）        |
| `nextTocUrl`  | 目录下一页规则。支持单个 URL、URL 数组，JS 返回 `[]`/`null`/`""` 时停止 |

## 6. 正文字段 (`ruleContent`)

| 字段               | 说明                                     |
|:-----------------|:---------------------------------------|
| `content`        | 正文规则                                   |
| `nextContentUrl` | 正文下一页 URL 规则。支持单个 URL、URL 数组           |
| `sourceRegex`    | 资源正则，用于嗅探媒体资源                          |
| `webJs`          | WebView JS，用于模拟点击等操作。必须有返回值（不为空表示执行成功） |

### WebView JS (`webJs`)

用于模拟鼠标点击等操作，返回值不为空表示执行成功（否则会无限循环），返回值用于资源正则或正文。

**示例：**

```javascript
getDecode();$('#content').html();
```

### 资源正则 (`sourceRegex`)

用于嗅探 WebView 加载的媒体资源。配合章节 URL 的 `{"webView": true}` 使用。

一般写 `.*\.(mp3|mp4).*` 即可匹配常见媒体格式。

## 7. 完整 JSON 结构

```json
{
    "bookSourceUrl": "https://www.example.com",
    "bookSourceName": "示例源",
    "bookSourceGroup": "分组",
    "bookSourceType": 0,
    "bookUrlPattern": "",
    "header": "",
    "loginUrl": "",
    "searchUrl": "/search?key={{key}}&page={{page}}",
    "exploreUrl": "",
    "enabled": true,
    "enabledExplore": false,
    "weight": 0,
    "ruleSearch": {
        "bookList": "",
        "name": "",
        "author": "",
        "kind": "",
        "wordCount": "",
        "lastChapter": "",
        "intro": "",
        "coverUrl": "",
        "bookUrl": ""
    },
    "ruleExplore": {
        "bookList": "",
        "name": "",
        "author": "",
        "bookUrl": ""
    },
    "ruleBookInfo": {
        "name": "",
        "author": "",
        "kind": "",
        "intro": "",
        "coverUrl": "",
        "tocUrl": ""
    },
    "ruleToc": {
        "chapterList": "",
        "chapterName": "",
        "chapterUrl": "",
        "nextTocUrl": ""
    },
    "ruleContent": {
        "content": "",
        "nextContentUrl": "",
        "sourceRegex": "",
        "webJs": ""
    }
}
```
