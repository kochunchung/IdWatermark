package tw.example.idwatermark

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import tw.example.idwatermark.databinding.ActivityCropBinding

/**
 * 裁切畫面：讓使用者把照片裁到只剩證件邊緣。
 * 影像透過 [CropHandoff] 在記憶體中交接，不寫入磁碟。
 */
class CropActivity : AppCompatActivity() {

    private lateinit var b: ActivityCropBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val source = CropHandoff.source
        if (source == null) {
            // 例如程序被系統回收後重建，已沒有原圖可用
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        b = ActivityCropBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tvCropTitle.setText(
            if (CropHandoff.isFront) R.string.crop_title_front else R.string.crop_title_back
        )

        // Manifest 已設定 configChanges，畫面旋轉時不會重建本 Activity，裁切框與旋轉結果會保留
        b.cropView.setBitmap(source)

        b.swLock.isChecked = true
        b.swLock.setOnCheckedChangeListener { _, checked -> b.cropView.lockAspect = checked }

        b.btnRotate.setOnClickListener { b.cropView.rotate90() }

        b.btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        b.btnOk.setOnClickListener {
            val out = b.cropView.croppedBitmap()
            if (out == null) {
                setResult(RESULT_CANCELED)
            } else {
                CropHandoff.result = out
                setResult(RESULT_OK)
            }
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 離開裁切畫面後就不再需要原圖，釋放記憶體
        if (isFinishing) CropHandoff.source = null
    }
}
