# 关联书籍 (Related Books) 配置规范

书源的 `ruleBookInfo.relatedBooks` 字段允许开发者声明一组关联书籍模块，用于在书籍详情页底部展示「关联书籍」横滑轮播。支持配置多个模块，每个模块有独立的标题和数据来源，例如「同作者作品」、「读这本书的人还在读」等。

---

## 1. 字段位置

在书源编辑器的 **详情页** 选项卡中，新增了 `relatedBooks` 字段。

```
详情页 → relatedBooks
```

对应 JSON 路径：`ruleBookInfo.relatedBooks`

---

## 2. 数据结构

`relatedBooks` 是一个包含多个模块定义对象的 JSON 数组。

### 模块字段

| 字段      | 类型       | 必须 | 说明                                     |
|:--------|:---------|:---|:---------------------------------------|
| **key** | `String` | 否  | 模块唯一标识。建议使用 `[a-z0-9_]` 字符。若未提供则使用 title。 |
| **title** | `String` | 是  | 模块标题，显示在轮播上方。如「同作者作品」。                  |
| **url** | `String` | 是  | 数据接口 URL。支持模板变量替换。                      |

---

## 3. 工作原理

1. 当用户打开某本书的详情页时，如果该书源配置了 `relatedBooks`，系统会解析 JSON 数组。
2. 对每个模块，系统将 URL 中的模板变量替换为当前书籍的实际值，然后发起请求。
3. 返回的数据使用书源的 **发现规则**（`ruleExplore`）进行解析，获取书籍列表。
4. 解析后的书籍以横滑轮播的形式展示在详情页的操作按钮和书籍简介之间。
5. 当前查看的书籍会自动从结果中过滤掉，避免重复显示。
6. 如果某个模块请求失败或返回空列表，该模块会被静默跳过，不影响其他模块和详情页功能。

> **注意**：关联书籍的解析复用的是「发现」规则（`ruleExplore`），而非「详情页」规则（`ruleBookInfo`）。请确保书源的发现规则已正确配置。

---

## 4. URL 语法

URL 支持与 `exploreUrl` 相同的 JS 语法，包括 `@js:` 前缀、`<js></js>` 标签和 `{{...}}` 内嵌表达式。

### JS 上下文中的可用对象

| 对象       | 说明                   | 示例属性                                                                |
|:---------|:---------------------|:--------------------------------------------------------------------|
| `book`   | 当前书籍对象               | `book.name`, `book.author`, `book.kind`, `book.bookUrl`, `book.tocUrl`, `book.origin` |
| `source` | 当前书源对象               | `source.bookSourceUrl`, `source.getVariable()` 等                      |
| `cookie` | Cookie 存储             | `cookie.getKey(domain, key)`                                        |
| `page`   | 页码（固定为 1）            | `page`                                                              |
| `java`   | JS 扩展工具（`AnalyzeUrl`） | `java.ajax()`, `java.log()` 等                                       |

### 简单模板语法

对于简单的 URL，可以直接使用 `{{book.属性名}}` 语法，值会自动进行 URL 编码：

```
https://example.com/search?keyword={{book.author}}&name={{book.name}}
```

### `@js:` 表达式

对于需要逻辑处理的 URL，使用 `@js:` 前缀：

```
@js:"https://example.com/api/related?author=" + java.net.URLEncoder.encode(book.author, "UTF-8")
```

---

## 5. 完整 JSON 示例

```json
[
  {
    "key": "same_author",
    "title": "同作者作品",
    "url": "https://example.com/search?keyword={{book.author}}&type=author&page=1"
  },
  {
    "key": "readers_also_read",
    "title": "读这本书的人还在读",
    "url": "https://example.com/api/related?book={{book.bookUrl}}&limit=20"
  },
  {
    "key": "same_genre",
    "title": "同类推荐",
    "url": "https://example.com/category/{{book.kind}}?page=1"
  }
]
```

以上配置会在详情页底部显示三行轮播：

```
┌─────────────────────────────────────────────┐
│ [操作按钮区域]                                  │
├─────────────────────────────────────────────┤
│ 同作者作品                                      │
│ [封面1] [封面2] [封面3] [封面4] →               │
│                                             │
│ 读这本书的人还在读                                │
│ [封面1] [封面2] [封面3] [封面4] →               │
│                                             │
│ 同类推荐                                       │
│ [封面1] [封面2] [封面3] [封面4] →               │
├─────────────────────────────────────────────┤
│ [书籍简介区域]                                  │
└─────────────────────────────────────────────┘
```

