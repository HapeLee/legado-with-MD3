package io.legado.app.ui.book.read.page.delegate

import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Region
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.MotionEvent
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.utils.screenshot
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

@Suppress("DEPRECATION")
class SimulationPageDelegate(readView: ReadView) : HorizontalPageDelegate(readView) {
    private var mTouchX = 0.01f
    private var mTouchY = 0.01f

    private var mCornerX = 1
    private var mCornerY = 1
    private val mPath0 = Path()
    private val mPath1 = Path()

    private val mBezierStart1 = PointF()
    private val mBezierControl1 = PointF()
    private val mBezierVertex1 = PointF()
    private val mBezierEnd1 = PointF()

    private val mBezierStart2 = PointF()
    private val mBezierControl2 = PointF()
    private val mBezierVertex2 = PointF()
    private val mBezierEnd2 = PointF()

    private var mMiddleX = 0f
    private var mMiddleY = 0f
    private var mDegrees = 0f
    private var mTouchToCornerDis = 0f
    private var mColorMatrixFilter: ColorMatrixColorFilter
    private val mMatrix = Matrix()
    private val mMatrixArray = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f)

    private var mIsRtOrLb = false
    private var mMaxLength = hypot(viewWidth.toDouble(), viewHeight.toDouble()).toFloat()

    private val mBackShadowColors: IntArray
    private val mFrontShadowColors: IntArray

    private val mBackShadowDrawableLR: GradientDrawable
    private val mBackShadowDrawableRL: GradientDrawable
    private val mFolderShadowDrawableLR: GradientDrawable
    private val mFolderShadowDrawableRL: GradientDrawable

    private val mFrontShadowDrawableHBT: GradientDrawable
    private val mFrontShadowDrawableHTB: GradientDrawable
    private val mFrontShadowDrawableVLR: GradientDrawable
    private val mFrontShadowDrawableVRL: GradientDrawable

    private val mPaint = Paint().apply { style = Paint.Style.FILL }

    private var curBitmap: android.graphics.Bitmap? = null
    private var prevBitmap: android.graphics.Bitmap? = null
    private var nextBitmap: android.graphics.Bitmap? = null
    private var canvas: Canvas = Canvas()

    private val tempPointF = PointF()
    private val crossPointF1 = PointF()
    private val crossPointF2 = PointF()

    init {
        val cm = ColorMatrix()
        val array = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        cm.set(array)
        mColorMatrixFilter = ColorMatrixColorFilter(cm)

        val color = intArrayOf(0x333333, 0xb0333333.toInt())
        mFolderShadowDrawableRL = GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, color)
        mFolderShadowDrawableRL.gradientType = GradientDrawable.LINEAR_GRADIENT

        mFolderShadowDrawableLR = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, color)
        mFolderShadowDrawableLR.gradientType = GradientDrawable.LINEAR_GRADIENT

        mBackShadowColors = intArrayOf(0xff111111.toInt(), 0x111111)
        mBackShadowDrawableRL =
            GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, mBackShadowColors)
        mBackShadowDrawableRL.gradientType = GradientDrawable.LINEAR_GRADIENT

        mBackShadowDrawableLR =
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, mBackShadowColors)
        mBackShadowDrawableLR.gradientType = GradientDrawable.LINEAR_GRADIENT

        mFrontShadowColors = intArrayOf(0x80111111.toInt(), 0x111111)
        mFrontShadowDrawableVLR =
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, mFrontShadowColors)
        mFrontShadowDrawableVLR.gradientType = GradientDrawable.LINEAR_GRADIENT

        mFrontShadowDrawableVRL =
            GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, mFrontShadowColors)
        mFrontShadowDrawableVRL.gradientType = GradientDrawable.LINEAR_GRADIENT

        mFrontShadowDrawableHTB =
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, mFrontShadowColors)
        mFrontShadowDrawableHTB.gradientType = GradientDrawable.LINEAR_GRADIENT

        mFrontShadowDrawableHBT =
            GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, mFrontShadowColors)
        mFrontShadowDrawableHBT.gradientType = GradientDrawable.LINEAR_GRADIENT
    }

    override fun setBitmap() {
        when (mDirection) {
            PageDirection.PREV -> {
                prevBitmap = prevPage.screenshot(prevBitmap, canvas)
                curBitmap = curPage.screenshot(curBitmap, canvas)
            }
            PageDirection.NEXT -> {
                nextBitmap = nextPage.screenshot(nextBitmap, canvas)
                curBitmap = curPage.screenshot(curBitmap, canvas)
            }
            else -> Unit
        }
    }

    override fun setViewSize(width: Int, height: Int) {
        super.setViewSize(width, height)
        mMaxLength = hypot(viewWidth.toDouble(), viewHeight.toDouble()).toFloat()
    }

    override fun onTouch(event: MotionEvent) {
        super.onTouch(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                calcCornerXY(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if ((startY > viewHeight / 3 && startY < viewHeight * 2 / 3)
                    || mDirection == PageDirection.PREV
                ) {
                    readView.touchY = viewHeight.toFloat()
                }
                if (startY > viewHeight / 3 && startY < viewHeight / 2
                    && mDirection == PageDirection.NEXT
                ) {
                    readView.touchY = 1f
                }
            }
        }
    }

    override fun setDirection(direction: PageDirection) {
        super.setDirection(direction)
        when (direction) {
            PageDirection.PREV ->
                if (startX > viewWidth / 2) {
                    calcCornerXY(startX, viewHeight.toFloat())
                } else {
                    calcCornerXY(viewWidth - startX, viewHeight.toFloat())
                }
            PageDirection.NEXT ->
                if (viewWidth / 2 > startX) {
                    calcCornerXY(viewWidth - startX, startY)
                }
            else -> Unit
        }
    }

    override fun onAnimStart(animationSpeed: Int) {
        var dx: Int
        val dy: Int
        if (isCancel) {
            if (mCornerX > 0 && mDirection == PageDirection.NEXT) {
                dx = (viewWidth - touchX).toInt()
            } else {
                dx = -touchX.toInt()
            }
            if (mDirection != PageDirection.NEXT) {
                dx = -(viewWidth + touchX).toInt()
            }
            dy = if (mCornerY > 0) {
                (viewHeight - touchY).toInt()
            } else {
                -touchY.toInt()
            }
        } else {
            if (mCornerX > 0 && mDirection == PageDirection.NEXT) {
                dx = -(viewWidth + touchX).toInt()
            } else {
                dx = (viewWidth - touchX + viewWidth).toInt()
            }
            dy = if (mCornerY > 0) {
                (viewHeight - touchY).toInt()
            } else {
                (1 - touchY).toInt()
            }
        }
        startScroll(touchX.toInt(), touchY.toInt(), dx, dy, animationSpeed)
    }

    override fun onAnimStop() {
        if (!isCancel) {
            readView.fillPage(mDirection)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!isRunning) return

        calcPoints()

        when (mDirection) {
            PageDirection.NEXT -> {
                drawCurrentPageArea(canvas, curBitmap)
                drawNextPageAreaAndShadow(canvas, nextBitmap)
                drawCurrentPageShadow(canvas)
                drawCurrentBackArea(canvas, curBitmap)
            }
            PageDirection.PREV -> {
                drawCurrentPageArea(canvas, prevBitmap)
                drawNextPageAreaAndShadow(canvas, curBitmap)
                drawCurrentPageShadow(canvas)
                drawCurrentBackArea(canvas, prevBitmap)
            }
            else -> return
        }
    }

    private fun drawCurrentBackArea(canvas: Canvas, bitmap: android.graphics.Bitmap?) {
        bitmap ?: return

        val i = ((mBezierStart1.x + mBezierControl1.x) * 0.5f).toInt()
        val f1 = abs(i - mBezierControl1.x)
        val i1 = ((mBezierStart2.y + mBezierControl2.y) * 0.5f).toInt()
        val f2 = abs(i1 - mBezierControl2.y)
        val f3 = min(f1, f2)

        mPath1.reset()
        mPath1.moveTo(mBezierVertex2.x, mBezierVertex2.y)
        mPath1.lineTo(mBezierVertex1.x, mBezierVertex1.y)
        mPath1.lineTo(mBezierEnd1.x, mBezierEnd1.y)
        mPath1.lineTo(mTouchX, mTouchY)
        mPath1.lineTo(mBezierEnd2.x, mBezierEnd2.y)
        mPath1.close()

        val folderShadowDrawable: GradientDrawable
        val left: Int
        val right: Int
        if (mIsRtOrLb) {
            left = (mBezierStart1.x - 1).toInt()
            right = (mBezierStart1.x + f3 + 1).toInt()
            folderShadowDrawable = mFolderShadowDrawableLR
        } else {
            left = (mBezierStart1.x - f3 - 1).toInt()
            right = (mBezierStart1.x + 1).toInt()
            folderShadowDrawable = mFolderShadowDrawableRL
        }

        canvas.save()
        try {
            canvas.clipPath(mPath0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                canvas.clipPath(mPath1)
            } else {
                canvas.clipPath(mPath1, Region.Op.INTERSECT)
            }

            mPaint.colorFilter = mColorMatrixFilter

            val dx = mCornerX - mBezierControl1.x
            val dy = mBezierControl2.y - mCornerY
            val dis = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val f8 = dx / dis
            val f9 = dy / dis
            mMatrixArray[0] = 1 - 2 * f9 * f9
            mMatrixArray[1] = 2 * f8 * f9
            mMatrixArray[3] = mMatrixArray[1]
            mMatrixArray[4] = 1 - 2 * f8 * f8
            mMatrix.reset()
            mMatrix.setValues(mMatrixArray)
            mMatrix.preTranslate(-mBezierControl1.x, -mBezierControl1.y)
            mMatrix.postTranslate(mBezierControl1.x, mBezierControl1.y)
            canvas.drawBitmap(bitmap, mMatrix, mPaint)

            mPaint.colorFilter = null

            canvas.rotate(mDegrees, mBezierStart1.x, mBezierStart1.y)
            folderShadowDrawable.setBounds(
                left, mBezierStart1.y.toInt(),
                right, (mBezierStart1.y + mMaxLength).toInt()
            )
            folderShadowDrawable.draw(canvas)
        } catch (_: Exception) {
        }
        canvas.restore()
    }

    private fun drawCurrentPageShadow(canvas: Canvas) {
        val degree: Double = if (mIsRtOrLb) {
            Math.PI / 4 - atan2((mBezierControl1.y - mTouchY).toDouble(), (mTouchX - mBezierControl1.x).toDouble())
        } else {
            Math.PI / 4 - atan2((mTouchY - mBezierControl1.y).toDouble(), (mTouchX - mBezierControl1.x).toDouble())
        }

        val shadowLen = 25.0 * 1.414
        val d1 = shadowLen * cos(degree)
        val d2 = shadowLen * sin(degree)
        val x = (mTouchX + d1).toFloat()
        val y = if (mIsRtOrLb) (mTouchY + d2).toFloat() else (mTouchY - d2).toFloat()

        mPath1.reset()
        mPath1.moveTo(x, y)
        mPath1.lineTo(mTouchX, mTouchY)
        mPath1.lineTo(mBezierControl1.x, mBezierControl1.y)
        mPath1.lineTo(mBezierStart1.x, mBezierStart1.y)
        mPath1.close()

        canvas.save()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                canvas.clipOutPath(mPath0)
            } else {
                canvas.clipPath(mPath0, Region.Op.XOR)
            }
            canvas.clipPath(mPath1, Region.Op.INTERSECT)

            var leftX: Int
            var rightX: Int
            var currentPageShadow: GradientDrawable
            if (mIsRtOrLb) {
                leftX = mBezierControl1.x.toInt()
                rightX = (mBezierControl1.x + 25).toInt()
                currentPageShadow = mFrontShadowDrawableVLR
            } else {
                leftX = (mBezierControl1.x - 25).toInt()
                rightX = (mBezierControl1.x + 1).toInt()
                currentPageShadow = mFrontShadowDrawableVRL
            }

            val rotateDegrees = Math.toDegrees(
                atan2((mTouchX - mBezierControl1.x).toDouble(), (mBezierControl1.y - mTouchY).toDouble())
            ).toFloat()
            canvas.rotate(rotateDegrees, mBezierControl1.x, mBezierControl1.y)
            currentPageShadow.setBounds(
                leftX, (mBezierControl1.y - mMaxLength).toInt(),
                rightX, mBezierControl1.y.toInt()
            )
            currentPageShadow.draw(canvas)
        } catch (_: Exception) {
        }
        canvas.restore()

        mPath1.reset()
        mPath1.moveTo(x, y)
        mPath1.lineTo(mTouchX, mTouchY)
        mPath1.lineTo(mBezierControl2.x, mBezierControl2.y)
        mPath1.lineTo(mBezierStart2.x, mBezierStart2.y)
        mPath1.close()

        canvas.save()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                canvas.clipOutPath(mPath0)
            } else {
                canvas.clipPath(mPath0, Region.Op.XOR)
            }
            canvas.clipPath(mPath1)

            var leftX: Int
            var rightX: Int
            var currentPageShadow: GradientDrawable
            if (mIsRtOrLb) {
                leftX = mBezierControl2.y.toInt()
                rightX = (mBezierControl2.y + 25).toInt()
                currentPageShadow = mFrontShadowDrawableHTB
            } else {
                leftX = (mBezierControl2.y - 25).toInt()
                rightX = (mBezierControl2.y + 1).toInt()
                currentPageShadow = mFrontShadowDrawableHBT
            }

            val rotateDegrees = Math.toDegrees(
                atan2((mBezierControl2.y - mTouchY).toDouble(), (mBezierControl2.x - mTouchX).toDouble())
            ).toFloat()
            canvas.rotate(rotateDegrees, mBezierControl2.x, mBezierControl2.y)

            val temp = if (mBezierControl2.y < 0) mBezierControl2.y - viewHeight else mBezierControl2.y
            val hmg = hypot(mBezierControl2.x.toDouble(), temp.toDouble()).toInt()

            if (hmg > mMaxLength) {
                currentPageShadow.setBounds(
                    (mBezierControl2.x - 25).toInt() - hmg, leftX,
                    (mBezierControl2.x + mMaxLength).toInt() - hmg, rightX
                )
            } else {
                currentPageShadow.setBounds(
                    (mBezierControl2.x - mMaxLength).toInt(), leftX,
                    mBezierControl2.x.toInt(), rightX
                )
            }

            currentPageShadow.draw(canvas)
        } catch (_: Exception) {
        }
        canvas.restore()
    }

    private fun drawNextPageAreaAndShadow(canvas: Canvas, bitmap: android.graphics.Bitmap?) {
        bitmap ?: return

        mPath1.reset()
        mPath1.moveTo(mBezierStart1.x, mBezierStart1.y)
        mPath1.lineTo(mBezierVertex1.x, mBezierVertex1.y)
        mPath1.lineTo(mBezierVertex2.x, mBezierVertex2.y)
        mPath1.lineTo(mBezierStart2.x, mBezierStart2.y)
        mPath1.lineTo(mCornerX.toFloat(), mCornerY.toFloat())
        mPath1.close()

        mDegrees = Math.toDegrees(
            atan2((mBezierControl1.x - mCornerX).toDouble(), (mBezierControl2.y - mCornerY).toDouble())
        ).toFloat()

        val leftX: Int
        val rightX: Int
        val backShadowDrawable: GradientDrawable
        if (mIsRtOrLb) {
            leftX = mBezierStart1.x.toInt()
            rightX = (mBezierStart1.x + mTouchToCornerDis / 4).toInt()
            backShadowDrawable = mBackShadowDrawableLR
        } else {
            leftX = (mBezierStart1.x - mTouchToCornerDis / 4).toInt()
            rightX = mBezierStart1.x.toInt()
            backShadowDrawable = mBackShadowDrawableRL
        }

        canvas.save()
        try {
            canvas.clipPath(mPath0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                canvas.clipPath(mPath1)
            } else {
                canvas.clipPath(mPath1, Region.Op.INTERSECT)
            }

            canvas.drawBitmap(bitmap, 0f, 0f, null)
            canvas.rotate(mDegrees, mBezierStart1.x, mBezierStart1.y)
            backShadowDrawable.setBounds(
                leftX, mBezierStart1.y.toInt(),
                rightX, (mMaxLength + mBezierStart1.y).toInt()
            )
            backShadowDrawable.draw(canvas)
        } catch (_: Exception) {
        }
        canvas.restore()
    }

    private fun drawCurrentPageArea(canvas: Canvas, bitmap: android.graphics.Bitmap?) {
        bitmap ?: return
        mPath0.reset()
        mPath0.moveTo(mBezierStart1.x, mBezierStart1.y)
        mPath0.quadTo(mBezierControl1.x, mBezierControl1.y, mBezierEnd1.x, mBezierEnd1.y)
        mPath0.lineTo(mTouchX, mTouchY)
        mPath0.lineTo(mBezierEnd2.x, mBezierEnd2.y)
        mPath0.quadTo(mBezierControl2.x, mBezierControl2.y, mBezierStart2.x, mBezierStart2.y)
        mPath0.lineTo(mCornerX.toFloat(), mCornerY.toFloat())
        mPath0.close()

        canvas.save()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipOutPath(mPath0)
        } else {
            canvas.clipPath(mPath0, Region.Op.XOR)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        try {
            canvas.restore()
        } catch (_: Exception) {
        }
    }

    private fun calcCornerXY(x: Float, y: Float) {
        mCornerX = if (x <= viewWidth / 2) 0 else viewWidth
        mCornerY = if (y <= viewHeight / 2) 0 else viewHeight
        mIsRtOrLb = (mCornerX == 0 && mCornerY == viewHeight)
                || (mCornerX == viewWidth && mCornerY == 0)
    }

    private fun calcPoints() {
        mTouchX = touchX
        mTouchY = touchY

        mMiddleX = (mTouchX + mCornerX) * 0.5f
        mMiddleY = (mTouchY + mCornerY) * 0.5f

        mBezierControl1.x =
            mMiddleX - (mCornerY - mMiddleY) * (mCornerY - mMiddleY) / (mCornerX - mMiddleX)
        mBezierControl1.y = mCornerY.toFloat()

        mBezierControl2.x = mCornerX.toFloat()
        if (mCornerY - mMiddleY == 0f) {
            mBezierControl2.y = mMiddleY - (mCornerX - mMiddleX) * (mCornerX - mMiddleX) / 0.1f
        } else {
            mBezierControl2.y = mMiddleY - (mCornerX - mMiddleX) * (mCornerX - mMiddleX) / (mCornerY - mMiddleY)
        }

        mBezierStart1.x = mBezierControl1.x - (mCornerX - mBezierControl1.x) * 0.5f
        mBezierStart1.y = mCornerY.toFloat()

        if (mTouchX > 0 && mTouchX < viewWidth) {
            if (mBezierStart1.x < 0 || mBezierStart1.x > viewWidth) {
                if (mBezierStart1.x < 0)
                    mBezierStart1.x = viewWidth - mBezierStart1.x

                val f1 = abs(mCornerX - mTouchX)
                val f2 = viewWidth * f1 / mBezierStart1.x
                mTouchX = abs(mCornerX - f2)

                val f3 = abs(mCornerX - mTouchX) * abs(mCornerY - mTouchY) / f1
                mTouchY = abs(mCornerY - f3)

                mMiddleX = (mTouchX + mCornerX) * 0.5f
                mMiddleY = (mTouchY + mCornerY) * 0.5f

                mBezierControl1.x =
                    mMiddleX - (mCornerY - mMiddleY) * (mCornerY - mMiddleY) / (mCornerX - mMiddleX)
                mBezierControl1.y = mCornerY.toFloat()

                mBezierControl2.x = mCornerX.toFloat()
                val f5 = mCornerY - mMiddleY
                if (f5 == 0f) {
                    mBezierControl2.y = mMiddleY - (mCornerX - mMiddleX) * (mCornerX - mMiddleX) / 0.1f
                } else {
                    mBezierControl2.y = mMiddleY - (mCornerX - mMiddleX) * (mCornerX - mMiddleX) / (mCornerY - mMiddleY)
                }

                mBezierStart1.x = mBezierControl1.x - (mCornerX - mBezierControl1.x) * 0.5f
            }
        }

        mBezierStart2.x = mCornerX.toFloat()
        mBezierStart2.y = mBezierControl2.y - (mCornerY - mBezierControl2.y) * 0.5f

        mTouchToCornerDis = hypot((mTouchX - mCornerX).toDouble(), (mTouchY - mCornerY).toDouble()).toFloat()

        tempPointF.set(mTouchX, mTouchY)
        getCross(tempPointF, mBezierControl1, mBezierStart1, mBezierStart2, mBezierEnd1)
        getCross(tempPointF, mBezierControl2, mBezierStart1, mBezierStart2, mBezierEnd2)

        mBezierVertex1.x = (mBezierStart1.x + 2 * mBezierControl1.x + mBezierEnd1.x) * 0.25f
        mBezierVertex1.y = (2 * mBezierControl1.y + mBezierStart1.y + mBezierEnd1.y) * 0.25f
        mBezierVertex2.x = (mBezierStart2.x + 2 * mBezierControl2.x + mBezierEnd2.x) * 0.25f
        mBezierVertex2.y = (2 * mBezierControl2.y + mBezierStart2.y + mBezierEnd2.y) * 0.25f
    }

    private fun getCross(P1: PointF, P2: PointF, P3: PointF, P4: PointF, out: PointF) {
        val a1 = (P2.y - P1.y) / (P2.x - P1.x)
        val b1 = (P1.x * P2.y - P2.x * P1.y) / (P1.x - P2.x)
        val a2 = (P4.y - P3.y) / (P4.x - P3.x)
        val b2 = (P3.x * P4.y - P4.x * P3.y) / (P3.x - P4.x)
        out.x = (b2 - b1) / (a1 - a2)
        out.y = a1 * out.x + b1
    }
}
