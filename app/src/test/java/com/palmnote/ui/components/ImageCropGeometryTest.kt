package com.palmnote.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 裁剪几何的钉：这些函数是纯计算，不依赖 Android，可在 JVM 直接跑。
 *
 * 重点钉住三件事：**选区不出图**、**锁比例时不变形**、**最小尺寸不被拖穿**。
 * 交互层（手势、绘制）无法在此覆盖，但所有「会不会算错」的地方都在这里。
 */
class ImageCropGeometryTest {

    @Test
    fun `锁 1比1 时宽图取居中方块，高图同样`() {
        val wide = initialCropRect(Size(1000f, 500f), 1f)
        assertEquals(500f, wide.width, 0.01f)
        assertEquals(500f, wide.height, 0.01f)
        assertEquals(250f, wide.left, 0.01f)

        val tall = initialCropRect(Size(500f, 1000f), 1f)
        assertEquals(500f, tall.width, 0.01f)
        assertEquals(500f, tall.height, 0.01f)
        assertEquals(250f, tall.top, 0.01f)
    }

    @Test
    fun `锁 9比16 时按高受限取整块`() {
        val rect = initialCropRect(Size(1000f, 1000f), 9f / 16f)
        assertEquals(562.5f, rect.width, 0.01f)
        assertEquals(1000f, rect.height, 0.01f)
    }

    @Test
    fun `自由比例初始就是整张图`() {
        val rect = initialCropRect(Size(640f, 480f), null)
        assertEquals(Rect(0f, 0f, 640f, 480f), rect)
    }

    @Test
    fun `输出尺寸按场景反推`() {
        assertEquals(1024 to 1024, outputSizeOf(400, 400, cropSpecOf(CropPurpose.ASSET)))
        assertEquals(512 to 512, outputSizeOf(300, 300, cropSpecOf(CropPurpose.VAULT_AVATAR)))
        assertEquals(1080 to 1920, outputSizeOf(540, 960, cropSpecOf(CropPurpose.WALLPAPER)))
        // 自由比例：长边压到上限，短边等比
        assertEquals(1600 to 1067, outputSizeOf(3000, 2000, cropSpecOf(CropPurpose.BILL)))
        // 原图比上限还小：不放大
        assertEquals(800 to 600, outputSizeOf(800, 600, cropSpecOf(CropPurpose.BILL)))
    }

    @Test
    fun `1x 适应矩形居中且保持比例`() {
        val fit = fitImageRect(2000, 1000, Size(1000f, 2000f))
        assertEquals(1000f, fit.width, 0.01f)
        assertEquals(500f, fit.height, 0.01f)
        assertEquals(0f, fit.left, 0.01f)
        assertEquals((2000f - 500f) / 2f, fit.top, 0.01f)
    }

    @Test
    fun `缩放与平移作用在 1x 矩形上`() {
        val viewport = Size(1000f, 1000f)
        val fit = fitImageRect(1000, 1000, viewport)
        val zoomed = imageRectFor(fit, 2f, Offset(50f, -20f), viewport)
        assertEquals(2000f, zoomed.width, 0.01f)
        assertEquals(-500f + 50f, zoomed.left, 0.01f)
        assertEquals(-500f - 20f, zoomed.top, 0.01f)
    }

    @Test
    fun `交集为空时返回零矩形`() {
        assertEquals(Rect.Zero, intersectRects(Rect(0f, 0f, 10f, 10f), Rect(20f, 20f, 30f, 30f)))
        assertEquals(Rect(5f, 5f, 10f, 10f), intersectRects(Rect(0f, 0f, 10f, 10f), Rect(5f, 5f, 20f, 20f)))
    }

