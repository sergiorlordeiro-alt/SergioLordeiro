import SwiftData
import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct AjustesView: View {
    let d: Dados
    @Environment(\.modelContext) private var ctx
    @Environment(Estado.self) private var estado
    @State private var novoPag = ""
    @State private var excluir: Categoria?
    @State private var compartilhar: URL?
    @State private var importando = false
    @State private var restaurarURL: URL?

    var body: some View {
        List {
            Section {
                ForEach(d.catDespesa) { c in EditorCategoria(c: c, comOrcamento: true) }
                    .onDelete { idx in excluir = idx.first.map { d.catDespesa[$0] } }
                Button("+ Categoria") { nova(Tipo.despesa) }
            } header: { Text("Categorias de despesa e orçamento") } footer: {
                Text("Limite mensal em R$ (vazio = sem limite). Toque na bolinha para trocar a cor; deslize para excluir.")
            }
            Section("Categorias de recebimento") {
                ForEach(d.catReceita) { c in EditorCategoria(c: c, comOrcamento: false) }
                    .onDelete { idx in excluir = idx.first.map { d.catReceita[$0] } }
                Button("+ Categoria") { nova(Tipo.receita) }
            }
            Section("Formas de pagamento") {
                ForEach(d.pagamentos, id: \.self) { Text($0) }
                    .onDelete { idx in
                        let nomes = idx.map { d.pagamentos[$0] }
                        let todas = (try? ctx.fetch(FetchDescriptor<FormaPagamento>())) ?? []
                        for f in todas where nomes.contains(f.nome) { ctx.delete(f) }
                        try? ctx.save()
                    }
                HStack {
                    TextField("Nova forma (ex.: Cartão Nubank)", text: $novoPag)
                    Button("Adicionar") {
                        let n = novoPag.trimmingCharacters(in: .whitespaces)
                        if !n.isEmpty && !d.pagamentos.contains(n) { ctx.insert(FormaPagamento(nome: n, ordem: d.pagamentos.count)); try? ctx.save() }
                        novoPag = ""
                    }
                }
            }
            Section {
                Text("\(d.tx.count) lançamento(s) guardados neste iPhone.").foregroundStyle(.secondary)
                Button("Exportar CSV (Excel)") { compartilhar = gravar("gastos-\(hojeISO()).csv", Backup.csv(d)) }
                Button("Salvar backup (.json)") { compartilhar = gravar("backup-gastos-\(hojeISO()).json", Backup.json(d)) }
                Button("Restaurar backup") { importando = true }
            } header: { Text("Exportar e backup") } footer: {
                Text("O backup usa o mesmo formato do app web e do app Android: dá para levar os dados de um para o outro. Guarde-o no iCloud Drive.")
            }
        }
        .listStyle(.insetGrouped)
        .sheet(item: $compartilhar) { url in Compartilhar(itens: [url]) }
        .fileImporter(isPresented: $importando, allowedContentTypes: [.json, .plainText, .data]) { r in
            if case .success(let url) = r { restaurarURL = url }
        }
        .confirmationDialog("Excluir “\(excluir?.nome ?? "")”?", isPresented: Binding(get: { excluir != nil }, set: { if !$0 { excluir = nil } }), titleVisibility: .visible) {
            Button("Excluir", role: .destructive) {
                if let c = excluir {
                    for l in d.tx where l.categoria == c.id { l.categoria = nil }
                    ctx.delete(c); try? ctx.save()
                }
                excluir = nil
            }
        } message: {
            let usados = d.tx.filter { $0.categoria == excluir?.id }.count
            if usados > 0 { Text("Há \(usados) lançamento(s) nesta categoria. Eles ficarão como “Sem categoria”.") }
        }
        .confirmationDialog("Restaurar backup?", isPresented: Binding(get: { restaurarURL != nil }, set: { if !$0 { restaurarURL = nil } }), titleVisibility: .visible) {
            Button("Restaurar") {
                if let url = restaurarURL {
                    do { estado.avisar("\(try Backup.restaurar(url, ctx)) lançamento(s) restaurado(s)") }
                    catch { estado.avisar("Não foi possível restaurar: \(error.localizedDescription)") }
                }
                restaurarURL = nil
            }
        } message: {
            Text("Categorias, planos e formas de pagamento serão substituídos pelos do backup. Os lançamentos serão mesclados, sem duplicar.")
        }
    }

    private func nova(_ tipo: String) {
        ctx.insert(Categoria(tipo: tipo, nome: "Nova categoria", icone: "🏷️", cor: "#64748B", ordem: (d.categorias.map(\.ordem).max() ?? 0) + 1))
        try? ctx.save()
    }

    private func gravar(_ nome: String, _ texto: String) -> URL? {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(nome)
        do { try texto.data(using: .utf8)?.write(to: url, options: .atomic); return url }
        catch { estado.avisar("Não foi possível gerar o arquivo"); return nil }
    }
}

