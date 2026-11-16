package app.revisatrt.notify

import android.Manifest
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
import app.revisatrt.MainActivity
import app.revisatrt.R
import app.revisatrt.data.Analise
import app.revisatrt.data.Lembrete
import app.revisatrt.data.Repositorio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

object Lembretes {
    const val CANAL = "lembretes"
    private const val EXTRA_ID = "lembrete_id"
    /** Ids possíveis de lembretes (para cancelar os antigos ao salvar). */
    private val FAIXA_IDS = 1..50

    fun criarCanal(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL, "Lembretes de estudo", NotificationManager.IMPORTANCE_DEFAULT)
            canal.description = "Metas diárias e revisões atrasadas"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    private fun pending(context: Context, id: Int, criar: Boolean): PendingIntent? {
        val intent = Intent(context, LembreteReceiver::class.java).putExtra(EXTRA_ID, id)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            (if (criar) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(context, id, intent, flags)
    }

    /** Próximo horário em que o lembrete deve tocar. */
    fun proximo(l: Lembrete, agora: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        if (l.dias.isEmpty()) return null
        for (i in 0..7) {
            val dia = agora.toLocalDate().plusDays(i.toLong())
            val momento = dia.atTime(l.hora, l.minuto)
            if (momento.isAfter(agora) && dia.dayOfWeek.value in l.dias) return momento
        }
        return null
    }

    fun agendar(context: Context, l: Lembrete) {
        val am = context.getSystemService(AlarmManager::class.java)
        val quando = proximo(l) ?: return
        val ms = quando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pending(context, l.id, true)!!
        val exato = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exato) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        else am.setWindow(AlarmManager.RTC_WAKEUP, ms, 10 * 60 * 1000L, pi)
    }

    fun cancelarTodos(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        for (id in FAIXA_IDS) pending(context, id, false)?.let { am.cancel(it); it.cancel() }
    }

    /** Recria todos os alarmes conforme os ajustes salvos. */
    suspend fun reagendar(context: Context) {
        val ajustes = Repositorio(context).ajustes.first()
        cancelarTodos(context)
        if (!ajustes.notificacoes) return
        ajustes.lembretes.filter { it.ativo }.forEach { agendar(context, it) }
    }

    suspend fun notificar(context: Context, idLembrete: Int) {
        val repo = Repositorio(context)
        val ajustes = repo.ajustes.first()
        if (!ajustes.notificacoes) return
        val revisoes = repo.revisoes.first()
        val pontos = repo.pontos.first()
        val hoje = Analise.revisadosHoje(revisoes)
        val meta = ajustes.metaDiaria
        val atrasados = pontos.count { Analise.faixaAtraso(it.ultimaRevisao) > 0 }
        val atencao = pontos.count { it.atencao }
        val nunca = pontos.count { it.nuncaRevisado }

        val titulo = if (hoje >= meta) "Meta de hoje cumprida! ($hoje/$meta)" else "Hora de revisar: $hoje/$meta pontos hoje"
        val partes = mutableListOf<String>()
        if (hoje < meta) partes += "Faltam ${meta - hoje} pontos para a meta."
        if (ajustes.avisarAtrasados) {
            if (atrasados > 0) partes += "$atrasados sem revisão há 7+ dias."
            if (atencao > 0) partes += "$atencao pontos de atenção."
            if (nunca > 0) partes += "$nunca nunca revisados."
        }
        partes += "Faltam ${Analise.diasParaProva()} dias para a prova."
        val corpo = partes.joinToString(" ")

        val abrir = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corpo))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        val permitido = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (permitido) NotificationManagerCompat.from(context).notify(1000 + idLembrete, n)
    }

    internal fun idDo(intent: Intent) = intent.getIntExtra(EXTRA_ID, -1)
}

private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

class LembreteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = Lembretes.idDo(intent)
        val pendente = goAsync()
        escopo.launch {
            try {
                Lembretes.notificar(context, id)
                // agenda a próxima ocorrência deste lembrete
                val l = Repositorio(context).ajustes.first().let { a -> if (a.notificacoes) a.lembretes.find { it.id == id && it.ativo } else null }
                if (l != null) Lembretes.agendar(context, l)
            } finally {
                pendente.finish()
            }
        }
    }
}

class InicioReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendente = goAsync()
        escopo.launch {
            try { Lembretes.reagendar(context) } finally { pendente.finish() }
        }
    }
}
