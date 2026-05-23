package `fun`.kirari.hanako.overlay

import android.graphics.Bitmap
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.data.AppSettings
import `fun`.kirari.hanako.data.ProcessingResult

internal val SheetDockOffset = 88.dp
internal val PanelHandleYOffset = 34.dp
internal const val SheetAnimationDurationMs = 260

internal enum class OverlaySheetMode {
    CROP,
    RESULT
}

internal data class OverlayUiState(
    val settings: AppSettings = AppSettings(),
    val screenshot: Bitmap? = null,
    val selectedBitmap: Bitmap? = null,
    val liveOcrText: String = "",
    val liveAnswerText: String = "",
    val result: ProcessingResult? = null,
    val error: String? = null,
    val working: Boolean = false,
    val sheetVisible: Boolean = false,
    val sheetMode: OverlaySheetMode = OverlaySheetMode.CROP
)

internal fun easeOutCubic(fraction: Float): Float {
    val inverse = 1f - fraction
    return 1f - inverse * inverse * inverse
}
