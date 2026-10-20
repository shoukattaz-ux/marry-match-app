package com.example.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.MarryMatchApplication
import com.example.R
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Notification channels, sounds and vibration, and the Firebase token. Pushes are sent by the
 * send-push Edge Function; newer apps get them data-only and show them here with the sound and
 * vibration the user picked for messages, matches and likes (Settings > Notifications).
 */
object PushNotifications {
    /** Used by pushes to older app versions, which Android shows on its own. */
    const val CHANNEL_ID = "marrymatch_default"
    private const val PREFS = "notification_style"

    fun style(context: Context, category: NotifyCategory): NotifyStyle {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return NotifyStyle(
            NotifySound.of(prefs.getString("${category.key}_sound", null), category.default.sound),
            NotifyVibration.of(prefs.getString("${category.key}_vibration", null), category.default.vibration)
        )
    }

    /** Saves the choice and replaces that category's channel (Android fixes a channel's sound). */
    fun setStyle(context: Context, category: NotifyCategory, style: NotifyStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("${category.key}_sound", style.sound.key)
            .putString("${category.key}_vibration", style.vibration.key)
            .apply()
        ensureChannel(context, category)
    }

    /** Must stay in step with what send-push sends; safe to call on every start. */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "MarryMatch", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Matches, messages and likes"
            }
        )
        NotifyCategory.entries.forEach { ensureChannel(context, it) }
    }

    /** The channel for the current choice exists; older choices of the category are removed. */
    private fun ensureChannel(context: Context, category: NotifyCategory): String {
        val style = style(context, category)
        val id = category.channelId(style)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return id
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notificationChannels
            .filter { it.id.startsWith(category.channelPrefix) && it.id != id }
            .forEach { manager.deleteNotificationChannel(it.id) }
        if (manager.getNotificationChannel(id) == null) {
            val german = Locale.getDefault().language == "de"
            val channel = NotificationChannel(id, channelName(category, german), NotificationManager.IMPORTANCE_HIGH)
            channel.setSound(soundUri(context, style.sound), AUDIO)
            channel.enableVibration(style.vibration.pattern != null)
            channel.vibrationPattern = style.vibration.pattern
            channel.enableLights(true)
            manager.createNotificationChannel(channel)
        }
        return id
    }

    private fun channelName(category: NotifyCategory, german: Boolean) = when (category) {
        NotifyCategory.MESSAGES -> if (german) "Nachrichten" else "Messages"
        NotifyCategory.MATCHES -> if (german) "Neue Matches" else "New matches"
        NotifyCategory.LIKES -> "Likes"
    }

    private val AUDIO = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun soundUri(context: Context, sound: NotifySound): Uri? = when (sound) {
        NotifySound.CHIME -> rawUri(context, R.raw.mm_chime)
        NotifySound.BELL -> rawUri(context, R.raw.mm_bell)
        NotifySound.MELODY -> rawUri(context, R.raw.mm_melody)
        NotifySound.SYSTEM -> Settings.System.DEFAULT_NOTIFICATION_URI
        NotifySound.SILENT -> null
    }

    private fun rawUri(context: Context, res: Int): Uri =
        Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$res")

    /** Plays a sound and vibration right away, so a choice can be heard before it is saved. */
    fun preview(context: Context, style: NotifyStyle) {
        soundUri(context, style.sound)?.let { uri ->
            runCatching {
                RingtoneManager.getRingtone(context, uri)?.apply { audioAttributes = AUDIO }?.play()
            }
        }
        val pattern = style.vibration.pattern ?: return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        }
    }

    /** Android 13+ asks the user before an app may show notifications. */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** This device's token, or null when Google Play services are missing. */
    suspend fun currentToken(): String? = try {
        suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    } catch (e: Throwable) {
        null
    }

    /** A new token is created on the next start, so the old one can no longer reach this phone. */
    fun deleteToken() {
        runCatching { FirebaseMessaging.getInstance().deleteToken() }
    }

    /**
     * Shows a push with the sound and vibration chosen for its [kind]: every data-only push, and
     * notification pushes that arrive while the app is open.
     */
    fun show(context: Context, title: String, body: String, tag: String, kind: String) {
        if (!canNotify(context)) return
        val category = NotifyCategory.forKind(kind)
        val style = style(context, category)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, ensureChannel(context, category))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setCategory(if (category == NotifyCategory.MESSAGES) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Android before 8 has no channels: sound and vibration are set on each notification.
            .setSound(soundUri(context, style.sound))
            .setVibrate(style.vibration.pattern ?: longArrayOf(0))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag, 0, notification)
        } catch (e: SecurityException) {
            // Permission was taken away in the meantime; nothing to show.
        }
    }
}

/** Receives pushes and token changes from Firebase. */
class MarryMatchMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        (application as? MarryMatchApplication)?.repository?.onPushTokenChanged(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Data-only pushes carry the texts in data; older notification pushes in notification.
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        if (title.isNullOrBlank()) return
        val kind = message.data["kind"].orEmpty()
        PushNotifications.show(this, title, body.orEmpty(), "${kind}_${message.data["match_id"].orEmpty()}", kind)
        // Keep the open app in step with what the push announced.
        (application as? MarryMatchApplication)?.repository?.refreshMatches()
        // A new message may be a photo, voice message or video: download it now if the settings allow.
        if (kind == "message") {
            com.example.data.media.ChatMediaManager.sync(this, message.data["match_id"]?.takeIf { it.isNotBlank() })
        }
    }
}