---

## 6. 简化示例

如果只需要一行关联书籍，JSON 数组只包含一个元素即可：

```json
[
  {
    "title": "同作者作品",
    "url": "https://example.com/search?keyword={{book.author}}"
  }
]
```

`key` 字段为可选，未提供时自动使用 `title` 作为标识。

---

## 7. URL 示例

### 按作者查找相关书籍

```
https://example.com/search?keyword={{book.author}}&type=author
```

### 按书籍名称查找同系列

```
https://example.com/search?keyword={{book.name}}&type=related
```

### 按分类查找同类书籍

```
https://example.com/category/{{book.kind}}?page=1
```

### 使用 JS 表达式

与 `exploreUrl` 一样，URL 完整支持 `@js:` 和 `<js>` 表达式。在 JS 上下文中，`book` 对象即当前书籍，可访问 `book.name`、`book.author`、`book.kind`、`book.bookUrl`、`book.tocUrl` 等属性。

**简单拼接：**

```
@js:"https://example.com/api/related?author=" + java.net.URLEncoder.encode(book.author, "UTF-8") + "&book_id=" + book.bookUrl.split("/").pop()
```

**带条件逻辑：**

```
@js:
var base = "https://example.com/api/related";
if (book.kind && book.kind.contains("玄幻")) {
  base + "?genre=fantasy&author=" + java.net.URLEncoder.encode(book.author, "UTF-8")
} else {
  base + "?author=" + java.net.URLEncoder.encode(book.author, "UTF-8")
}
```

**带 Cookie/鉴权的请求：**

```
@js:
var ck = "sessionid=" + (String(cookie.getKey("example.com", "sessionid")) || "");
"https://example.com/api/related?book_id=" + book.bookUrl.split("/").pop() + "&cookie=" + java.net.URLEncoder.encode(ck, "UTF-8")
```

---

## 8. 显示逻辑

| 条件                                  | 行为                        |
|:------------------------------------|:-------------------------|
| `relatedBooks` 为空或未配置       | 不显示关联书籍模块               |
| JSON 格式错误                          | 静默跳过，不影响详情页其他内容        |
| 某个模块的 URL 请求失败                    | 跳过该模块，其他模块正常显示         |
| 某个模块返回空列表                         | 跳过该模块，其他模块正常显示         |
| 所有模块均无结果                          | 不显示关联书籍区域               |
| 结果中包含当前书籍                         | 自动过滤掉当前书籍               |
| 本地书籍（无书源）                         | 不显示关联书籍模块               |
| 切换书源时                              | 清空关联书籍，重新加载新源的数据       |

---

## 9. 书源编辑器配置

在书源编辑器的 **详情页** 选项卡中：

| 字段                  | 值                                                                 |
|:--------------------|:------------------------------------------------------------------|
| relatedBooks | `[{"title":"同作者作品","url":"https://example.com/search?keyword={{book.author}}"}]` |

---

## 10. 最佳实践

1. **合理设置模块数量**：建议 1-3 个模块，过多的轮播行会影响页面体验。
2. **使用有意义的标题**：标题应清晰描述推荐来源，如「同作者作品」比「推荐」更具引导性。
3. **优先使用作者或分类**：按作者查找是最常见的关联方式，能有效推荐同作者的其他作品。
4. **避免过于宽泛的查询**：如果 URL 返回的结果与当前书籍关联性不强，用户体验会下降。
5. **确保发现规则兼容**：URL 返回的数据必须能被 `ruleExplore` 正确解析。
6. **简单场景用模板，复杂场景用 JS**：简单的 `{{book.author}}` 替换直接用模板语法；需要条件判断、字符串拼接、Cookie 处理等复杂逻辑时用 `@js:` 表达式。
7. **测试边界情况**：测试作者名包含特殊字符（如 `&`、`#`、中文）时 URL 是否正常工作。模板变量会自动进行 URL 编码，但 `@js:` 表达式中需要手动调用 `java.net.URLEncoder.encode()`。
8. **控制返回数量**：建议服务端限制返回数量（如 10-20 本），过多的轮播内容会影响体验。
