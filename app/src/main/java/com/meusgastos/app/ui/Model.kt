package com.meusgastos.app.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meusgastos.app.data.AppDatabase
import com.meusgastos.app.data.Categoria
import com.meusgastos.app.data.FormaPagamento
import com.meusgastos.app.data.Lancamento
import com.meusgastos.app.data.Plano
import com.meusgastos.app.data.Repo
import com.meusgastos.app.data.Tipo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ---------------- formatação ----------------
val BR: Locale = Locale("pt", "BR")
private val moeda: NumberFormat = NumberFormat.getCurrencyInstance(BR)
fun brl(v: Double): String = moeda.format(v)
fun pct(v: Double): String = if (v.isFinite()) String.format(BR, "%.1f%%", v * 100) else "—"
fun fmtInput(v: Double): String = if (v == 0.0) "" else String.format(BR, "%,.2f", v)
fun parseMoney(s: String): Double {
    var t = s.trim().replace("R$", "").replace(" ", "").replace(" ", "")
    if (t.isEmpty()) return 0.0
    if (t.contains(',')) t = t.replace(".", "").replace(',', '.')
    return t.toDoubleOrNull()?.let { Math.round(it * 100) / 100.0 } ?: 0.0
}
fun ymLabel(ym: YearMonth): String =
    ym.month.getDisplayName(TextStyle.FULL, BR).replaceFirstChar { it.uppercase() } + " " + ym.year
fun ymShort(ym: YearMonth): String =
    ym.month.getDisplayName(TextStyle.SHORT, BR).replace(".", "") + "/" + (ym.year % 100).toString().padStart(2, '0')
fun dataBR(iso: String): String = try { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) } catch (e: Exception) { iso }
fun diaLabel(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMM", BR)).replaceFirstChar { it.uppercase() }
} catch (e: Exception) { iso }
fun cor(hex: String): Color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }

// ---------------- estado e cálculos ----------------
data class Totais(val r: Double, val d: Double, val a: Double) {
    val saldo get() = r - d - a
}
fun totais(l: List<Lancamento>) = Totais(
    l.filter { it.tipo == Tipo.RECEITA }.sumOf { it.valor },
    l.filter { it.tipo == Tipo.DESPESA }.sumOf { it.valor },
    l.filter { it.tipo == Tipo.APORTE }.sumOf { it.valor },
)
fun List<Lancamento>.doMes(ym: YearMonth) = filter { it.data.startsWith(ym.toString()) }
fun List<Lancamento>.doAno(ano: Int) = filter { it.data.startsWith("$ano-") }

data class Meta(val nome: String, val icone: String, val cor: Color)

data class BudgetSt(val c: Categoria, val gasto: Double) {
    val r get() = if (c.orcamento > 0) gasto / c.orcamento else 0.0
    /** 0 = ok, 1 = atenção (>= 80%), 2 = estourado */
    val nivel get() = if (r > 1) 2 else if (r >= 0.8) 1 else 0
}

data class AppState(
    val tx: List<Lancamento> = emptyList(),
    val categorias: List<Categoria> = emptyList(),
    val planos: List<Plano> = emptyList(),
    val pagamentos: List<String> = emptyList(),
    val ym: YearMonth = YearMonth.now(),
) {
    val catDespesa get() = categorias.filter { it.tipo == Tipo.DESPESA }
    val catReceita get() = categorias.filter { it.tipo == Tipo.RECEITA }
    fun cats(tipo: String) = if (tipo == Tipo.RECEITA) catReceita else catDespesa
    fun meta(l: Lancamento): Meta = if (l.tipo == Tipo.APORTE) {
        Meta(planos.firstOrNull { it.id == l.planoId }?.nome ?: "Previdência", "🛡️", Color(0xFF1D4ED8))
    } else {
        categorias.firstOrNull { it.id == l.categoria }?.let { Meta(it.nome, it.icone, cor(it.cor)) }
            ?: Meta("Sem categoria", "❔", Color.Gray)
    }
    fun orcamentos(ym: YearMonth): List<BudgetSt> {
        val d = tx.doMes(ym).filter { it.tipo == Tipo.DESPESA }
        return catDespesa.filter { it.orcamento > 0 }.map { c -> BudgetSt(c, d.filter { it.categoria == c.id }.sumOf { it.valor }) }
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repo(AppDatabase.get(app))
    private val dao = repo.dao
    private val ym = MutableStateFlow(YearMonth.now())
    private val _msg = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val mensagens: SharedFlow<String> = _msg

    val state: StateFlow<AppState> = combine(
        dao.lancamentos(), dao.categorias(), dao.planos(), dao.pagamentos(), ym,
    ) { tx, cats, planos, pags, m ->
        AppState(tx, cats, planos, pags.map(FormaPagamento::nome), m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppState())

    init { viewModelScope.launch { repo.semearSeVazio() } }

    fun mudarMes(delta: Long) { ym.value = ym.value.plusMonths(delta) }
    fun aviso(s: String) { _msg.tryEmit(s) }

    fun salvar(l: Lancamento, meses: Int, parcelar: Boolean, editando: Boolean) = viewModelScope.launch {
        if (editando) { dao.salvar(l); aviso("Lançamento atualizado") }
        else {
            repo.criarSerie(l, meses, parcelar, state.value.meta(l).nome)
            aviso(if (meses > 1) "$meses lançamentos criados" else "Lançamento salvo")
        }
        if (l.tipo == Tipo.DESPESA) {
            val b = state.value.copy(tx = dao.todosLancamentos()).orcamentos(YearMonth.parse(l.data.take(7)))
                .firstOrNull { it.c.id == l.categoria }
            if (b != null && b.nivel == 2) aviso("⛔ ${b.c.nome}: orçamento estourado em ${brl(b.gasto - b.c.orcamento)}")
            else if (b != null && b.nivel == 1) aviso("⚠️ ${b.c.nome}: já usou ${pct(b.r)} do orçamento")
        }
    }
    fun excluir(id: String) = viewModelScope.launch { dao.excluirLancamento(id); aviso("Lançamento excluído") }
    fun salvarCategoria(c: Categoria) = viewModelScope.launch { dao.salvarCategoria(c) }
    fun excluirCategoria(c: Categoria) = viewModelScope.launch { repo.excluirCategoria(c) }
    fun novaCategoria(tipo: String) = viewModelScope.launch {
        dao.salvarCategoria(Categoria(com.meusgastos.app.data.novoId(), tipo, "Nova categoria", "🏷️", "#64748B", 0.0,
            (state.value.categorias.maxOfOrNull { it.ordem } ?: 0) + 1))
    }
    fun salvarPlano(p: Plano) = viewModelScope.launch { dao.salvarPlano(p); aviso("Plano salvo") }
    fun excluirPlano(p: Plano) = viewModelScope.launch { repo.excluirPlano(p) }
    fun addPagamento(nome: String) = viewModelScope.launch {
        if (nome.isNotBlank()) dao.inserirPagamento(FormaPagamento(nome.trim(), state.value.pagamentos.size))
    }
    fun delPagamento(nome: String) = viewModelScope.launch { dao.excluirPagamento(nome) }
}
