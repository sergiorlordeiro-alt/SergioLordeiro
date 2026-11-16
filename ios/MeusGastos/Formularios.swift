import SwiftData
import SwiftUI

struct FormLancamento: View {
    let d: Dados
    let editar: Lancamento?
    @Environment(\.modelContext) private var ctx
    @Environment(\.dismiss) private var dismiss
    @Environment(Estado.self) private var estado

    @State private var tipo: String
    @State private var valor: String
    @State private var data: Date
    @State private var categoria: String?
    @State private var plano: String?
    @State private var descricao: String
    @State private var pagamento: String
    @State private var tributavel: Bool
    @State private var meses = 1
    @State private var parcelar = false
    @State private var erro: String?
    @State private var confirmarExclusao = false

    init(d: Dados, editar: Lancamento?, tipoInicial: String) {
        self.d = d; self.editar = editar
        _tipo = State(initialValue: editar?.tipo ?? tipoInicial)
        _valor = State(initialValue: editar.map { fmtInput($0.valor) } ?? "")
        _data = State(initialValue: dataDe(editar?.data ?? hojeISO()))
        _categoria = State(initialValue: editar?.categoria)
        _plano = State(initialValue: editar?.planoId ?? d.planos.first?.id)
        _descricao = State(initialValue: editar?.descricao ?? "")
        _pagamento = State(initialValue: editar?.pagamento ?? d.pagamentos.first ?? "")
        _tributavel = State(initialValue: editar?.tributavel ?? false)
    }

    private var cats: [Categoria] { d.cats(tipo) }
    private var catEfetiva: String? {
        if let c = categoria, cats.contains(where: { $0.id == c }) { return c }
        return cats.first?.id
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Tipo", selection: $tipo) {
                        Text("Despesa").tag(Tipo.despesa); Text("Recebimento").tag(Tipo.receita); Text("Aporte").tag(Tipo.aporte)
                    }
                    .pickerStyle(.segmented)
                    TextField("0,00", text: $valor)
                        .keyboardType(.decimalPad)
                        .font(.system(size: 30, weight: .bold)).multilineTextAlignment(.center)
                }
                Section {
                    DatePicker("Data", selection: $data, displayedComponents: .date).environment(\.locale, localeBR)
                    if tipo == Tipo.aporte {
                        if d.planos.isEmpty {
                            Text("Cadastre um plano na aba Previdência primeiro.").foregroundStyle(.secondary)
                        } else {
                            Picker("Plano", selection: Binding(get: { plano ?? d.planos.first?.id ?? "" }, set: { plano = $0 })) {
                                ForEach(d.planos) { p in Text("\(p.nome) (\(p.tipo))").tag(p.id) }
                            }
                        }
                    } else {
                        Picker("Categoria", selection: Binding(get: { catEfetiva ?? "" }, set: { categoria = $0 })) {
                            ForEach(cats) { c in Text("\(c.icone) \(c.nome)").tag(c.id) }
                        }
                    }
                    TextField(tipo == Tipo.receita ? "Descrição (ex.: salário)" : tipo == Tipo.aporte ? "Descrição (ex.: aporte mensal)" : "Descrição (ex.: supermercado)",
                              text: $descricao)
                    if tipo == Tipo.despesa {
                        Picker("Pagamento", selection: $pagamento) {
                            ForEach(d.pagamentos, id: \.self) { Text($0).tag($0) }
                            Text("Não informar").tag("")
                        }
                    }
                    if tipo == Tipo.receita {
                        Toggle("Renda tributável (entra no limite do PGBL)", isOn: $tributavel)
                    }
                }
                if editar == nil {
                    Section {
                        Stepper("Repetir por \(meses) \(meses == 1 ? "mês" : "meses")", value: $meses, in: 1...60)
                        if meses > 1 {
                            Toggle("Dividir o valor em parcelas", isOn: $parcelar)
                            let v = parseMoney(valor)
                            Text(parcelar ? "\(meses) parcelas de \(brl((v * 100 / Double(meses)).rounded() / 100))" : "\(meses) lançamentos de \(brl(v)), um por mês")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                if let erro { Section { Text(erro).foregroundStyle(.red) } }
                if editar != nil {
                    Section { Button("Excluir lançamento", role: .destructive) { confirmarExclusao = true } }
                }
            }
            .navigationTitle(editar == nil ? "Novo lançamento" : "Editar lançamento")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Salvar") { salvar() }.fontWeight(.bold) }
            }
            .confirmationDialog("Excluir este lançamento?", isPresented: $confirmarExclusao, titleVisibility: .visible) {
                Button("Excluir", role: .destructive) {
                    if let e = editar { ctx.delete(e); try? ctx.save(); estado.avisar("Lançamento excluído") }
                    dismiss()
                }
            }
        }
    }

    private func salvar() {
        let v = parseMoney(valor)
        guard v > 0 else { erro = "Informe um valor maior que zero"; return }
        if tipo == Tipo.aporte && (plano ?? d.planos.first?.id) == nil { erro = "Cadastre um plano em Previdência primeiro"; return }
        let dia = diaISO(data)
        let cat = tipo == Tipo.aporte ? nil : catEfetiva
        let pl = tipo == Tipo.aporte ? (plano ?? d.planos.first?.id) : nil
        let pag: String? = tipo == Tipo.despesa ? pagamento : nil
        let trib = tipo == Tipo.receita && tributavel
        let desc = descricao.trimmingCharacters(in: .whitespaces)
        if let e = editar {
            e.tipo = tipo; e.valor = v; e.data = dia; e.categoria = cat; e.planoId = pl
            e.descricao = desc; e.pagamento = pag; e.tributavel = trib
            estado.avisar("Lançamento atualizado")
        } else {
            let n = max(1, min(60, meses))
            let grupo = n > 1 ? UUID().uuidString : nil
            let agora = agoraISO()
            let centavos = Int((v * 100).rounded())
            let nomePadrao = tipo == Tipo.aporte ? (d.planos.first { $0.id == pl }?.nome ?? "Aporte") : (d.categorias.first { $0.id == cat }?.nome ?? "Lançamento")
            for k in 0..<n {
                var vk = v
                if parcelar && n > 1 { let p = centavos / n; vk = Double(k == n - 1 ? centavos - p * (n - 1) : p) / 100 }
                let dk = n > 1 ? "\(desc.isEmpty ? nomePadrao : desc) (\(k + 1)/\(n))" : desc
                ctx.insert(Lancamento(tipo: tipo, valor: vk, data: somarMesesDia(dia, k), categoria: cat, planoId: pl,
                                      descricao: dk, pagamento: pag, tributavel: trib, criadoEm: agora, grupo: grupo))
            }
            estado.avisar(n > 1 ? "\(n) lançamentos criados" : "Lançamento salvo")
        }
        try? ctx.save()
        // Alerta de orçamento
        if tipo == Tipo.despesa, let c = d.categorias.first(where: { $0.id == cat }), c.orcamento > 0 {
            let ym = String(dia.prefix(7))
            let todos = (try? ctx.fetch(FetchDescriptor<Lancamento>())) ?? []
            let gasto = todos.filter { $0.tipo == Tipo.despesa && $0.categoria == c.id && $0.data.hasPrefix(ym) }.soma()
            let r = gasto / c.orcamento
            if r > 1 { estado.avisar("⛔ \(c.nome): orçamento estourado em \(brl(gasto - c.orcamento))") }
            else if r >= 0.8 { estado.avisar("⚠️ \(c.nome): já usou \(pct(r)) do orçamento") }
        }
        dismiss()
    }
}

