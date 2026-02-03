package li.kelp.hywindplugin

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.InputStreamReader

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
    private val meta: HywindMeta by lazy {
        val stream = HywindMetaLoader::class.java.classLoader.getResourceAsStream("hywind-meta.json")
            ?: return@lazy HywindMeta()
        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            Gson().fromJson(reader, HywindMeta::class.java)
        }
    }

    fun getClasses(): List<HyClass> = meta.classes
    fun getProps(): List<HyProp> = meta.props
}
