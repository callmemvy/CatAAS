package com.example.cataas

import android.graphics.drawable.Drawable
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.headersContentLength
import okhttp3.logging.HttpLoggingInterceptor
import pl.droidsonroids.gif.GifDrawable
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import ru.gildor.coroutines.okhttp.await
import java.io.ByteArrayInputStream
import java.io.IOException
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.ZonedDateTime

object CatAAS {

    private const val BASE_URL = "https://cataas.com"

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        })
        .build()

    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, object : JsonDeserializer<LocalDateTime?> {
            override fun deserialize(
                json: JsonElement?,
                typeOfT: Type?,
                context: JsonDeserializationContext?
            ): LocalDateTime? = if (json != null) {
                ZonedDateTime.parse(json.asJsonPrimitive.asString).toLocalDateTime()
            } else {
                null
            }
        })
        .create()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(httpClient)
        .build()

    val rest: RestApi = retrofit.create(RestApi::class.java)

    interface RestApi {

        @GET("/api/cats")
        suspend fun getCatsList(
            @Query("skip") skip: Int,
            @Query("limit") limit: Int,
        ): List<Cat>
    }

    fun getCatImageUrl(id: String): String = "$BASE_URL/cat/$id"

    suspend fun getCatImageBytes(id: String): Pair<ByteArray, String>? {
        val url = getCatImageUrl(id)

        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).await()
        val stream = response.body?.byteStream() ?: return null
        val type = response.headers["Content-Type"] ?: "image/jpeg"

        if (Runtime.getRuntime().freeMemory() < response.headersContentLength()) {
            System.gc()
            return null
        }

        return stream.readBytes() to type
    }

    suspend fun getCatImage(id: String): Drawable? {
        val data = (getCatImageBytes(id) ?: return null).first

        return try {
            val image = withContext(Dispatchers.IO) {
                @Suppress("BlockingMethodInNonBlockingContext")
                GifDrawable(data)
            }

            image
        } catch (e: IOException) {
            Drawable.createFromStream(ByteArrayInputStream(data), getCatImageUrl(id))
        }
    }
}
