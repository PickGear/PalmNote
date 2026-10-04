package com.palmnote.data

import android.content.Context
import java.io.File

/**
 * 演示图片的落盘仓库（生活 / 记账 / 物品共用）。
 *
 * 落到 `filesDir/images/`（**用户图片同一目录**，带 `demo_` 前缀区分）——
 * 该目录的所有页面渲染链路都经过真机验证，且会被备份打包。
 * 这与「备份忠实」政策一致：示例行进备份，示例图同样进备份。
 * （asset 协议直读试过：coil3 在真机上不渲染 `file:///android_asset/`，弃用。）
 *
 * 关闭演示时只删 `demo_*` 文件，用户图片分毫不碰。
 */
internal object DemoImageStore {

    private const val ASSET_DIR = "demo"
    private const val TARGET_DIR = "images"
    private const val PREFIX = "demo_"

    /** 总是覆盖写（播种低频，复制成本可忽略）；返回绝对路径。覆盖保证 APK 内插画
     *  随版本更新时，同名落盘文件不会停留在旧内容上。 */
    fun materialize(context: Context, assetName: String): String {
        val dir = File(context.filesDir, TARGET_DIR).apply { mkdirs() }
        val target = File(dir, PREFIX + assetName)
        context.assets.open("$ASSET_DIR/$assetName").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target.absolutePath
    }

    /** 关闭演示：只清示例图文件，用户图片不受影响。 */
    fun clear(context: Context) {
        File(context.filesDir, TARGET_DIR).listFiles()
            ?.filter { it.name.startsWith(PREFIX) }
            ?.forEach { it.delete() }
    }
}
