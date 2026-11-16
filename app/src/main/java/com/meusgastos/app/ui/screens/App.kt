@file:OptIn(ExperimentalMaterial3Api::class)

package com.meusgastos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meusgastos.app.data.Lancamento
import com.meusgastos.app.data.Plano
import com.meusgastos.app.data.Tipo
import com.meusgastos.app.ui.AppState
import com.meusgastos.app.ui.MainViewModel
import com.meusgastos.app.ui.brl
import com.meusgastos.app.ui.ymLabel
import com.meusgastos.app.ui.theme.Ambar
import com.meusgastos.app.ui.theme.Azul
import com.meusgastos.app.ui.theme.Verde
import com.meusgastos.app.ui.theme.Vermelho

enum class Aba(val titulo: String, val icone: ImageVector) {
    INICIO("Início", Icons.Filled.Home),
    LANC("Lançamentos", Icons.Filled.Receipt),
    REL("Relatórios", Icons.Filled.PieChart),
    PREV("Previdência", Icons.Filled.Savings),
    AJUSTES("Ajustes", Icons.Filled.Settings),
}

/** O que está aberto por cima da tela: formulário de lançamento ou de plano. */
sealed interface Folha {
    data class Lanc(val editar: Lancamento?, val tipoInicial: String = Tipo.DESPESA) : Folha
    data class PlanoF(val editar: Plano?) : Folha
}

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var aba by rememberSaveable { mutableStateOf(Aba.INICIO) }
    var folha by remember { mutableStateOf<Folha?>(null) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.mensagens.collect { snack.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(aba.titulo, fontWeight = FontWeight.Bold) },
                actions = { if (aba != Aba.AJUSTES) SeletorMes(s, vm) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Aba.entries.forEach { a ->
                    NavigationBarItem(selected = aba == a, onClick = { aba = a },
                        icon = { Icon(a.icone, null) }, label = { Text(a.titulo, fontSize = 10.sp, maxLines = 1) })
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { folha = Folha.Lanc(null, if (aba == Aba.PREV) Tipo.APORTE else Tipo.DESPESA) },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Icon(Icons.Filled.Add, "Novo lançamento")
            }
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val editar: (Lancamento) -> Unit = { folha = Folha.Lanc(it) }
            when (aba) {
                Aba.INICIO -> TelaInicio(s, editar) { aba = it }
                Aba.LANC -> TelaLancamentos(s, editar)
                Aba.REL -> TelaRelatorios(s)
                Aba.PREV -> TelaPrevidencia(s, editar, abrirPlano = { folha = Folha.PlanoF(it) })
                Aba.AJUSTES -> TelaAjustes(s, vm)
            }
        }
    }

    when (val f = folha) {
        is Folha.Lanc -> FormLancamento(s, f.editar, f.tipoInicial, vm, onClose = { folha = null })
        is Folha.PlanoF -> FormPlano(s, f.editar, vm, onClose = { folha = null })
        null -> {}
    }
}

@Composable
private fun SeletorMes(s: AppState, vm: MainViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 8.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))) {
        IconButton(onClick = { vm.mudarMes(-1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.ChevronLeft, "Mês anterior") }
        Text(ymLabel(s.ym), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.width(108.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
        IconButton(onClick = { vm.mudarMes(1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.ChevronRight, "Próximo mês") }
    }
}

// ---------------- componentes reutilizáveis ----------------

val padTela = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)

@Composable
fun Cartao(titulo: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            if (titulo != null) {
                Text(titulo.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.4.sp)
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun Kpi(rotulo: String, valor: String, cor: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    Card(modifier.padding(vertical = 4.dp), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Text(rotulo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(valor, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = cor, maxLines = 1)
        }
    }
}

@Composable
fun KpiGrande(rotulo: String, valor: String, fundo: Color = MaterialTheme.colorScheme.primary) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = fundo, contentColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Text(rotulo, fontSize = 13.sp, color = Color.White.copy(alpha = .85f))
            Text(valor, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Barra(fracao: Double, nivel: Int = 0, cor: Color? = null) {
    val c = cor ?: when (nivel) { 2 -> Vermelho; 1 -> Ambar; else -> MaterialTheme.colorScheme.primary }
    LinearProgressIndicator(progress = { fracao.coerceIn(0.0, 1.0).toFloat() },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp),
        color = c, trackColor = MaterialTheme.colorScheme.surfaceVariant)
}

fun corTipo(tipo: String) = when (tipo) { Tipo.RECEITA -> Verde; Tipo.APORTE -> Azul; else -> Vermelho }

@Composable
fun LinhaLanc(s: AppState, l: Lancamento, onClick: (Lancamento) -> Unit) {
    val m = s.meta(l)
    Row(Modifier.fillMaxWidth().clickable { onClick(l) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(m.cor.copy(alpha = .15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(m.icone, fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(l.descricao.ifBlank { m.nome }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(m.nome, l.pagamento?.ifBlank { null }, if (l.tipo == Tipo.RECEITA && l.tributavel) "tributável" else null)
            Text(sub.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text((if (l.tipo == Tipo.RECEITA) "+ " else "− ") + brl(l.valor), fontWeight = FontWeight.Bold, color = corTipo(l.tipo))
    }
}

@Composable
fun Vazio(texto: String) {
    Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable
fun LinhaValor(rotulo: String, valor: String, cor: Color = MaterialTheme.colorScheme.onSurface, negrito: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(rotulo, Modifier.weight(1f), fontSize = 14.sp, fontWeight = if (negrito) FontWeight.Bold else FontWeight.Normal)
        Text(valor, color = cor, fontSize = 14.sp, fontWeight = if (negrito) FontWeight.Bold else FontWeight.SemiBold)
    }
}