    @Test
    fun `平移夹取后图片仍盖住选区`() {
        val viewport = Size(1000f, 1000f)
        val fit = Size(1000f, 1000f)
        val frame = Rect(200f, 200f, 800f, 800f)
        // 图片半宽 = 1000*2/2 = 1000，容器中心 500 → 图左 = 500 + offset - 1000
        // 往右推很远：图片左边缘最多贴到选区左缘
        val right = clampImageOffset(Offset(99999f, 0f), fit, 2f, frame, viewport)
        assertEquals("图片左缘应贴住选区左缘", 200f, 500f + right.x - 1000f, 0.01f)
        // 往左推很远：图片右边缘贴住选区右缘
        val left = clampImageOffset(Offset(-99999f, 0f), fit, 2f, frame, viewport)
        assertEquals("图片右缘应贴住选区右缘", 800f, 500f + left.x + 1000f, 0.01f)
    }

    @Test
    fun `自由比例初始选区整图可见并居中`() {
        val fit = Rect(0f, 250f, 1000f, 750f) // 1000x500 居中
        val bounds = Rect(50f, 50f, 950f, 950f)
        val frame = initialCropFrame(fit, bounds, null)
        assertEquals(900f, frame.width, 0.01f)
        assertEquals(450f, frame.height, 0.01f)
        assertEquals(500f, frame.center.x, 0.01f)
        assertEquals(500f, frame.center.y, 0.01f)
    }

    @Test
    fun `锁比例初始选区取范围内最大内接矩形`() {
        val fit = Rect.Zero
        val bounds = Rect(24f, 24f, 1024f, 824f)
        val frame = initialCropFrame(Rect(0f, 0f, 1000f, 1000f), bounds, 1f)
        assertEquals(800f, frame.width, 0.01f)
        assertEquals(800f, frame.height, 0.01f)
        assertEquals(bounds.center.x, frame.center.x, 0.01f)
    }

    @Test
    fun `双击放大以点击点为锚点`() {
        val viewport = Size(1000f, 1000f)
        val fit = fitImageRect(1000, 1000, viewport)
        val frame = Rect(100f, 100f, 900f, 900f)
        val tap = Offset(750f, 250f)
        val offset = zoomAround(tap, 1f, Offset.Zero, 2f, fit, frame, viewport)
        val after = imageRectFor(fit, 2f, offset, viewport)
        // 点击落在图片的哪一点，放大后这一点仍应在指尖下
        val before = imageRectFor(fit, 1f, Offset.Zero, viewport)
        val nx = (tap.x - before.left) / before.width
        val ny = (tap.y - before.top) / before.height
        assertEquals(tap.x, after.left + nx * after.width, 0.5f)
        assertEquals(tap.y, after.top + ny * after.height, 0.5f)
    }

    @Test
    fun `双击放大后图片仍盖住选区`() {
        val viewport = Size(1000f, 1000f)
        val fit = fitImageRect(800, 1200, viewport)
        val frame = Rect(100f, 100f, 900f, 900f)
        val offset = zoomAround(Offset(900f, 900f), 1f, Offset.Zero, 2.5f, fit, frame, viewport)
        val after = imageRectFor(fit, 2.5f, offset, viewport)
        assertTrue("左: ${after.left} > ${frame.left}", after.left <= frame.left + 0.01f)
        assertTrue("上: ${after.top} > ${frame.top}", after.top <= frame.top + 0.01f)
        assertTrue("右: ${after.right} < ${frame.right}", after.right >= frame.right - 0.01f)
        assertTrue("下: ${after.bottom} < ${frame.bottom}", after.bottom >= frame.bottom - 0.01f)
    }

