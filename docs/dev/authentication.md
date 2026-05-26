# 认证与登录 (Authentication)

[[toc]]

Legado 支持通过 CookieJar、登录 UI 表单和登录 URL 脚本三种方式实现网站认证。

## 1. CookieJar

启用后自动保存每次响应头中的 `Set-Cookie` 值，适用于需要 session 的网站（如验证码图片）。

在源编辑器中勾选「启用 CookieJar」即可，无需额外配置。

## 2. 登录 UI (Login UI)

通过 JSON 数组定义登录表单，替代内置 WebView 登录方式。

### 字段定义

| 字段       | 类型       | 必须 | 说明                     |
|:---------|:---------|:---|:-----------------------|
| `name`   | `String` | 是  | 字段名称，显示为输入框标签          |
| `type`   | `String` | 是  | 字段类型                   |
| `action` | `String` | 否  | 仅 `button` 类型：点击时执行的动作 |
| `style`  | `Object` | 否  | Flexbox 布局配置           |

### type 取值

| 值          | 说明                                     |
|:-----------|:---------------------------------------|
| `text`     | 文本输入框                                  |
| `password` | 密码输入框                                  |
| `button`   | 可点击按钮，`action` 为 URL 时打开浏览器，为函数名时调用 JS |

### JSON 示例

```json
[
    { "name": "telephone", "type": "text" },
    { "name": "password", "type": "password" },
    {
        "name": "注册",
        "type": "button",
        "action": "http://www.example.com/register"
    },
    {
        "name": "获取验证码",
        "type": "button",
        "action": "getVerificationCode()",
        "style": {
            "layout_flexGrow": 0,
            "layout_flexShrink": 1,
            "layout_alignSelf": "auto",
            "layout_flexBasisPercent": -1,
            "layout_wrapBefore": false
        }
    }
]
```

### style 布局属性

| 属性                        | 类型        | 说明             |
|:--------------------------|:----------|:---------------|
| `layout_flexGrow`         | `Int`     | 弹性增长比例         |
| `layout_flexShrink`       | `Int`     | 弹性收缩比例         |
| `layout_alignSelf`        | `String`  | 交叉轴对齐方式        |
| `layout_flexBasisPercent` | `Int`     | 基础尺寸百分比，-1 为自动 |
| `layout_wrapBefore`       | `Boolean` | 是否强制换行         |

::: tip 版本变更
从版本 20221113 起，按钮支持调用「登录 URL」规则中的函数，必须实现 `login` 函数。
:::

## 3. 登录 URL (Login URL)

可填写登录链接或实现登录逻辑的 JavaScript。配合登录 UI 使用时，需要实现 `login` 函数。

### JS 示例

```js
function login() {
    java.log("模拟登录请求");
    java.log(source.getLoginInfoMap());
}
function getVerificationCode() {
    java.log("登录UI按钮：获取到手机号码" + result.get("telephone"))
}
```

### 获取登录信息

在登录按钮函数和 `login` 函数中，可通过以下方式获取用户输入：

```js
// 登录按钮函数中
result.get("telephone")

// login 函数中
source.getLoginInfo()
source.getLoginInfoMap().get("telephone")
```

### source 登录相关方法

| 方法                             | 返回值       | 说明        |
|:-------------------------------|:----------|:----------|
| `login()`                      | —         | 执行登录      |
| `getHeaderMap(hasLoginHeader)` | `Map`     | 获取请求头     |
| `getLoginHeader()`             | `String?` | 获取登录头字符串  |
| `getLoginHeaderMap()`          | `Map?`    | 获取登录头 Map |
| `putLoginHeader(header)`       | —         | 保存登录头     |
| `removeLoginHeader()`          | —         | 清除登录头     |
| `setVariable(variable)`        | —         | 设置源变量     |
| `getVariable()`                | `String?` | 获取源变量     |

### AnalyzeUrl 函数

以下函数仅在「登录检查 JS」规则中有效：

| 方法                                                 | 返回值           | 说明                  |
|:---------------------------------------------------|:--------------|:--------------------|
| `initUrl()`                                        | —             | 重新解析 URL            |
| `getHeaderMap().putAll(source.getHeaderMap(true))` | —             | 重新设置登录头             |
| `getStrResponse(jsStr, sourceRegex)`               | `StrResponse` | 返回文本类型的访问结果         |
| `getResponse()`                                    | `Response`    | 返回 Response 类型的访问结果 |

## 4. 登录检查 JS (Login Check)

在源的「登录检查 JS」字段中填写 JavaScript，用于判断当前登录状态。返回 `true` 表示已登录，返回其他值会触发重新登录流程。