extension URL: Identifiable { public var id: String { absoluteString } }

struct EditorCategoria: View {
    @Bindable var c: Categoria
    let comOrcamento: Bool
    @State private var orc = ""

    var body: some View {
        HStack(spacing: 8) {
            Circle().fill(Color(hex: c.cor)).frame(width: 24, height: 24)
                .onTapGesture { c.cor = paleta[((paleta.firstIndex(of: c.cor.uppercased()) ?? -1) + 1) % paleta.count] }
            TextField("", text: $c.icone).frame(width: 32).multilineTextAlignment(.center)
            TextField("Nome", text: $c.nome)
            if comOrcamento {
                TextField("Sem limite", text: $orc).keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 100)
                    .onChange(of: orc) { _, novo in c.orcamento = parseMoney(novo) }
            }
        }
        .onAppear { orc = fmtInput(c.orcamento) }
    }
}

/// Folha de compartilhamento do iOS (salvar em Arquivos, enviar por e-mail, WhatsApp…).
struct Compartilhar: UIViewControllerRepresentable {
    let itens: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: itens, applicationActivities: nil) }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}

// MARK: - Backup e CSV (mesmo formato do app web e do Android)

enum Backup {
    static func json(_ d: Dados) -> String {
        func cat(_ c: Categoria) -> [String: Any] { ["id": c.id, "nome": c.nome, "icone": c.icone, "cor": c.cor, "orcamento": c.orcamento] }
        let cfg: [String: Any] = [
            "versao": 1,
            "catDespesa": d.catDespesa.map(cat),
            "catReceita": d.catReceita.map(cat),
            "pagamentos": d.pagamentos,
            "planos": d.planos.map { ["id": $0.id, "nome": $0.nome, "tipo": $0.tipo, "instituicao": $0.instituicao, "saldo": $0.saldo, "saldoData": $0.saldoData] as [String: Any] },
        ]
        let tx: [[String: Any]] = d.tx.sorted { $0.data < $1.data }.map { l in
            var o: [String: Any] = ["id": l.id, "tipo": l.tipo, "valor": l.valor, "data": l.data, "descricao": l.descricao,
                                    "tributavel": l.tributavel, "criadoEm": l.criadoEm]
            if let v = l.categoria { o["categoria"] = v }
            if let v = l.planoId { o["planoId"] = v }
            if let v = l.pagamento { o["pagamento"] = v }
            if let v = l.grupo { o["grupo"] = v }
            return o
        }
        let raiz: [String: Any] = ["app": "meus-gastos", "versao": 1, "exportadoEm": agoraISO(), "cfg": cfg, "tx": tx]
        let data = (try? JSONSerialization.data(withJSONObject: raiz, options: [.prettyPrinted, .sortedKeys])) ?? Data()
        return String(data: data, encoding: .utf8) ?? "{}"
    }

    static func csv(_ d: Dados) -> String {
        func q(_ s: String) -> String { (s.contains(";") || s.contains("\"") || s.contains("\n")) ? "\"" + s.replacingOccurrences(of: "\"", with: "\"\"") + "\"" : s }
        var linhas = ["\u{FEFF}Data;Tipo;Categoria/Plano;Descrição;Forma de pagamento;Tributável;Valor"]
        for l in d.tx.sorted(by: { $0.data < $1.data }) {
            let tipo = l.tipo == Tipo.despesa ? "Despesa" : l.tipo == Tipo.receita ? "Recebimento" : "Aporte previdência"
            let trib = l.tipo == Tipo.receita ? (l.tributavel ? "Sim" : "Não") : ""
            let valor = String(format: "%.2f", l.valor).replacingOccurrences(of: ".", with: ",")
            linhas.append([dataBR(l.data), tipo, q(d.meta(l).nome), q(l.descricao), q(l.pagamento ?? ""), trib, valor].joined(separator: ";"))
        }
        return linhas.joined(separator: "\r\n")
    }

