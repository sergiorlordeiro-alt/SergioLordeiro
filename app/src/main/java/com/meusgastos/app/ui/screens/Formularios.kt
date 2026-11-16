@file:OptIn(ExperimentalMaterial3Api::class)

package com.meusgastos.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meusgastos.app.data.Lancamento
import com.meusgastos.app.data.Plano
import com.meusgastos.app.data.Tipo
import com.meusgastos.app.data.novoId
import com.meusgastos.app.ui.AppState
import com.meusgastos.app.ui.MainViewModel
import com.meusgastos.app.ui.brl
import com.meusgastos.app.ui.dataBR
import com.meusgastos.app.ui.fmtInput
import com.meusgastos.app.ui.parseMoney
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

@Composable
fun FormLancamento(s: AppState, editar: Lancamento?, tipoInicial: String, vm: MainViewModel, onClose: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ctx = LocalContext.current
    var tipo by remember { mutableStateOf(editar?.tipo ?: tipoInicial) }
    var valor by remember { mutableStateOf(editar?.let { fmtInput(it.valor) } ?: "") }
    var data by remember {
        mutableStateOf(editar?.data ?: if (s.ym == YearMonth.now()) LocalDate.now().toString() else s.ym.atDay(1).toString())
    }
    var categoria by remember { mutableStateOf(editar?.categoria) }
    var plano by remember { mutableStateOf(editar?.planoId ?: s.planos.firstOrNull()?.id) }
    var descricao by remember { mutableStateOf(editar?.descricao ?: "") }
    var pagamento by remember { mutableStateOf(editar?.pagamento ?: s.pagamentos.firstOrNull() ?: "") }
    var tributavel by remember { mutableStateOf(editar?.tributavel ?: false) }
    var meses by remember { mutableStateOf("1") }
    var parcelar by remember { mutableStateOf(false) }
    var confirmarExclusao by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    val cats = s.cats(tipo)
    val catEfetiva = categoria?.takeIf { id -> cats.any { it.id == id } } ?: cats.firstOrNull()?.id

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(if (editar != null) "Editar lançamento" else "Novo lançamento", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                listOf(Tipo.DESPESA to "Despesa", Tipo.RECEITA to "Recebimento", Tipo.APORTE to "Aporte").forEach { (k, l) ->
                    FilterChip(selected = tipo == k, onClick = { tipo = k }, label = { Text(l) })
                }
            }
            OutlinedTextField(valor, { valor = it }, Modifier.fillMaxWidth(), label = { Text("Valor (R$)") }, singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = {
                    val d = LocalDate.parse(data)
                    DatePickerDialog(ctx, { _, y, m, dd -> data = LocalDate.of(y, m + 1, dd).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                }, Modifier.weight(1f)) { Text("📅 " + dataBR(data)) }
                Box(Modifier.weight(1.3f)) {
                    if (tipo == Tipo.APORTE) {
                        Seletor(s.planos.firstOrNull { it.id == plano }?.let { "${it.nome} (${it.tipo})" } ?: "Cadastre um plano",
                            s.planos.map { it.id to "${it.nome} (${it.tipo})" }) { plano = it }
                    } else {
                        Seletor(cats.firstOrNull { it.id == catEfetiva }?.let { "${it.icone} ${it.nome}" } ?: "Categoria",
                            cats.map { it.id to "${it.icone} ${it.nome}" }) { categoria = it }
                    }
                }
            }
            OutlinedTextField(descricao, { descricao = it.take(120) }, Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Descrição") }, singleLine = true,
                placeholder = { Text(when (tipo) { Tipo.RECEITA -> "Ex.: salário"; Tipo.APORTE -> "Ex.: aporte mensal"; else -> "Ex.: supermercado" }) })
            if (tipo == Tipo.DESPESA) {
                Text("Forma de pagamento", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                Seletor(pagamento.ifBlank { "Não informar" }, s.pagamentos.map { it to it } + ("" to "Não informar")) { pagamento = it }
            }
            if (tipo == Tipo.RECEITA) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(tributavel, { tributavel = it }); Text("Renda tributável (entra no limite do PGBL)", fontSize = 14.sp)
            }
            if (editar == null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(meses, { meses = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), singleLine = true,
                        label = { Text("Repetir por (meses)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Checkbox(parcelar, { parcelar = it }); Text("Dividir em parcelas", fontSize = 14.sp)
                }
                val n = meses.toIntOrNull() ?: 1
                val v = parseMoney(valor)
                if (n > 1) Text(if (parcelar) "$n parcelas de ${brl((v * 100 / n).roundToLong() / 100.0)}" else "$n lançamentos de ${brl(v)}, um por mês",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            erro?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
                if (editar != null) OutlinedButton(onClick = { confirmarExclusao = true }, Modifier.weight(1f)) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                } else OutlinedButton(onClick = onClose, Modifier.weight(1f)) { Text("Cancelar") }
                Button(onClick = {
                    val v = parseMoney(valor)
                    when {
                        v <= 0 -> erro = "Informe um valor maior que zero"
                        tipo == Tipo.APORTE && plano == null -> erro = "Cadastre um plano em Previdência primeiro"
                        else -> {
                            val l = Lancamento(
                                id = editar?.id ?: novoId(), tipo = tipo, valor = v, data = data,
                                categoria = if (tipo == Tipo.APORTE) null else catEfetiva,
                                planoId = if (tipo == Tipo.APORTE) plano else null,
                                descricao = descricao.trim(),
                                pagamento = if (tipo == Tipo.DESPESA) pagamento else null,
                                tributavel = tipo == Tipo.RECEITA && tributavel,
                                criadoEm = editar?.criadoEm ?: Instant.now().toString(),
                                grupo = editar?.grupo,
                            )
                            vm.salvar(l, (meses.toIntOrNull() ?: 1).coerceIn(1, 60), parcelar, editar != null)
                            onClose()
                        }
                    }
                }, Modifier.weight(1f)) { Text("Salvar") }
            }
        }
    }
    if (confirmarExclusao && editar != null) AlertDialog(onDismissRequest = { confirmarExclusao = false },
        title = { Text("Excluir este lançamento?") },
        confirmButton = { TextButton(onClick = { vm.excluir(editar.id); confirmarExclusao = false; onClose() }) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = { confirmarExclusao = false }) { Text("Cancelar") } })
}

