package tw.example.idwatermark

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import tw.example.idwatermark.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var frontBitmap: Bitmap? = null
    private var backBitmap: Bitmap? = null
    private var lastPdfUri: Uri? = null

    private val date: Calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN)

    /** 目前正在為哪一面挑圖：true = 正面、false = 反面 */
    private var pickingFront = true

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) loadImage(uri, pickingFront)
        }

    /** 裁切畫面回來：確定 → 取出裁好的影像；取消 → 什麼都不改。 */
    private val cropImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val cropped = CropHandoff.result
            CropHandoff.clear()
            if (res.resultCode == RESULT_OK && cropped != null) {
                setImage(pickingFront, cropped)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.etDate.setText(dateFormat.format(date.time))
        b.etDate.setOnClickListener { showDatePicker() }

        val pickFront = View.OnClickListener { startPick(true) }
        val pickBack = View.OnClickListener { startPick(false) }
        b.btnFront.setOnClickListener(pickFront)
        b.ivFront.setOnClickListener(pickFront)
        b.btnBack.setOnClickListener(pickBack)
        b.ivBack.setOnClickListener(pickBack)

        b.btnPreview.setOnClickListener { generate(export = false) }
        b.btnExport.setOnClickListener { generate(export = true) }
        b.btnShare.setOnClickListener { sharePdf() }
        b.btnOpen.setOnClickListener { openPdf() }
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdown()
    }

    // ------------------------------------------------------------------ 選圖

    private fun startPick(front: Boolean) {
        pickingFront = front
        pickImage.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /** 解碼挑選的照片，接著進入裁切畫面；裁切確定後才會顯示在主畫面。 */
    private fun loadImage(uri: Uri, front: Boolean) {
        setBusy(true)
        worker.execute {
            val result = runCatching { decode(uri) }
            main.post {
                setBusy(false)
                result.onSuccess { bmp ->
                    CropHandoff.source = bmp
                    CropHandoff.result = null
                    CropHandoff.isFront = front
                    cropImage.launch(Intent(this, CropActivity::class.java))
                }.onFailure {
                    Toast.makeText(this, R.string.err_load_image, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setImage(front: Boolean, bmp: Bitmap) {
        if (front) {
            frontBitmap = bmp
            b.ivFront.setImageBitmap(bmp)
        } else {
            backBitmap = bmp
            b.ivBack.setImageBitmap(bmp)
        }
        // 圖片換了，之前匯出的 PDF 連結不再對應目前內容
        hideResult()
    }

    /** 解碼圖片：自動套用 EXIF 旋轉，並限制最長邊避免記憶體不足。 */
    private fun decode(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // 軟體 bitmap 才能被畫到另一張 bitmap 的 Canvas 上
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt(),
                    (info.size.height * scale).roundToInt()
                )
            }
        }
    }

    // ------------------------------------------------------------------ 日期

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                date.set(year, month, day)
                b.etDate.setText(dateFormat.format(date.time))
                hideResult()
            },
            date.get(Calendar.YEAR),
            date.get(Calendar.MONTH),
            date.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ------------------------------------------------------------------ 產生 / 匯出

    private fun generate(export: Boolean) {
        val front = frontBitmap
        val back = backBitmap
        if (front == null || back == null) {
            Toast.makeText(this, R.string.err_need_images, Toast.LENGTH_SHORT).show()
            return
        }
        val purpose = b.etPurpose.text?.toString()?.trim().orEmpty()
        if (purpose.isEmpty()) {
            Toast.makeText(this, R.string.err_need_purpose, Toast.LENGTH_SHORT).show()
            return
        }

        val text = getString(R.string.watermark_text, purpose, dateFormat.format(date.time))
        val style = if (b.rbCenter.isChecked) {
            WatermarkRenderer.Style.CENTER
        } else {
            WatermarkRenderer.Style.TILED
        }

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val page = WatermarkRenderer.render(front, back, text, style)
                val uri = if (export) savePdfToDownloads(page) else null
                page to uri
            }
            main.post {
                setBusy(false)
                result.onSuccess { (page, uri) ->
                    b.ivPreview.setImageBitmap(page)
                    b.ivPreview.visibility = View.VISIBLE
                    b.tvPreviewLabel.visibility = View.VISIBLE
                    if (uri != null) {
                        lastPdfUri = uri
                        b.tvResult.text = getString(R.string.exported, lastPdfName)
                        b.resultBar.visibility = View.VISIBLE
                    }
                }.onFailure { e ->
                    Toast.makeText(
                        this,
                        getString(R.string.err_export, e.message ?: e.javaClass.simpleName),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private var lastPdfName: String = ""

    /** 用 MediaStore 寫入「下載」資料夾（Android 10 以上不需要儲存權限）。 */
    private fun savePdfToDownloads(page: Bitmap): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "身分證影本_$stamp.pdf"

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("無法建立檔案")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                WatermarkRenderer.writePdf(page, out)
            } ?: error("無法寫入檔案")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        lastPdfName = name
        return uri
    }

    // ------------------------------------------------------------------ 分享 / 開啟

    private fun sharePdf() {
        val uri = lastPdfUri ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_title)))
    }

    private fun openPdf() {
        val uri = lastPdfUri ?: return
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(view)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_viewer, Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------ UI 狀態

    private fun setBusy(busy: Boolean) {
        b.progress.visibility = if (busy) View.VISIBLE else View.GONE
        b.btnPreview.isEnabled = !busy
        b.btnExport.isEnabled = !busy
        b.btnFront.isEnabled = !busy
        b.btnBack.isEnabled = !busy
    }

    private fun hideResult() {
        lastPdfUri = null
        b.resultBar.visibility = View.GONE
    }

    private companion object {
        /**
         * 解碼時最長邊上限（像素）。證件在 A4 上只有 85.6 mm 寬（300 dpi 約 1011 像素），
         * 照片裡證件通常只佔一部分，所以保留 3000px，裁切後才有足夠細節。
         */
        const val MAX_SIDE = 3000
    }
}
