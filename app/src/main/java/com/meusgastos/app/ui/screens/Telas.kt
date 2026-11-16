@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.meusgastos.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meusgastos.app.data.Categoria
import com.meusgastos.app.data.Lancamento
import com.meusgastos.app.data.Plano
import com.meusgastos.app.data.Tipo
import com.meusgastos.app.ui.AppState
import com.meusgastos.app.ui.MainViewModel
import com.meusgastos.app.ui.brl
import com.meusgastos.app.ui.cor
import com.meusgastos.app.ui.dataBR
import com.meusgastos.app.ui.diaLabel
import com.meusgastos.app.ui.doAno
import com.meusgastos.app.ui.doMes
import com.meusgastos.app.ui.fmtInput
import com.meusgastos.app.ui.parseMoney
import com.meusgastos.app.ui.pct
import com.meusgastos.app.ui.totais
import com.meusgastos.app.ui.ymLabel
import com.meusgastos.app.ui.ymShort
import com.meusgastos.app.ui.theme.Ambar
import com.meusgastos.app.ui.theme.Azul
import com.meusgastos.app.ui.theme.Verde
import com.meusgastos.app.ui.theme.Vermelho
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min

private fun ordenar(l: List<Lancamento>) = l.sortedWith(compareByDescending<Lancamento> { it.data }.thenByDescending { it.criadoEm })

// ====================== INÍCIO ======================
@Composable
fun TelaInicio(s: AppState, editar: (Lancamento) -> Unit, irPara: (Aba) -> Unit) {
    val mes = s.tx.doMes(s.ym)
    val t = totais(mes)
    val ant = totais(s.tx.doMes(s.ym.minusMonths(1)))
    val variacao = if (ant.d > 0) (t.d - ant.d) / ant.d else Double.NaN
    val orc = s.orcamentos(s.ym)
    val alertas = orc.filter { it.nivel > 0 }.sortedByDescending { it.r }
    LazyColumn(contentPadding = padTela) {
        item { KpiGrande("Sobra do mês (receitas − despesas − aportes)", brl(t.saldo)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("Receitas", brl(t.r), Verde, Modifier.weight(1f)); Kpi("Despesas", brl(t.d), Vermelho, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("Aportes previdência", brl(t.a), Azul, Modifier.weight(1f))
                Kpi("Despesas vs mês anterior",
                    if (variacao.isFinite()) (if (variacao > 0) "▲ " else "▼ ") + pct(abs(variacao)) else "—",
                    if (variacao > 0) Vermelho else Verde, Modifier.weight(1f))
            }
        }
        if (alertas.isNotEmpty()) item {
            Cartao("Alertas de orçamento") {
                alertas.forEach { b ->
                    val c = if (b.nivel == 2) Vermelho else Ambar
                    Text((if (b.nivel == 2) "⛔ " else "⚠️ ") + "${b.c.nome}: ${brl(b.gasto)} de ${brl(b.c.orcamento)} (${pct(b.r)})" +
                        if (b.nivel == 2) " — passou ${brl(b.gasto - b.c.orcamento)}" else "",
                        color = c, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).background(c.copy(alpha = .1f), RoundedCornerShape(10.dp)).padding(8.dp))
                }
            }
        }
        item {
            Cartao("Orçamento por categoria") {
                if (orc.isEmpty()) {
                    Vazio("Nenhum orçamento definido.")
                    OutlinedButton(onClick = { irPara(Aba.AJUSTES) }) { Text("Definir limites") }
                } else orc.forEach { b ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${b.c.icone} ${b.c.nome}"); Text("${brl(b.gasto)} / ${brl(b.c.orcamento)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Barra(b.r, b.nivel)
                }
            }
        }
        item {
            Cartao("Últimos lançamentos") {
                val rec = ordenar(mes).take(6)
                if (rec.isEmpty()) Vazio("Nada lançado em ${ymLabel(s.ym)}. Toque em + para começar.")
                else {
                    rec.forEachIndexed { i, l -> if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); LinhaLanc(s, l, editar) }
                    TextButton(onClick = { irPara(Aba.LANC) }) { Text("Ver todos") }
                }
            }
        }
    }
}

