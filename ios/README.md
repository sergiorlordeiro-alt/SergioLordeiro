# Meus Gastos — app iOS (iPhone)

Versão nativa para iPhone, em SwiftUI + SwiftData (iOS 17 ou mais novo). Mesmos recursos e mesmo
formato de backup do app web e do app Android.

## Instalar no iPhone

A Apple só deixa instalar apps fora da App Store se eles forem assinados com uma conta Apple.
Há três caminhos:

1. **Mac com Xcode (grátis, com seu Apple ID)**
   - Baixe `MeusGastos-iOS-Xcode.zip` na página de *Releases* do repositório e descompacte.
   - Abra `MeusGastos.xcodeproj` no Xcode 15 ou mais novo.
   - Em *Signing & Capabilities*, escolha seu Apple ID em *Team* (o Xcode cria o perfil sozinho).
   - Conecte o iPhone pelo cabo, selecione-o no topo e clique em ▶ Run.
   - No iPhone: *Ajustes → Geral → VPN e Gerenciamento de Dispositivos* → confiar no seu Apple ID.
   - Com Apple ID gratuito, o app precisa ser reinstalado a cada 7 dias. Com a conta de
     desenvolvedor paga (US$ 99/ano) vale por 1 ano e dá para publicar no TestFlight/App Store.

2. **Windows ou Mac, sem Xcode — Sideloadly**
   - Baixe `MeusGastos-sem-assinatura.ipa` em *Releases*.
   - Instale o Sideloadly (sideloadly.io), conecte o iPhone, arraste o .ipa e entre com seu Apple ID.
   - Mesma regra dos 7 dias para Apple ID gratuito.

3. **Sem instalar nada:** use o app web no Safari → Compartilhar → *Adicionar à Tela de Início*.

## Gerar o projeto Xcode a partir do código
O projeto é descrito em `project.yml` (XcodeGen):
```
brew install xcodegen
cd ios && xcodegen generate
open MeusGastos.xcodeproj
```
O GitHub faz isso sozinho a cada envio (`.github/workflows/ios.yml`) e publica o zip e o .ipa em *Releases*.
