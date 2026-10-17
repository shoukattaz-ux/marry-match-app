package com.example.data.media

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.MarryMatchApplication
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.concurrent.TimeUnit

/** Names and data keys shared by the workers and the chat screen. */
object MediaWork {
    const val KEY_MESSAGE_ID = "message_id"
    const val KEY_MATCH_ID = "match_id"
    const val KEY_TYPE = "type"
    const val KEY_PATH = "path"
    const val KEY_THUMB = "thumb"
    const val KEY_BYTES = "bytes"
    const val KEY_PROGRESS = "progress"
    const val KEY_REASON = "reason"

    fun sendName(messageId: String) = "media-send-$messageId"
    fun downloadName(messageId: String) = "media-download-$messageId"
    fun chatTag(matchId: String) = "media-chat-$matchId"
    fun messageTag(messageId: String) = "media-msg-$messageId"
    const val TAG_SEND = "media-send"
    const val TAG_DOWNLOAD = "media-download"

    /** The message id stored in a work's tags. */
    fun messageIdOf(tags: Set<String>): String? =
        tags.firstOrNull { it.startsWith("media-msg-") }?.removePrefix("media-msg-")
}

private fun Context.mediaRemote(): ChatMediaRemote? =
    (applicationContext as? MarryMatchApplication)?.repository?.supabase?.let { ChatMediaRemote(it) }

/**
 * Sends one media message: uploads the file (and thumbnail) to chat-media/<match>/<message>/,
 * then inserts the message row with the phone's message id. Waits for a connection (the bubble
 * shows a clock meanwhile), survives app restarts, and retries at most 3 times with backoff.
 */
class SendMediaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val messageId = inputData.getString(MediaWork.KEY_MESSAGE_ID) ?: return Result.failure()
        val pending = Outbox.read(applicationContext, messageId) ?: return Result.failure()
        val remote = applicationContext.mediaRemote() ?: return Result.retry()
        val file = Outbox.file(applicationContext, pending)
        val thumb = Outbox.thumb(applicationContext, pending)
        if (!file.isFile) return fail(pending, "media_file_missing")
        if (file.length() > ChatMediaRules.MAX_BYTES) return fail(pending, "media_too_large")
        if (!ChatMediaRules.mimeAllowed(pending.mediaType.mime)) return fail(pending, "media_type_refused")

        val mediaPath = ChatMediaRules.remotePath(pending.matchId, messageId, pending.mediaType.fileName)
        val thumbPath = thumb?.takeIf { it.isFile }?.let { ChatMediaRules.remotePath(pending.matchId, messageId, ChatMediaRules.THUMB_FILE) }
        val total = file.length() + (thumb?.takeIf { it.isFile }?.length() ?: 0L)
        var lastPercent = -1

        // Called from the upload loop; setProgressAsync does not suspend.
        fun report(done: Long) {
            val percent = ((done * 100) / total.coerceAtLeast(1)).toInt().coerceIn(0, 99)
            if (percent != lastPercent) {
                lastPercent = percent
                setProgressAsync(workDataOf(MediaWork.KEY_PROGRESS to percent))
            }
        }

        return try {
            report(0)
            var offset = 0L
            if (thumbPath != null && thumb != null) {
                remote.upload(thumbPath, thumb, ChatMediaRules.THUMB_MIME, { sent, _ -> report(sent) }, { isStopped })
                offset = thumb.length()
            }
            remote.upload(mediaPath, file, pending.mediaType.mime, { sent, _ -> report(offset + sent) }, { isStopped })
            remote.insertMessage(pending, mediaPath, thumbPath)
            // Keep the sent file as this phone's own copy, so it shows without downloading.
            val local = MediaFiles.localFile(applicationContext, pending.matchId, messageId, pending.mediaType)
            local.parentFile?.mkdirs()
            if (!file.renameTo(local)) file.copyTo(local, overwrite = true)
            Outbox.remove(applicationContext, messageId)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: MediaCancelledException) {
            Result.failure()
        } catch (e: MediaRefusedException) {
            fail(pending, if (e.code == 413) "media_too_large" else "media_send_refused")
        } catch (e: NotSignedInException) {
            fail(pending, "media_send_failed")
        } catch (e: Throwable) {
            if (runAttemptCount + 1 >= ChatMediaRules.MAX_AUTO_RETRIES) fail(pending, "media_send_failed") else Result.retry()
        }
    }

    private fun fail(pending: PendingMedia, key: String): Result {
        Outbox.write(applicationContext, pending.copy(failed = true, failureKey = key))
        return Result.failure(workDataOf(MediaWork.KEY_REASON to key))
    }
}

