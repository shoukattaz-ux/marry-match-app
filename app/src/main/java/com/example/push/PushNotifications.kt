package com.example.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Notification channel and the Firebase token. Pushes are sent by the send-push Edge Function. */
object PushNotifications {
    const val CHANNEL_ID = "marrymatch_default"

    /** Must match the channel_id send-push uses; safe to call on every start. */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "MarryMatch", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Matches, messages and likes"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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

    /** Shows a push that arrived while the app was open (Android shows it itself otherwise). */
    fun show(context: Context, title: String, body: String, tag: String) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
        val n = message.notification ?: return
        val kind = message.data["kind"].orEmpty()
        PushNotifications.show(this, n.title.orEmpty(), n.body.orEmpty(), "${kind}_${message.data["match_id"].orEmpty()}")
        // Keep the open app in step with what the push announced.
        (application as? MarryMatchApplication)?.repository?.refreshMatches()
        // A new message may be a photo, voice message or video: download it now if the settings allow.
        if (kind == "message") {
            com.example.data.media.ChatMediaManager.sync(this, message.data["match_id"]?.takeIf { it.isNotBlank() })
        }
    }
}