    @Test
    fun `规格表：首图锁比例，附加图与账单允许用原图`() {
        assertEquals(1f, cropSpecOf(CropPurpose.ASSET).aspect ?: 0f, 0.001f)
        assertEquals(false, cropSpecOf(CropPurpose.ASSET).optionalCrop)
        assertEquals(null, cropSpecOf(CropPurpose.ASSET_DETAIL).aspect)
        assertEquals(true, cropSpecOf(CropPurpose.ASSET_DETAIL).optionalCrop)
        assertEquals(true, cropSpecOf(CropPurpose.BILL).optionalCrop)
        assertEquals(false, cropSpecOf(CropPurpose.VAULT_AVATAR).optionalCrop)
        assertEquals("wallpaper_custom.jpg", cropSpecOf(CropPurpose.WALLPAPER).fixedFileName)
        // 「我的」页头像：1:1 / 512 / avatars 目录，与密码本头像规格一致但不共用目录
        val profile = cropSpecOf(CropPurpose.PROFILE_AVATAR)
        assertEquals(1f, profile.aspect ?: 0f, 0.001f)
        assertEquals(512, profile.maxOutputPx)
        assertEquals("avatars", profile.dirName)
        assertEquals(false, profile.optionalCrop)
        // 遮罩形状跟随最终显示形态：头像按圆形显示 → 圆形遮罩；其余方形
        assertEquals(CropMaskShape.CIRCLE, cropSpecOf(CropPurpose.VAULT_AVATAR).maskShape)
        assertEquals(CropMaskShape.CIRCLE, cropSpecOf(CropPurpose.PROFILE_AVATAR).maskShape)
        assertEquals(CropMaskShape.RECTANGLE, cropSpecOf(CropPurpose.ASSET).maskShape)
        assertEquals(CropMaskShape.RECTANGLE, cropSpecOf(CropPurpose.BILL).maskShape)
        assertEquals(CropMaskShape.RECTANGLE, cropSpecOf(CropPurpose.WALLPAPER).maskShape)
    }

    @Test
    fun `缩到最小时选区等于图片内最大内接矩形`() {
        val image = Rect(0f, 300f, 1080f, 810f) // 宽图
        val locked = initialCropFrame(image, image, 1f)
        assertEquals(510f, locked.height, 0.01f)
        assertEquals(510f, locked.width, 0.01f)
        assertEquals(image.top, locked.top, 0.01f)
        assertEquals(image.bottom, locked.bottom, 0.01f)
        // 自由比例：整张图
        assertEquals(image, initialCropFrame(image, image, null))
    }

    @Test
    fun `停在 1x 时仍可上下拖动选图`() {
        // 图片 1000x1400 适配进 1000x1400 容器；1:1 选区 1000x1000 → 纵向可滑动 ±200
        val viewport = Size(1000f, 1400f)
        val fit = Size(1000f, 1400f)
        val frame = Rect(0f, 200f, 1000f, 1200f)
        val up = clampImageOffset(Offset(0f, -999f), fit, 1f, frame, viewport)
        assertEquals(-200f, up.y, 0.01f)
        val down = clampImageOffset(Offset(0f, 999f), fit, 1f, frame, viewport)
        assertEquals(200f, down.y, 0.01f)
    }

    // ===== 框恒居中 + 拖角对称缩放（2026-11-04 定案：与小米相册、iOS 同形态）=====

    @Test
    fun `框的中心恒为给定中心点`() {
        val center = Offset(540f, 1200f)
        val rect = centeredRectOf(Size(400f, 300f), center)
        assertEquals(540f, rect.center.x, 0.01f)
        assertEquals(1200f, rect.center.y, 0.01f)
        assertEquals(400f, rect.width, 0.01f)
        assertEquals(300f, rect.height, 0.01f)
        // 换尺寸后中心不变 —— 「居中」由结构保证，不依赖任何 clamp
        val bigger = centeredRectOf(Size(800f, 600f), center)
        assertEquals(540f, bigger.center.x, 0.01f)
        assertEquals(1200f, bigger.center.y, 0.01f)
    }

    @Test
    fun `拖右下角时宽度按两倍位移变化，手指跟手`() {
        val max = Size(1000f, 1000f)
        val grown = resizeCenteredCropSize(Size(400f, 400f), CropHandle.BOTTOM_RIGHT, 50f, 0f, null, 64f, max)
        // 手指右移 50 → 右边沿跟着走 50，左边镜像 → 总宽 +100
        assertEquals(500f, grown.width, 0.01f)
        assertEquals(400f, grown.height, 0.01f)
    }

    @Test
    fun `拖左上角时同样向外放大`() {
        val max = Size(1000f, 1000f)
        val grown = resizeCenteredCropSize(Size(400f, 400f), CropHandle.TOP_LEFT, -50f, -50f, null, 64f, max)
        assertEquals(500f, grown.width, 0.01f)
        assertEquals(500f, grown.height, 0.01f)
    }

