package io.legado.app.feature.reader.core.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 角色胶囊的行内几何。测量侧（分页宽度）与绘制侧（头像落点）共用 [CastCapsuleGeometry]，
 * 两边判定不一致就是「量到的宽度」和「画出来的样子」错开——头像歪在胶囊里、
 * 关掉文字后还留一截空。
 */
class CastCapsuleGeometryTest {

    private val fontSize = 48f
    private val height = CastCapsuleGeometry.heightPx(fontSize)
    private val avatarOnlyStyle = CastCapsuleStyle(showName = false, showPool = false)

    @Test
    fun `avatar only capsule is a square`() {
        val width = CastCapsuleGeometry.widthOf(
            fontSizePx = fontSize,
            labelWidthPx = 200f,
            poolWidthPx = 120f,
            withAvatar = true,
            style = avatarOnlyStyle,
        )
        assertEquals(height, width, 1e-3f)
    }

    @Test
    fun `avatar sits dead center in that square`() {
        val diameter = avatarOnlyStyle.avatarDiameter(height)
        val left = avatarOnlyStyle.avatarLeft(height)
        assertEquals((height - diameter) / 2f, left, 1e-3f)
        assertEquals(height, left + diameter + left, 1e-3f)
    }

    @Test
    fun `avatar does not move when the name or the pool label appears`() {
        // 胶囊是行内元素、左沿不动，头像落点只由 avatarLeft 决定，与名字/池小字开关无关
        val withName = CastCapsuleStyle(showPool = false).avatarLeft(height)
        val withPool = CastCapsuleStyle(showName = false).avatarLeft(height)
        val allOn = CastCapsuleStyle().avatarLeft(height)
        assertEquals(avatarOnlyStyle.avatarLeft(height), withName, 1e-3f)
        assertEquals(avatarOnlyStyle.avatarLeft(height), withPool, 1e-3f)
        assertEquals(avatarOnlyStyle.avatarLeft(height), allOn, 1e-3f)
        // 未分配那颗现在也是正方形底板，图标落点必须与角色那颗同一个：整格居中。
        assertEquals(
            (height - CastCapsuleStyle().avatarDiameter(height)) / 2f,
            CastCapsuleStyle().avatarLeft(height),
            1e-3f,
        )
    }

    @Test
    fun `any visible content keeps the capsule wider than tall`() {
        val withName = CastCapsuleGeometry.widthOf(
            fontSize, 200f, 0f, true, style = CastCapsuleStyle(showPool = false),
        )
        val withPool = CastCapsuleGeometry.widthOf(
            fontSize, 0f, 120f, true, style = CastCapsuleStyle(showName = false),
        )
        val withEffect = CastCapsuleGeometry.widthOf(
            fontSize, 0f, 0f, true, withEffect = true, style = avatarOnlyStyle,
        )
        assertTrue(withName > height)
        assertTrue(withPool > height)
        assertTrue(withEffect > height)
    }

    @Test
    fun `no avatar means no square`() {
        assertFalse(CastCapsuleGeometry.isAvatarOnly(CastCapsuleStyle(showAvatar = false), false, false))
        // 池小字那一栏开着、也确实有内容：还不算「只剩头像」。关掉那一栏才算。
        assertFalse(CastCapsuleGeometry.isAvatarOnly(CastCapsuleStyle(showName = false), hasPoolText = true, withEffect = false))
        assertTrue(CastCapsuleGeometry.isAvatarOnly(avatarOnlyStyle, hasPoolText = true, withEffect = false))
        assertFalse(CastCapsuleGeometry.isAvatarOnly(avatarOnlyStyle, hasPoolText = false, withEffect = true))
        assertTrue(CastCapsuleGeometry.isAvatarOnly(avatarOnlyStyle, hasPoolText = false, withEffect = false))
        // 名字还开着就谈不上「只剩头像」
        assertFalse(CastCapsuleGeometry.isAvatarOnly(CastCapsuleStyle(showPool = false), false, false))
    }

    @Test
    fun `corner radius turns that square into a circle`() {
        assertEquals(0f, CastCapsuleStyle(cornerRadius = 0).cornerPx(height), 1e-3f)
        assertEquals(height / 2f, CastCapsuleStyle(cornerRadius = 100).cornerPx(height), 1e-3f)
    }

    @Test
    fun `bgm capsule never takes the square shortcut`() {
        // 配乐那颗本来就不画头像，宽度必须还是「字加两头内边距」。
        val width = CastCapsuleGeometry.bgmWidthPx(fontSize, 300f)
        assertTrue(width > height)
    }

    @Test
    fun `placeholder capsule is a square too`() {
        // 未分配那颗只有人形图标：宽=高，所以圆角 0 是正方形、拉满就是正圆，
        // 与只显头像的角色那颗同一条规则。头像位移不许把它撑成长方。
        assertEquals(height, CastCapsuleGeometry.placeholderWidthPx(fontSize), 1e-3f)
        assertEquals(
            height,
            CastCapsuleGeometry.placeholderWidthPx(
                fontSize,
                CastCapsuleStyle(avatarDx = 100, avatarScale = 150),
            ),
            1e-3f,
        )
        val iconRight = CastCapsuleStyle(avatarDx = 100, avatarScale = 150)
            .let { it.avatarLeft(height) + it.avatarDiameter(height) }
        assertTrue("图标必须留在胶囊内：$iconRight > $height", iconRight <= height + 1e-3f)
    }
}
