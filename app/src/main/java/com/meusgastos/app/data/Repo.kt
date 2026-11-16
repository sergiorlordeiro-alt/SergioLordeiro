package com.meusgastos.app.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

fun novoId(): String = UUID.randomUUID().toString()

class Repo(private val db: AppDatabase) {
    val dao = db.dao()

    suspend fun semearSeVazio() {
        if (dao.contarCategorias() > 0) return
        var o = 0
        fun c(tipo: String, nome: String, icone: String, cor: String) =
            Categoria(novoId(), tipo, nome, icone, cor, 0.0, o++)
        dao.salvarCategorias(
            listOf(
                c(Tipo.DESPESA, "Moradia", "🏠", "#0F766E"), c(Tipo.DESPESA, "Mercado", "🛒", "#16A34A"),
                c(Tipo.DESPESA, "Alimentação fora", "🍽️", "#EA580C"), c(Tipo.DESPESA, "Transporte", "🚗", "#2563EB"),
                c(Tipo.DESPESA, "Contas da casa", "💡", "#CA8A04"), c(Tipo.DESPESA, "Saúde", "🩺", "#DC2626"),
                c(Tipo.DESPESA, "Educação", "📚", "#7C3AED"), c(Tipo.DESPESA, "Lazer", "🎬", "#DB2777"),
                c(Tipo.DESPESA, "Vestuário", "👕", "#0891B2"), c(Tipo.DESPESA, "Pets", "🐾", "#A16207"),
                c(Tipo.DESPESA, "Impostos e taxas", "🧾", "#475569"), c(Tipo.DESPESA, "Outros", "📦", "#6B7280"),
                c(Tipo.RECEITA, "Salário", "💼", "#15803D"), c(Tipo.RECEITA, "13º salário", "🎁", "#0D9488"),
                c(Tipo.RECEITA, "Férias", "🏖️", "#0284C7"), c(Tipo.RECEITA, "PLR / Bônus", "⭐", "#CA8A04"),
                c(Tipo.RECEITA, "Rendimentos", "📈", "#7C3AED"), c(Tipo.RECEITA, "Aluguel recebido", "🏘️", "#A16207"),
                c(Tipo.RECEITA, "Reembolso", "↩️", "#64748B"), c(Tipo.RECEITA, "Outros", "💰", "#6B7280"),
            )
        )
        listOf("Pix", "Débito", "Crédito", "Dinheiro", "Boleto").forEachIndexed { i, n ->
            dao.inserirPagamento(FormaPagamento(n, i))
        }
    }

    /** Cria 1 ou mais lançamentos mensais. Com [parcelar], divide o valor entre as parcelas. */
    suspend fun criarSerie(base: Lancamento, meses: Int, parcelar: Boolean, nomePadrao: String) {
        val n = meses.coerceIn(1, 60)
        val grupo = if (n > 1) novoId() else null
        val agora = Instant.now().toString()
        val centavos = (base.valor * 100).roundToLong()
        val inicio = LocalDate.parse(base.data)
        val lista = (0 until n).map { k ->
            val valor = if (parcelar && n > 1) {
                val p = centavos / n
                (if (k == n - 1) centavos - p * (n - 1) else p) / 100.0
            } else base.valor
            val desc = if (n > 1) "${base.descricao.ifBlank { nomePadrao }} (${k + 1}/$n)" else base.descricao
            base.copy(id = novoId(), valor = valor, data = inicio.plusMonths(k.toLong()).toString(),
                descricao = desc, criadoEm = agora, grupo = grupo)
        }
        dao.salvarTodos(lista)
    }

    suspend fun excluirCategoria(c: Categoria) = db.withTransaction {
        dao.limparCategoria(c.id); dao.excluirCategoria(c.id)
    }

    suspend fun excluirPlano(p: Plano) = db.withTransaction {
        dao.limparPlano(p.id); dao.excluirPlano(p.id)
    }

    // ---------- Backup (mesmo formato do app web) ----------

    suspend fun exportarJson(): String {
        val cats = dao.todasCategorias()
        fun catJson(c: Categoria) = JSONObject().put("id", c.id).put("nome", c.nome).put("icone", c.icone)
            .put("cor", c.cor).put("orcamento", c.orcamento)
        val cfg = JSONObject()
            .put("versao", 1)
            .put("catDespesa", JSONArray(cats.filter { it.tipo == Tipo.DESPESA }.map(::catJson)))
            .put("catReceita", JSONArray(cats.filter { it.tipo == Tipo.RECEITA }.map(::catJson)))
            .put("pagamentos", JSONArray(dao.todosPagamentos().map { it.nome }))
            .put("planos", JSONArray(dao.todosPlanos().map {
                JSONObject().put("id", it.id).put("nome", it.nome).put("tipo", it.tipo)
                    .put("instituicao", it.instituicao).put("saldo", it.saldo).put("saldoData", it.saldoData)
            }))
        val tx = JSONArray(dao.todosLancamentos().map { l ->
            JSONObject().put("id", l.id).put("tipo", l.tipo).put("valor", l.valor).put("data", l.data)
                .put("descricao", l.descricao).put("tributavel", l.tributavel).put("criadoEm", l.criadoEm)
                .apply {
                    l.categoria?.let { put("categoria", it) }; l.planoId?.let { put("planoId", it) }
                    l.pagamento?.let { put("pagamento", it) }; l.grupo?.let { put("grupo", it) }
                }
        })
        return JSONObject().put("app", "meus-gastos").put("versao", 1)
            .put("exportadoEm", Instant.now().toString()).put("cfg", cfg).put("tx", tx).toString(1)
    }