@Composable
fun Seletor(atual: String, opcoes: List<Pair<String, String>>, onEscolher: (String) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { aberto = true }, Modifier.fillMaxWidth()) { Text("$atual ▾", maxLines = 1) }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            opcoes.forEach { (id, rotulo) -> DropdownMenuItem(text = { Text(rotulo) }, onClick = { onEscolher(id); aberto = false }) }
        }
    }
}

@Composable
fun FormPlano(s: AppState, editar: Plano?, vm: MainViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var nome by remember { mutableStateOf(editar?.nome ?: "") }
    var tipo by remember { mutableStateOf(editar?.tipo ?: "PGBL") }
    var inst by remember { mutableStateOf(editar?.instituicao ?: "") }
    var saldo by remember { mutableStateOf(editar?.let { fmtInput(it.saldo) } ?: "") }
    var saldoData by remember { mutableStateOf(editar?.saldoData?.ifBlank { null } ?: LocalDate.now().toString()) }
    var confirmar by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(if (editar != null) "Plano de previdência" else "Novo plano", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(nome, { nome = it.take(60) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Nome do plano") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                listOf("PGBL", "VGBL", "Fundo de pensão", "Outro").forEach { t -> FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t, fontSize = 12.sp) }) }
            }
            OutlinedTextField(inst, { inst = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Instituição") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(saldo, { saldo = it }, Modifier.weight(1f), label = { Text("Saldo atual (R$)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedButton(onClick = {
                    val d = LocalDate.parse(saldoData)
                    DatePickerDialog(ctx, { _, y, m, dd -> saldoData = LocalDate.of(y, m + 1, dd).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                }) { Text("em " + dataBR(saldoData)) }
            }
            Text("Atualize o saldo pelo extrato; o app calcula o rendimento como saldo − aportes lançados.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
                if (editar != null) OutlinedButton(onClick = { confirmar = true }, Modifier.weight(1f)) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                else OutlinedButton(onClick = onClose, Modifier.weight(1f)) { Text("Cancelar") }
                Button(onClick = {
                    if (nome.isNotBlank()) {
                        vm.salvarPlano(Plano(editar?.id ?: novoId(), nome.trim(), tipo, inst.trim(), parseMoney(saldo), saldoData)); onClose()
                    }
                }, Modifier.weight(1f)) { Text("Salvar") }
            }
        }
    }
    if (confirmar && editar != null) {
        val usados = s.tx.count { it.planoId == editar.id }
        AlertDialog(onDismissRequest = { confirmar = false },
            title = { Text("Excluir este plano?") },
            text = { if (usados > 0) Text("Ele tem $usados aporte(s). Os aportes continuam lançados, sem plano associado.") },
            confirmButton = { TextButton(onClick = { vm.excluirPlano(editar); confirmar = false; onClose() }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } })
    }
}
