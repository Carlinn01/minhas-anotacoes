package com.carlinn.anotacoes

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class Versao(val numero: Int, val nome: String, val apkUrl: String, val notas: String)

/** Procura versões novas nos Releases do GitHub, baixa e abre o instalador do Android. */
object Atualizador {
    private const val API = "https://api.github.com/repos/Carlinn01/minhas-anotacoes/releases/latest"
    private const val CANAL = "atualizacoes"
    private const val ID_NOTIFICACAO = 424242

    /** Devolve a versão nova, ou null se já está atualizado. Lança exceção se não conseguir verificar. */
    suspend fun buscarNova(): Versao? = withContext(Dispatchers.IO) {
        val con = URL(API).openConnection() as HttpURLConnection
        con.setRequestProperty("Accept", "application/vnd.github+json")
        con.connectTimeout = 10_000
        con.readTimeout = 10_000
        try {
            if (con.responseCode != 200) error("GitHub respondeu ${con.responseCode}")
            val json = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val numero = tag.substringAfterLast('.').toIntOrNull() ?: return@withContext null
            if (numero <= BuildConfig.VERSION_CODE) return@withContext null
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@withContext null
            val notas = json.optString("body").lines()
                .filterNot { it.startsWith("Co-Authored-By", ignoreCase = true) }
                .joinToString("\n").trim()
            Versao(numero, tag.removePrefix("v"), apk.getString("browser_download_url"), notas)
        } finally {
            con.disconnect()
        }
    }

    suspend fun baixar(ctx: Context, v: Versao, progresso: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val pasta = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val arquivo = File(pasta, "MinhasAnotacoes.apk")
        val con = URL(v.apkUrl).openConnection() as HttpURLConnection
        try {
            val total = con.contentLengthLong
            con.inputStream.use { entrada ->
                arquivo.outputStream().use { saida ->
                    val buffer = ByteArray(64 * 1024)
                    var lido = 0L
                    while (true) {
                        val n = entrada.read(buffer)
                        if (n < 0) break
                        saida.write(buffer, 0, n)
                        lido += n
                        if (total > 0) progresso(lido / total.toFloat())
                    }
                }
            }
        } finally {
            con.disconnect()
        }
        arquivo
    }

    fun podeInstalar(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun abrirPermissaoInstalar(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
        )
    }

    fun instalar(ctx: Context, arquivo: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.arquivos", arquivo)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun agendarVerificacao(ctx: Context) {
        val pedido = PeriodicWorkRequestBuilder<VerificarAtualizacaoWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork("verificar-atualizacao", ExistingPeriodicWorkPolicy.KEEP, pedido)
    }

    @SuppressLint("MissingPermission")
    fun notificar(ctx: Context, v: Versao) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CANAL, "Atualizações do app", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val abrir = PendingIntent.getActivity(
            ctx, ID_NOTIFICACAO,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificacao = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle("Nova versão disponível")
            .setContentText("Versão ${v.nome} — toque para atualizar")
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_NOTIFICACAO, notificacao)
    }
}

/** Roda em segundo plano a cada 12 horas e avisa uma vez por versão nova. */
class VerificarAtualizacaoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val v = runCatching { Atualizador.buscarNova() }.getOrNull() ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences("atualizacao", Context.MODE_PRIVATE)
        if (prefs.getInt("ultimaAvisada", 0) >= v.numero) return Result.success()
        Atualizador.notificar(applicationContext, v)
        prefs.edit().putInt("ultimaAvisada", v.numero).apply()
        return Result.success()
    }
}
