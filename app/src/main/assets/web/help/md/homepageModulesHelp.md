# 首页模块 (Homepage Modules) 配置规范

书源的 `homepageModules` 字段允许开发者声明该书源在首页展示的内容模块。这些模块通过 JSON
数组进行定义，支持高度自定义的布局和数据来源。

---

## 1. 数据结构 (Data Structure)

`homepageModules` 是一个包含多个模块定义对象的 JSON 数组。

### 模块通用字段

| 字段               | 类型       | 必须 | 说明                                                    |
|:-----------------|:---------|:---|:------------------------------------------------------|
| **key**          | `String` | 是  | 模块唯一标识。建议使用 `[a-z0-9_]` 字符。用于保存用户的排序/显隐设置。            |
| **type**         | `Enum`   | 是  | 模块类型。定义了渲染方式和交互逻辑。详见 [模块类型](#2-模块类型-module-types)。    |
| **title**        | `String` | 是  | 模块默认标题。用户可在本地自定义覆盖。                                   |
| **kindTitle**    | `String` | 否  | 用于匹配书源「发现」规则中的分类标题。匹配成功后自动继承其 URL 和规则。                |
| **url**          | `String` | 否  | 显式指定数据接口 URL。优先级高于 `kindTitle`。支持变量替换。                |
| **args**         | `String` | 否  | 附加参数。在 `buttonGroup` 类型中为 JSON 数组字符串。                 |
| **layoutConfig** | `Object` | 否  | 布局配置对象，用于调整列数、行数、图标等。详见 [布局配置](#3-布局配置-layoutconfig)。 |

---

## 2. 模块类型 (Module Types)

### 列表与轮播类

| 类型 (Type) | 描述    | 特点                     |
|:----------|:------|:-----------------------|
| `banner`  | 横滑轮播图 | 适合展示高权重的精品推荐，使用大图封面。   |
| `ranking` | 排行榜列表 | 垂直列表展示，带排名序号。          |
| `card`    | 推荐卡片  | 横向滑动的卡片流，同时显示封面、标题及简介。 |

### 网格类

| 类型 (Type)      | 描述    | 特点                |
|:---------------|:------|:------------------|
| `grid`         | 标准网格  | 最常用的展示形式。支持自定义行列。 |
| `gridRanking`  | 网格排行榜 | 多行多列的排行展示。横向翻页。   |
| `infiniteGrid` | 无限网格  | 垂直滚动的网格流。无限加载。    |
| `waterfall`    | 错位瀑布流 | 垂直错位排列的书架流。无限加载。  |

### 功能类

| 类型 (Type)     | 描述    | 特点                                          |
|:--------------|:------|:--------------------------------------------|
| `buttonGroup` | 快捷按钮组 | 渲染为一组圆形/图标按钮，支持自动填充宽度与自动分列。通常用于放置常用分类或功能入口。 |

---

## 3. 布局配置 (LayoutConfig)

通过 `layoutConfig` 对象，可以精细化控制模块的表现。

| 属性 (Property) | 类型       | 适用类型                                | 默认值 | 说明                                       |
|:--------------|:---------|:------------------------------------|:----|:-----------------------------------------|
| `columns`     | `Int`    | `grid`, `waterfall`, `infiniteGrid` | 3   | 每行显示的列数。                                 |
| `icon`        | `String` | `buttonGroup`                       | -   | 按钮组的默认统一图标 URL。                          |
| `icons`       | `Object` | `buttonGroup`                       | -   | 图标映射表。例：`{"排行": "http://path/to/icon"}`。 |

---

## 4. 数据绑定逻辑 (Data Binding)

1. **自动匹配**：如果提供了 `kindTitle`，系统会遍历书源 `exploreKinds()` 返回的列表。如果某个分类的
   `title` 与之完全一致，该模块将自动使用该分类的 `url`。
2. **静态指定**：如果提供了 `url`，系统将直接请求该 URL。
3. **降级逻辑**：若 `kindTitle` 未匹配且无 `url`，模块将回退至书源的主 `exploreUrl`。

---

## 5. 完整 JSON 示例

```json
[
  {
    "key": "top_banner",
    "type": "banner",
    "title": "精品强推",
    "kindTitle": "首页推荐"
  },
  {
    "key": "quick_nav",
    "type": "buttonGroup",
    "title": "分类导航",
    "args": "[\"武侠\", \"仙侠\", \"都市\", \"历史\"]",
    "layoutConfig": {
      "icon": "https://example.com/icons/default.png",
      "icons": {
        "武侠": "https://example.com/icons/wuxia.png"
      }
    }
  },
  {
    "key": "hot_rank",
    "type": "ranking",
    "title": "热门榜单",
    "kindTitle": "排行榜",
    "layoutConfig": {
      "rows": 5
    }
  },
  {
    "key": "explore_waterfall",
    "type": "waterfall",
    "title": "发现更多",
    "kindTitle": "全部",
    "layoutConfig": {
      "columns": 2
    }
  }
]
```