struct FormPlano: View {
    let d: Dados
    let editar: Plano?
    @Environment(\.modelContext) private var ctx
    @Environment(\.dismiss) private var dismiss
    @Environment(Estado.self) private var estado
    @State private var nome: String
    @State private var tipo: String
    @State private var inst: String
    @State private var saldo: String
    @State private var saldoData: Date
    @State private var confirmar = false

    init(d: Dados, editar: Plano?) {
        self.d = d; self.editar = editar
        _nome = State(initialValue: editar?.nome ?? "")
        _tipo = State(initialValue: editar?.tipo ?? "PGBL")
        _inst = State(initialValue: editar?.instituicao ?? "")
        _saldo = State(initialValue: editar.map { fmtInput($0.saldo) } ?? "")
        _saldoData = State(initialValue: dataDe((editar?.saldoData).flatMap { $0.isEmpty ? nil : $0 } ?? hojeISO()))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Nome do plano", text: $nome)
                    Picker("Tipo", selection: $tipo) { ForEach(["PGBL", "VGBL", "Fundo de pensão", "Outro"], id: \.self) { Text($0).tag($0) } }
                    TextField("Instituição", text: $inst)
                }
                Section {
                    TextField("Saldo atual (R$)", text: $saldo).keyboardType(.decimalPad)
                    DatePicker("Saldo em", selection: $saldoData, displayedComponents: .date).environment(\.locale, localeBR)
                } footer: { Text("Atualize o saldo pelo extrato; o app calcula o rendimento como saldo − aportes lançados.") }
                if editar != nil {
                    Section { Button("Excluir plano", role: .destructive) { confirmar = true } }
                }
            }
            .navigationTitle(editar == nil ? "Novo plano" : "Plano de previdência")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Salvar") {
                        let n = nome.trimmingCharacters(in: .whitespaces)
                        guard !n.isEmpty else { return }
                        if let p = editar {
                            p.nome = n; p.tipo = tipo; p.instituicao = inst; p.saldo = parseMoney(saldo); p.saldoData = diaISO(saldoData)
                        } else {
                            ctx.insert(Plano(nome: n, tipo: tipo, instituicao: inst, saldo: parseMoney(saldo), saldoData: diaISO(saldoData)))
                        }
                        try? ctx.save(); estado.avisar("Plano salvo"); dismiss()
                    }
                    .fontWeight(.bold)
                }
            }
            .confirmationDialog("Excluir este plano?", isPresented: $confirmar, titleVisibility: .visible) {
                Button("Excluir", role: .destructive) {
                    if let p = editar {
                        for l in d.tx where l.planoId == p.id { l.planoId = nil }
                        ctx.delete(p); try? ctx.save()
                    }
                    dismiss()
                }
            } message: {
                let usados = d.tx.filter { $0.planoId == editar?.id }.count
                if usados > 0 { Text("Ele tem \(usados) aporte(s). Os aportes continuam lançados, sem plano associado.") }
            }
        }
    }
}
