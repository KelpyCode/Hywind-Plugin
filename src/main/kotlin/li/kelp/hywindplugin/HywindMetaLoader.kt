package li.kelp.hywindplugin

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class HyClass(
    @SerializedName("className") val className: String,
    val description: String?,
    val previewColor: ColorObject?,
    val code: String?,
    val origin: String?
)

data class HyProp(
    @SerializedName("propName") val propName: String,
    val description: String?,
    val code: String?,
    val origin: String?
)

data class ColorObject(val r: Int, val g: Int, val b: Int)

data class HywindMeta(val classes: List<HyClass> = emptyList(), val props: List<HyProp> = emptyList())

object HywindMetaLoader {
    @Volatile
    private var meta: HywindMeta = loadDefaultMeta()

    private fun loadDefaultMeta(): HywindMeta {
        val stream = HywindMetaLoader::class.java.classLoader.getResourceAsStream("hywind-meta.json")
            ?: return HywindMeta()
        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            return Gson().fromJson(reader, HywindMeta::class.java)
        }
    }

    fun getClasses(): List<HyClass> = meta.classes
    fun getProps(): List<HyProp> = meta.props

    // Reload from configured URL in settings; caches to a temp file and updates in-memory meta
    fun reloadFromRemote(): Boolean {
        val settings = HywindSettings.getInstance()
        val url = settings.metaUrl()
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return false
            }
            val tmp = File.createTempFile("hywind-meta", ".json")
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            conn.disconnect()

            // parse
            val parsed = InputStreamReader(tmp.inputStream(), Charsets.UTF_8).use { reader ->
                Gson().fromJson(reader, HywindMeta::class.java)
            }

            // apply origin override if set
            val origin = settings.originOverride()
            val adjusted = if (!origin.isNullOrBlank()) {
                HywindMeta(
                    classes = parsed.classes.map { it.copy(origin = origin) },
                    props = parsed.props.map { it.copy(origin = origin) }
                )
            } else parsed

            meta = adjusted
            true
        } catch (e: Exception) {
            false
        }
    }
}
