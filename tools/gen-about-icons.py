#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 Android vector drawable 生成 `:feature:about` 的 `AboutIcons.kt`（M5-1c-2）。

    python tools/gen-about-icons.py

## 为什么需要它

Android vector XML **不能**进 composeResources：CMP 只在 Android 侧认 `.xml` 矢量，desktop 会缺图。
所以「关于」页用的四个图形（`ic_web_outline` / `ic_github` / `ic_import` / `ic_launcher_foreground`）
必须用 `androidx.compose.ui.graphics.vector.addPathNodes(String)`（**commonMain** API）+ `ImageVector.Builder`
**逐字重建**。手抄 200 行 pathData 不可维护，故用本脚本生成。

## 做了哪三处机械转换

1. `#RRGGBB` → `Color(0xFFRRGGBB)`；`#00000000` 填充 → 不写 `fill`（那两个图形只靠描边上色）；
2. `@android:color/white` → `Color.White`（真正的着色由 `Icon(tint = …)` 承担）；
3. ⚠️ **Compose 1.12 的 `ImageVector.Builder.addGroup` 不再收 content lambda**：它把分组
   `nodes.push(group)` 压进 Builder 的内部节点栈，之后添加的 `addPath` 都归属该分组，直到
   `clearGroup()` 才弹栈。所以生成的是 `addGroup(...)` 后**继续链式** `.addPath(...)`——
   写成 `addGroup(...) { ... }` 会报 `Too many arguments` / 尾随 lambda 被当成
   `clipPathData: List<PathNode>`（症状：`Unresolved reference 'addPath'`）。分组只有一层且
   `build()` 紧随其后，故不需要 `clearGroup()`。

## 换成别的图标时

改 `ICONS` 与 `OUT` 即可——三处转换对任何 Android vector drawable 都成立。改这些 `pathData`
等于改**用户可见图形**，务必连同本脚本一起改（`AboutIcons.kt` 的 KDoc 里也写了这句）。
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

NS = '{http://schemas.android.com/apk/res/android}'
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(
    ROOT, 'feature', 'about', 'src', 'commonMain', 'kotlin',
    'io', 'legado', 'app', 'feature', 'about', 'AboutIcons.kt',
)

ICONS = [
    ('WebOutline', 'app/src/main/res/drawable/ic_web_outline.xml'),
    ('GitHub', 'app/src/main/res/drawable/ic_github.xml'),
    ('Import', 'app/src/main/res/drawable/ic_import.xml'),
    ('LauncherForeground', 'app/src/main/res/drawable/ic_launcher_foreground.xml'),
]


def dim(v):
    m = re.match(r'^([\d.]+)dp$', v)
    assert m, v
    return '%sf' % m.group(1) if '.' in m.group(1) else '%s.dp' % m.group(1)


def floatlit(v):
    f = float(v)
    return '%sf' % repr(f).rstrip('0').rstrip('.') if f != int(f) else '%sf' % repr(f)


def color(v):
    m = re.match(r'^#([0-9A-Fa-f]{6,8})$', v)
    if m:
        hexs = m.group(1).upper()
        if len(hexs) == 6:
            hexs = 'FF' + hexs
        if hexs == '00000000':
            return None
        return 'Color(0x%s)' % hexs
    if v == '@android:color/white':
        return 'Color.White'
    raise AssertionError('unknown color %s' % v)


def emit_path(p, indent, dot=True):
    data = p.get(NS + 'pathData')
    fill = color(p.get(NS + 'fillColor', '#FF000000'))
    stroke = p.get(NS + 'strokeColor')
    width = p.get(NS + 'strokeWidth')
    head = '.' if dot else ''
    lines = []
    lines.append('%s%saddPath(' % (indent, head))
    lines.append('%s    pathData = addPathNodes(' % indent)
    # wrap the long path data string across lines for readability
    chunk = 96
    parts = [data[i:i + chunk] for i in range(0, len(data), chunk)]
    if len(parts) == 1:
        lines.append('%s        "%s",' % (indent, parts[0]))
    else:
        lines.append('%s        "%s" +' % (indent, parts[0]))
        for pt in parts[1:-1]:
            lines.append('%s            "%s" +' % (indent, pt))
        lines.append('%s            "%s",' % (indent, parts[-1]))
    lines.append('%s    ),' % indent)
    if fill is not None:
        lines.append('%s    fill = SolidColor(%s),' % (indent, fill))
    if stroke is not None:
        lines.append('%s    stroke = SolidColor(%s),' % (indent, color(stroke)))
        lines.append('%s    strokeLineWidth = %s,' % (indent, floatlit(width)))
    lines.append('%s)' % indent)
    return lines


