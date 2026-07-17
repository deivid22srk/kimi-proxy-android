# Kimi Proxy Android

App Android (Kotlin + Jetpack Compose + Material You 3 Expressive) que
**faz login no Kimi** (incluindo "Continuar com Google") e **captura o
token JWT** exatamente como o script Playwright do
[`kimi-proxy-web`](https://github.com/Maicon501a/kimi-proxy-web) faz.

O token capturado é enviado automaticamente para a sua instância local do
`kimi-proxy-web` (configurável em **Configurações**), com fallback de
copiar/colar para o `accounts.json` do proxy.

## Recursos

- **WebView com login Google** — o popup OAuth do Google é aberto dentro
  do app (não externo).
- **Captura automática de JWT** — intercepta requests do próprio Kimi
  (`kimi.com/apiv2/`) e extrai `Authorization: Bearer …` +
  `x-msh-device-id` / `x-msh-session-id` / `x-msh-shield-data` /
  `x-traffic-id`.
- **Material You 3 Expressive** — usa cores dinâmicas (Android 12+),
  tipografia atualizada, cartões com cantos generosos e paleta fallback
  estática para Android < 12.
- **Múltiplas contas** — lista todas as contas capturadas, com indicador
  de expiração (baseado no claim `exp` do JWT).
- **Envio para o proxy** — `POST /__accounts` (convenção usada por forks
  endurecidos do `kimi-proxy-web`). Falha graciosamente: o usuário pode
  copiar o JSON completo da conta e colar em `accounts.json` do servidor.
- **Configurável** — URL do proxy, API key, tema (sistema/claro/escuro),
  cor dinâmica on/off, envio automático on/off.

## Arquitetura

```
UI (Compose)  ──►  AppViewModel  ──►  AccountRepository (DataStore)
                          │
                          ▼
                       ProxyApi (OkHttp)  ──►  kimi-proxy-web local
```

A WebView usa um `WebViewClient` customizado que implementa
`shouldInterceptRequest` para ler os headers de cada request saído do
Kimi. Quando detecta um Bearer token no path `/apiv2/`, decodifica o
JWT, valida os claims `sub` e `ssid`, monta o objeto `KimiAccount` e
emite via callback.

## Build

### Local

```bash
./gradlew assembleDebug
# APK em app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions

O workflow `.github/workflows/build.yml` roda em todo push/PR para
`main`. Ele:

1. Configura JDK 17 + Gradle 8.11.1
2. Roda `assembleDebug` + `assembleRelease`
3. Sobe ambos os APKs como artifacts (retenção 30 dias)
4. Em pushes de tags `v*` cria uma GitHub Release com os APKs

## Como usar

1. Instale o APK no celular.
2. Abra o app, vá em **Configurações** e ajuste a URL do proxy para o
   endereço IP do PC onde o `kimi-proxy-web` está rodando
   (ex.: `http://192.168.0.10:8080`). No emulador, use
   `http://10.0.2.2:8080`.
3. Vá em **Login** e entre normalmente no Kimi (você pode usar login
   Google).
4. Quando o token for capturado, você verá um banner verde. Ele é salvo
   automaticamente e enviado ao proxy (se habilitado).
5. Em **Contas** você vê todas as contas capturadas, pode reenviar para
   o proxy, copiar o token, ver detalhes completos ou excluir.

## Segurança

- Os tokens ficam **apenas no dispositivo** (DataStore) — não são
  sincronizados com nuvem.
- O `network_security_config.xml` permite tráfego HTTP apenas para
  `127.0.0.1`, `localhost`, `10.0.2.2` e `kimi.com`. Para outros hosts
  use HTTPS.
- **Nunca** exponha o proxy na internet pública sem `API_KEY` configurada.

## Licença

Uso por sua conta e risco. Projeto não-oficial, não afiliado à Moonshot
AI / Kimi.