    /** Restaura um backup. Substitui categorias, planos e formas de pagamento; mescla os lançamentos. */
    suspend fun importarJson(texto: String): Int {
        val root = JSONObject(texto)
        val txArr = root.optJSONArray("tx") ?: throw IllegalArgumentException("Arquivo não é um backup do app")
        val cfg = root.optJSONObject("cfg")
        var n = 0
        db.withTransaction {
            if (cfg != null && cfg.has("catDespesa")) {
                dao.limparCategorias()
                var o = 0
                for ((tipo, chave) in listOf(Tipo.DESPESA to "catDespesa", Tipo.RECEITA to "catReceita")) {
                    val arr = cfg.optJSONArray(chave) ?: continue
                    for (i in 0 until arr.length()) {
                        val c = arr.getJSONObject(i)
                        dao.salvarCategoria(Categoria(c.optString("id", novoId()), tipo, c.optString("nome"),
                            c.optString("icone", "🏷️"), c.optString("cor", "#64748B"), c.optDouble("orcamento", 0.0), o++))
                    }
                }
                cfg.optJSONArray("pagamentos")?.let { arr ->
                    dao.limparPagamentos()
                    for (i in 0 until arr.length()) dao.inserirPagamento(FormaPagamento(arr.getString(i), i))
                }
                cfg.optJSONArray("planos")?.let { arr ->
                    dao.limparPlanos()
                    for (i in 0 until arr.length()) {
                        val p = arr.getJSONObject(i)
                        dao.salvarPlano(Plano(p.optString("id", novoId()), p.optString("nome"), p.optString("tipo", "PGBL"),
                            p.optString("instituicao"), p.optDouble("saldo", 0.0), p.optString("saldoData")))
                    }
                }
            }
            val lista = mutableListOf<Lancamento>()
            for (i in 0 until txArr.length()) {
                val t = txArr.getJSONObject(i)
                val valor = t.optDouble("valor", 0.0)
                val data = t.optString("data").take(10)
                val tipo = t.optString("tipo")
                if (valor <= 0 || data.length != 10 || tipo !in listOf(Tipo.DESPESA, Tipo.RECEITA, Tipo.APORTE)) continue
                fun opt(k: String) = if (t.has(k) && !t.isNull(k)) t.optString(k).ifBlank { null } else null
                lista += Lancamento(
                    id = opt("id") ?: novoId(), tipo = tipo, valor = valor, data = data,
                    categoria = opt("categoria"), planoId = opt("planoId"), descricao = t.optString("descricao"),
                    pagamento = opt("pagamento"), tributavel = t.optBoolean("tributavel"),
                    criadoEm = t.optString("criadoEm"), grupo = opt("grupo"),
                )
            }
            dao.salvarTodos(lista); n = lista.size
        }
        return n
    }

    /** CSV com ponto e vírgula e vírgula decimal, para abrir direto no Excel em português. */
    suspend fun exportarCsv(): String {
        val cats = dao.todasCategorias().associateBy { it.id }
        val planos = dao.todosPlanos().associateBy { it.id }
        val br = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        fun q(s: String?): String {
            val v = s ?: ""
            return if (v.contains(';') || v.contains('"') || v.contains('\n')) "\"" + v.replace("\"", "\"\"") + "\"" else v
        }
        val sb = StringBuilder("﻿Data;Tipo;Categoria/Plano;Descrição;Forma de pagamento;Tributável;Valor\r\n")
        for (l in dao.todosLancamentos()) {
            val tipo = when (l.tipo) { Tipo.DESPESA -> "Despesa"; Tipo.RECEITA -> "Recebimento"; else -> "Aporte previdência" }
            val nome = if (l.tipo == Tipo.APORTE) planos[l.planoId]?.nome ?: "Previdência" else cats[l.categoria]?.nome ?: "Sem categoria"
            val trib = if (l.tipo == Tipo.RECEITA) (if (l.tributavel) "Sim" else "Não") else ""
            sb.append(listOf(LocalDate.parse(l.data).format(br), tipo, q(nome), q(l.descricao), q(l.pagamento), trib,
                String.format(Locale.US, "%.2f", l.valor).replace('.', ',')).joinToString(";")).append("\r\n")
        }
        return sb.toString()
    }
}
