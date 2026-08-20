package br.com.lfsystem.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import push.PushTokenManager

/**
 * Recebe push notifications (FCM) — mensagem do admin, CNH vencendo,
 * manutenção preventiva vencendo (ver fora/admin/fcm_helper.php).
 * Registrada em AndroidManifest.xml.
 */
class TrakviaMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "avisos_push"
        private var proximoId = 4001

        fun criarCanal(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Avisos",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Mensagens do admin, CNH e manutenção vencendo"
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushTokenManager.setToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val titulo = message.notification?.title ?: message.data["titulo"] ?: return
        val corpo = message.notification?.body ?: message.data["corpo"] ?: ""

        val semPermissao = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (semPermissao) return

        criarCanal(applicationContext)

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this).notify(proximoId++, notification)
    }
}
