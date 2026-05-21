# 首页模块 (homepageModules)

在书源编辑页的「发现」Tab 中，可以配置 `首页模块 (JSON)` 字段，声明该书源为首页提供哪些内容模块。

## 概述

- 每个模块代表首页上的一个内容区块（如轮播图、排行榜、网格书架等）
- 一个书源可以声明多个模块（例如同时提供"热门推荐"Banner 和"周榜"排行榜）
- 用户在首页可以自由拖拽排序、隐藏/显示各个模块
- 模块内容来自书源的**发现（explore）**接口，通过 `kindTitle` 匹配分类URL

## JSON 格式

`homepageModules` 是一个 JSON 数组，每个元素定义一个模块：

```json
[
  {
    "key": "模块唯一标识",
    "type": "模块类型",
    "title": "模块标题",
    "kindTitle": "匹配的分类标题（可选）",
    "url": "覆盖分类URL（可选）",
    "args": "特殊参数（可选）",
    "layoutConfig": {
      "columns": 2,
      "rows": 3
    }
  }
]
```

### 字段说明

| 字段             | 类型        | 必填 | 说明                                                                                                    |
|----------------|-----------|----|-------------------------------------------------------------------------------------------------------|
| `key`          | `String`  | 是  | 模块在书源内的唯一标识，用于关联用户偏好。建议使用英文，如 `"hot"`, `"rank_week"`                                                  |
| `type`         | `String`  | 是  | 模块类型，可选值：`"banner"`, `"ranking"`, `"gridRanking"`, `"grid"`, `"card"`, `"waterfall"`, `"buttonGroup"` |
| `title`        | `String`  | 是  | 模块标题，会在首页模块头部展示                                                                                       |
| `kindTitle`    | `String?` | 否  | 用于匹配该书源「发现」中的分类标题。不填则使用默认 `exploreUrl`                                                                |
| `url`          | `String?` | 否  | 显式指定该模块的 URL，优先级高于 `kindTitle` 匹配到的 URL                                                               |
| `args`         | `String?` | 否  | 模块特有参数。在 `buttonGroup` 中为包含分类标题的 JSON 数组字符串                                                           |
| `layoutConfig` | `Object?` | 否  | 布局配置对象。支持 `columns` (列数), `rows` (行数)                                                                 |

## 模块类型

### banner — 横滑轮播图

适合展示热门推荐、本周强推等内容。以大图封面横向滑动展示。

```json
{
  "key": "hot_banner",
  "type": "banner",
  "title": "热门推荐",
  "kindTitle": "热门",
  "displayCount": 6
}
```

### ranking — 排行榜

带排名序号的列表。前三名高亮为橙色，其余为灰色。默认折叠显示前 5 本，末尾有"展开更多"按钮。

```json
{
  "key": "week_rank",
  "type": "ranking",
  "title": "周排行榜",
  "kindTitle": "周榜",
  "displayCount": 10
}
```

### gridRanking — 网格排行

4×4 列式排列（可通过 `layoutConfig.rows` 修改行数），每页多本书，可横向翻页。封面较小，仅显示书名和作者。适合作为首页入口展示大量书籍。

```json
{
  "key": "all_grid",
  "type": "gridRanking",
  "title": "全部分类",
  "kindTitle": "全部分类",
  "layoutConfig": {
    "rows": 4
  }
}
```

### grid — 网格书架

网格布局，适合展示分类书单。支持自定义行列。

- `columns`: 默认 3
- `rows`: 默认 2。若设置为 `0`，则显示为平铺列表并支持下拉加载更多。

```json
{
  "key": "scifi_grid",
  "type": "grid",
  "title": "科幻精选",
  "kindTitle": "科幻",
  "layoutConfig": {
    "columns": 3,
    "rows": 2
  }
}
```

### card — 推荐卡片

大图横向滑动卡片，展示封面 + 书名 + 简介。适合需要更多信息展示的场景。

```json
{
  "key": "editor_pick",
  "type": "card",
  "title": "编辑推荐",
  "kindTitle": "编辑推荐"
}
```

