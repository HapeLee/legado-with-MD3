package io.legado.app.feature.about

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

    /** `ic_web_outline.xml`（`app/src/main/res/drawable/ic_web_outline.xml`）。 */
    val WebOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "AboutWebOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        )
            .addPath(
                pathData = addPathNodes(
                    "M480,880Q397,880 324,848.5Q251,817 197,763Q143,709 111.5,636Q80,563 80,480Q80,397 111.5,324Q143," +
                        "251 197,197Q251,143 324,111.5Q397,80 480,80Q563,80 636,111.5Q709,143 763,197Q817,251 848.5,324Q8" +
                        "80,397 880,480Q880,563 848.5,636Q817,709 763,763Q709,817 636,848.5Q563,880 480,880ZM440,798L440," +
                        "720Q407,720 383.5,696.5Q360,673 360,640L360,600L168,408Q165,426 162.5,444Q160,462 160,480Q160,60" +
                        "1 239.5,692Q319,783 440,798ZM716,696Q757,651 778.5,595.5Q800,540 800,480Q800,382 745.5,301Q691,2" +
                        "20 600,184L600,200Q600,233 576.5,256.5Q553,280 520,280L440,280L440,360Q440,377 428.5,388.5Q417,4" +
                        "00 400,400L320,400L320,480L560,480Q577,480 588.5,491.5Q600,503 600,520L600,640L640,640Q666,640 6" +
                        "87,655.5Q708,671 716,696Z",
                ),
                fill = SolidColor(Color.White),
            )
            .build()
    }

    /** `ic_github.xml`（`app/src/main/res/drawable/ic_github.xml`）。 */
    val GitHub: ImageVector by lazy {
        ImageVector.Builder(
            name = "AboutGitHub",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .addPath(
                pathData = addPathNodes(
                    "M12 1C5.923 1 1 5.923 1 12c0 4.867 3.149 8.979 7.521 10.436.55.096.756-.233.756-.522 0-.262-.013" +
                        "-1.128-.013-2.049-2.764.509-3.479-.674-3.699-1.292-.124-.317-.66-1.293-1.127-1.554-.385-.207-.93" +
                        "6-.715-.014-.729.866-.014 1.485.797 1.691 1.128.99 1.663 2.571 1.196 3.204.907.096-.715.385-1.19" +
                        "6.701-1.471-2.448-.275-5.005-1.224-5.005-5.432 0-1.196.426-2.186 1.128-2.956-.111-.275-.496-1.40" +
                        "2.11-2.915 0 0 .921-.288 3.024 1.128a10.193 10.193 0 0 1 2.75-.371c.936 0 1.871.123 2.75.371 2.1" +
                        "04-1.43 3.025-1.128 3.025-1.128.605 1.513.221 2.64.111 2.915.701.77 1.127 1.747 1.127 2.956 0 4." +
                        "222-2.571 5.157-5.019 5.432.399.344.743 1.004.743 2.035 0 1.471-.014 2.654-.014 3.025 0 .289.206" +
                        ".632.756.522C19.851 20.979 23 16.854 23 12c0-6.077-4.922-11-11-11Z",
                ),
                fill = SolidColor(Color.White),
            )
            .build()
    }

    /** `ic_import.xml`（`app/src/main/res/drawable/ic_import.xml`）。 */
    val Import: ImageVector by lazy {
        ImageVector.Builder(
            name = "AboutImport",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        )
            .addPath(
                pathData = addPathNodes(
                    "M260,800Q169,800 104.5,737Q40,674 40,583Q40,505 87,444Q134,383 210,366Q227,294 295,229Q363,164 4" +
                        "40,164Q473,164 496.5,187.5Q520,211 520,244L520,486L584,424L640,480L480,640L320,480L376,424L440,4" +
                        "86L440,244Q364,258 322,317.5Q280,377 280,440L260,440Q202,440 161,481Q120,522 120,580Q120,638 161" +
                        ",679Q202,720 260,720L740,720Q782,720 811,691Q840,662 840,620Q840,578 811,549Q782,520 740,520L680" +
                        ",520L680,440Q680,392 658,350.5Q636,309 600,280L600,187Q674,222 717,290.5Q760,359 760,440L760,440" +
                        "L760,440Q829,448 874.5,499.5Q920,551 920,620Q920,695 867.5,747.5Q815,800 740,800L260,800ZM480,44" +
                        "2Q480,442 480,442Q480,442 480,442L480,442Q480,442 480,442Q480,442 480,442L480,442Q480,442 480,44" +
                        "2Q480,442 480,442L480,442Q480,442 480,442Q480,442 480,442Q480,442 480,442Q480,442 480,442L480,44" +
                        "2Q480,442 480,442Q480,442 480,442Q480,442 480,442Q480,442 480,442L480,442L480,442Q480,442 480,44" +
                        "2Q480,442 480,442Z",
                ),
                fill = SolidColor(Color.White),
            )
            .build()
    }

    /** `ic_launcher_foreground.xml`（`app/src/main/res/drawable/ic_launcher_foreground.xml`）。 */
    val LauncherForeground: ImageVector by lazy {
        ImageVector.Builder(
            name = "AboutLauncherForeground",
            defaultWidth = 108.dp,
            defaultHeight = 108.dp,
            viewportWidth = 370f,
            viewportHeight = 370f,
        )
            .addGroup(
                name = "group",
                rotate = 0.0f,
                pivotX = 0.0f,
                pivotY = 0.0f,
                scaleX = 0.5011111f,
                scaleY = 0.5011111f,
                translationX = 92.29444f,
                translationY = 92.29444f,
            )
            .addPath(
                pathData = addPathNodes(
                    "M185,185m-155,0a155,155 0,1 1,310 0a155,155 0,1 1,-310 0",
                ),
                fill = SolidColor(Color(0xFFEAEAEA)),
            )
            .addPath(
                pathData = addPathNodes(
                    "M282.03,102.39C255.76,125.1 231.53,164.6 228.16,170.19C227.9,170.63 227.82,171.11 227.95,171.6C2" +
                        "33,190.65 270.65,236.1 288.9,256.43C289.48,257.08 290.39,257.25 291.18,256.9L310.69,248.17C311.4" +
                        ",247.85 311.86,247.13 311.87,246.35C312.84,165.86 297.04,120.01 290.34,104.26C288.9,100.89 284.8" +
                        ",99.99 282.03,102.39Z",
                ),
                fill = SolidColor(Color(0xFF656688)),
            )
            .addPath(
                pathData = addPathNodes(
                    "M76.9,225.49C70.96,199.23 62.48,141.34 71.93,110.92C72.84,107.99 75.98,106.56 78.89,107.54C92.9," +
                        "112.25 133.46,126.86 172.04,150.18C172.04,150.18 202.32,146.09 234.97,157.66C235.65,157.9 236.39" +
                        ",157.79 236.98,157.37C244.43,152.05 296.56,121.77 328.3,290.06C328.41,290.62 328.33,291.15 327.9" +
                        "8,291.59C319.96,301.61 215.17,426.64 64.88,314.76C64.35,314.36 64.02,313.69 64.06,313.02C67.07,2" +
                        "61.9 76.9,225.49 76.9,225.49Z",
                ),
                fill = SolidColor(Color(0xFF656688)),
            )
            .addPath(
                pathData = addPathNodes(
                    "M281.66,102.07C254.7,125.39 231.56,164.87 228.37,170.41C228.13,170.84 228.05,171.32 228.18,171.7" +
                        "9C232.93,190.12 265.7,230.56 284.23,251.64C285.37,252.94 287.49,252.24 287.67,250.51C293.77,189." +
                        "2 291.81,129.68 290.62,106.11C290.39,101.44 285.19,99.02 281.66,102.07Z",
                ),
                fill = SolidColor(Color(0xFF554C64)),
            )
            .addPath(
                pathData = addPathNodes(
                    "M70.02,114.52C63.38,146.6 71.36,200.97 76.9,225.49C89.13,190.98 113.72,172.15 126.41,164.37L126." +
                        "41,164.37C129.34,162.58 130.8,161.68 131.31,160.71C131.79,159.79 131.9,158.94 131.66,157.93C131." +
                        "41,156.86 130.31,155.72 128.1,153.43C107.55,132.13 92.03,118.26 82.89,110.55C80.21,108.29 78.86," +
                        "107.16 76.95,107.01C75.5,106.89 73.46,107.6 72.39,108.58C70.98,109.89 70.66,111.43 70.02,114.52Z",
                ),
                fill = SolidColor(Color(0xFF554C64)),
            )
            .addPath(
                pathData = addPathNodes(
                    "M115,277.33V237.67C115,224.78 135,224.78 135,237.67V277.33C135,290.22 115,290.22 115,277.33Z",
                ),
                fill = SolidColor(Color(0xFFEAEAEA)),
            )
            .addPath(
                pathData = addPathNodes(
                    "M176,277.33V237.67C176,224.78 196,224.78 196,237.67V277.33C196,290.22 176,290.22 176,277.33Z",
                ),
                fill = SolidColor(Color(0xFFEAEAEA)),
            )
            .addPath(
                pathData = addPathNodes(
                    "M185,185m-170,0a170,170 0,1 1,340 0a170,170 0,1 1,-340 0",
                ),
                stroke = SolidColor(Color(0xFFD2487D)),
                strokeLineWidth = 30.0f,
            )
            .build()
    }

}
