import Foundation
import SwiftUI

// MARK: - Formatação

let localeBR = Locale(identifier: "pt_BR")

private let moeda: NumberFormatter = {
    let f = NumberFormatter(); f.numberStyle = .currency; f.locale = localeBR; return f
}()
private let decimal: NumberFormatter = {
    let f = NumberFormatter(); f.numberStyle = .decimal; f.locale = localeBR
    f.minimumFractionDigits = 2; f.maximumFractionDigits = 2; return f
}()

func brl(_ v: Double) -> String { moeda.string(from: NSNumber(value: v)) ?? "R$ 0,00" }
func pct(_ v: Double) -> String {
    guard v.isFinite else { return "—" }
    return String(format: "%.1f%%", v * 100).replacingOccurrences(of: ".", with: ",")
}
func fmtInput(_ v: Double) -> String { v == 0 ? "" : (decimal.string(from: NSNumber(value: v)) ?? "") }
func parseMoney(_ s: String) -> Double {
    var t = s.trimmingCharacters(in: .whitespaces)
        .replacingOccurrences(of: "R$", with: "").replacingOccurrences(of: " ", with: "")
        .replacingOccurrences(of: "\u{00A0}", with: "")
    if t.isEmpty { return 0 }
    if t.contains(",") { t = t.replacingOccurrences(of: ".", with: "").replacingOccurrences(of: ",", with: ".") }
    guard let v = Double(t) else { return 0 }
    return (v * 100).rounded() / 100
}

// MARK: - Datas (guardadas como texto yyyy-MM-dd; mês como yyyy-MM)

private let isoDia: DateFormatter = {
    let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.locale = Locale(identifier: "en_US_POSIX")
    f.dateFormat = "yyyy-MM-dd"; return f
}()
private let br: DateFormatter = {
    let f = DateFormatter(); f.locale = localeBR; f.dateFormat = "dd/MM/yyyy"; return f
}()
private let diaLongo: DateFormatter = {
    let f = DateFormatter(); f.locale = localeBR; f.dateFormat = "EEEE, dd 'de' MMM"; return f
}()

func agoraISO() -> String { ISO8601DateFormatter().string(from: Date()) }
func diaISO(_ d: Date) -> String { isoDia.string(from: d) }
func dataDe(_ iso: String) -> Date { isoDia.date(from: String(iso.prefix(10))) ?? Date() }
func hojeISO() -> String { diaISO(Date()) }
func dataBR(_ iso: String) -> String { br.string(from: dataDe(iso)) }
func diaLabel(_ iso: String) -> String { diaLongo.string(from: dataDe(iso)).capitalized(with: localeBR) }

func ymAtual() -> String { String(hojeISO().prefix(7)) }
func somarMeses(_ ym: String, _ k: Int) -> String {
    let p = ym.split(separator: "-").compactMap { Int($0) }
    guard p.count == 2 else { return ym }
    let total = p[0] * 12 + (p[1] - 1) + k
    return String(format: "%04d-%02d", total / 12, total % 12 + 1)
}
func somarMesesDia(_ iso: String, _ k: Int) -> String {
    let d = dataDe(iso)
    let r = Calendar(identifier: .gregorian).date(byAdding: .month, value: k, to: d) ?? d // ajusta 31 → último dia
    return diaISO(r)
}
private let mesesLongos = ["janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"]
private let mesesCurtos = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"]
func ymLabel(_ ym: String) -> String {
    let p = ym.split(separator: "-"); guard p.count == 2, let m = Int(p[1]), (1...12).contains(m) else { return ym }
    return mesesLongos[m - 1].capitalized + " " + p[0]
}
func ymShort(_ ym: String) -> String {
    let p = ym.split(separator: "-"); guard p.count == 2, let m = Int(p[1]), (1...12).contains(m) else { return ym }
    return mesesCurtos[m - 1] + "/" + p[0].suffix(2)
}

// MARK: - Cores

