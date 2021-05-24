package me.alex

import com.jayway.jsonpath.JsonPath
import net.dv8tion.jda.api.entities.Message
import okhttp3.*
import org.jetbrains.annotations.NotNull
import java.util.*
import java.util.function.Consumer
import kotlin.concurrent.thread

private val client = OkHttpClient.Builder().followRedirects(true).build()

class ImgurRequest(private val clientId: String, private val data: ByteArray) {
    fun queue(@NotNull urlSuccess: Consumer<String>) {
        thread(start = true) {
            val dataUrl = "data:image/png;base64,${Base64.getEncoder().encodeToString(data)}"
            val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", dataUrl)
                    .addFormDataPart("upload_preset", "x8fnpsas").build()
            val req = Request.Builder().url("https://api.cloudinary.com/v1_1/de42sby5l/image/upload")
                    .post(body)
                    .build()
            val res = client.newCall(req).execute()
            res.body()?.string()?.let { urlSuccess.accept(JsonPath.parse(it).read<String?>("$.secure_url").replace("\\", "")) }
            res.close()
        }
    }
}