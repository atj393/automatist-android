package com.automatist.app.data.offline

import android.app.ActivityManager
import android.content.Context
import com.automatist.app.domain.offline.OfflineModelCatalog
import com.automatist.app.domain.offline.OfflineModelEntry
import com.automatist.app.domain.offline.OfflineModelStatus
import com.automatist.app.di.FileDownloadClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages downloading, storage, and removal of app-managed offline AI model files.
 *
 * Responsibilities:
 * - Downloads model files from a remote URL to app-internal storage
 * - Verifies file integrity via SHA-256 checksum (when available)
 * - Deletes model files on user-triggered removal
 * - Checks device capability (RAM, storage) before download
 * - Reports whether a downloaded model file exists on disk
 *
 * This class does NOT manage system-managed models (AICore / Gemini Nano).
 * It only handles [OfflineRuntimeType.DOWNLOADABLE] models.
 *
 * Model files are stored in [Context.getFilesDir]/offline_models/ so they are:
 * - Private to the app (not accessible by other apps)
 * - Automatically removed on app uninstall
 * - Not included in device backups by default
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @FileDownloadClient private val okHttpClient: OkHttpClient
) {
    /** Active OkHttp calls keyed by model file name, for cancellation support. */
    private val activeCalls = ConcurrentHashMap<String, Call>()

    private val modelsDir: File
        get() = File(context.filesDir, "offline_models").also { it.mkdirs() }

    /**
     * Returns the local file path for a downloaded model, or null if not downloaded.
     */
    fun getModelFile(entry: OfflineModelEntry): File? {
        val fileName = entry.modelFileName ?: return null
        val file = File(modelsDir, fileName)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Checks whether this device meets the minimum requirements for a downloadable model.
     *
     * @return null if the device is suitable, or a human-readable reason string if not.
     */
    fun checkDeviceSuitability(entry: OfflineModelEntry): String? {
        // Check minimum RAM
        if (entry.minimumRamMb > 0) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val totalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt()
            if (totalRamMb < entry.minimumRamMb) {
                return "This device has ${totalRamMb} MB RAM, but ${entry.displayName} " +
                    "requires at least ${entry.minimumRamMb} MB."
            }
        }

        // Check available storage
        if (entry.downloadSizeBytes > 0) {
            val usableSpace = modelsDir.usableSpace
            // Require 1.5x the download size to account for temp file during download
            val requiredSpace = (entry.downloadSizeBytes * 1.5).toLong()
            if (usableSpace < requiredSpace) {
                val requiredGb = String.format("%.1f", requiredSpace / 1_000_000_000.0)
                val availableGb = String.format("%.1f", usableSpace / 1_000_000_000.0)
                return "Not enough storage. ${entry.displayName} needs ~${requiredGb} GB free, " +
                    "but only ${availableGb} GB is available."
            }
        }

        return null // Device is suitable
    }

    /**
     * Downloads a model file from [entry]'s [OfflineModelEntry.downloadUrl] to app-internal storage.
     *
     * @param entry The model entry with download metadata.
     * @param onProgress Called periodically with (bytesDownloaded, totalBytes).
     *   totalBytes is -1 if the server does not provide Content-Length.
     * @return [OfflineModelStatus.INSTALLED] on success, [OfflineModelStatus.FAILED] on failure,
     *   [OfflineModelStatus.UNSUPPORTED] if device does not meet requirements.
     * @throws CancellationException if the coroutine is cancelled (partial file is cleaned up).
     */
    suspend fun downloadModel(
        entry: OfflineModelEntry,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): OfflineModelStatus {
        val url = entry.downloadUrl
            ?: return OfflineModelStatus.FAILED
        val fileName = entry.modelFileName
            ?: return OfflineModelStatus.FAILED

        val parsedUrl = url.toHttpUrlOrNull()
        if (parsedUrl == null || !isSafeModelUrl(parsedUrl)) {
            return OfflineModelStatus.FAILED
        }

        val suitabilityIssue = checkDeviceSuitability(entry)
        if (suitabilityIssue != null) {
            return OfflineModelStatus.UNSUPPORTED
        }

        val targetFile = File(modelsDir, fileName)
        val tempFile = File(modelsDir, "$fileName.tmp")

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val call = okHttpClient.newCall(request)
                activeCalls[fileName] = call

                val response = call.execute()

                // GitHub Releases and Hugging Face may redirect to their download CDN.
                // The final resolved request must still be an ordinary HTTPS model download.
                if (!isSafeModelUrl(response.request.url)) {
                    response.close()
                    return@withContext OfflineModelStatus.FAILED
                }

                if (!response.isSuccessful) {
                    response.close()
                    return@withContext OfflineModelStatus.FAILED
                }

                val body = response.body ?: run {
                    response.close()
                    return@withContext OfflineModelStatus.FAILED
                }

                val serverContentLength = body.contentLength() // -1 if unknown
                // Use the server-reported size when available; otherwise fall back to
                // the catalog's known size so the UI can show determinate progress.
                // GitHub Releases CDN may use chunked transfer encoding on some edges,
                // which omits Content-Length and causes body.contentLength() to return -1.
                val effectiveTotalBytes = if (serverContentLength > 0) {
                    serverContentLength
                } else if (entry.downloadSizeBytes > 0) {
                    entry.downloadSizeBytes
                } else {
                    -1L // truly unknown — UI will show indeterminate progress
                }
                var totalDownloaded = 0L
                var lastReportedBytes = 0L

                tempFile.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            // Check for coroutine cancellation between chunks
                            ensureActive()
                            output.write(buffer, 0, bytesRead)
                            totalDownloaded += bytesRead
                            // Report progress every ~500 KB to avoid excessive UI updates
                            // (8 KB chunks would fire ~68,000 callbacks for a 557 MB file)
                            if (totalDownloaded - lastReportedBytes >= 500_000) {
                                onProgress(totalDownloaded, effectiveTotalBytes)
                                lastReportedBytes = totalDownloaded
                            }
                        }
                    }
                }
                // Final progress report to ensure 100% is reached
                onProgress(totalDownloaded, effectiveTotalBytes)
                response.close()

                // A declared size protects users from a truncated or unexpectedly
                // large payload before the model file reaches the native runtime.
                if (entry.downloadSizeBytes > 0 && totalDownloaded != entry.downloadSizeBytes) {
                    tempFile.delete()
                    return@withContext OfflineModelStatus.FAILED
                }

                // Verify SHA-256 checksum if provided
                if (entry.fileSha256 != null) {
                    val actualHash = sha256(tempFile)
                    if (!actualHash.equals(entry.fileSha256, ignoreCase = true)) {
                        tempFile.delete()
                        return@withContext OfflineModelStatus.FAILED
                    }
                }

                // Atomic rename: temp → final
                if (tempFile.renameTo(targetFile)) {
                    OfflineModelStatus.INSTALLED
                } else {
                    tempFile.delete()
                    OfflineModelStatus.FAILED
                }
            } catch (e: CancellationException) {
                tempFile.delete()
                throw e
            } catch (e: IOException) {
                tempFile.delete()
                OfflineModelStatus.FAILED
            } catch (e: Exception) {
                tempFile.delete()
                OfflineModelStatus.FAILED
            } finally {
                activeCalls.remove(fileName)
            }
        }
    }

    /**
     * Cancels an in-progress download for the given model.
     *
     * Cancels the active OkHttp [Call], which causes the blocking read to throw [IOException].
     * The temp file is cleaned up by the [downloadModel] error handlers.
     * Safe to call if no download is in progress (no-op).
     */
    fun cancelActiveDownload(entry: OfflineModelEntry) {
        val fileName = entry.modelFileName ?: return
        activeCalls.remove(fileName)?.cancel()
    }

    /**
     * Deletes a downloaded model file from app-internal storage.
     *
     * @return true if the file was deleted or didn't exist, false if deletion failed.
     */
    fun deleteModel(entry: OfflineModelEntry): Boolean {
        val fileName = entry.modelFileName ?: return true
        val file = File(modelsDir, fileName)
        val tempFile = File(modelsDir, "$fileName.tmp")
        tempFile.delete() // Clean up any partial download
        return !file.exists() || file.delete()
    }

    private fun isSafeModelUrl(url: HttpUrl): Boolean =
        url.isHttps &&
            url.port == 443 &&
            url.username.isEmpty() &&
            url.password.isEmpty()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
