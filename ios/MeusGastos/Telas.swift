import Charts
import SwiftData
import SwiftUI

// MARK: - Início

struct InicioView: View {
    let d: Dados
    let editar: (Lancamento) -> Void
    let irPara: (Int) -> Void
    @Environment(Estado.self) private var estado

    var body: some View {
        let mes = d.tx.doMes(estado.ym)
        let t = totais(mes)
        let ant = totais(d.tx.doMes(somarMeses(estado.ym, -1)))
        let variacao = ant.d > 0 ? (t.d - ant.d) / ant.d : Double.nan
        let orc = d.orcamentos(estado.ym)
        let alertas = orc.filter { $0.nivel > 0 }.sorted { $0.r > $1.r }
        List {
            Section {
                VStack(spacing: 10) {
                    KpiGrande(rotulo: "Sobra do mês (receitas − despesas − aportes)", valor: brl(t.saldo))
                    HStack(spacing: 10) {
                        Kpi(rotulo: "Receitas", valor: brl(t.r), cor: .verde)
                        Kpi(rotulo: "Despesas", valor: brl(t.d), cor: .vermelho)
                    }
                    HStack(spacing: 10) {
                        Kpi(rotulo: "Aportes previdência", valor: brl(t.a), cor: .azul)
                        Kpi(rotulo: "Despesas vs mês anterior",
                            valor: variacao.isFinite ? (variacao > 0 ? "▲ " : "▼ ") + pct(abs(variacao)) : "—",
                            cor: variacao > 0 ? .vermelho : .verde)
                    }
                }
                .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
            }
            if !alertas.isEmpty {
                Section("Alertas de orçamento") {
                    ForEach(alertas) { b in
                        Text((b.nivel == 2 ? "⛔ " : "⚠️ ") + "\(b.c.nome): \(brl(b.gasto)) de \(brl(b.c.orcamento)) (\(pct(b.r)))"
                             + (b.nivel == 2 ? " — passou \(brl(b.gasto - b.c.orcamento))" : ""))
                            .font(.subheadline).foregroundStyle(b.nivel == 2 ? Color.vermelho : Color.ambar)
                    }
                }
            }
            Section("Orçamento por categoria") {
                if orc.isEmpty {
                    Text("Nenhum orçamento definido.").foregroundStyle(.secondary)
                    Button("Definir limites") { irPara(4) }
                } else {
                    ForEach(orc) { b in
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text("\(b.c.icone) \(b.c.nome)")
                                Spacer()
                                Text("\(brl(b.gasto)) / \(brl(b.c.orcamento))").font(.footnote).foregroundStyle(.secondary)
                            }
                            Barra(fracao: b.r, nivel: b.nivel)
                        }
                    }
                }
            }
            Section("Últimos lançamentos") {
                let rec = Array(mes.ordenados().prefix(6))
                if rec.isEmpty {
                    Text("Nada lançado em \(ymLabel(estado.ym)). Toque em + para começar.").foregroundStyle(.secondary)
                } else {
                    ForEach(rec) { l in LinhaLanc(d: d, l: l).onTapGesture { editar(l) } }
                    Button("Ver todos") { irPara(1) }
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}

// MARK: - Lançamentos

struct LancamentosView: View {
    let d: Dados
    let editar: (Lancamento) -> Void
    @Environment(Estado.self) private var estado
    @Environment(\.modelContext) private var ctx
    @State private var filtro = "todos"
    @State private var busca = ""

    var body: some View {
        var lista = d.tx.doMes(estado.ym)
        if filtro != "todos" { lista = lista.filter { $0.tipo == filtro } }
        let q = busca.trimmingCharacters(in: .whitespaces).lowercased()
        if !q.isEmpty {
            lista = lista.filter {
                $0.descricao.lowercased().contains(q) || d.meta($0).nome.lowercased().contains(q) || ($0.pagamento ?? "").lowercased().contains(q)
            }
        }
        lista = lista.ordenados()
        let t = totais(lista)
        let dias = Dictionary(grouping: lista, by: \.data).keys.sorted(by: >)
        return List {
            Section {
                Picker("Tipo", selection: $filtro) {
                    Text("Todos").tag("todos"); Text("Despesas").tag(Tipo.despesa)
                    Text("Receitas").tag(Tipo.receita); Text("Aportes").tag(Tipo.aporte)
                }
                .pickerStyle(.segmented)
                Text("\(lista.count) lançamento(s) · receitas \(brl(t.r)) · despesas \(brl(t.d)) · aportes \(brl(t.a))")
                    .font(.caption).foregroundStyle(.secondary)
            }
            if lista.isEmpty { Text("Nenhum lançamento encontrado.").foregroundStyle(.secondary) }
            ForEach(dias, id: \.self) { dia in
                Section(diaLabel(dia)) {
                    ForEach(lista.filter { $0.data == dia }) { l in
                        LinhaLanc(d: d, l: l).onTapGesture { editar(l) }
                            .swipeActions {
                                Button(role: .destructive) { ctx.delete(l); try? ctx.save(); estado.avisar("Lançamento excluído") } label: { Label("Excluir", systemImage: "trash") }
                            }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .searchable(text: $busca, prompt: "Buscar descrição, categoria, pagamento")
    }
}

// MARK: - Relatórios

private struct LinhaCat: Identifiable {
    let id: String; let nome: String; let icone: String; let cor: Color; let v: Double; let media: Double
}
private struct PontoMes: Identifiable {
    let id = UUID(); let mes: String; let serie: String; let v: Double
}

struct RelatoriosView: View {
    let d: Dados
    @Environment(Estado.self) private var estado

    var body: some View {
        let ym = estado.ym
        let desp = d.tx.doMes(ym).filter { $0.tipo == Tipo.despesa }
        let total = desp.soma()
        var linhas: [LinhaCat] = d.catDespesa.compactMap { c in
            let v = desp.filter { $0.categoria == c.id }.soma()
            let media = (1...3).map { k in d.tx.doMes(somarMeses(ym, -k)).filter { $0.tipo == Tipo.despesa && $0.categoria == c.id }.soma() }.reduce(0, +) / 3
            return (v > 0 || media > 0) ? LinhaCat(id: c.id, nome: c.nome, icone: c.icone, cor: Color(hex: c.cor), v: v, media: media) : nil
        }
        let ids = Set(d.catDespesa.map(\.id))
        let sem = desp.filter { !ids.contains($0.categoria ?? "") }.soma()
        if sem > 0 { linhas.append(LinhaCat(id: "sem", nome: "Sem categoria", icone: "❔", cor: .gray, v: sem, media: 0)) }
        linhas.sort { $0.v > $1.v }
        let comValor = linhas.filter { $0.v > 0 }
        let meses = (0..<12).map { somarMeses(ym, $0 - 11) }
        let pontos: [PontoMes] = meses.flatMap { m -> [PontoMes] in
            let t = totais(d.tx.doMes(m))
            return [PontoMes(mes: ymShort(m), serie: "Receitas", v: t.r), PontoMes(mes: ymShort(m), serie: "Despesas", v: t.d),
                    PontoMes(mes: ymShort(m), serie: "Aportes", v: t.a)]
        }
        let ano = String(ym.prefix(4))
        let ty = totais(d.tx.doAno(ano))
        let trib = d.tx.doAno(ano).filter { $0.tipo == Tipo.receita && $0.tributavel }.soma()
        var porPag: [String: Double] = [:]
        for l in desp { porPag[(l.pagamento ?? "").isEmpty ? "Não informado" : l.pagamento!, default: 0] += l.valor }

        return List {
            Section("Despesas por categoria · \(ymLabel(ym))") {
                if total <= 0 { Text("Sem despesas neste mês.").foregroundStyle(.secondary) }
                else {
                    HStack(alignment: .center, spacing: 14) {
                        Chart(comValor) { x in
                            SectorMark(angle: .value("Valor", x.v), innerRadius: .ratio(0.6), angularInset: 1).foregroundStyle(x.cor)
                        }
                        .frame(width: 140, height: 140)
                        VStack(alignment: .leading, spacing: 3) {
                            ForEach(comValor.prefix(8)) { x in
                                HStack(spacing: 6) {
                                    Circle().fill(x.cor).frame(width: 9, height: 9)
                                    Text("\(x.nome) \(pct(x.v / total))").font(.caption).lineLimit(1)
                                }
                            }
                        }
                    }
                    .padding(.vertical, 6)
                }
            }
            if !linhas.isEmpty {
                Section {
                    Grid(alignment: .leading, horizontalSpacing: 8, verticalSpacing: 6) {
                        GridRow { Text("Categoria"); Text("Mês"); Text("%"); Text("Média 3m") }.font(.caption).foregroundStyle(.secondary)
                        ForEach(linhas) { x in
                            GridRow {
                                Text("\(x.icone) \(x.nome)").lineLimit(1)
                                Text(brl(x.v))
                                Text(pct(total > 0 ? x.v / total : .nan))
                                Text(brl(x.media)).foregroundStyle(x.media > 0 && x.v > x.media * 1.15 ? Color.vermelho : Color.primary)
                            }
                            .font(.footnote)
                        }
                    }
                } header: { Text("Detalhe por categoria") } footer: { Text("Em vermelho: mais de 15% acima da média dos 3 meses anteriores.") }
            }
            Section("Últimos 12 meses") {
                Chart(pontos) { p in
                    BarMark(x: .value("Mês", p.mes), y: .value("Valor", p.v))
                        .foregroundStyle(by: .value("Tipo", p.serie))
                        .position(by: .value("Tipo", p.serie))
                }
                .chartForegroundStyleScale(["Receitas": Color.verde, "Despesas": Color.vermelho, "Aportes": Color.azul])
                .chartXAxis { AxisMarks { _ in AxisValueLabel().font(.system(size: 8)) } }
                .frame(height: 220)
                .padding(.vertical, 6)
            }
            if !porPag.isEmpty {
                Section("Por forma de pagamento") {
                    ForEach(porPag.sorted { $0.value > $1.value }, id: \.key) { item in
                        LinhaValor(rotulo: "\(item.key) (\(pct(item.value / total)))", valor: brl(item.value))
                    }
                }
            }
            Section("Resumo de \(ano)") {
                LinhaValor(rotulo: "Receitas", valor: brl(ty.r), cor: .verde)
                LinhaValor(rotulo: "   das quais tributáveis", valor: brl(trib))
                LinhaValor(rotulo: "Despesas", valor: brl(ty.d), cor: .vermelho)
                LinhaValor(rotulo: "Aportes em previdência", valor: brl(ty.a), cor: .azul)
                LinhaValor(rotulo: "Sobra no ano", valor: brl(ty.saldo), negrito: true)
                LinhaValor(rotulo: "Taxa de poupança", valor: pct(ty.r > 0 ? (ty.saldo + ty.a) / ty.r : .nan))
            }
        }
        .listStyle(.insetGrouped)
    }
}

// MARK: - Previdência

struct PrevidenciaView: View {
    let d: Dados
    let editar: (Lancamento) -> Void
    let abrirPlano: (Plano?) -> Void
    @Environment(Estado.self) private var estado

    var body: some View {
        let ano = String(estado.ym.prefix(4))
        let ap = d.tx.filter { $0.tipo == Tipo.aporte }
        let apAno = ap.doAno(ano)
        let pgbl = Set(d.planos.filter { $0.tipo == "PGBL" }.map(\.id))
        let pgblAno = apAno.filter { pgbl.contains($0.planoId ?? "") }.soma()
        let trib = d.tx.doAno(ano).filter { $0.tipo == Tipo.receita && $0.tributavel }.soma()
        let limite = trib * 0.12
        return List {
            Section {
                VStack(spacing: 10) {
                    KpiGrande(rotulo: "Saldo total informado", valor: brl(d.planos.reduce(0) { $0 + $1.saldo }), fundo: .azul)
                    HStack(spacing: 10) {
                        Kpi(rotulo: "Aportado em \(ano)", valor: brl(apAno.soma()), cor: .azul)
                        Kpi(rotulo: "Aportado (total)", valor: brl(ap.soma()))
                    }
                }
                .listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
            }
            Section("PGBL × limite de dedução \(ano)") {
                if trib <= 0 {
                    Text("Marque recebimentos como tributáveis para ver quanto ainda cabe de PGBL na dedução do IR (12% da renda tributável).")
                        .font(.subheadline).foregroundStyle(.secondary)
                } else {
                    VStack(alignment: .leading, spacing: 6) {
                        LinhaValor(rotulo: "Aportes PGBL", valor: brl(pgblAno), negrito: true)
                        LinhaValor(rotulo: "Limite: 12% de \(brl(trib)) tributáveis", valor: brl(limite))
                        Barra(fracao: limite > 0 ? pgblAno / limite : 0, nivel: pgblAno > limite ? 2 : 0, cor: pgblAno > limite ? nil : .azul)
                        Text((pgblAno >= limite ? "Limite atingido: aportes PGBL acima disso não reduzem o IR deste ano."
                              : "Ainda cabem \(brl(limite - pgblAno)) em PGBL para dedução neste ano.")
                             + " Estimativa com base só nas receitas marcadas como tributáveis. Confirme com seu contador.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            Section("Planos") {
                if d.planos.isEmpty { Text("Cadastre seus planos (PGBL/VGBL) para acompanhar aportes e saldo.").foregroundStyle(.secondary) }
                ForEach(d.planos) { p in
                    let a = ap.filter { $0.planoId == p.id }
                    let tot = a.soma()
                    Button { abrirPlano(p) } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(p.nome) · \(p.tipo)").fontWeight(.semibold)
                                Text([p.instituicao, p.saldoData.isEmpty ? "" : "saldo em \(dataBR(p.saldoData))"].filter { !$0.isEmpty }.joined(separator: " · "))
                                    .font(.caption).foregroundStyle(.secondary)
                                Text("Aportado \(brl(tot)) (\(brl(a.doAno(ano).soma())) em \(ano)) · Rendimento \(brl(p.saldo - tot))")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text(brl(p.saldo)).fontWeight(.bold)
                        }
                    }
                    .tint(.primary)
                }
                Button("+ Novo plano") { abrirPlano(nil) }
            }
            Section("Aportes recentes") {
                let rec = Array(ap.ordenados().prefix(10))
                if rec.isEmpty { Text("Nenhum aporte lançado. Use + → Aporte.").foregroundStyle(.secondary) }
                ForEach(rec) { l in LinhaLanc(d: d, l: l).onTapGesture { editar(l) } }
            }
        }
        .listStyle(.insetGrouped)
    }
}