// ====================== LANÇAMENTOS ======================
@Composable
fun TelaLancamentos(s: AppState, editar: (Lancamento) -> Unit) {
    var filtro by rememberSaveable { mutableStateOf("todos") }
    var busca by rememberSaveable { mutableStateOf("") }
    var lista = s.tx.doMes(s.ym)
    if (filtro != "todos") lista = lista.filter { it.tipo == filtro }
    val q = busca.trim().lowercase()
    if (q.isNotEmpty()) lista = lista.filter {
        it.descricao.lowercase().contains(q) || s.meta(it).nome.lowercase().contains(q) || (it.pagamento ?: "").lowercase().contains(q)
    }
    lista = ordenar(lista)
    val t = totais(lista)
    val porDia = lista.groupBy { it.data }
    LazyColumn(contentPadding = padTela) {
        item {
            OutlinedTextField(busca, { busca = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Buscar descrição, categoria, pagamento…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (busca.isNotEmpty()) IconButton(onClick = { busca = "" }) { Icon(Icons.Filled.Close, "Limpar") } })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                listOf("todos" to "Todos", Tipo.DESPESA to "Despesas", Tipo.RECEITA to "Receitas", Tipo.APORTE to "Aportes").forEach { (k, l) ->
                    FilterChip(selected = filtro == k, onClick = { filtro = k }, label = { Text(l) })
                }
            }
            Text("${lista.size} lançamento(s) · receitas ${brl(t.r)} · despesas ${brl(t.d)} · aportes ${brl(t.a)}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (lista.isEmpty()) item { Cartao { Vazio("Nenhum lançamento encontrado.") } }
        porDia.forEach { (dia, itens) ->
            item(key = "d$dia") {
                Text(diaLabel(dia), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, start = 2.dp))
                Cartao {
                    itens.forEachIndexed { i, l -> if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); LinhaLanc(s, l, editar) }
                }
            }
        }
    }
}

