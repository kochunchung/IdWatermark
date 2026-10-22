package tw.example.idwatermark

import android.graphics.Bitmap

/**
 * MainActivity 與 CropActivity 之間傳遞影像用的暫存區。
 *
 * 證件影像不寫入磁碟，只在記憶體中交接；用完立刻清掉，避免佔用記憶體。
 */
object CropHandoff {
    /** 待裁切的原圖（MainActivity 放入，CropActivity 取用）。 */
    @Volatile
    var source: Bitmap? = null

    /** 裁切完成的結果（CropActivity 放入，MainActivity 取用）。 */
    @Volatile
    var result: Bitmap? = null

    /** true = 正面、false = 反面，只用來在裁切畫面顯示標題。 */
    @Volatile
    var isFront: Boolean = true

    fun clear() {
        source = null
        result = null
    }
}
