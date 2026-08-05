# XPath 路径表达式

[[toc]]

XPath 是一种用于在 XML/HTML 文档中定位节点的语言。Legado 源支持使用 XPath 规则提取网页内容，以 `//`
开头的规则会自动识别为 XPath 表达式。

_注：本文所有代码均通过 Chrome(版本 123.0.6312.86) 验证_

## 1. 轴 (Axes)

XPath 规范定义了 13 种轴，用于定位元素树上相对于当前元素的节点。

| 轴                    | 说明                         | 缩写   |
|:---------------------|:---------------------------|:-----|
| `attribute`          | 元素的属性                      | `@`  |
| `self`               | 元素本身                       | `.`  |
| `parent`             | 当前元素的父元素                   | `..` |
| `child`              | 当前元素的子元素                   | —    |
| `ancestor`           | 当前元素的所有直属祖先                | —    |
| `ancestor-or-self`   | 当前元素及其所有直属祖先               | —    |
| `descendant`         | 当前元素的所有递归子元素               | —    |
| `descendant-or-self` | 当前元素及其所有递归子元素              | —    |
| `following`          | 当前元素之后出现的所有元素（无视层级，不含直属后代） | —    |
| `following-sibling`  | 当前元素之后出现的所有同级元素            | —    |
| `preceding`          | 当前元素之前出现的所有元素（无视层级，不含直属祖先） | —    |
| `preceding-sibling`  | 当前元素之前出现的所有同级元素            | —    |
| `namespace`          | 不支持                        | —    |

**语法**：`轴名::表达式`

```js
> $x('//body/ancestor-or-self::*')
< [body, html]
```

## 2. 路径格式

XPath 通过"路径表达式"（Path Expression）选取元素，形式上与文件系统路径类似。

| 概念   | 说明                                |
|:-----|:----------------------------------|
| `/`  | 路径内部分隔符                           |
| 绝对路径 | 以 `/` 起首，后跟根元素，如 `/step/step/...` |
| 相对路径 | 除绝对路径外的其他写法，如 `step/step`         |
| `.`  | 当前元素                              |
| `..` | 当前元素的父元素                          |

### 选取语法

| 符号         | 说明          |
|:-----------|:------------|
| `/`        | 选取根元素       |
| `//`       | 选取任意位置的某个元素 |
| `nodename` | 选取指定名称的元素   |
| `@`        | 选取某个属性      |

## 3. 示例

以下示例基于这段 HTML：

```html
<!DOCTYPE html>
<html>
    <head>
        <meta charset="utf-8" />
        <title>标题</title>
        <meta property="author" content="作者" />
    </head>
    <body>
        <div>
            <title lang="eng">Harry Potter</title>
            <p>29.39</p>
            <p>usd</p>
        </div>
        <div>
            <title lang="cn">Cpp高级编程</title>
            <p>39.95</p>
            <p>rmb</p>
        </div>
        <div id="list">
            <dl>
                <dd><a href="/1">一</a></dd>
                <dd><a href="/2">二</a></dd>
                <dd><a href="/3">三</a></dd>
            </dl>
        </div>
    </body>
</html>
```

| 表达式                    | 结果             | 说明            |
|:-----------------------|:---------------|:--------------|
| `$x('/')`              | `[document]`   | 选取根元素         |
| `$x('/html')`          | `[html]`       | 绝对路径选取        |
| `$x('html/head/meta')` | `[meta, meta]` | 相对路径选取        |
| `$x('//p')`            | `[p, p, p, p]` | 选取所有 p 元素     |
| `$x('html/body//a')`   | `[a, a, a]`    | 选取 body 下所有 a |
| `$x('//@lang')`        | `[lang, lang]` | 选取属性          |
| `$x('//meta/..')`      | `[head]`       | 选取父元素         |

## 4. 谓语条件 (Predicate)

谓语条件是对路径表达式的附加筛选条件，写在方括号 `[]` 中。

| 表达式                            | 结果                             | 说明      |
|:-------------------------------|:-------------------------------|:--------|
| `html/head/meta[1]`            | `<meta charset="utf-8">`       | 选取第一个   |
| `html/head/meta[last()]`       | `<meta property="author" ...>` | 选取最后一个  |
| `html/head/meta[last()-1]`     | `<meta charset="utf-8">`       | 选取倒数第二个 |
| `html/head/meta[position()>1]` | `<meta property="author" ...>` | 位置大于 1  |
| `//title[@lang]`               | 两个 title                       | 具有特定属性  |
| `//title[@lang="eng"]`         | English title                  | 属性值匹配   |
| `/html/body/div[dl]`           | `<div id="list">`              | 包含特定子元素 |
| `/html/body/div[p>35.00]`      | Cpp 高级编程 div                   | 子元素值条件  |

## 5. 通配符

| 符号   | 说明      |
|:-----|:--------|
| `*`  | 匹配任何元素  |
| `@*` | 匹配任何属性名 |

```js
$x('//*')          // 选取所有元素
$x('/*/*')         // 选取所有第二层元素
$x('//title[@*]')  // 选取所有带属性的 title
```

## 6. 多路径选择

用 `|` 合并多个表达式的选取结果：

```js
$x('//title | //a')  // 选取所有 title 和 a 元素
```

## 7. 函数

XPath 函数的参数可以是静态字符串或表达式，函数可嵌套调用。XPath 索引从 **1** 开始。

| 函数                   | 说明        | 示例                                                    |
|:---------------------|:----------|:------------------------------------------------------|
| `boolean()`          | 转换为布尔值    | `boolean(//title)` → `true`                           |
| `number()`           | 转换为数字     | `number(//p[1])` → `29.39`                            |
| `round()`            | 四舍五入      | `round(//p[1])` → `29`                                |
| `ceiling()`          | 向上取整      | `ceiling(//p[1])` → `30`                              |
| `floor()`            | 向下取整      | `floor(//p[1])` → `29`                                |
| `concat()`           | 字符串拼接     | `concat("cost:", //p[1], //p[2])` → `'cost:29.39usd'` |
| `contains()`         | 判断是否包含    | `contains(//p[1], "29.39")` → `true`                  |
| `count()`            | 统计元素个数    | `count(//p)` → `4`                                    |
| `id()`               | 根据 id 选取  | `id("list")` → `[dl#list]`                            |
| `last()`             | 同级元素集合数量  | `//p[last()]`                                         |
| `name()`             | 返回元素名     | `name(//*[@id])` → `'dl'`                             |
| `normalize-space()`  | 去除前后空白    | `normalize-space("  test  ")` → `'test'`              |
| `not()`              | 返回布尔反值    | `//title[not(@lang)]`                                 |
| `position()`         | 返回元素位置    | `//meta[position()=2]`                                |
| `starts-with()`      | 检查字符串开头   | `//title[starts-with(., "Cpp")]`                      |
| `string()`           | 转换为字符串    | `string(//p)` → `'29.39'`                             |
| `string-length()`    | 返回字符串长度   | `string-length(string(//p))` → `5`                    |
| `substring()`        | 截取字符串     | `substring(string(//p), 1, 3)` → `'29.'`              |
| `substring-after()`  | 某字符之后的字符串 | `substring-after(string(//p), ".")` → `'39'`          |
| `substring-before()` | 某字符之前的字符串 | `substring-before(string(//p), ".")` → `'29'`         |
| `sum()`              | 对数字求和     | `sum(//p[1])` → `69.34`                               |
| `translate()`        | 依次替换字符    | `translate("aabbcc", "ac", "V8")` → `'VVbb88'`        |
