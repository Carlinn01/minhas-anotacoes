# Minhas Anotações

App Android para anotar tarefas, a lista de compras e metas, com lembretes no celular.

## Funções

- **Tarefas**: adicionar, data para realizar, lembrete com horário, marcar como concluída, apagar
- **Compras**: adicionar item (com quantidade), lembrete, marcar como comprado, apagar
- **Metas**: adicionar meta com prazo, barra de progresso (+10% por toque), marcar como concluída, apagar
- Toque num item para editar; ao apagar aparece **Desfazer**
- A notificação do lembrete tem um botão **Concluído / Comprado**
- Os lembretes continuam funcionando depois de reiniciar o celular
- Tudo fica salvo no próprio celular

## Instalar no celular

1. No celular, abra a página **Releases** deste repositório e baixe `MinhasAnotacoes.apk` da versão mais recente.
2. Abra o arquivo. Se o Android pedir, permita "instalar apps desta fonte".
3. Ao abrir o app, permita as **notificações** para receber os lembretes.

## Atualizações

O app verifica os Releases deste repositório ao abrir (e a cada 12 horas em segundo plano).
Quando há versão nova, aparece **Nova versão disponível**: toque em **Atualizar** e o app baixa e
abre o instalador. Na primeira vez o Android pede para permitir "instalar desta fonte".
O botão ⟳ no topo verifica na hora. A mensagem do commit aparece como "Novidades".

## Como o APK é gerado

Cada `push` na branch `main` roda o GitHub Actions ([.github/workflows/build.yml](.github/workflows/build.yml)),
que compila o app e publica um novo Release com o APK. Não precisa ter Android Studio instalado.

Para editar no computador, abra a pasta no Android Studio.
