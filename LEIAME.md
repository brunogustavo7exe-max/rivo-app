# RIVO — app Android + PWA iPhone

## 1) Site (PWA, vale para iPhone e Android)
Suba para a raiz de dcastrolabs.com (mesma pasta) o conteúdo de `site/`:
`rivo.html`, `rivo.webmanifest`, `rivo-sw.js`, pasta `rivo-icons/` (nomes próprios para não colidir com outros apps do mesmo site).
No iPhone: Safari → Compartilhar → Adicionar à Tela de Início.

## 2) APK Android (GPS em segundo plano)
1. Crie um repositório no GitHub e envie esta pasta (menos `site/`, se quiser).
2. Aba **Actions → Gerar APK do RIVO → Run workflow**.
3. Ao terminar (~5 min), baixe o artefato `rivo-apk` (contém `rivo.apk`).
4. Suba `rivo.apk` para a raiz de dcastrolabs.com. O botão dentro do RIVO aponta para
   `https://www.dcastrolabs.com/rivo.apk` (constante `APK_URL` no rivo.html).

## Atualizações
- Mudou o rivo.html? Copie para `www/index.html` e `site/rivo.html`, rode o workflow de novo e suba o novo APK.
- O APK usa `androidScheme: http` para que a origem `http://localhost` já seja aceita pelo CORS atual do Worker. Não precisa mexer no Worker.

## Observações
- O APK é "debug" (assinado com chave de depuração): instala por download direto. Para Google Play é preciso gerar versão assinada (release) e justificar localização em segundo plano.
- iPhone: o PWA só rastreia com o app aberto e a tela acesa (Wake Lock). Rastreio com tela apagada no iPhone só com app na App Store.

## Erro 404 ao baixar o APK
O botão aponta para `https://www.dcastrolabs.com/rivo.apk`. Se der 404, o arquivo `rivo.apk` ainda não está na raiz do site.
O APK **não** é gerado pelo site: ele sai do GitHub Actions (aba Actions → Gerar APK do RIVO → Run workflow).
O artefato baixado é um .zip: descompacte e suba só o `rivo.apk` ao lado do `rivo.html`.
Alternativa: o workflow também publica o APK em Releases (tag `apk-latest`), com link direto:
`https://github.com/SEU_USUARIO/SEU_REPOSITORIO/releases/download/apk-latest/rivo.apk`
