# 源示例 (Source Examples)

[[toc]]

本文档提供三种典型源的完整 JSON 示例，分别演示 CSS+正则、XPath+正则、JSONPath 的用法。

## 1. CSS + 正则

使用 JSOUP CSS 选择器和正则表达式的源示例。

```json
{
    "bookSourceComment": "",
    "bookSourceGroup": "CSS; 正则",
    "bookSourceName": "示例源",
    "bookSourceType": 0,
    "bookSourceUrl": "https://www.example.com",
    "bookUrlPattern": "",
    "customOrder": 0,
    "enabled": true,
    "enabledExplore": false,
    "exploreUrl": "",
    "lastUpdateTime": 0,
    "loginUrl": "",
    "ruleBookInfo": {
        "author": "##:author\"[^\"]+\"([^\"]*)##$1###",
        "coverUrl": "##og:image\"[^\"]+\"([^\"]*)##$1###",
        "intro": "##:description\"[^\"]+\"([\\w\\W]*?)\"/##$1###",
        "kind": "##:category\"[^\"]+\"([^\"]*)##$1###",
        "lastChapter": "##_chapter_name\"[^\"]+\"([^\"]*)##$1###",
        "name": "##:book_name\"[^\"]+\"([^\"]*)##$1###",
        "tocUrl": ""
    },
    "ruleContent": {
        "content": "@css:.chapter-content p@textNodes##广告文字|本站声明.*|<!\\[CDATA\\[|\\]\\]>",
        "nextContentUrl": ""
    },
    "ruleExplore": {},
    "ruleSearch": {
        "author": "@css:p:eq(2)>a@text",
        "bookList": "@css:li.clearfix",
        "bookUrl": "@css:.name>a@href",
        "coverUrl": "@css:img@src",
        "intro": "@css:.note.clearfix p@text",
        "kind": "@css:.note_text,p:eq(4)@text",
        "lastChapter": "@css:p:eq(3)@text",
        "name": "@css:.name@text"
    },
    "ruleToc": {
        "chapterList": "-:<li><a[^\"]+\"([^\"]*)\">([^<]*)",
        "chapterName": "$2",
        "chapterUrl": "$1",
        "nextTocUrl": ""
    },
    "searchUrl": "/search?q={{key}}&page={{page}}",
    "weight": 0
}
```

**要点：**

- 详情页规则使用 OnlyOne 正则（`##...###`）从 meta 标签提取信息
- 正文规则使用 CSS 选择器取文本节点，后接净化正则移除广告
- 目录规则使用 AllInOne 正则（`:` 开头），`-` 前缀使列表倒序

## 2. XPath + 正则

使用 XPath 和正则表达式的源示例。

```json
{
    "bookSourceComment": "",
    "bookSourceGroup": "XPath; 正则",
    "bookSourceName": "示例移动源",
    "bookSourceType": 0,
    "bookSourceUrl": "https://m.example.com",
    "bookUrlPattern": "",
    "customOrder": 0,
    "enabled": true,
    "enabledExplore": false,
    "exploreUrl": "",
    "lastUpdateTime": 0,
    "loginUrl": "",
    "ruleBookInfo": {
        "author": "//*[@property=\"og:novel:author\"]/@content",
        "coverUrl": "//*[@property=\"og:image\"]/@content",
        "intro": "//*[@property=\"og:description\"]/@content",
        "kind": "//*[@property=\"og:novel:category\"]/@content",
        "lastChapter": "//*[@id=\"latest-chapter\"]//li[1]/a/text()",
        "name": "//*[@property=\"og:novel:book_name\"]/@content",
        "tocUrl": "//a[text()=\"阅读\"]/@href"
    },
    "ruleContent": {
        "content": "//*[@id=\"content\"]",
        "nextContentUrl": ""
    },
    "ruleExplore": {},
    "ruleSearch": {
        "author": "//dd[2]/text()",
        "bookList": "//*[@id=\"search-result\"]/dl",
        "bookUrl": "//dt/a/@href",
        "coverUrl": "//img/@src",
        "kind": "//dd[2]/span/text()",
        "lastChapter": "",
        "name": "//h3/a/text()"
    },
    "ruleToc": {
        "chapterList": ":href=\"(/read[^\"]*html)\">([^<]*)",
        "chapterName": "$2",
        "chapterUrl": "$1",
        "nextTocUrl": "//*[@id=\"chapter-list\"]/*[position()>1]/@value"
    },
    "searchUrl": "/search,{\n  \"method\": \"POST\",\n  \"body\": \"q={{key}}\"\n}",
    "weight": 0
}
```

**要点：**

- 详情页使用 XPath 从 `og:novel` meta 标签提取信息
- 目录使用 AllInOne 正则提取链接和标题
- 目录下一页使用 XPath 的 `position()` 谓语条件
- 搜索使用 POST 方法

## 3. JSONPath

使用 JSON API 的源示例。

```json
{
    "bookSourceComment": "",
    "bookSourceGroup": "JSON",
    "bookSourceName": "示例 API 源",
    "bookSourceType": 0,
    "bookSourceUrl": "http://api.example.com",
    "customOrder": 0,
    "enabled": true,
    "enabledExplore": false,
    "header": "{\n  \"User-Agent\": \"Mozilla/5.0\"\n}",
    "lastUpdateTime": 0,
    "ruleBookInfo": {},
    "ruleContent": {
        "content": "$.chapter.body"
    },
    "ruleExplore": {},
    "ruleSearch": {
        "author": "$.author",
        "bookList": "$..books[*]",
        "bookUrl": "/book/detail?id={$._id}",
        "coverUrl": "$.cover",
        "intro": "$.shortIntro",
        "kind": "$.minorCate",
        "lastChapter": "$.lastChapter",
        "name": "$.title"
    },
    "ruleToc": {
        "chapterList": "$.chapterInfo.chapters.[*]",
        "chapterName": "$.title",
        "chapterUrl": "$.link"
    },
    "searchUrl": "/book/search?query={{key}}&start={{(page-1)*20}}&limit=20",
    "weight": 0
}
```

**要点：**

- 详情页为空（`ruleBookInfo: {}`），直接从搜索结果获取信息
- 目录 URL 使用 JSONPath 表达式 `{$._id}` 拼接
- 书列表使用递归查找 `$..books[*]`
- 搜索 URL 使用 `{{(page-1)*20}}` 计算偏移量

## 4. 调试技巧

### JS 错误处理

使用 try-catch 包裹 JS 逻辑，方便调试：

```javascript
(function(result){
    try{
        // 处理 result
        return result;
    }
    catch(e){
        return "" + e;  // 返回错误信息
    }
})(result);
```

### 调试入口

| 调试项 | 输入示例                                              |
|:----|:--------------------------------------------------|
| 搜索  | `系统`                                              |
| 发现  | `排行榜::https://www.example.com/rank?page={{page}}` |
| 详情页 | `https://www.example.com/book/12345`              |
| 目录页 | `++https://www.example.com/read/12345`            |
| 正文页 | `--https://www.example.com/chapter/12345/67890`   |

详细调试方法参见 [源调试](./debug)。
