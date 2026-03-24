package util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Gerencia notificações de itens pendentes de sincronização.
 *
 * Mostra notificação quando o app vai para background com dados não sincronizados.
 * Remove automaticamente quando tudo é sincronizado.
 *
 * Uso:
 *   SyncNotificationHelper.mostrar(context, 5)   // 5 pendentes
 *   SyncNotificationHelper.cancelar(context)       // limpa notificação
 */
object SyncNotificationHelper {

    private const val CHANNEL_ID = "sync_pendentes_channel"
    private const val NOTIFICATION_ID = 3001

    fun criarCanal(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dados Pendentes",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Lembrete de dados não sincronizados"
                setShowBadge(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun mostrar(context: Context, pendentes: Long) {
        if (pendentes <= 0) {
            cancelar(context)
            return
        }

        criarCanal(context)

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val texto = if (pendentes == 1L) "1 item aguardando sincronização"
                    else "$pendentes itens aguardando sincronização"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Dados pendentes")
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$texto\nConecte-se à internet para enviar os dados ao servidor.")
            )
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelar(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
