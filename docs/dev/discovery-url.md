# 发现 URL (Discovery URL) 配置规范

[[toc]]

源的「发现 URL」字段支持通过 JSON 数组定义多个发现页入口，每个入口包含标题、URL
和可选的布局样式。这使得一个源可以提供多个分类或榜单的快捷入口。

## 1. 字段位置

在源编辑器的 **发现** 选项卡中：

```
发现 → 发现 URL
```

## 2. 数据结构

发现 URL 是一个包含多个入口对象的 JSON 数组。

### 入口字段

| 字段      | 类型       | 必须 | 说明                        |
|:--------|:---------|:---|:--------------------------|
| `title` | `String` | 是  | 入口标题，显示在发现页列表中            |
| `url`   | `String` | 是  | 目标 URL，支持 `{{page}}` 分页变量 |
| `style` | `Object` | 否  | Flexbox 布局配置              |

### style 布局属性

| 属性                        | 类型        | 说明             |
|:--------------------------|:----------|:---------------|
| `layout_flexGrow`         | `Int`     | 弹性增长比例         |
| `layout_flexShrink`       | `Int`     | 弹性收缩比例         |
| `layout_alignSelf`        | `String`  | 交叉轴对齐方式        |
| `layout_flexBasisPercent` | `Int`     | 基础尺寸百分比，-1 为自动 |
| `layout_wrapBefore`       | `Boolean` | 是否强制换行         |

## 3. 完整 JSON 示例

```json
[
  {
    "title": "热门榜",
    "url": "https://example.com/rank/hot?page={{page}}"
  },
  {
    "title": "新书榜",
    "url": "https://example.com/rank/new?page={{page}}"
  },
  {
    "title": "完本榜",
    "url": "https://example.com/rank/finish?page={{page}}"
  }
]
```

## 4. 两种格式

发现 URL 支持两种填写格式：

| 格式      | 说明                                       | 适用场景      |
|:--------|:-----------------------------------------|:----------|
| 字符串     | 直接填写 URL，如 `https://example.com/explore` | 单个发现页     |
| JSON 数组 | 定义多个入口                                   | 多个分类/榜单入口 |

## 5. 分页变量

URL 中可使用 `{{page}}` 变量实现分页，Legado 会自动递增页码：

```
https://example.com/rank?page={{page}}
```

## 6. 与首页模块的关系

发现 URL 定义的入口可被 [首页模块](../spec/homepage-modules) 的 `kindTitle` 字段引用。当首页模块的
`kindTitle` 与某个发现入口的 `title` 完全匹配时，模块会自动使用该入口的 URL 和规则。