### waterfall — 错位瀑布流

瀑布流布局，展示封面 + 书名 + 简介。支持 `layoutConfig.columns` 自定义列数（默认 2）。支持无限加载更多。

```json
{
  "key": "hot_wf",
  "type": "waterfall",
  "title": "大家都在看",
  "kindTitle": "热门",
  "layoutConfig": {
    "columns": 2
  }
}
```

### buttonGroup — 按钮组

显示为网格排列的分类按钮。适合放置常用的分类或动作入口。

- **布局特性**：自动平衡每行按钮数量（例如 6 个按钮显示为 3+3，8 个显示为 4+4）。每行最多 5 个。
- **args**: 可选。由分类标题组成的 JSON 数组字符串，如 `["排行", "分类", "完本"]`。若不填则默认显示该书源前
  5 个分类。
- **layoutConfig**:
    - `icon`: 全局默认图标。支持网络图片 URL。
    - `icons`: 图标映射对象。以分类标题为键，网络图片 URL 为值。

```json
{
  "key": "entry_buttons",
  "type": "buttonGroup",
  "title": "快捷入口",
  "args": "[\"排行\", \"分类\", \"我的\"]",
  "layoutConfig": {
    "icon": "https://example.com/default.png",
    "icons": {
      "排行": "https://example.com/rank.png",
      "我的": "https://example.com/my.png"
    }
  }
}
```

## kindTitle 匹配规则

模块加载时，系统会根据 `kindTitle` 在书源的「发现」分类列表中进行匹配：

1. 在 `exploreKinds()`（即发现页的分类列表）中查找 `title` 等于 `kindTitle` 的分类
2. 如果匹配成功，使用该分类的 `url` 来加载数据
3. 如果匹配失败，或 `kindTitle` 为空，则使用书源的默认 `exploreUrl`
4. 如果两个 URL 都没有，该模块将显示加载错误

**建议**：确保 `kindTitle` 的值与发现页分类的标题**完全一致**（包括大小写和标点）。

## 完整示例

以下是一个书源配置了多种模块的完整 JSON：

```json
[
  {
    "key": "hot",
    "type": "banner",
    "title": "热门推荐",
    "kindTitle": "热门",
    "displayCount": 6
  },
  {
    "key": "rank_week",
    "type": "ranking",
    "title": "周榜",
    "kindTitle": "周排行",
    "displayCount": 10
  },
  {
    "key": "rank_month",
    "type": "ranking",
    "title": "月榜",
    "kindTitle": "月排行",
    "displayCount": 10
  },
  {
    "key": "scifi",
    "type": "grid",
    "title": "科幻精选",
    "kindTitle": "科幻",
    "displayCount": 6
  },
  {
    "key": "new_book",
    "type": "card",
    "title": "新书上架",
    "kindTitle": "新书",
    "displayCount": 8
  }
]
```

每个 `key` 必须唯一，系统会按 JSON 数组中的声明顺序创建模块标识。

## 用户体验

- **发现模块**：首页会自动出现配置了 `homepageModules` 的已启用书源所声明的模块
- **拖拽排序**：长按模块标题旁的拖拽手柄可调整顺序
- **隐藏模块**：编辑模式下可隐藏不需要的模块
- **下拉刷新**：刷新后所有模块重新加载数据
- **点击书籍**：跳转到书籍详情页
- **点击模块标题**：跳转到该书源的完整发现页

## 注意事项

- JSON 格式必须**严格有效**，建议使用在线 JSON 校验工具检查
- 如果 JSON 解析失败，该书源的所有模块将被静默跳过
- 模块使用的图片从书籍封面（`coverUrl`）获取，请确保发现规则正确提取了封面
- `homepageModules` 为空的旧书源不会报错，也不会有首页模块展示
- 同一书源可配置多个同类型模块（如多个排行榜），只需 `key` 不同即可
- 模块标识全局唯一 ID 格式为 `"{setId}::{书源URL}::{key}"`，其中 `setId` 用于区分模块所属的集（书源集或自定义集）。