// ====================== RELATÓRIOS ======================
@Composable
fun TelaRelatorios(s: AppState) {
    val desp = s.tx.doMes(s.ym).filter { it.tipo == Tipo.DESPESA }
    val total = desp.sumOf { it.valor }
    data class Lin(val nome: String, val icone: String, val cor: Color, val v: Double, val media: Double)
    val linhas = buildList {
        s.catDespesa.forEach { c ->
            val v = desp.filter { it.categoria == c.id }.sumOf { it.valor }
            val media = (1L..3L).sumOf { k -> s.tx.doMes(s.ym.minusMonths(k)).filter { it.tipo == Tipo.DESPESA && it.categoria == c.id }.sumOf { it.valor } } / 3
            if (v > 0 || media > 0) add(Lin(c.nome, c.icone, cor(c.cor), v, media))
        }
        val sem = desp.filter { l -> s.catDespesa.none { it.id == l.categoria } }.sumOf { it.valor }
        if (sem > 0) add(Lin("Sem categoria", "❔", Color.Gray, sem, 0.0))
    }.sortedByDescending { it.v }
    val ano = s.ym.year
    val ty = totais(s.tx.doAno(ano))
    val tribAno = s.tx.doAno(ano).filter { it.tipo == Tipo.RECEITA && it.tributavel }.sumOf { it.valor }
    val meses = (11 downTo 0).map { s.ym.minusMonths(it.toLong()) }
    val serie = meses.map { totais(s.tx.doMes(it)) }

    LazyColumn(contentPadding = padTela) {
        item {
            Cartao("Despesas por categoria · ${ymLabel(s.ym)}") {
                if (total <= 0) Vazio("Sem despesas neste mês.")
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    Rosca(linhas.filter { it.v > 0 }.map { it.v to it.cor }, Modifier.size(150.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        linhas.filter { it.v > 0 }.take(8).forEach {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Box(Modifier.size(10.dp).background(it.cor, CircleShape)); Spacer(Modifier.width(6.dp))
                                Text("${it.nome} ${pct(it.v / total)}", fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        if (linhas.isNotEmpty()) item {
            Cartao("Detalhe por categoria") {
                Row { Text("Categoria", Modifier.weight(1.6f), fontSize = 12.sp); Text("Mês", Modifier.weight(1f), fontSize = 12.sp); Text("%", Modifier.weight(.6f), fontSize = 12.sp); Text("Média 3m", Modifier.weight(1f), fontSize = 12.sp) }
                linhas.forEach {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Text("${it.icone} ${it.nome}", Modifier.weight(1.6f), fontSize = 13.sp, maxLines = 1)
                        Text(brl(it.v), Modifier.weight(1f), fontSize = 13.sp)
                        Text(pct(if (total > 0) it.v / total else Double.NaN), Modifier.weight(.6f), fontSize = 13.sp)
                        Text(brl(it.media), Modifier.weight(1f), fontSize = 13.sp, color = if (it.media > 0 && it.v > it.media * 1.15) Vermelho else Color.Unspecified)
                    }
                }
                Text("Em vermelho: mais de 15% acima da média dos 3 meses anteriores.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Cartao("Últimos 12 meses") {
                Barras(serie.map { listOf(it.r, it.d, it.a) }, listOf(Verde, Vermelho, Azul), Modifier.fillMaxWidth().height(180.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    meses.forEachIndexed { i, m -> if (i % 2 == 1) Text(ymShort(m), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf("Receitas" to Verde, "Despesas" to Vermelho, "Aportes" to Azul).forEach { (n, c) ->
                        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(c, CircleShape)); Text(" $n", fontSize = 12.sp) }
                    }
                }
            }
        }
        val porPag = desp.groupBy { it.pagamento?.ifBlank { null } ?: "Não informado" }.mapValues { e -> e.value.sumOf { it.valor } }
        if (porPag.isNotEmpty()) item {
            Cartao("Por forma de pagamento") {
                porPag.entries.sortedByDescending { it.value }.forEach { LinhaValor("${it.key} (${pct(it.value / total)})", brl(it.value)) }
            }
        }
        item {
            Cartao("Resumo de $ano") {
                LinhaValor("Receitas", brl(ty.r), Verde)
                LinhaValor("   das quais tributáveis", brl(tribAno))
                LinhaValor("Despesas", brl(ty.d), Vermelho)
                LinhaValor("Aportes em previdência", brl(ty.a), Azul)
                LinhaValor("Sobra no ano", brl(ty.saldo), negrito = true)
                LinhaValor("Taxa de poupança (sobra + aportes) / receitas", pct(if (ty.r > 0) (ty.saldo + ty.a) / ty.r else Double.NaN))
            }
        }
    }
}

@Composable
fun Rosca(fatias: List<Pair<Double, Color>>, modifier: Modifier) {
    val tot = fatias.sumOf { it.first }
    Canvas(modifier) {
        val esp = size.minDimension * 0.2f
        val d = size.minDimension - esp
        var ini = -90f
        fatias.forEach { (v, c) ->
            val ang = (v / tot * 360).toFloat()
            drawArc(c, ini, ang, useCenter = false, topLeft = Offset(esp / 2, esp / 2), size = Size(d, d), style = Stroke(esp))
            ini += ang
        }
    }
}

@Composable
fun Barras(grupos: List<List<Double>>, cores: List<Color>, modifier: Modifier) {
    val max = grupos.flatten().maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val grade = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        for (i in 0..4) { val y = size.height * i / 4; drawLine(grade, Offset(0f, y), Offset(size.width, y), 1f) }
        val gw = size.width / grupos.size
        val bw = gw * 0.75f / cores.size
        grupos.forEachIndexed { g, vals ->
            vals.forEachIndexed { k, v ->
                val h = (v / max * size.height).toFloat()
                drawRect(cores[k], topLeft = Offset(g * gw + gw * 0.125f + k * bw, size.height - h), size = Size(bw * 0.9f, h))
            }
        }
    }
}

// ====================== PREVIDÊNCIA ======================
@Composable
fun TelaPrevidencia(s: AppState, editar: (Lancamento) -> Unit, abrirPlano: (Plano?) -> Unit) {
    val ano = s.ym.year
    val ap = s.tx.filter { it.tipo == Tipo.APORTE }
    val apAno = ap.filter { it.data.startsWith("$ano-") }
    val pgbl = s.planos.filter { it.tipo == "PGBL" }.map { it.id }.toSet()
    val pgblAno = apAno.filter { it.planoId in pgbl }.sumOf { it.valor }
    val trib = s.tx.doAno(ano).filter { it.tipo == Tipo.RECEITA && it.tributavel }.sumOf { it.valor }
    val limite = trib * 0.12
    LazyColumn(contentPadding = padTela) {
        item {
            KpiGrande("Saldo total informado", brl(s.planos.sumOf { it.saldo }), Azul)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("Aportado em $ano", brl(apAno.sumOf { it.valor }), Azul, Modifier.weight(1f))
                Kpi("Aportado (total)", brl(ap.sumOf { it.valor }), modifier = Modifier.weight(1f))
            }
        }
        item {
            Cartao("PGBL × limite de dedução $ano") {
                if (trib <= 0) Vazio("Marque recebimentos como tributáveis para ver quanto ainda cabe de PGBL na dedução do IR (12% da renda tributável).")
                else {
                    LinhaValor("Aportes PGBL", brl(pgblAno), negrito = true)
                    LinhaValor("Limite: 12% de ${brl(trib)} tributáveis", brl(limite))
                    Barra(if (limite > 0) pgblAno / limite else 0.0, if (pgblAno > limite) 2 else 0, if (pgblAno > limite) null else Azul)
                    Text((if (pgblAno >= limite) "Limite atingido: aportes PGBL acima disso não reduzem o IR deste ano."
                        else "Ainda cabem ${brl(limite - pgblAno)} em PGBL para dedução neste ano.") +
                        " Estimativa com base só nas receitas marcadas como tributáveis. Confirme com seu contador.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item {
            Cartao("Planos") {
                if (s.planos.isEmpty()) Vazio("Cadastre seus planos (PGBL/VGBL) para acompanhar aportes e saldo.")
                s.planos.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val a = ap.filter { it.planoId == p.id }
                    val tot = a.sumOf { it.valor }
                    Row(Modifier.fillMaxWidth().clickable { abrirPlano(p) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${p.nome} · ${p.tipo}", fontWeight = FontWeight.SemiBold)
                            Text(listOf(p.instituicao, if (p.saldoData.isNotBlank()) "saldo em ${dataBR(p.saldoData)}" else "").filter { it.isNotBlank() }.joinToString(" · "),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Aportado ${brl(tot)} (${brl(a.filter { it.data.startsWith("$ano-") }.sumOf { it.valor })} em $ano) · Rendimento ${brl(p.saldo - tot)}",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(brl(p.saldo), fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(onClick = { abrirPlano(null) }) { Text("+ Novo plano") }
            }
        }
        item {
            Cartao("Aportes recentes") {
                val rec = ordenar(ap).take(10)
                if (rec.isEmpty()) Vazio("Nenhum aporte lançado. Use + → Aporte.")
                rec.forEachIndexed { i, l -> if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); LinhaLanc(s, l, editar) }
            }
        }
    }
}

// ====================== AJUSTES ======================
private val PALETA = listOf("#0F766E", "#16A34A", "#EA580C", "#2563EB", "#CA8A04", "#DC2626", "#7C3AED", "#DB2777", "#0891B2", "#A16207", "#475569", "#6B7280")

@Composable
fun TelaAjustes(s: AppState, vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var excluir by remember { mutableStateOf<Categoria?>(null) }
    var novoPag by remember { mutableStateOf("") }
    var confirmarRestaurar by remember { mutableStateOf<Uri?>(null) }

    val salvarCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { escrever(ctx, uri, vm.repo.exportarCsv()); vm.aviso("CSV exportado") }
    }
    val salvarJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { escrever(ctx, uri, vm.repo.exportarJson()); vm.aviso("Backup salvo") }
    }
    val abrirJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmarRestaurar = uri }

    LazyColumn(contentPadding = padTela) {
        item { Cartao("Categorias de despesa e orçamento") {
            Text("Limite mensal em R$. Deixe vazio para não ter limite. Toque na bolinha para trocar a cor.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.catDespesa.forEach { c -> EditorCategoria(c, true, vm) { excluir = c } }
            OutlinedButton(onClick = { vm.novaCategoria(Tipo.DESPESA) }, Modifier.padding(top = 8.dp)) { Text("+ Categoria") }
        } }
        item { Cartao("Categorias de recebimento") {
            s.catReceita.forEach { c -> EditorCategoria(c, false, vm) { excluir = c } }
            OutlinedButton(onClick = { vm.novaCategoria(Tipo.RECEITA) }, Modifier.padding(top = 8.dp)) { Text("+ Categoria") }
        } }
        item { Cartao("Formas de pagamento") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.pagamentos.forEach { p -> AssistChip(onClick = { vm.delPagamento(p) }, label = { Text("$p ×") }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(novoPag, { novoPag = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Ex.: Cartão Nubank") })
                TextButton(onClick = { vm.addPagamento(novoPag); novoPag = "" }) { Text("Adicionar") }
            }
        } }
        item { Cartao("Exportar e backup") {
            Text("${s.tx.size} lançamento(s) guardados neste aparelho.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { salvarCsv.launch("gastos-${LocalDate.now()}.csv") }) { Text("Exportar CSV (Excel)") }
                OutlinedButton(onClick = { salvarJson.launch("backup-gastos-${LocalDate.now()}.json") }) { Text("Salvar backup") }
                OutlinedButton(onClick = { abrirJson.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Restaurar backup") }
            }
            Text("O backup usa o mesmo formato do app web: dá para levar os dados de um para o outro. Salve o backup no Google Drive para não perder nada se trocar de celular.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        } }
    }

    excluir?.let { c ->
        val usados = s.tx.count { it.categoria == c.id }
        AlertDialog(onDismissRequest = { excluir = null },
            title = { Text("Excluir “${c.nome}”?") },
            text = { if (usados > 0) Text("Há $usados lançamento(s) nesta categoria. Eles ficarão como “Sem categoria”.") },
            confirmButton = { TextButton(onClick = { vm.excluirCategoria(c); excluir = null }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { excluir = null }) { Text("Cancelar") } })
    }
    confirmarRestaurar?.let { uri ->
        AlertDialog(onDismissRequest = { confirmarRestaurar = null },
            title = { Text("Restaurar backup?") },
            text = { Text("As categorias, planos e formas de pagamento serão substituídos pelos do backup. Os lançamentos serão mesclados, sem duplicar.") },
            confirmButton = { TextButton(onClick = {
                confirmarRestaurar = null
                scope.launch {
                    try {
                        val txt = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } } ?: ""
                        vm.aviso("${vm.repo.importarJson(txt)} lançamento(s) restaurado(s)")
                    } catch (e: Exception) { vm.aviso("Não foi possível restaurar: ${e.message}") }
                }
            }) { Text("Restaurar") } },
            dismissButton = { TextButton(onClick = { confirmarRestaurar = null }) { Text("Cancelar") } })
    }
}

private suspend fun escrever(ctx: Context, uri: Uri, texto: String) = withContext(Dispatchers.IO) {
    ctx.contentResolver.openOutputStream(uri)?.use { it.write(texto.toByteArray(Charsets.UTF_8)) }
}

@Composable
private fun EditorCategoria(c: Categoria, comOrcamento: Boolean, vm: MainViewModel, onExcluir: () -> Unit) {
    var nome by remember(c.id) { mutableStateOf("${c.icone} ${c.nome}") }
    var orc by remember(c.id) { mutableStateOf(fmtInput(c.orcamento)) }
    fun salvar() {
        val t = nome.trim()
        val partes = t.split(" ", limit = 2)
        // Se o primeiro "pedaço" não tem letras nem números, é o ícone (emoji)
        val (icone, n) = if (partes.size == 2 && partes[0].none { it.isLetterOrDigit() }) partes[0] to partes[1].trim() else c.icone to t
        val novo = c.copy(icone = icone, nome = n.ifBlank { c.nome }, orcamento = if (comOrcamento) parseMoney(orc) else 0.0)
        if (novo != c) vm.salvarCategoria(novo)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(Modifier.size(28.dp).background(cor(c.cor), CircleShape).clickable {
            vm.salvarCategoria(c.copy(cor = PALETA[(PALETA.indexOf(c.cor.uppercase()) + 1) % PALETA.size]))
        })
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(nome, { nome = it }, Modifier.weight(1f).onBlur { salvar() }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium)
        if (comOrcamento) {
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(orc, { orc = it }, Modifier.width(110.dp).onBlur { salvar() }, singleLine = true,
                placeholder = { Text("Sem limite", fontSize = 12.sp) }, textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        IconButton(onClick = onExcluir) { Icon(Icons.Filled.Close, "Excluir") }
    }
}

/** Executa [acao] quando o campo perde o foco (para salvar edições sem botão). */
fun Modifier.onBlur(acao: () -> Unit): Modifier = composed {
    var teveFoco by remember { mutableStateOf(false) }
    onFocusChanged { f -> if (f.isFocused) teveFoco = true else if (teveFoco) { teveFoco = false; acao() } }
}