/**
 * Downloads one received file into the app's private storage, checks its size against
 * media_bytes, marks the message downloaded and then removes the file from the server
 * (unless it is reported). A file that is gone shows as expired.
 */
class DownloadMediaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val messageId = inputData.getString(MediaWork.KEY_MESSAGE_ID) ?: return Result.failure()
        val matchId = inputData.getString(MediaWork.KEY_MATCH_ID) ?: return Result.failure()
        val type = MediaType.fromWire(inputData.getString(MediaWork.KEY_TYPE)) ?: return Result.failure()
        val path = inputData.getString(MediaWork.KEY_PATH) ?: return Result.failure()
        val expected = inputData.getLong(MediaWork.KEY_BYTES, -1L).takeIf { it > 0 }
        val remote = applicationContext.mediaRemote() ?: return Result.retry()
        val target = MediaFiles.localFile(applicationContext, matchId, messageId, type)

        // Never download the same file twice.
        if (MediaFiles.hasLocalCopy(applicationContext, matchId, messageId, type, expected)) {
            return finish(remote, messageId)
        }
        if (expected != null && expected > ChatMediaRules.MAX_BYTES) return Result.failure(workDataOf(MediaWork.KEY_REASON to "media_too_large"))
        target.parentFile?.mkdirs()
        val free = target.parentFile?.usableSpace ?: applicationContext.filesDir.usableSpace
        if (!ChatMediaRules.hasRoomFor(expected ?: ChatMediaRules.MAX_BYTES, free)) {
            return Result.failure(workDataOf(MediaWork.KEY_REASON to "media_no_space"))
        }

        val partial = File(target.parentFile, "$messageId.part")
        var lastPercent = -1
        return try {
            setProgress(workDataOf(MediaWork.KEY_PROGRESS to 0))
            val url = remote.signedUrl(path) ?: throw MediaNotFoundException()
            remote.download(url, partial, { received, contentLength ->
                val total = expected ?: contentLength
                if (total > 0) {
                    val percent = ((received * 100) / total).toInt().coerceIn(0, 99)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        setProgressAsync(workDataOf(MediaWork.KEY_PROGRESS to percent))
                    }
                }
            }, { isStopped })
            if (expected != null && partial.length() != expected) {
                partial.delete()
                return retryOrFail("media_download_failed")
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            finish(remote, messageId)
        } catch (e: CancellationException) {
            partial.delete()
            throw e
        } catch (e: MediaCancelledException) {
            partial.delete()
            Result.failure()
        } catch (e: MediaNotFoundException) {
            partial.delete()
            runCatching { remote.markMissing(messageId) }
            Result.failure(workDataOf(MediaWork.KEY_REASON to "media_expired"))
        } catch (e: java.io.IOException) {
            partial.delete()
            if (e.message?.contains("ENOSPC") == true) Result.failure(workDataOf(MediaWork.KEY_REASON to "media_no_space"))
            else retryOrFail("media_download_failed")
        } catch (e: Throwable) {
            partial.delete()
            retryOrFail("media_download_failed")
        }
    }

    /** The local copy is complete: mark it downloaded, then remove the server copy. */
    private suspend fun finish(remote: ChatMediaRemote, messageId: String): Result {
        setProgress(workDataOf(MediaWork.KEY_PROGRESS to 100))
        try {
            val paths = remote.markDownloaded(messageId)
            remote.removeQuietly(paths)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // The file is safe on the phone; the daily server job removes leftovers.
        }
        return Result.success()
    }

    private fun retryOrFail(key: String): Result =
        if (runAttemptCount + 1 >= ChatMediaRules.MAX_AUTO_RETRIES) Result.failure(workDataOf(MediaWork.KEY_REASON to key))
        else Result.retry()
}

/** Finds received files that are not downloaded yet and starts automatic downloads per the settings. */
class MediaSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val remote = applicationContext.mediaRemote() ?: return Result.success()
        val matchId = inputData.getString(MediaWork.KEY_MATCH_ID)
        return try {
            val settings = MediaSettingsStore(applicationContext).current()
            remote.incomingNotDownloaded(matchId).forEach { item ->
                val type = MediaType.fromWire(item.type) ?: return@forEach
                ChatMediaManager.autoDownload(applicationContext, item.id, item.matchId, type, item.mediaPath, item.mediaBytes, settings.forType(type))
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (runAttemptCount + 1 >= ChatMediaRules.MAX_AUTO_RETRIES) Result.success() else Result.retry()
        }
    }
}

