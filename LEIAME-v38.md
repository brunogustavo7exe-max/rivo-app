# RIVO v38 — alerta que grita, toque escolhido, login que não expira

## No GitHub (repositório rivo-app)
Suba, mantendo as pastas (pode arrastar tudo de uma vez, o GitHub substitui o que já existe):
- `.github/workflows/build-apk.yml`   (substitui o atual)
- `www/rivo.html`                     (substitui o atual)
- `google-services.json`              (raiz do repositório; não é secreto)
- `native/`                           (pasta nova: código Android e os 5 toques)
Ao subir, o GitHub Actions gera o APK sozinho. Baixe em Releases > "RIVO APK (mais recente)".

## No Cloudflare (worker do MiHelp)
1. worker.js: na linha do bloco v26 que diz `export default {`, troque por `const worker26 = {`.
2. Cole o conteúdo de `worker-v38.js` no FIM do worker.js e publique.
3. Settings > Variables and Secrets > Add > tipo Secret, nome `FCM_SA`, valor = o conteúdo INTEIRO do
   arquivo .json da chave do Firebase (Contas de serviço > Gerar nova chave privada).
4. Settings > Triggers > Cron Triggers > Add: `* * * * *` (todo minuto).