def build(name, rel):
    root = ET.parse('%s/%s' % (ROOT, rel)).getroot()
    out = []
    out.append('    /** `%s`（`%s`）。 */' % (rel.split('/')[-1], rel))
    out.append('    val %s: ImageVector by lazy {' % name)
    out.append('        ImageVector.Builder(')
    out.append('            name = "About%s",' % name)
    out.append('            defaultWidth = %s,' % dim(root.get(NS + 'width')))
    out.append('            defaultHeight = %s,' % dim(root.get(NS + 'height')))
    out.append('            viewportWidth = %sf,' % root.get(NS + 'viewportWidth'))
    out.append('            viewportHeight = %sf,' % root.get(NS + 'viewportHeight'))
    out.append('        )')
    groups = root.findall('group')
    if not groups:
        # `Builder.addPath(...)` 返回 Builder 本身，直接链式即可。
        for p in root.findall('path'):
            out.extend(emit_path(p, '            '))
    else:
        for g in groups:
            out.append('            .addGroup(')
            out.append('                name = "%s",' % (g.get(NS + 'name') or 'group'))
            out.append('                rotate = %s,' % floatlit(g.get(NS + 'rotation', '0')))
            out.append('                pivotX = %s,' % floatlit(g.get(NS + 'pivotX', '0')))
            out.append('                pivotY = %s,' % floatlit(g.get(NS + 'pivotY', '0')))
            out.append('                scaleX = %s,' % floatlit(g.get(NS + 'scaleX', '1')))
            out.append('                scaleY = %s,' % floatlit(g.get(NS + 'scaleY', '1')))
            out.append('                translationX = %s,' % floatlit(g.get(NS + 'translateX', '0')))
            out.append('                translationY = %s,' % floatlit(g.get(NS + 'translateY', '0')))
            out.append('            )')
            # ⚠️ Compose 1.12 的 `addGroup` **不再收 content lambda**，而是把分组压进
            # Builder 内部的节点栈（源码：`nodes.push(group)`），直到 `clearGroup()` 才弹栈。
            # 因此后续的 `addPath` 天然归属该分组，这里只需继续链式（不能再写 `) { ... }`）。
            for p in g.findall('path'):
                out.extend(emit_path(p, '            '))
    out.append('            .build()')
    out.append('    }')
    out.append('')
    return out


HEADER = '''package io.legado.app.feature.about

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 「关于」页用到的四个图形（M5-1c）。
 *
 * **为什么不用 `Icons.Default.*` / `AppIcons.*` 了事**：这四个里有三个**没有图形等价的
 * Material 图标**——GitHub 标记、本应用的应用图标（`ic_launcher_foreground`），以及
 * Material Symbols 960 viewport 的 `web_outline` / `import`。已有四个 CMP Feature 的做法是
 * 「有等价物就换 `Icons`」，但这里换掉就是**用户可见的改变**（GitHub 变通用链接图标、
 * 应用图标变成别人的图标），所以按原 `pathData` **逐字重建**成 `ImageVector`：
 * 图形、viewport、分组变换、描边宽度与填充色全部照抄，渲染结果与 `painterResource` 一致。
 *
 * **为什么可行且该这么做**：`androidx.compose.ui.graphics.vector.addPathNodes(String)` 是
 * **commonMain** 的 API（本片实测：`:feature:about:compileKotlinDesktop` 通过），所以 SVG
 * path 能跨平台解析；而 Android vector XML **本身不能**进 `composeResources`——CMP 只在
 * Android 侧认 `.xml` 矢量，desktop 会缺图。写成 `ImageVector` 则两边同一份代码。
 *
 * ⚠️ **Compose 1.12 的 `ImageVector.Builder.addGroup` 不再收 content lambda**：它把分组
 * `nodes.push(group)` 压进 Builder 的内部节点栈，之后添加的 `addPath` 都归属该分组，
 * 直到 `clearGroup()` 才弹栈。所以这里的写法是 `addGroup(...)` 之后**继续链式** `addPath(...)`，
 * 不能写成 `addGroup(...) { ... }`（实测报 `Too many arguments` / 尾随 lambda 被当成
 * `clipPathData: List<PathNode>`）。本文件的分组只有一层、且 `build()` 紧跟在最后，故不需要
 * `clearGroup()`。
 *
 * 本文件由 `tools/gen-about-icons.py` 从四个 drawable 逐字段生成，只做了三处机械转换：
 * `#RRGGBB` → `Color(0xFFRRGGBB)`；`@android:color/white` → `Color.White`（真正的着色由
 * `Icon(tint = ...)` 承担，与迁移前 `android:tint="?attr/colorControlNormal"` 同理）；
 * `#00000000` 填充 → 不写 `fill`（那两个图形只靠描边上色）。
 *
 * ⚠️ 改动这几个 `pathData` 等于改用户可见图形，请连同上面的生成脚本一起改。
 */
internal object AboutIcons {

'''

text = HEADER
for name, rel in ICONS:
    text += '\n'.join(build(name, rel)) + '\n'
text += '}\n'

open(OUT, 'wb').write(text.replace('\r\n', '\n').replace('\n', '\r\n').encode('utf-8'))
b = open(OUT, 'rb').read()
print('written %d bytes, %d lines, crlf=%d bareLF=%d' % (
    len(b), b.decode('utf-8').count('\n'), b.count(b'\r\n'), b.count(b'\n') - b.count(b'\r\n')))