/** Starts, retries and cancels media work. All state lives in WorkManager and the outbox. */
object ChatMediaManager {

    fun send(context: Context, pending: PendingMedia) {
        Outbox.write(context, pending)
        enqueueSend(context, pending.messageId, pending.matchId, ExistingWorkPolicy.KEEP)
    }

    /** "Failed, tap to retry": uses the file kept in the outbox, so nothing has to be picked again. */
    fun retrySend(context: Context, messageId: String) {
        val pending = Outbox.read(context, messageId) ?: return
        Outbox.write(context, pending.copy(failed = false, failureKey = null))
        enqueueSend(context, messageId, pending.matchId, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueueSend(context: Context, messageId: String, matchId: String, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<SendMediaWorker>()
            .setInputData(workDataOf(MediaWork.KEY_MESSAGE_ID to messageId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(MediaWork.TAG_SEND)
            .addTag(MediaWork.chatTag(matchId))
            .addTag(MediaWork.messageTag(messageId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MediaWork.sendName(messageId), policy, request)
    }

    /** Cancel while sending: stops the upload, removes partly uploaded files and the outbox copy. */
    suspend fun cancelSend(context: Context, messageId: String) {
        val pending = Outbox.read(context, messageId)
        WorkManager.getInstance(context).cancelUniqueWork(MediaWork.sendName(messageId))
        if (pending != null) {
            val remote = context.mediaRemote()
            val paths = listOf(
                ChatMediaRules.remotePath(pending.matchId, messageId, pending.mediaType.fileName),
                ChatMediaRules.remotePath(pending.matchId, messageId, ChatMediaRules.THUMB_FILE)
            )
            remote?.removeQuietly(paths)  // the server only allows this before the message exists
        }
        Outbox.remove(context, messageId)
    }

    /** Download on tap; any connection is fine. */
    fun download(context: Context, messageId: String, matchId: String, type: MediaType, path: String, bytes: Long?) {
        enqueueDownload(context, messageId, matchId, type, path, bytes, NetworkType.CONNECTED, ExistingWorkPolicy.REPLACE)
    }

    /** Automatic download if the setting allows it; Wi-Fi only waits for an unmetered network. */
    fun autoDownload(context: Context, messageId: String, matchId: String, type: MediaType, path: String, bytes: Long?, setting: AutoDownload) {
        val network = when (setting) {
            AutoDownload.NEVER -> return
            AutoDownload.WIFI -> NetworkType.UNMETERED
            AutoDownload.ANY -> NetworkType.CONNECTED
        }
        if (MediaFiles.hasLocalCopy(context, matchId, messageId, type, bytes)) return
        enqueueDownload(context, messageId, matchId, type, path, bytes, network, ExistingWorkPolicy.KEEP)
    }

    private fun enqueueDownload(
        context: Context, messageId: String, matchId: String, type: MediaType, path: String, bytes: Long?,
        network: NetworkType, policy: ExistingWorkPolicy
    ) {
        val data = Data.Builder()
            .putString(MediaWork.KEY_MESSAGE_ID, messageId)
            .putString(MediaWork.KEY_MATCH_ID, matchId)
            .putString(MediaWork.KEY_TYPE, type.wire)
            .putString(MediaWork.KEY_PATH, path)
            .putLong(MediaWork.KEY_BYTES, bytes ?: -1L)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadMediaWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(MediaWork.TAG_DOWNLOAD)
            .addTag(MediaWork.chatTag(matchId))
            .addTag(MediaWork.messageTag(messageId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MediaWork.downloadName(messageId), policy, request)
    }

    fun cancelDownload(context: Context, messageId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(MediaWork.downloadName(messageId))
    }

    /** Looks for new received files (from a push or when a chat opens); [matchId] null = all chats. */
    fun sync(context: Context, matchId: String?) {
        val request = OneTimeWorkRequestBuilder<MediaSyncWorker>()
            .setInputData(workDataOf(MediaWork.KEY_MATCH_ID to matchId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("media-sync-${matchId ?: "all"}", ExistingWorkPolicy.REPLACE, request)
    }

    /** Blocked or unmatched: stop every upload and download of this chat. */
    suspend fun stopChat(context: Context, matchId: String) {
        Outbox.forMatch(context, matchId).forEach { cancelSend(context, it.messageId) }
        WorkManager.getInstance(context).cancelAllWorkByTag(MediaWork.chatTag(matchId))
    }
}
