package com.example.cataas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.collection.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.cataas.databinding.ListItemBinding
import kotlinx.coroutines.*
import pl.droidsonroids.gif.GifDrawable
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import java.util.concurrent.TimeUnit


class CatsAdapter(private val applicationContext: Context) : RecyclerView.Adapter<CatsAdapter.CatViewHolder>() {

    companion object {

        val DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

        const val PAGE_SIZE = 15
        const val LOADING_THRESHOLD = 5

        const val CHANNEL_ID = "channelId"
    }

    private var loading = false
    private var allLoaded = false
    private val cats = mutableListOf<Cat>()
    private val catsCache: LruCache<String, Drawable>
    private val loadingCats = mutableSetOf<String>()

    init {
        val cacheSize = (Runtime.getRuntime().freeMemory() / 1024 / 8).toInt()
        catsCache = object : LruCache<String, Drawable>(cacheSize) {

            override fun sizeOf(key: String, value: Drawable) = when (value) {
                is GifDrawable -> (value.allocationByteCount / 1024).toInt()
                is BitmapDrawable -> value.bitmap.byteCount / 1024
                else -> 0
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            with(NotificationManagerCompat.from(applicationContext)) {
                createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Notification channel",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }
        }

        loadMoreCats()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CatViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val view = inflater.inflate(R.layout.list_item, parent, false)
        return CatViewHolder(view)
    }

    override fun onBindViewHolder(holder: CatViewHolder, position: Int) = holder.bind(cats[position])
    override fun getItemCount() = cats.size

    fun loadMoreCats(): Boolean {
        if (loading || allLoaded) {
            return allLoaded
        }

        loading = true
        val oldSize = cats.size

        CoroutineScope(Dispatchers.Default).launch {
            val loadingStartedTime = System.nanoTime()

            val loadedCats = try {
                CatAAS.rest.getCatsList(oldSize, PAGE_SIZE)
            } catch (e: IOException) {
                CoroutineScope(Dispatchers.Main).launch {
                    handleLoadingException(e)
                    loading = false
                }

                return@launch
            }

            val loadingElapsedTime = System.nanoTime() - loadingStartedTime
            CoroutineScope(Dispatchers.Main).launch {
                if (loadedCats.isNotEmpty()) {
                    cats.addAll(loadedCats)
                    notifyItemRangeInserted(oldSize, loadedCats.size)

                    val text = applicationContext.getString(R.string.notification_text,
                        loadedCats.size,
                        loadingElapsedTime.toDouble() / TimeUnit.SECONDS.toNanos(1),
                        cats.size)

                    val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle(applicationContext.getString(R.string.notification_title))
                        .setContentText(text)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)

                    val notification = NotificationCompat.BigTextStyle(notificationBuilder)
                        .bigText(text).build() ?: notificationBuilder.build()

                    with(NotificationManagerCompat.from(applicationContext)) {
                        notify(1, notification)
                    }
                }

                if (loadedCats.size < PAGE_SIZE) {
                    allLoaded = true
                }

                loading = false
            }
        }

        return allLoaded
    }

    private fun getCatImage(id: String, position: Int): Drawable? {
        return catsCache.get(id) ?: run {
            if (id !in loadingCats) {
                loadingCats.add(id)

                CoroutineScope(Dispatchers.Default).launch {
                    val image = try {
                        CatAAS.getCatImage(id) ?: return@launch
                    } catch (e: IOException) {
                        CoroutineScope(Dispatchers.Main).launch {
                            handleLoadingException(e)
                        }

                        return@launch
                    }

                    catsCache.put(id, image)

                    CoroutineScope(Dispatchers.Main).launch {
                        loadingCats.remove(id)
                        notifyItemChanged(position)
                    }
                }
            }

            return@run null
        }
    }

    private fun handleLoadingException(e: Exception) {
        e.printStackTrace()

        Toast.makeText(
            applicationContext,
            applicationContext.getString(R.string.couldnt_load_data),
            Toast.LENGTH_SHORT
        ).show()
    }

    inner class CatViewHolder(root: View) : RecyclerView.ViewHolder(root) {

        private val binding = ListItemBinding.bind(root)

        fun bind(cat: Cat) {
            with (binding) {
                val image = getCatImage(cat.id, this@CatViewHolder.bindingAdapterPosition)

                if (image != null) {
                    imageView.setImageDrawable(image)
                    root.setOnClickListener(CatViewOnClick(cat.id))
                } else {
                    imageView.setImageResource(android.R.drawable.ic_popup_sync)
                    root.setOnClickListener(null)
                }

                createdAtView.text = applicationContext.getString(R.string.created_at, DATE_FORMAT.format(cat.createdAt))

                if (cat.tags.isEmpty()) {
                    tagsView.visibility = View.GONE
                } else {
                    tagsView.visibility = View.VISIBLE
                    tagsView.text = applicationContext.getString(R.string.tags, cat.tags.joinToString(", "))
                }
            }
        }
    }

    inner class CatViewOnClick(private val id: String): View.OnClickListener {

        override fun onClick(v: View?) {
            CoroutineScope(Dispatchers.Default).launch {
                if (!shareWithImage(id)) {
                    withContext(Dispatchers.Main) {
                         shareWithoutImage(id)
                    }
                }
            }
        }

        private fun shareWithoutImage(id: String) {
            val url = CatAAS.getCatImageUrl(id)

            val intent = Intent(Intent.ACTION_SEND)
            intent.putExtra(Intent.EXTRA_TEXT, url)
            intent.type = "text/plain"

            val chooser = Intent.createChooser(intent, applicationContext.getString(R.string.share))
            applicationContext.startActivity(chooser)
        }

        private suspend fun shareWithImage(id: String): Boolean {
            try {
                val imageBytes = CatAAS.getCatImageBytes(id) ?: return false

                val contentValues = ContentValues()
                contentValues.put(MediaStore.Images.Media.TITLE, CatAAS.getCatImageUrl(id))
                contentValues.put(MediaStore.Images.Media.MIME_TYPE, imageBytes.second)

                val uri = applicationContext.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues,
                ) ?: return false

                @Suppress("BlockingMethodInNonBlockingContext")
                val result = withContext(Dispatchers.IO) {
                    val stream = applicationContext.contentResolver.openOutputStream(uri)
                        ?: return@withContext false
                    stream.write(imageBytes.first)
                    stream.close()

                    return@withContext true
                }

                if (!result) {
                    return false
                }

                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "image/*"
                    intent.putExtra(Intent.EXTRA_TEXT, CatAAS.getCatImageUrl(id))
                    intent.putExtra(Intent.EXTRA_STREAM, uri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    val chooser =
                        Intent.createChooser(intent, applicationContext.getString(R.string.share))
                    applicationContext.startActivity(chooser)
                }

                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
    }
}
