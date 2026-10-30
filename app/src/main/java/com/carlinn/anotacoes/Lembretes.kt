package com.carlinn.anotacoes

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Lembretes {
    const val CANAL = "lembretes"
    const val EXTRA_ID = "id"
    const val ACAO_CONCLUIR = "com.carlinn.anotacoes.CONCLUIR"

    fun criarCanal(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Lembretes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos de tarefas, compras e metas"
            }
        )
    }

    private fun pendente(ctx: Context, id: Long): PendingIntent {
        val intent = Intent(ctx, LembreteReceiver::class.java).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            ctx, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun agendar(ctx: Context, item: Item) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pendente(ctx, item.id)
        val quando = item.lembrete
        if (item.feito || quando == null || quando <= System.currentTimeMillis()) {
            am.cancel(pi)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
        }
    }

    fun cancelar(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pendente(ctx, id))
        NotificationManagerCompat.from(ctx).cancel(id.hashCode())
    }
}

class LembreteReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val id = intent.getLongExtra(Lembretes.EXTRA_ID, -1)
        val nm = NotificationManagerCompat.from(context)

        if (intent.action == Lembretes.ACAO_CONCLUIR) {
            Store.get(id)?.let { if (!it.feito) Store.alternarFeito(id) }
            nm.cancel(id.hashCode())
            return
        }

        val item = Store.get(id) ?: return
        if (item.feito) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        Lembretes.criarCanal(context)
        val abrir = PendingIntent.getActivity(
            context, id.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val concluir = PendingIntent.getBroadcast(
            context, id.hashCode(),
            Intent(context, LembreteReceiver::class.java)
                .setAction(Lembretes.ACAO_CONCLUIR)
                .putExtra(Lembretes.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val titulo = when (item.tipo) {
            Tipo.TAREFA -> item.titulo
            Tipo.COMPRA -> "Comprar: ${item.titulo}"
            Tipo.META -> "Meta: ${item.titulo}"
        }
        val notificacao = NotificationCompat.Builder(context, Lembretes.CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(titulo)
            .setContentText(item.nota.ifBlank { "Lembrete" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .addAction(0, if (item.tipo == Tipo.COMPRA) "Comprado" else "Concluído", concluir)
            .build()
        nm.notify(id.hashCode(), notificacao)
    }
}

/** Alarmes somem ao reiniciar o celular; reagenda tudo. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Store.reagendarTodos()
    }
}
