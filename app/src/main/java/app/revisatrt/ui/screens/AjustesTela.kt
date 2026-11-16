package app.revisatrt.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revisatrt.BuildConfigInfo
import app.revisatrt.RevisaApp
import app.revisatrt.data.Analise
import app.revisatrt.data.Ajustes
import app.revisatrt.data.CorTema
import app.revisatrt.data.Lembrete
import app.revisatrt.data.Tema
import app.revisatrt.notify.Lembretes
import app.revisatrt.ui.LocalRepo
import app.revisatrt.ui.TituloSecao
import app.revisatrt.ui.theme.Pastel
import kotlinx.coroutines.launch
import java.time.LocalDate

private val DIAS = listOf(1 to "S", 2 to "T", 3 to "Q", 4 to "Q", 5 to "S", 6 to "S", 7 to "D")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AjustesTela() {
    val repo = LocalRepo.current
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val a by repo.ajustes.collectAsStateWithLifecycle(Ajustes())

    fun salvar(novo: Ajustes) = escopo.launch {
        repo.salvarAjustes(novo)
        Lembretes.reagendar(contexto)
    }

    val pedirPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        salvar(a.copy(notificacoes = ok))
        if (!ok) escopo.launch { snackbar.showSnackbar("Permissão de notificação negada. Ative nas configurações do Android.") }
    }

    var editandoHorario by remember { mutableStateOf<Lembrete?>(null) }
    var confirmarImportar by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) escopo.launch {
            val msg = runCatching { repo.exportar(uri); "Backup salvo." }.getOrElse { "Erro ao salvar: ${it.message}" }
            snackbar.showSnackbar(msg)
        }
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmarImportar = uri
    }

    TelaBase(titulo = "Ajustes", snackbar = snackbar) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { TituloSecao("Aparência") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tema.entries.forEach { t ->
                        FilterChip(selected = a.tema == t, onClick = { salvar(a.copy(tema = t)) }, label = { Text(t.rotulo) })
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CorTema.entries.forEach { c ->
                        val cor = when (c) {
                            CorTema.ROSA -> Pastel.Rosa
                            CorTema.AZUL -> Pastel.Azul
                            CorTema.VERDE -> Pastel.Verde
                            CorTema.ROXO -> Pastel.Roxo
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { salvar(a.copy(cor = c)) }) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape).background(cor)
                                    .border(if (a.cor == c) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { if (a.cor == c) Icon(Icons.Filled.Check, null, tint = Color(0xFF221D24)) }
                            Spacer(Modifier.height(4.dp))
                            Text(c.rotulo, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            item { TituloSecao("Metas") }
            item { Contador("Meta diária (pontos revisados)", a.metaDiaria, 1, 200) { salvar(a.copy(metaDiaria = it)) } }
            item { Contador("Meta semanal (revisões)", a.metaSemanal, 5, 1000, passo = 5) { salvar(a.copy(metaSemanal = it)) } }

            item { TituloSecao("Notificações") }
            item {
                LinhaSwitch("Receber lembretes", a.notificacoes) { ligar ->
                    if (ligar && Build.VERSION.SDK_INT >= 33) pedirPermissao.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else salvar(a.copy(notificacoes = ligar))
                }
            }
            item {
                LinhaSwitch("Incluir atrasados e pontos de atenção na mensagem", a.avisarAtrasados, enabled = a.notificacoes) {
                    salvar(a.copy(avisarAtrasados = it))
                }
            }
            items(a.lembretes, key = { it.id }) { l ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "%02d:%02d".format(l.hora, l.minuto),
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { editandoHorario = l }.padding(end = 8.dp),
                        )
                        Text("toque para mudar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Switch(checked = l.ativo, onCheckedChange = { at -> salvar(a.copy(lembretes = a.lembretes.map { if (it.id == l.id) it.copy(ativo = at) else it })) })
                        IconButton(onClick = { salvar(a.copy(lembretes = a.lembretes.filter { it.id != l.id })) }) { Icon(Icons.Filled.Delete, "Remover lembrete") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DIAS.forEach { (n, letra) ->
                            val sel = n in l.dias
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        val dias = if (sel) l.dias - n else l.dias + n
                                        salvar(a.copy(lembretes = a.lembretes.map { if (it.id == l.id) it.copy(dias = dias) else it }))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(letra, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        val novoId = ((a.lembretes.maxOfOrNull { it.id } ?: 0) + 1).coerceAtMost(50)
                        if (a.lembretes.none { it.id == novoId }) salvar(a.copy(lembretes = a.lembretes + Lembrete(novoId, 8, 0)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Adicionar lembrete") }
            }

            item { TituloSecao("Sincronização entre aparelhos") }
            item { CartaoSync() }

            item { TituloSecao("Backup") }
            item {
                Text(
                    "Exporte um arquivo com todo o seu progresso (revisões, anotações, favoritos e pontos criados). " +
                        "Na tela de salvar, escolha o Google Drive para guardar na nuvem. " +
                        "O Android também faz backup automático do app se o backup do Google estiver ativado no celular.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { exportar.launch("revisatrt-backup-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) { Text("Exportar") }
                    OutlinedButton(onClick = { importar.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.weight(1f)) { Text("Restaurar") }
                }
            }
            item { TituloSecao("Sobre") }
            item {
                Text(
                    "RevisaTRT ${BuildConfigInfo.versao(contexto)}\nTRT-RS · Analista Judiciário – Área Judiciária · FCC\nProva: 31/01/2027",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editandoHorario?.let { l ->
        val estado = rememberTimePickerState(l.hora, l.minuto, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editandoHorario = null },
            title = { Text("Horário do lembrete") },
            text = { TimePicker(estado) },
            confirmButton = {
                TextButton(onClick = {
                    salvar(a.copy(lembretes = a.lembretes.map { if (it.id == l.id) it.copy(hora = estado.hour, minuto = estado.minute) else it }))
                    editandoHorario = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editandoHorario = null }) { Text("Cancelar") } },
        )
    }

    confirmarImportar?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmarImportar = null },
            title = { Text("Restaurar backup?") },
            text = { Text("Todos os dados atuais do app serão substituídos pelos do arquivo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarImportar = null
                    escopo.launch {
                        val msg = runCatching { repo.importar(uri) }.getOrElse { "Não foi possível restaurar: ${it.message}" }
                        Lembretes.reagendar(contexto)
                        snackbar.showSnackbar(msg)
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { confirmarImportar = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Contador(rotulo: String, valor: Int, min: Int, max: Int, passo: Int = 1, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rotulo, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onChange((valor - passo).coerceAtLeast(min)) }) { Text("–", style = MaterialTheme.typography.titleLarge) }
        Text("$valor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { onChange((valor + passo).coerceAtMost(max)) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun LinhaSwitch(rotulo: String, valor: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rotulo, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = valor, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun CartaoSync() {
    val contexto = LocalContext.current
    val app = contexto.applicationContext as RevisaApp
    val estado by app.sync.estado.collectAsStateWithLifecycle()
    val pendentes by app.repo.dao.totalPendentes().collectAsStateWithLifecycle(0)
    val escopo = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            !estado.configurado -> Text(
                "A sincronização ainda não foi configurada nesta versão do app (falta o arquivo do Firebase).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            estado.email == null -> {
                Text(
                    "Entre com a sua conta Google para manter revisões, anotações e favoritos iguais no celular e no tablet. " +
                        "Use a mesma conta nos dois aparelhos.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = { (contexto as? android.app.Activity)?.let { act -> escopo.launch { app.sync.entrar(act) } } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Entrar com Google") }
            }
            else -> {
                Text("Conectada como ${estado.email}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        estado.sincronizando -> "Sincronizando…"
                        pendentes > 0 -> "$pendentes alteração(ões) aguardando envio"
                        estado.ultima != null -> "Tudo sincronizado · ${Analise.dataHora(estado.ultima!!)}"
                        else -> "Conectada"
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { escopo.launch { app.sync.sincronizarAgora() } }, modifier = Modifier.weight(1f)) { Text("Sincronizar agora") }
                    OutlinedButton(onClick = { app.sync.sair() }, modifier = Modifier.weight(1f)) { Text("Sair") }
                }
            }
        }
        estado.erro?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}
