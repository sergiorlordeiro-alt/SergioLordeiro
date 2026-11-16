# Meus Gastos — app Android nativo

Controle de gastos com lançamentos de despesas, recebimentos e aportes em previdência privada,
categorias com orçamento mensal, relatórios com gráficos, exportação para CSV (Excel) e backup.

Feito em Kotlin com Jetpack Compose (Material 3) e Room (banco SQLite local). Funciona 100% offline;
os dados ficam no celular.

## Como gerar o APK

### Opção 1 — Android Studio (no seu computador)
1. Instale o [Android Studio](https://developer.android.com/studio) (versão Koala ou mais nova).
2. *File → Open* e escolha a pasta `MeusGastos`. Aguarde o Gradle baixar as dependências (só na primeira vez).
3. Para testar no celular: ative *Opções do desenvolvedor → Depuração USB*, conecte o cabo e clique em ▶ *Run*.
4. Para gerar o arquivo: *Build → Build App Bundle(s) / APK(s) → Build APK(s)*.
   O APK sai em `app/build/outputs/apk/debug/app-debug.apk`. Copie para o celular e abra para instalar
   (o Android vai pedir para permitir "instalar apps desconhecidos").

### Opção 2 — GitHub (sem instalar nada)
1. Crie um repositório no GitHub e envie esta pasta.
2. O fluxo `.github/workflows/build.yml` compila sozinho a cada envio e publica o APK em
   *Releases*. No celular, abra `https://github.com/<seu-usuario>/<repositorio>/releases/latest`,
   toque em `MeusGastos.apk` e instale.

## Recursos
- **Lançamentos**: despesa, recebimento ou aporte; valor, data, categoria, descrição, forma de pagamento.
  Repetição mensal ou parcelamento (divide o valor em N parcelas).
- **Recebimentos**: categorias próprias (salário, 13º, férias, PLR…) e marcação de renda tributável.
- **Orçamento**: limite mensal por categoria, barra de progresso e alerta a partir de 80% e ao estourar.
- **Relatórios**: rosca por categoria, comparação com a média dos 3 meses anteriores, barras de 12 meses,
  por forma de pagamento e resumo do ano com taxa de poupança.
- **Previdência privada**: planos PGBL/VGBL/fundo de pensão, saldo informado, aportes, rendimento estimado e
  acompanhamento do limite de dedução do PGBL (12% das receitas tributáveis lançadas).
- **Exportar e backup**: CSV para Excel (ponto e vírgula, vírgula decimal) e backup JSON
  no mesmo formato do app web — dá para migrar os dados de um para o outro.

## Estrutura
```
app/src/main/java/com/meusgastos/app/
  MainActivity.kt
  data/Data.kt        entidades, DAO e banco Room
  data/Repo.kt        dados iniciais, parcelamento, backup JSON e CSV
  ui/Model.kt         ViewModel, estado e cálculos
  ui/theme/Theme.kt   cores claro/escuro
  ui/screens/App.kt          navegação inferior e componentes
  ui/screens/Telas.kt        Início, Lançamentos, Relatórios, Previdência, Ajustes
  ui/screens/Formularios.kt  formulário de lançamento e de plano
```
