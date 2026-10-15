---
name: release-check
description: Confere se a branch atual está pronta para ir à main, onde cada push vira uma Release pública do RosaNotes (APK baixado pelo link fixo do site). Roda os testes, confere a versão e prepara as notas da versão. Use só quando o usuário pedir /release-check.
disable-model-invocation: true
---

# Conferência antes de publicar

Todo push na `main` dispara `.github/workflows/android.yml`: compila o APK, roda o
`FunctionalTest` e, se passar, cria a Release `rosanotes-v<versionName>-build<N>` marcada como
`--latest`. Quem já usa o app recebe o aviso de versão nova. Esta skill confere a branch
**antes** do merge e **não** faz merge, push nem Release: só relata.

## 1. Situação da branch

```bash
git fetch origin main
git status --short
git log --oneline origin/main..HEAD
git diff --stat origin/main...HEAD
```

- Há alterações não commitadas? Avise: elas ficam fora da versão.
- A branch está atrás da `main` (`git log --oneline HEAD..origin/main`)? Avise que precisa
  trazer a `main` antes.
- Nenhum commit novo além da `main`? Diga que não há nada a publicar e pare.

## 2. Arquivos que não devem mudar à mão

Veja no `git diff --name-only origin/main...HEAD` se mudou algum destes. Se mudou, destaque:

- `app/debug.keystore`: é a chave que deixa o APK novo instalar por cima do antigo. Trocá-la
  obriga quem usa o app a desinstalar e **perder as notas**.
- `docs/screenshots/**`: são geradas pelo CI (commit "Atualiza capturas de tela [skip ci]").
- `applicationId` ou `namespace` em `app/build.gradle.kts`: mudar cria outro app no aparelho.

## 3. Testes

Se `ANDROID_HOME` (ou `ANDROID_SDK_ROOT`) estiver definido, rode os mesmos testes do CI:

```bash
./gradlew testDebugUnitTest --tests "*FunctionalTest*" --stacktrace
```

Sem o Android SDK (caso comum nas sessões na nuvem), não tente instalar. Em vez disso,
consulte pelo GitHub o resultado do workflow "RosaNotes (APK Android)" no último commit da
branch (`git rev-parse HEAD`). Relate: verde, vermelho (com o passo e o teste que falhou) ou
ainda rodando. Commits com `[skip ci]` não rodam o CI; use o último commit sem essa marca.

## 4. Versão

Leia `versionName` em `app/build.gradle.kts` e compare com o da `main`:

```bash
git show origin/main:app/build.gradle.kts | grep versionName
grep versionName app/build.gradle.kts
```

Se o `versionName` não mudou e a branch traz recurso novo ou correção visível, sugira o
próximo número (patch para correção, minor para recurso), sem editar o arquivo.
O `versionCode` vem do número da execução do CI; não mexa nele.

## 5. Notas da versão

Escreva um rascunho em português, a partir dos commits de `origin/main..HEAD`, ignorando
"Atualiza capturas de tela", merges e commits só de docs. Agrupe em **Novidades** e
**Correções**, uma linha por item, na linguagem de quem usa o app (não de quem programa).

## 6. Relatório

Termine com uma lista curta:

- Branch: N commits à frente, atualizada ou não com a `main`.
- Testes: passou / falhou / sem SDK, CI verde / vermelho / rodando.
- Arquivos sensíveis: nenhum, ou quais.
- Versão: atual e sugerida.
- Veredito: **pronta para a main** ou **não publicar ainda**, com o motivo.

Depois, as notas da versão. Não faça merge nem push: pergunte se o usuário quer seguir.