    @Test
    fun `尺寸不会超过上限也不会小于最小边`() {
        val max = Size(600f, 500f)
        val tooBig = resizeCenteredCropSize(Size(400f, 400f), CropHandle.BOTTOM_RIGHT, 9999f, 9999f, null, 64f, max)
        assertEquals(600f, tooBig.width, 0.01f)
        assertEquals(500f, tooBig.height, 0.01f)

        val tooSmall = resizeCenteredCropSize(Size(100f, 100f), CropHandle.BOTTOM_RIGHT, -9999f, -9999f, null, 64f, max)
        assertEquals(64f, tooSmall.width, 0.01f)
        assertEquals(64f, tooSmall.height, 0.01f)
    }

    @Test
    fun `锁比例时无论往哪个方向拖都保持比例`() {
        val max = Size(1000f, 1000f)
        val base = Size(400f, 400f) // 1:1
        val cases = listOf(
            CropHandle.TOP_LEFT to Offset(-80f, -10f),
            CropHandle.TOP_RIGHT to Offset(60f, -40f),
            CropHandle.BOTTOM_LEFT to Offset(-30f, 70f),
            CropHandle.BOTTOM_RIGHT to Offset(50f, 20f)
        )
        cases.forEach { (handle, d) ->
            val size = resizeCenteredCropSize(base, handle, d.x, d.y, 1f, 64f, max)
            assertEquals("锁 1:1 后宽高必须相等，handle=$handle", size.width, size.height, 0.01f)
        }
    }

    @Test
    fun `锁 9比16 时纯竖直拖动也能改变大小`() {
        val max = Size(1080f, 1920f)
        val base = Size(540f, 960f) // 9:16
        val taller = resizeCenteredCropSize(base, CropHandle.BOTTOM_RIGHT, 0f, 60f, 9f / 16f, 64f, max)
        assertEquals("高度应随竖直拖动增加", 1080f, taller.height, 1f)
        assertEquals(9f / 16f, taller.width / taller.height, 0.001f)
    }

    @Test
    fun `锁比例的上限同时受可用宽高约束`() {
        // 可用区域 1000x1000，9:16 的极限是宽 562.5、高 1000
        val size = resizeCenteredCropSize(Size(200f, 355f), CropHandle.BOTTOM_RIGHT, 9999f, 9999f, 9f / 16f, 64f, Size(1000f, 1000f))
        assertEquals(562.5f, size.width, 0.5f)
        assertEquals(1000f, size.height, 1f)
    }

    // ===== 旋转/容器变化后选框收进新上限（框恒居中定案下的不变量）=====

    @Test
    fun `超限选框同比例收进新上限`() {
        // 9:16 竖图转横图：旧框 787.5x1400，新上限 1000x562.5 —— 高度是瓶颈
        val clamped = clampFrameSizeInside(Size(787.5f, 1400f), Size(1000f, 562.5f))
        assertEquals(787.5f * 562.5f / 1400f, clamped.width, 0.01f)
        assertEquals(562.5f, clamped.height, 0.01f)
    }

    @Test
    fun `未超限的选框原样保留`() {
        val size = Size(300f, 200f)
        assertEquals(size, clampFrameSizeInside(size, Size(1000f, 562.5f)))
    }

    @Test
    fun `锁比例选框收进后仍是锁定比例`() {
        // 1:1 框 1400x1400 收进 1000x562.5 → 562.5x562.5
        val clamped = clampFrameSizeInside(Size(1400f, 1400f), Size(1000f, 562.5f))
        assertEquals(562.5f, clamped.width, 0.01f)
        assertEquals(562.5f, clamped.height, 0.01f)
    }

    @Test
    fun `非法尺寸收进结果为零框`() {
        assertEquals(Size.Zero, clampFrameSizeInside(Size.Zero, Size(100f, 100f)))
        assertEquals(Size.Zero, clampFrameSizeInside(Size(100f, 100f), Size.Zero))
    }

    // ── 拖角松手收口（settleToMaxFrame，2026-11-05 真机反馈）──

