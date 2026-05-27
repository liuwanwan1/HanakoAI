package `fun`.kirari.hanako

import android.content.Context
import `fun`.kirari.hanako.data.SettingsRepository
import `fun`.kirari.hanako.data.SettingsStore
import `fun`.kirari.hanako.localocr.LocalOcrManager
import `fun`.kirari.hanako.network.AiGateway
import `fun`.kirari.hanako.network.UnifiedLLMClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal class AppContainer(appContext: Context) {
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    val settingsStore = SettingsStore(appContext)
    val aiGateway = AiGateway(client = okHttpClient)
    val unifiedLLMClient = UnifiedLLMClient(client = okHttpClient)
    val localOcrManager = LocalOcrManager(appContext)
    val settingsRepository = SettingsRepository(settingsStore)
}
