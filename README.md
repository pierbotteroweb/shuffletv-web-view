# ShuffleTV Web View

Aplicativo Android para tablet que abre, em tela cheia, a exibição de vídeos hospedada em `http://192.168.1.40`.

## Comportamento

- Inicia bloqueado em modo paisagem, sem barras do sistema e sem desligar a tela.
- Mantém a área de vídeo centralizada na proporção **4:3**, sem distorção em telas widescreen.
- Carrega a página local em um `WebView` com JavaScript, armazenamento DOM e reprodução de mídia habilitados.
- Permite autoplay quando o servidor disponibilizar mídia compatível.
- Tenta reconectar automaticamente a cada 5 segundos quando o servidor não estiver acessível.
- Permite tráfego HTTP **somente** para `192.168.1.40`; outros hosts continuam bloqueados por padrão.
- Um toque nos 20% superiores da tela alterna uma camada preta sobre toda a área do vídeo. Enquanto a camada estiver ativa, os demais toques no vídeo são bloqueados.

## Gerar o APK

1. Abra `G:\ShuffleTV\shuffletv-web-view` no Android Studio.
2. Aguarde a sincronização do Gradle e instale os componentes Android solicitados (o projeto usa `compileSdk 35` e Java 17).
3. Execute **Build > Build APK(s)**.
4. O APK de desenvolvimento será gerado em:
   `G:\ShuffleTV\shuffletv-web-view\app\build\outputs\apk\debug\app-debug.apk`

Após abrir o projeto no Android Studio, use o terminal integrado com o Gradle instalado/configurado na máquina:

```bat
gradle assembleDebug
```

## Instalar no tablet por USB

1. Ative **Opções do desenvolvedor** e **Depuração USB** no tablet Android.
2. Conecte-o por USB e aceite a chave de depuração exibida no dispositivo.
3. Execute:

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

O tablet deve estar conectado à mesma rede local do servidor `192.168.1.40`.