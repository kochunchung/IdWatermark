package tw.example.idwatermark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 簡單的裁切檢視：影像等比例置中顯示，上面疊一個可拖曳的裁切框。
 *
 * - 拖曳框內：移動整個框
 * - 拖曳四個角：縮放（鎖定比例時維持證件比例 85.6 : 54）
 * - 取消鎖定後，四邊中點也可以單獨拖曳
 */
class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private enum class Drag { NONE, MOVE, TL, TR, BL, BR, L, R, T, B }

    private val density = resources.displayMetrics.density
    private val touchSlop = 28f * density
    private val handleRadius = 9f * density

    private var bitmap: Bitmap? = null

    /** 裁切框，單位是「影像像素」座標。 */
    private val crop = RectF()

    // 影像在 View 上的顯示位置：screen = bitmap * scale + off
    private var scale = 1f
    private var offX = 0f
    private var offY = 0f

    private var drag = Drag.NONE
    private var lastX = 0f
    private var lastY = 0f

    /** 是否鎖定為證件比例。 */
    var lockAspect: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (value && bitmap != null) applyAspect()
            invalidate()
        }

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 30, 30, 30)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    // ------------------------------------------------------------------ 對外 API

    fun setBitmap(b: Bitmap) {
        bitmap = b
        layoutImage()
        resetCrop()
        invalidate()
    }

    /** 順時針旋轉 90 度，並重設裁切框。 */
    fun rotate90() {
        val b = bitmap ?: return
        val m = Matrix().apply { postRotate(90f) }
        bitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        layoutImage()
        resetCrop()
        invalidate()
    }

    /** 回傳裁切框內的影像（新的 Bitmap）。 */
    fun croppedBitmap(): Bitmap? {
        val b = bitmap ?: return null
        val l = crop.left.roundToInt().coerceIn(0, b.width - 1)
        val t = crop.top.roundToInt().coerceIn(0, b.height - 1)
        val w = crop.width().roundToInt().coerceIn(1, b.width - l)
        val h = crop.height().roundToInt().coerceIn(1, b.height - t)
        return Bitmap.createBitmap(b, l, t, w, h)
    }

    // ------------------------------------------------------------------ 版面

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutImage()
    }

    private fun layoutImage() {
        val b = bitmap ?: return
        if (width == 0 || height == 0) return
        val pad = 16f * density
        scale = min((width - 2 * pad) / b.width, (height - 2 * pad) / b.height)
        offX = (width - b.width * scale) / 2f
        offY = (height - b.height * scale) / 2f
    }

    private fun resetCrop() {
        val b = bitmap ?: return
        val bw = b.width.toFloat()
        val bh = b.height.toFloat()
        if (lockAspect) {
            var w = bw * 0.9f
            var h = w / CARD_ASPECT
            if (h > bh * 0.9f) {
                h = bh * 0.9f
                w = h * CARD_ASPECT
            }
            crop.set(bw / 2f - w / 2f, bh / 2f - h / 2f, bw / 2f + w / 2f, bh / 2f + h / 2f)
        } else {
            crop.set(bw * 0.05f, bh * 0.05f, bw * 0.95f, bh * 0.95f)
        }
    }

    /** 把目前的框收斂成證件比例（以中心為準，只會縮小不會超出）。 */
    private fun applyAspect() {
        val cx = crop.centerX()
        val cy = crop.centerY()
        var w = crop.width()
        var h = w / CARD_ASPECT
        if (h > crop.height()) {
            h = crop.height()
            w = h * CARD_ASPECT
        }
        crop.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    private fun cropOnScreen() = RectF(
        crop.left * scale + offX,
        crop.top * scale + offY,
        crop.right * scale + offX,
        crop.bottom * scale + offY
    )

    // ------------------------------------------------------------------ 繪製

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = bitmap ?: return

        val imageRect = RectF(offX, offY, offX + b.width * scale, offY + b.height * scale)
        canvas.drawBitmap(b, null, imageRect, bitmapPaint)

        val r = cropOnScreen()

        // 框外變暗
        canvas.save()
        canvas.clipOutRect(r)
        canvas.drawRect(imageRect, dimPaint)
        canvas.restore()

        // 框線與三等分輔助線
        canvas.drawRect(r, borderPaint)
        for (i in 1..2) {
            val x = r.left + r.width() * i / 3f
            val y = r.top + r.height() * i / 3f
            canvas.drawLine(x, r.top, x, r.bottom, gridPaint)
            canvas.drawLine(r.left, y, r.right, y, gridPaint)
        }

        // 四角把手
        drawHandle(canvas, r.left, r.top, handleRadius)
        drawHandle(canvas, r.right, r.top, handleRadius)
        drawHandle(canvas, r.left, r.bottom, handleRadius)
        drawHandle(canvas, r.right, r.bottom, handleRadius)

        // 未鎖定時，四邊中點也可拖曳
        if (!lockAspect) {
            val small = handleRadius * 0.7f
            drawHandle(canvas, r.centerX(), r.top, small)
            drawHandle(canvas, r.centerX(), r.bottom, small)
            drawHandle(canvas, r.left, r.centerY(), small)
            drawHandle(canvas, r.right, r.centerY(), small)
        }
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float, radius: Float) {
        canvas.drawCircle(x, y, radius, handleFill)
        canvas.drawCircle(x, y, radius, handleStroke)
    }

    // ------------------------------------------------------------------ 觸控

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                drag = hitTest(event.x, event.y)
                lastX = event.x
                lastY = event.y
                if (drag != Drag.NONE) parent?.requestDisallowInterceptTouchEvent(true)
                return drag != Drag.NONE
            }

            MotionEvent.ACTION_MOVE -> {
                if (drag == Drag.NONE) return false
                val dx = (event.x - lastX) / scale
                val dy = (event.y - lastY) / scale
                lastX = event.x
                lastY = event.y
                applyDrag(dx, dy)
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                drag = Drag.NONE
            }
        }
        return true
    }

    private fun hitTest(x: Float, y: Float): Drag {
        val r = cropOnScreen()
        fun near(px: Float, py: Float) = hypot(x - px, y - py) <= touchSlop

        if (near(r.left, r.top)) return Drag.TL
        if (near(r.right, r.top)) return Drag.TR
        if (near(r.left, r.bottom)) return Drag.BL
        if (near(r.right, r.bottom)) return Drag.BR

        if (!lockAspect) {
            if (near(r.left, r.centerY())) return Drag.L
            if (near(r.right, r.centerY())) return Drag.R
            if (near(r.centerX(), r.top)) return Drag.T
            if (near(r.centerX(), r.bottom)) return Drag.B
        }

        return if (r.contains(x, y)) Drag.MOVE else Drag.NONE
    }

    private fun applyDrag(dx: Float, dy: Float) {
        val b = bitmap ?: return
        val bw = b.width.toFloat()
        val bh = b.height.toFloat()
        val minSize = min(48f * density / scale, min(bw, bh) * 0.2f)

        when (drag) {
            Drag.MOVE -> {
                val nl = (crop.left + dx).coerceIn(0f, bw - crop.width())
                val nt = (crop.top + dy).coerceIn(0f, bh - crop.height())
                crop.offsetTo(nl, nt)
            }

            Drag.L -> crop.left = (crop.left + dx).coerceIn(0f, crop.right - minSize)
            Drag.R -> crop.right = (crop.right + dx).coerceIn(crop.left + minSize, bw)
            Drag.T -> crop.top = (crop.top + dy).coerceIn(0f, crop.bottom - minSize)
            Drag.B -> crop.bottom = (crop.bottom + dy).coerceIn(crop.top + minSize, bh)

            Drag.TL, Drag.TR, Drag.BL, Drag.BR -> dragCorner(dx, dy, bw, bh, minSize)
            Drag.NONE -> Unit
        }
    }

    private fun dragCorner(dx: Float, dy: Float, bw: Float, bh: Float, minSize: Float) {
        val movingLeft = drag == Drag.TL || drag == Drag.BL
        val movingTop = drag == Drag.TL || drag == Drag.TR

        // 固定不動的對角
        val fx = if (movingLeft) crop.right else crop.left
        val fy = if (movingTop) crop.bottom else crop.top

        // 被拖曳的角（先套用位移，再限制在影像範圍內）
        val mx = ((if (movingLeft) crop.left else crop.right) + dx).coerceIn(0f, bw)
        val my = ((if (movingTop) crop.top else crop.bottom) + dy).coerceIn(0f, bh)

        var w = abs(mx - fx)
        var h = abs(my - fy)

        if (lockAspect) {
            // 依較大的位移決定大小，再限制在影像可容納的範圍內
            val maxW = if (movingLeft) fx else bw - fx
            val maxH = if (movingTop) fy else bh - fy
            var nw = max(w, h * CARD_ASPECT)
            nw = min(nw, min(maxW, maxH * CARD_ASPECT))
            nw = max(nw, min(minSize, min(maxW, maxH * CARD_ASPECT)))
            w = nw
            h = nw / CARD_ASPECT
        } else {
            w = max(w, minSize)
            h = max(h, minSize)
        }

        val left = if (movingLeft) fx - w else fx
        val right = if (movingLeft) fx else fx + w
        val top = if (movingTop) fy - h else fy
        val bottom = if (movingTop) fy else fy + h
        crop.set(left, top, right, bottom)
    }

    private companion object {
        /** 證件長寬比 85.6 : 54 */
        const val CARD_ASPECT = 85.6f / 54f
    }
}