    /** 公共夹具：容器 1000×800、fit 800×500 居中、最大框 = fit。 */
    private val container = Size(1000f, 800f)
    private val fit = Rect(100f, 150f, 900f, 650f)
    private val maxFrame = Size(800f, 500f)

    @Test
    fun `收口把选框回最大并按两轴较大比放大`() {
        // 当前框 400×250（各为最大框的一半）→ k = 2
        val frame = centeredRectOf(Size(400f, 250f), fit.center)
        val result = settleToMaxFrame(
            frameSize = Size(400f, 250f), maxFrameSize = maxFrame, frame = frame,
            scale = 1f, offset = Offset.Zero, fit = fit, container = container, maxZoom = 6f
        )
        assertEquals(2f, result.scale, 0.01f)
        assertEquals(maxFrame, result.frameSize)
        // 以框中心为锚：所选区域中心的内容留在屏幕中心
        val image = imageRectFor(fit, result.scale, result.offset, container)
        assertEquals(fit.center.x, image.center.x, 0.01f)
        assertEquals(fit.center.y, image.center.y, 0.01f)
    }

    @Test
    fun `收口后图片盖住最大选框`() {
        val frameSize = Size(200f, 500f) // 自由比例窄框
        val frame = centeredRectOf(frameSize, fit.center)
        val result = settleToMaxFrame(
            frameSize = frameSize, maxFrameSize = maxFrame, frame = frame,
            scale = 1f, offset = Offset.Zero, fit = fit, container = container, maxZoom = 6f
        )
        val image = imageRectFor(fit, result.scale, result.offset, container)
        val settled = centeredRectOf(maxFrame, fit.center)
        assertTrue("image=$image settled=$settled", image.left <= settled.left + 0.01f)
        assertTrue("image=$image settled=$settled", image.top <= settled.top + 0.01f)
        assertTrue("image=$image settled=$settled", image.right >= settled.right - 0.01f)
        assertTrue("image=$image settled=$settled", image.bottom >= settled.bottom - 0.01f)
    }

    @Test
    fun `框已是最大时收口为无操作`() {
        val frame = centeredRectOf(maxFrame, fit.center)
        val result = settleToMaxFrame(
            frameSize = maxFrame, maxFrameSize = maxFrame, frame = frame,
            scale = 1.5f, offset = Offset(10f, -10f), fit = fit, container = container, maxZoom = 6f
        )
        assertEquals(1.5f, result.scale, 0.001f)
        assertEquals(maxFrame, result.frameSize)
    }

    @Test
    fun `放大顶到 maxZoom 时仍回最大框且图片盖住选框`() {
        val frameSize = Size(50f, 31.25f) // k = 16，远超 maxZoom=6
        val frame = centeredRectOf(frameSize, fit.center)
        val result = settleToMaxFrame(
            frameSize = frameSize, maxFrameSize = maxFrame, frame = frame,
            scale = 1f, offset = Offset.Zero, fit = fit, container = container, maxZoom = 6f
        )
        assertEquals(6f, result.scale, 0.01f)
        assertEquals(maxFrame, result.frameSize)
        val image = imageRectFor(fit, result.scale, result.offset, container)
        val settled = centeredRectOf(maxFrame, fit.center)
        assertTrue(image.left <= settled.left && image.top <= settled.top)
        assertTrue(image.right >= settled.right && image.bottom >= settled.bottom)
    }

    @Test
    fun `零框或零上限时收口原样返回`() {
        val r1 = settleToMaxFrame(
            frameSize = Size.Zero, maxFrameSize = maxFrame, frame = Rect.Zero,
            scale = 1f, offset = Offset.Zero, fit = fit, container = container, maxZoom = 6f
        )
        assertEquals(1f, r1.scale, 0.001f)
        val r2 = settleToMaxFrame(
            frameSize = Size(100f, 100f), maxFrameSize = Size.Zero, frame = centeredRectOf(Size(100f, 100f), fit.center),
            scale = 1f, offset = Offset.Zero, fit = fit, container = container, maxZoom = 6f
        )
        assertEquals(Size(100f, 100f), r2.frameSize)
    }
}