    struct Erro: LocalizedError { let errorDescription: String? }

    @discardableResult
    static func restaurar(_ url: URL, _ ctx: ModelContext) throws -> Int {
        let acesso = url.startAccessingSecurityScopedResource()
        defer { if acesso { url.stopAccessingSecurityScopedResource() } }
        let dados = try Data(contentsOf: url)
        guard let raiz = try JSONSerialization.jsonObject(with: dados) as? [String: Any],
              let tx = raiz["tx"] as? [[String: Any]] else { throw Erro(errorDescription: "o arquivo não é um backup do app") }
        func num(_ v: Any?) -> Double { (v as? NSNumber)?.doubleValue ?? Double(v as? String ?? "") ?? 0 }
        func str(_ v: Any?) -> String? { (v as? String).flatMap { $0.isEmpty ? nil : $0 } }

        if let cfg = raiz["cfg"] as? [String: Any], cfg["catDespesa"] != nil {
            try ctx.delete(model: Categoria.self)
            var o = 0
            for (tipo, chave) in [(Tipo.despesa, "catDespesa"), (Tipo.receita, "catReceita")] {
                for c in (cfg[chave] as? [[String: Any]]) ?? [] {
                    ctx.insert(Categoria(id: str(c["id"]) ?? UUID().uuidString, tipo: tipo, nome: str(c["nome"]) ?? "Categoria",
                                         icone: str(c["icone"]) ?? "🏷️", cor: str(c["cor"]) ?? "#64748B", orcamento: num(c["orcamento"]), ordem: o))
                    o += 1
                }
            }
            if let pags = cfg["pagamentos"] as? [String] {
                try ctx.delete(model: FormaPagamento.self)
                for (i, p) in pags.enumerated() { ctx.insert(FormaPagamento(nome: p, ordem: i)) }
            }
            if let planos = cfg["planos"] as? [[String: Any]] {
                try ctx.delete(model: Plano.self)
                for p in planos {
                    ctx.insert(Plano(id: str(p["id"]) ?? UUID().uuidString, nome: str(p["nome"]) ?? "Plano", tipo: str(p["tipo"]) ?? "PGBL",
                                     instituicao: str(p["instituicao"]) ?? "", saldo: num(p["saldo"]), saldoData: str(p["saldoData"]) ?? ""))
                }
            }
        }
        let existentes = Dictionary(uniqueKeysWithValues: ((try? ctx.fetch(FetchDescriptor<Lancamento>())) ?? []).map { ($0.id, $0) })
        var n = 0
        for t in tx {
            let valor = num(t["valor"])
            let data = String((str(t["data"]) ?? "").prefix(10))
            let tipo = str(t["tipo"]) ?? ""
            guard valor > 0, data.count == 10, [Tipo.despesa, Tipo.receita, Tipo.aporte].contains(tipo) else { continue }
            let id = str(t["id"]) ?? UUID().uuidString
            if let e = existentes[id] {
                e.tipo = tipo; e.valor = valor; e.data = data; e.categoria = str(t["categoria"]); e.planoId = str(t["planoId"])
                e.descricao = (t["descricao"] as? String) ?? ""; e.pagamento = str(t["pagamento"])
                e.tributavel = (t["tributavel"] as? Bool) ?? false; e.grupo = str(t["grupo"])
                n += 1; continue
            }
            ctx.insert(Lancamento(id: id, tipo: tipo, valor: valor, data: data, categoria: str(t["categoria"]), planoId: str(t["planoId"]),
                                  descricao: (t["descricao"] as? String) ?? "", pagamento: str(t["pagamento"]),
                                  tributavel: (t["tributavel"] as? Bool) ?? false, criadoEm: (t["criadoEm"] as? String) ?? agoraISO(),
                                  grupo: str(t["grupo"])))
            n += 1
        }
        try ctx.save()
        return n
    }
}
