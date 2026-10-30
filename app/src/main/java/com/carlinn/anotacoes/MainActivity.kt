package com.carlinn.anotacoes

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        Lembretes.criarCanal(this)
        Store.reagendarTodos()
        setContent { TemaApp { App() } }
    }
}

@Composable
fun TemaApp(content: @Composable () -> Unit) {
    val escuro = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val cores = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (escuro) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        escuro -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = cores, content = content)
}

val Tipo.nomeAba
    get() = when (this) {
        Tipo.TAREFA -> "Tarefas"
        Tipo.COMPRA -> "Compras"
        Tipo.META -> "Metas"
    }

val Tipo.icone: ImageVector
    get() = when (this) {
        Tipo.TAREFA -> Icons.Default.CheckCircle
        Tipo.COMPRA -> Icons.Default.ShoppingCart
        Tipo.META -> Icons.Default.Star
    }

// ---------- Datas ----------

private val zona: ZoneId get() = ZoneId.systemDefault()
private val fmtData = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val fmtDataHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

fun formatarData(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zona).format(fmtData)
fun formatarDataHora(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zona).format(fmtDataHora)
fun inicioDeHoje(): Long = LocalDate.now().atStartOfDay(zona).toInstant().toEpochMilli()

fun escolherData(ctx: Context, atual: Long?, onOk: (Long) -> Unit) {
    val d = atual?.let { Instant.ofEpochMilli(it).atZone(zona).toLocalDate() } ?: LocalDate.now()
    DatePickerDialog(
        ctx,
        { _, ano, mes, dia -> onOk(LocalDate.of(ano, mes + 1, dia).atStartOfDay(zona).toInstant().toEpochMilli()) },
        d.year, d.monthValue - 1, d.dayOfMonth
    ).show()
}

fun escolherDataHora(ctx: Context, atual: Long?, onOk: (Long) -> Unit) {
    val dt = atual?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zona) }
        ?: LocalDateTime.now().plusHours(1).withMinute(0)
    DatePickerDialog(
        ctx,
        { _, ano, mes, dia ->
            TimePickerDialog(
                ctx,
                { _, hora, minuto ->
                    onOk(LocalDateTime.of(ano, mes + 1, dia, hora, minuto).atZone(zona).toInstant().toEpochMilli())
                },
                dt.hour, dt.minute, true
            ).show()
        },
        dt.year, dt.monthValue - 1, dt.dayOfMonth
    ).show()
}

