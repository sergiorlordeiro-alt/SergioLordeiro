import SwiftData
import SwiftUI

@main
struct MeusGastosApp: App {
    var body: some Scene {
        WindowGroup { RaizView() }
            .modelContainer(for: [Lancamento.self, Categoria.self, Plano.self, FormaPagamento.self])
    }
}

/// O que está aberto por cima das abas.
enum Folha: Identifiable {
    case novo(String)
    case editar(Lancamento)
    case plano(Plano?)
    var id: String {
        switch self {
        case .novo(let t): return "novo-" + t
        case .editar(let l): return "ed-" + l.id
        case .plano(let p): return "pl-" + (p?.id ?? "novo")
        }
    }
}

struct RaizView: View {
    @Environment(\.modelContext) private var ctx
    @Query(sort: \Lancamento.data, order: .reverse) private var tx: [Lancamento]
    @Query(sort: \Categoria.ordem) private var categorias: [Categoria]
    @Query(sort: \Plano.nome) private var planos: [Plano]
    @Query(sort: \FormaPagamento.ordem) private var pagamentos: [FormaPagamento]
    @State private var estado = Estado()
    @State private var folha: Folha?
    @State private var aba = 0

    var dados: Dados { Dados(tx: tx, categorias: categorias, planos: planos, pagamentos: pagamentos.map(\.nome)) }

    var body: some View {
        let d = dados
        TabView(selection: $aba) {
            Tela("Início", aba: 0) { InicioView(d: d, editar: editar, irPara: { aba = $0 }) }
                .tabItem { Label("Início", systemImage: "house") }.tag(0)
            Tela("Lançamentos", aba: 1) { LancamentosView(d: d, editar: editar) }
                .tabItem { Label("Lançamentos", systemImage: "list.bullet.rectangle") }.tag(1)
            Tela("Relatórios", aba: 2) { RelatoriosView(d: d) }
                .tabItem { Label("Relatórios", systemImage: "chart.pie") }.tag(2)
            Tela("Previdência", aba: 3) { PrevidenciaView(d: d, editar: editar, abrirPlano: { folha = .plano($0) }) }
                .tabItem { Label("Previdência", systemImage: "shield") }.tag(3)
            Tela("Ajustes", aba: 4, comMes: false) { AjustesView(d: d) }
                .tabItem { Label("Ajustes", systemImage: "gearshape") }.tag(4)
        }
        .tint(.marca)
        .environment(estado)
        .environment(\.novoLancamento, { folha = .novo(aba == 3 ? Tipo.aporte : Tipo.despesa) })
        .sheet(item: $folha) { f in
            Group {
                switch f {
                case .novo(let tipo): FormLancamento(d: d, editar: nil, tipoInicial: tipo)
                case .editar(let l): FormLancamento(d: d, editar: l, tipoInicial: l.tipo)
                case .plano(let p): FormPlano(d: d, editar: p)
                }
            }
            .environment(estado)
        }
        .overlay(alignment: .bottom) {
            if let msg = estado.aviso {
                Text(msg).font(.subheadline).padding(.horizontal, 16).padding(.vertical, 10)
                    .background(.black.opacity(0.85), in: Capsule()).foregroundStyle(.white)
                    .padding(.bottom, 70).transition(.opacity)
            }
        }
        .animation(.easeInOut, value: estado.aviso)
        .task { Semente.semearSeVazio(ctx) }
    }

    private func editar(_ l: Lancamento) { folha = .editar(l) }
}

// Ação "novo lançamento" disponível para as barras de todas as abas
private struct NovoLancamentoKey: EnvironmentKey { static let defaultValue: () -> Void = {} }
extension EnvironmentValues {
    var novoLancamento: () -> Void {
        get { self[NovoLancamentoKey.self] }
        set { self[NovoLancamentoKey.self] = newValue }
    }
}

/// Moldura de cada aba: título, seletor de mês e botão +.
struct Tela<Conteudo: View>: View {
    let titulo: String
    let aba: Int
    var comMes = true
    @ViewBuilder let conteudo: () -> Conteudo
    @Environment(Estado.self) private var estado
    @Environment(\.novoLancamento) private var novo

    init(_ titulo: String, aba: Int, comMes: Bool = true, @ViewBuilder conteudo: @escaping () -> Conteudo) {
        self.titulo = titulo; self.aba = aba; self.comMes = comMes; self.conteudo = conteudo
    }

    var body: some View {
        NavigationStack {
            conteudo()
                .navigationTitle(titulo)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    if comMes {
                        ToolbarItem(placement: .topBarLeading) {
                            HStack(spacing: 2) {
                                Button { estado.ym = somarMeses(estado.ym, -1) } label: { Image(systemName: "chevron.left") }
                                Text(ymLabel(estado.ym)).font(.subheadline.weight(.semibold)).frame(minWidth: 110)
                                Button { estado.ym = somarMeses(estado.ym, 1) } label: { Image(systemName: "chevron.right") }
                            }
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { novo() } label: { Image(systemName: "plus.circle.fill").font(.title2) }
                            .accessibilityLabel("Novo lançamento")
                    }
                }
        }
    }
}

// MARK: - Componentes

struct LinhaLanc: View {
    let d: Dados
    let l: Lancamento
    var body: some View {
        let m = d.meta(l)
        HStack(spacing: 10) {
            Text(m.icone).font(.title3).frame(width: 38, height: 38)
                .background(m.cor.opacity(0.15), in: RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 2) {
                Text(l.descricao.isEmpty ? m.nome : l.descricao).fontWeight(.semibold).lineLimit(1)
                Text([m.nome, l.pagamento ?? "", (l.tipo == Tipo.receita && l.tributavel) ? "tributável" : ""]
                    .filter { !$0.isEmpty }.joined(separator: " · "))
                    .font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
            Text((l.tipo == Tipo.receita ? "+ " : "− ") + brl(l.valor)).fontWeight(.bold).foregroundStyle(corTipo(l.tipo))
        }
        .contentShape(Rectangle())
    }
}

struct Kpi: View {
    let rotulo: String; let valor: String; var cor: Color = .primary
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(rotulo).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            Text(valor).font(.headline).foregroundStyle(cor).lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12).background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
    }
}

struct KpiGrande: View {
    let rotulo: String; let valor: String; var fundo: Color = .marca
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(rotulo).font(.footnote).foregroundStyle(.white.opacity(0.85))
            Text(valor).font(.system(size: 28, weight: .bold)).foregroundStyle(.white).minimumScaleFactor(0.6).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16).background(fundo, in: RoundedRectangle(cornerRadius: 16))
    }
}

struct Barra: View {
    let fracao: Double; var nivel = 0; var cor: Color? = nil
    var body: some View {
        let c = cor ?? (nivel == 2 ? Color.vermelho : nivel == 1 ? Color.ambar : Color.marca)
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Capsule().fill(Color(.systemGray5))
                Capsule().fill(c).frame(width: g.size.width * min(1, max(0, fracao)))
            }
        }
        .frame(height: 8)
    }
}

struct LinhaValor: View {
    let rotulo: String; let valor: String; var cor: Color = .primary; var negrito = false
    var body: some View {
        HStack {
            Text(rotulo).fontWeight(negrito ? .bold : .regular)
            Spacer()
            Text(valor).foregroundStyle(cor).fontWeight(negrito ? .bold : .semibold)
        }
        .font(.subheadline)
    }
}
