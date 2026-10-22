package tw.example.idwatermark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 負責把「正面 + 反面」排成一張 A4，並在每張證件影像上壓浮水印。
 *
 * 證件以真實尺寸（85.6 x 54 mm，ISO/IEC 7810 ID-1，台灣身分證同此規格）印在 A4 上，
 * 所以呼叫端必須先把照片裁到只剩證件邊緣，尺寸才會正確。
 *
 * 浮水印是直接畫進像素裡（不是 PDF 的獨立文字圖層），
 * 所以匯出的 PDF 無法用 PDF 編輯器把浮水印單獨移除。
 */
object WatermarkRenderer {

    enum class Style { TILED, CENTER }

    /** 排版解析度。證件只佔 85.6 mm 寬，用 300 dpi 才能保留證件上的細小文字。 */
    private const val DPI = 300f

    private fun mm(v: Float): Float = v / 25.4f * DPI

    // A4 = 210 x 297 mm → 300 dpi 約 2480 x 3508 像素
    private val PAGE_W = mm(210f).roundToInt()
    private val PAGE_H = mm(297f).roundToInt()

    // 證件真實尺寸
    private val CARD_W = mm(85.6f)
    private val CARD_H = mm(54f)

    // 版面：兩張證件水平置中，由上而下排列
    private val TOP_MARGIN = mm(40f)
    private val CARD_GAP = mm(15f)

    // PDF 頁面尺寸單位是 point (1/72 inch)，A4 = 595 x 842。
    private const val PDF_W = 595
    private const val PDF_H = 842

    private val WATERMARK_COLOR = Color.rgb(211, 47, 47)

    fun render(front: Bitmap, back: Bitmap, text: String, style: Style): Bitmap {
        val page = Bitmap.createBitmap(PAGE_W, PAGE_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)

        var y = TOP_MARGIN
        y = drawOne(canvas, front, y, text, style) + CARD_GAP
        drawOne(canvas, back, y, text, style)
        return page
    }

    /** 把整頁 bitmap 放進單頁 A4 PDF 並寫出。 */
    fun writePdf(pageBitmap: Bitmap, out: OutputStream) {
        val doc = PdfDocument()
        try {
            val info = PdfDocument.PageInfo.Builder(PDF_W, PDF_H, 1).create()
            val pdfPage = doc.startPage(info)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            pdfPage.canvas.drawBitmap(pageBitmap, null, Rect(0, 0, PDF_W, PDF_H), paint)
            doc.finishPage(pdfPage)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    // ---------------------------------------------------------------------------------------

    /**
     * 在 [boxTop] 開始的位置畫一張證件，回傳這張證件所佔區塊的底部 y 座標。
     */
    private fun drawOne(
        canvas: Canvas,
        src: Bitmap,
        boxTop: Float,
        text: String,
        style: Style
    ): Float {
        // 直式（高 > 寬）的影像用 54 x 85.6 mm 的框，橫式用 85.6 x 54 mm
        val portrait = src.height > src.width
        val boxW = if (portrait) CARD_H else CARD_W
        val boxH = if (portrait) CARD_W else CARD_H

        val dst = fit(src, boxTop, boxW, boxH)

        canvas.drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))

        // 細邊框，讓證件邊界在白底上看得清楚
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            this.style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRect(dst, border)

        // 浮水印只畫在證件範圍內（裁掉超出的部分）
        canvas.save()
        canvas.clipRect(dst)
        when (style) {
            Style.TILED -> drawTiled(canvas, dst, text)
            Style.CENTER -> drawCenter(canvas, dst, text)
        }
        canvas.restore()

        return boxTop + boxH
    }

    /**
     * 等比例縮放到「不超過」證件框，水平置中於 A4、垂直置中於該框。
     * 影像已裁成證件比例時，會剛好等於 85.6 x 54 mm。
     */
    private fun fit(src: Bitmap, boxTop: Float, boxW: Float, boxH: Float): RectF {
        val scale = min(boxW / src.width, boxH / src.height)
        val w = src.width * scale
        val h = src.height * scale
        val left = (PAGE_W - w) / 2f
        val top = boxTop + (boxH - h) / 2f
        return RectF(left, top, left + w, top + h)
    }

    private fun drawTiled(canvas: Canvas, dst: RectF, text: String) {
        val size = dst.width() / 22f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WATERMARK_COLOR
            alpha = 120
            textSize = size
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(3f, 0f, 0f, Color.WHITE)
        }
        val textW = paint.measureText(text)
        val stepX = textW + size * 2f
        val stepY = size * 3.2f
        val half = hypot(dst.width(), dst.height()) / 2f + stepX

        canvas.save()
        canvas.rotate(-30f, dst.centerX(), dst.centerY())

        var row = 0
        var y = dst.centerY() - half
        while (y < dst.centerY() + half) {
            val offset = if (row % 2 == 0) 0f else stepX / 2f
            var x = dst.centerX() - half - offset
            while (x < dst.centerX() + half) {
                canvas.drawText(text, x, y, paint)
                x += stepX
            }
            y += stepY
            row++
        }
        canvas.restore()
    }

    private fun drawCenter(canvas: Canvas, dst: RectF, text: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        // 先用 100px 量寬度，再縮放到佔證件寬度約 92%，同時限制高度不要過大
        paint.textSize = 100f
        val widthAt100 = paint.measureText(text)
        val fitted = 100f * (dst.width() * 0.92f) / widthAt100
        paint.textSize = min(fitted, dst.height() / 5f)

        val fm = paint.fontMetrics
        val baseline = dst.centerY() - (fm.ascent + fm.descent) / 2f

        // 先畫白色描邊，再畫紅色半透明文字，深色或紅色區域上也看得清楚
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = paint.textSize / 8f
        paint.color = Color.WHITE
        paint.alpha = 180
        canvas.drawText(text, dst.centerX(), baseline, paint)

        paint.style = Paint.Style.FILL
        paint.color = WATERMARK_COLOR
        paint.alpha = 170
        canvas.drawText(text, dst.centerX(), baseline, paint)
    }
}