extension Color {
    init(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespaces); if s.hasPrefix("#") { s.removeFirst() }
        let v = UInt64(s, radix: 16) ?? 0x6B7280
        self.init(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
    }
    static let verde = Color(hex: "#15803D")
    static let vermelho = Color(hex: "#C2410C")
    static let azul = Color(hex: "#1D4ED8")
    static let ambar = Color(hex: "#B45309")
    static let marca = Color(hex: "#0F766E")
}
let paleta = ["#0F766E", "#16A34A", "#EA580C", "#2563EB", "#CA8A04", "#DC2626", "#7C3AED", "#DB2777", "#0891B2", "#A16207", "#475569", "#6B7280"]

func corTipo(_ tipo: String) -> Color {
    switch tipo { case Tipo.receita: return .verde; case Tipo.aporte: return .azul; default: return .vermelho }
}

// MARK: - Cálculos

struct Totais {
    var r = 0.0, d = 0.0, a = 0.0
    var saldo: Double { r - d - a }
}
func totais(_ l: [Lancamento]) -> Totais {
    var t = Totais()
    for x in l {
        switch x.tipo { case Tipo.receita: t.r += x.valor; case Tipo.aporte: t.a += x.valor; default: t.d += x.valor }
    }
    return t
}
extension Array where Element == Lancamento {
    func doMes(_ ym: String) -> [Lancamento] { filter { $0.data.hasPrefix(ym) } }
    func doAno(_ ano: String) -> [Lancamento] { filter { $0.data.hasPrefix(ano + "-") } }
    func soma() -> Double { reduce(0) { $0 + $1.valor } }
    func ordenados() -> [Lancamento] { sorted { ($0.data, $0.criadoEm) > ($1.data, $1.criadoEm) } }
}

struct Meta { let nome: String; let icone: String; let cor: Color }

struct OrcamentoStatus: Identifiable {
    let c: Categoria; let gasto: Double
    var id: String { c.id }
    var r: Double { c.orcamento > 0 ? gasto / c.orcamento : 0 }
    /// 0 = ok, 1 = atenção (>= 80%), 2 = estourado
    var nivel: Int { r > 1 ? 2 : (r >= 0.8 ? 1 : 0) }
}

/// Tudo o que as telas precisam, lido do banco pela tela raiz.
struct Dados {
    var tx: [Lancamento] = []
    var categorias: [Categoria] = []
    var planos: [Plano] = []
    var pagamentos: [String] = []

    var catDespesa: [Categoria] { categorias.filter { $0.tipo == Tipo.despesa } }
    var catReceita: [Categoria] { categorias.filter { $0.tipo == Tipo.receita } }
    func cats(_ tipo: String) -> [Categoria] { tipo == Tipo.receita ? catReceita : catDespesa }

    func meta(_ l: Lancamento) -> Meta {
        if l.tipo == Tipo.aporte {
            return Meta(nome: planos.first { $0.id == l.planoId }?.nome ?? "Previdência", icone: "🛡️", cor: .azul)
        }
        if let c = categorias.first(where: { $0.id == l.categoria }) { return Meta(nome: c.nome, icone: c.icone, cor: Color(hex: c.cor)) }
        return Meta(nome: "Sem categoria", icone: "❔", cor: .gray)
    }

    func orcamentos(_ ym: String) -> [OrcamentoStatus] {
        let d = tx.doMes(ym).filter { $0.tipo == Tipo.despesa }
        return catDespesa.filter { $0.orcamento > 0 }.map { c in
            OrcamentoStatus(c: c, gasto: d.filter { $0.categoria == c.id }.soma())
        }
    }
}

/// Estado compartilhado entre as abas: mês selecionado e avisos rápidos.
@Observable
final class Estado {
    var ym: String = ymAtual()
    var aviso: String?
    func avisar(_ s: String) {
        aviso = s
        let atual = s
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { [weak self] in if self?.aviso == atual { self?.aviso = nil } }
    }
}