// ---------- Telas ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var aba by rememberSaveable { mutableStateOf(Tipo.TAREFA) }
    var criando by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Item?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val pedirPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) pedirPermissao.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(aba.nomeAba) }) },
        bottomBar = {
            NavigationBar {
                Tipo.entries.forEach { t ->
                    NavigationBarItem(
                        selected = aba == t,
                        onClick = { aba = t },
                        icon = { Icon(t.icone, contentDescription = null) },
                        label = { Text(t.nomeAba) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { criando = true }) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Lista(
            tipo = aba,
            modifier = Modifier.padding(padding),
            onEditar = { editando = it },
            onApagar = { item ->
                Store.remover(item.id)
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val r = snackbar.showSnackbar(
                        "\"${item.titulo}\" apagado",
                        actionLabel = "Desfazer",
                        duration = SnackbarDuration.Short,
                    )
                    if (r == SnackbarResult.ActionPerformed) Store.salvar(item)
                }
            },
        )
    }

    if (criando || editando != null) {
        ItemDialog(
            tipo = editando?.tipo ?: aba,
            original = editando,
            onFechar = { criando = false; editando = null },
            onSalvar = {
                Store.salvar(it)
                criando = false
                editando = null
            },
        )
    }
}

@Composable
fun Lista(
    tipo: Tipo,
    modifier: Modifier,
    onEditar: (Item) -> Unit,
    onApagar: (Item) -> Unit,
) {
    val itens = Store.itens.filter { it.tipo == tipo }
    val pendentes = itens.filter { !it.feito }
        .sortedWith(compareBy<Item>({ it.prazo ?: Long.MAX_VALUE }, { it.criadoEm }))
    val feitos = itens.filter { it.feito }.sortedByDescending { it.feitoEm ?: 0L }

    if (itens.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                when (tipo) {
                    Tipo.TAREFA -> "Nenhuma tarefa ainda.\nToque em + para adicionar."
                    Tipo.COMPRA -> "Lista de compras vazia.\nToque em + para adicionar."
                    Tipo.META -> "Nenhuma meta ainda.\nToque em + para criar uma."
                },
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(pendentes, key = { it.id }) { ItemCard(it, onEditar, onApagar) }
        if (feitos.isNotEmpty()) {
            item(key = "cabecalho") {
                Text(
                    "${if (tipo == Tipo.COMPRA) "Comprados" else "Concluídos"} (${feitos.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(feitos, key = { it.id }) { ItemCard(it, onEditar, onApagar) }
        }
    }
}

@Composable
fun ItemCard(item: Item, onEditar: (Item) -> Unit, onApagar: (Item) -> Unit) {
    val cores = MaterialTheme.colorScheme
    Card(
        onClick = { onEditar(item) },
        modifier = Modifier.fillMaxWidth(),
        colors = if (item.feito) CardDefaults.cardColors(containerColor = cores.surfaceVariant.copy(alpha = 0.5f))
        else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.feito, onCheckedChange = { Store.alternarFeito(item.id) })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.titulo,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (item.feito) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (item.feito) cores.onSurfaceVariant else cores.onSurface,
                )
                if (item.quantidade.isNotBlank()) {
                    Text("Qtd: ${item.quantidade}", style = MaterialTheme.typography.bodySmall, color = cores.onSurfaceVariant)
                }
                if (item.nota.isNotBlank()) {
                    Text(
                        item.nota,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cores.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.tipo == Tipo.META) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { item.progresso / 100f },
                            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                        )
                        Text("  ${item.progresso}%", style = MaterialTheme.typography.labelMedium)
                        if (!item.feito) {
                            TextButton(onClick = { Store.avancarMeta(item.id) }) { Text("+10%") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item.prazo?.let {
                        val atrasado = !item.feito && it < inicioDeHoje()
                        Info(Icons.Default.DateRange, formatarData(it), if (atrasado) cores.error else cores.onSurfaceVariant)
                    }
                    item.lembrete?.let {
                        val passou = it <= System.currentTimeMillis()
                        Info(
                            Icons.Default.Notifications,
                            formatarDataHora(it),
                            if (passou) cores.onSurfaceVariant.copy(alpha = 0.6f) else cores.primary,
                        )
                    }
                }
            }
            IconButton(onClick = { onApagar(item) }) {
                Icon(Icons.Default.Delete, contentDescription = "Apagar", tint = cores.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun Info(icone: ImageVector, texto: String, cor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(texto, style = MaterialTheme.typography.labelMedium, color = cor)
    }
}

@Composable
fun ItemDialog(tipo: Tipo, original: Item?, onFechar: () -> Unit, onSalvar: (Item) -> Unit) {
    val ctx = LocalContext.current
    var titulo by remember { mutableStateOf(original?.titulo ?: "") }
    var nota by remember { mutableStateOf(original?.nota ?: "") }
    var quantidade by remember { mutableStateOf(original?.quantidade ?: "") }
    var prazo by remember { mutableStateOf(original?.prazo) }
    var lembrete by remember { mutableStateOf(original?.lembrete) }
    var progresso by remember { mutableFloatStateOf((original?.progresso ?: 0).toFloat()) }

    val nome = when (tipo) {
        Tipo.TAREFA -> "tarefa"
        Tipo.COMPRA -> "compra"
        Tipo.META -> "meta"
    }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(if (original == null) "Nova $nome" else "Editar $nome") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text(if (tipo == Tipo.COMPRA) "O que comprar" else "Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tipo == Tipo.COMPRA) {
                    OutlinedTextField(
                        value = quantidade,
                        onValueChange = { quantidade = it },
                        label = { Text("Quantidade (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it },
                    label = { Text("Observações (opcional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tipo != Tipo.COMPRA) {
                    CampoData(
                        rotulo = if (tipo == Tipo.META) "Prazo da meta" else "Data para realizar",
                        valor = prazo?.let(::formatarData),
                        icone = Icons.Default.DateRange,
                        onEscolher = { escolherData(ctx, prazo) { prazo = it } },
                        onLimpar = { prazo = null },
                    )
                }
                CampoData(
                    rotulo = "Lembrete",
                    valor = lembrete?.let(::formatarDataHora),
                    icone = Icons.Default.Notifications,
                    onEscolher = { escolherDataHora(ctx, lembrete) { lembrete = it } },
                    onLimpar = { lembrete = null },
                )
                lembrete?.let {
                    if (it <= System.currentTimeMillis()) {
                        Text(
                            "Esse horário já passou — escolha um horário futuro para ser avisado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (tipo == Tipo.META) {
                    Text("Progresso: ${progresso.roundToInt()}%")
                    Slider(value = progresso, onValueChange = { progresso = it }, valueRange = 0f..100f, steps = 9)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = titulo.isNotBlank(),
                onClick = {
                    val p = progresso.roundToInt()
                    val base = original ?: Item(tipo = tipo, titulo = "")
                    val concluiu = tipo == Tipo.META && p >= 100 && !base.feito
                    onSalvar(
                        base.copy(
                            titulo = titulo.trim(),
                            nota = nota.trim(),
                            quantidade = quantidade.trim(),
                            prazo = prazo,
                            lembrete = lembrete,
                            progresso = p,
                            feito = base.feito || concluiu,
                            feitoEm = if (concluiu) System.currentTimeMillis() else base.feitoEm,
                        )
                    )
                },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } },
    )
}

@Composable
fun CampoData(
    rotulo: String,
    valor: String?,
    icone: ImageVector,
    onEscolher: () -> Unit,
    onLimpar: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onEscolher, modifier = Modifier.weight(1f)) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (valor == null) "Adicionar ${rotulo.lowercase()}" else "$rotulo: $valor", maxLines = 1)
        }
        if (valor != null) {
            IconButton(onClick = onLimpar) { Icon(Icons.Default.Close, contentDescription = "Remover $rotulo") }
        }
    }
}
