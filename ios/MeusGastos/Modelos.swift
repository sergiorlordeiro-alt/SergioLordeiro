import Foundation
import SwiftData
import SwiftUI

enum Tipo {
    static let despesa = "despesa"
    static let receita = "receita"
    static let aporte = "aporte"
}

/// Um lançamento: despesa, recebimento ou aporte em previdência. `data` no formato yyyy-MM-dd.
@Model
final class Lancamento {
    @Attribute(.unique) var id: String
    var tipo: String
    var valor: Double
    var data: String
    var categoria: String?
    var planoId: String?
    var descricao: String
    var pagamento: String?
    var tributavel: Bool
    var criadoEm: String
    var grupo: String?

    init(id: String = UUID().uuidString, tipo: String, valor: Double, data: String, categoria: String? = nil,
         planoId: String? = nil, descricao: String = "", pagamento: String? = nil, tributavel: Bool = false,
         criadoEm: String = agoraISO(), grupo: String? = nil) {
        self.id = id; self.tipo = tipo; self.valor = valor; self.data = data; self.categoria = categoria
        self.planoId = planoId; self.descricao = descricao; self.pagamento = pagamento; self.tributavel = tributavel
        self.criadoEm = criadoEm; self.grupo = grupo
    }
}

/// Categoria de despesa (com orçamento mensal opcional) ou de recebimento.
@Model
final class Categoria {
    @Attribute(.unique) var id: String
    var tipo: String
    var nome: String
    var icone: String
    var cor: String
    var orcamento: Double
    var ordem: Int

    init(id: String = UUID().uuidString, tipo: String, nome: String, icone: String, cor: String, orcamento: Double = 0, ordem: Int = 0) {
        self.id = id; self.tipo = tipo; self.nome = nome; self.icone = icone; self.cor = cor
        self.orcamento = orcamento; self.ordem = ordem
    }
}

/// Plano de previdência privada (PGBL, VGBL, fundo de pensão).
@Model
final class Plano {
    @Attribute(.unique) var id: String
    var nome: String
    var tipo: String
    var instituicao: String
    var saldo: Double
    var saldoData: String

    init(id: String = UUID().uuidString, nome: String, tipo: String, instituicao: String = "", saldo: Double = 0, saldoData: String = "") {
        self.id = id; self.nome = nome; self.tipo = tipo; self.instituicao = instituicao
        self.saldo = saldo; self.saldoData = saldoData
    }
}

@Model
final class FormaPagamento {
    @Attribute(.unique) var nome: String
    var ordem: Int
    init(nome: String, ordem: Int = 0) { self.nome = nome; self.ordem = ordem }
}

// MARK: - Dados iniciais

enum Semente {
    static func semearSeVazio(_ ctx: ModelContext) {
        let n = (try? ctx.fetchCount(FetchDescriptor<Categoria>())) ?? 0
        guard n == 0 else { return }
        let desp: [(String, String, String)] = [
            ("Moradia", "🏠", "#0F766E"), ("Mercado", "🛒", "#16A34A"), ("Alimentação fora", "🍽️", "#EA580C"),
            ("Transporte", "🚗", "#2563EB"), ("Contas da casa", "💡", "#CA8A04"), ("Saúde", "🩺", "#DC2626"),
            ("Educação", "📚", "#7C3AED"), ("Lazer", "🎬", "#DB2777"), ("Vestuário", "👕", "#0891B2"),
            ("Pets", "🐾", "#A16207"), ("Impostos e taxas", "🧾", "#475569"), ("Outros", "📦", "#6B7280"),
        ]
        let rec: [(String, String, String)] = [
            ("Salário", "💼", "#15803D"), ("13º salário", "🎁", "#0D9488"), ("Férias", "🏖️", "#0284C7"),
            ("PLR / Bônus", "⭐", "#CA8A04"), ("Rendimentos", "📈", "#7C3AED"), ("Aluguel recebido", "🏘️", "#A16207"),
            ("Reembolso", "↩️", "#64748B"), ("Outros", "💰", "#6B7280"),
        ]
        var o = 0
        for (nome, ic, cor) in desp { ctx.insert(Categoria(tipo: Tipo.despesa, nome: nome, icone: ic, cor: cor, ordem: o)); o += 1 }
        for (nome, ic, cor) in rec { ctx.insert(Categoria(tipo: Tipo.receita, nome: nome, icone: ic, cor: cor, ordem: o)); o += 1 }
        for (i, p) in ["Pix", "Débito", "Crédito", "Dinheiro", "Boleto"].enumerated() { ctx.insert(FormaPagamento(nome: p, ordem: i)) }
        try? ctx.save()
    }
}
