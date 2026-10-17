package com.example.data.media

/** What a media bubble shows. */
enum class MediaUiState {
    /** Waiting to send (no connection yet): clock icon. */
    QUEUED,
    /** Uploading: progress ring and cancel. */
    SENDING,
    /** Automatic attempts stopped: "Failed, tap to retry". */
    SEND_FAILED,
    /** The file is on this phone: show or play it. */
    READY,
    /** On the server, not downloaded: blurred preview, size and download button. */
    REMOTE,
    /** Downloading: progress ring and cancel. */
    DOWNLOADING,
    /** Never downloaded and removed from the server: "Expired. Ask them to send again." */
    EXPIRED,
    /** Not on this phone and not on the server any more: "No longer available". */
    GONE
}

/** A WorkManager job for one message, reduced to what the bubble needs. */
data class TransferInfo(val kind: Kind, val phase: Phase, val progress: Int = 0, val reasonKey: String? = null) {
    enum class Kind { SEND, DOWNLOAD }
    enum class Phase { WAITING, RUNNING, FAILED, DONE, CANCELLED }

    companion object {
        private val priority = listOf(Phase.RUNNING, Phase.WAITING, Phase.FAILED, Phase.DONE, Phase.CANCELLED)

        /** Several jobs can exist for one message (an old failed one and a retry): the active one wins. */
        fun pick(infos: List<TransferInfo>): TransferInfo? = infos.minByOrNull { priority.indexOf(it.phase) }
    }
}

object MediaUiLogic {

    /** A message that exists only in the outbox (not inserted yet). */
    fun forPending(pending: PendingMedia, transfer: TransferInfo?): MediaUiState = when {
        pending.failed || transfer?.phase == TransferInfo.Phase.FAILED -> MediaUiState.SEND_FAILED
        transfer?.phase == TransferInfo.Phase.RUNNING -> MediaUiState.SENDING
        else -> MediaUiState.QUEUED
    }

    /** A message row from the server. */
    fun forMessage(isMine: Boolean, status: String, hasLocalCopy: Boolean, download: TransferInfo?): MediaUiState = when {
        hasLocalCopy -> MediaUiState.READY
        isMine -> MediaUiState.GONE
        download?.phase == TransferInfo.Phase.RUNNING -> MediaUiState.DOWNLOADING
        status == MediaStatus.EXPIRED -> MediaUiState.EXPIRED
        download?.phase == TransferInfo.Phase.FAILED && download.reasonKey == "media_expired" -> MediaUiState.EXPIRED
        status == MediaStatus.DOWNLOADED -> MediaUiState.GONE
        else -> MediaUiState.REMOTE
    }

    /** "1.2 MB", "350 KB" */
    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes <= 0) return ""
        return if (bytes >= 1024 * 1024) String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
        else "${(bytes + 1023) / 1024} KB"
    }

    /** "0:07", "2:59" */
    fun formatDuration(ms: Long?): String {
        val total = ((ms ?: 0L) / 1000).coerceAtLeast(0)
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }
}
