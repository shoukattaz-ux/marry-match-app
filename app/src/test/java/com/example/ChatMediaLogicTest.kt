package com.example

import com.example.data.media.AutoDownload
import com.example.data.media.ChatMediaRules
import com.example.data.media.MediaAutoDownloadSettings
import com.example.data.media.MediaStatus
import com.example.data.media.MediaType
import com.example.data.media.MediaUiLogic
import com.example.data.media.MediaUiState
import com.example.data.media.PendingMedia
import com.example.data.media.TransferInfo
import com.example.data.media.TransferInfo.Kind
import com.example.data.media.TransferInfo.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMediaLogicTest {

    private val pending = PendingMedia(
        messageId = "m1", matchId = "x", senderId = "me", type = "image", fileName = "image.jpg",
        bytes = 1000, createdAtMs = 0
    )

    @Test
    fun outgoingStatesFollowTheUpload() {
        assertEquals(MediaUiState.QUEUED, MediaUiLogic.forPending(pending, null))
        assertEquals(MediaUiState.QUEUED, MediaUiLogic.forPending(pending, TransferInfo(Kind.SEND, Phase.WAITING)))
        assertEquals(MediaUiState.SENDING, MediaUiLogic.forPending(pending, TransferInfo(Kind.SEND, Phase.RUNNING, 40)))
        assertEquals(MediaUiState.SEND_FAILED, MediaUiLogic.forPending(pending, TransferInfo(Kind.SEND, Phase.FAILED)))
        assertEquals(MediaUiState.SEND_FAILED, MediaUiLogic.forPending(pending.copy(failed = true), null))
    }

    @Test
    fun aLocalCopyAlwaysWinsSoNothingDownloadsTwice() {
        assertEquals(MediaUiState.READY, MediaUiLogic.forMessage(false, MediaStatus.DOWNLOADED, true, null))
        assertEquals(MediaUiState.READY, MediaUiLogic.forMessage(false, MediaStatus.EXPIRED, true, null))
        assertEquals(MediaUiState.READY, MediaUiLogic.forMessage(true, MediaStatus.UPLOADED, true, null))
    }

    @Test
    fun receivedFilesShowPlaceholderProgressOrExpiry() {
        assertEquals(MediaUiState.REMOTE, MediaUiLogic.forMessage(false, MediaStatus.UPLOADED, false, null))
        assertEquals(MediaUiState.REMOTE, MediaUiLogic.forMessage(false, MediaStatus.UPLOADED, false, TransferInfo(Kind.DOWNLOAD, Phase.WAITING)))
        assertEquals(MediaUiState.DOWNLOADING, MediaUiLogic.forMessage(false, MediaStatus.UPLOADED, false, TransferInfo(Kind.DOWNLOAD, Phase.RUNNING, 10)))
        assertEquals(MediaUiState.EXPIRED, MediaUiLogic.forMessage(false, MediaStatus.EXPIRED, false, null))
        assertEquals(
            MediaUiState.EXPIRED,
            MediaUiLogic.forMessage(false, MediaStatus.UPLOADED, false, TransferInfo(Kind.DOWNLOAD, Phase.FAILED, reasonKey = "media_expired"))
        )
        // Downloaded on another phone, or the app data was cleared.
        assertEquals(MediaUiState.GONE, MediaUiLogic.forMessage(false, MediaStatus.DOWNLOADED, false, null))
        // A failed download (not expired) offers the download button again.
        assertEquals(MediaUiState.REMOTE, MediaUiLogic.forMessage(false, MediaStatus.UPLOADED, false, TransferInfo(Kind.DOWNLOAD, Phase.FAILED, reasonKey = "media_download_failed")))
    }

    @Test
    fun ownMediaWithoutLocalCopyIsNoLongerAvailable() {
        assertEquals(MediaUiState.GONE, MediaUiLogic.forMessage(true, MediaStatus.UPLOADED, false, null))
    }

    @Test
    fun theActiveTransferWinsOverOldOnes() {
        val old = TransferInfo(Kind.DOWNLOAD, Phase.FAILED)
        val now = TransferInfo(Kind.DOWNLOAD, Phase.RUNNING, 50)
        assertEquals(now, TransferInfo.pick(listOf(old, now)))
        assertEquals(old, TransferInfo.pick(listOf(old, TransferInfo(Kind.DOWNLOAD, Phase.CANCELLED))))
    }

    @Test
    fun serverRulesAreMirroredOnThePhone() {
        assertTrue(ChatMediaRules.mimeAllowed("image/jpeg"))
        assertTrue(ChatMediaRules.mimeAllowed("audio/mp4"))
        assertTrue(ChatMediaRules.mimeAllowed("video/mp4"))
        assertFalse(ChatMediaRules.mimeAllowed("image/png"))
        assertFalse(ChatMediaRules.mimeAllowed("application/pdf"))
        assertEquals("a/b/voice.m4a", ChatMediaRules.remotePath("a", "b", MediaType.VOICE.fileName))
        assertEquals(20L * 1024 * 1024, ChatMediaRules.MAX_BYTES)
        assertEquals(3, ChatMediaRules.MAX_AUTO_RETRIES)
    }

    @Test
    fun lowStorageIsDetectedBeforeWriting() {
        val mb = 1024L * 1024
        assertTrue(ChatMediaRules.hasRoomFor(5 * mb, 100 * mb))
        assertFalse(ChatMediaRules.hasRoomFor(5 * mb, 10 * mb))
    }

    @Test
    fun defaultAutoDownloadSettingsMatchTheSpec() {
        val d = MediaAutoDownloadSettings()
        assertEquals(AutoDownload.ANY, d.forType(MediaType.IMAGE))
        assertEquals(AutoDownload.ANY, d.forType(MediaType.VOICE))
        assertEquals(AutoDownload.WIFI, d.forType(MediaType.VIDEO))
        assertEquals(AutoDownload.WIFI, AutoDownload.fromKey("wifi", AutoDownload.NEVER))
        assertEquals(AutoDownload.NEVER, AutoDownload.fromKey("bogus", AutoDownload.NEVER))
    }

    @Test
    fun sizesAndDurationsAreShortAndReadable() {
        assertEquals("350 KB", MediaUiLogic.formatBytes(350L * 1024))
        assertEquals("1.5 MB", MediaUiLogic.formatBytes(1536L * 1024))
        assertEquals("", MediaUiLogic.formatBytes(null))
        assertEquals("0:07", MediaUiLogic.formatDuration(7_400))
        assertEquals("3:00", MediaUiLogic.formatDuration(180_000))
    }

    @Test
    fun wireNamesMatchTheDatabase() {
        assertEquals(MediaType.VIDEO, MediaType.fromWire("video"))
        assertEquals(null, MediaType.fromWire("text"))
        assertEquals("image.jpg", MediaType.IMAGE.fileName)
        assertEquals("video/mp4", MediaType.VIDEO.mime)
    }
}
