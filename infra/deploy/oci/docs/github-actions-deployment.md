# Implantação via GitHub Actions

A [workflow](../../../../.github/workflows/backend-oci-deploy.yml) é exclusivamente manual por `workflow_dispatch`: pushes e pull requests não implantam. A concorrência `backend-oci-deploy` serializa execuções sem cancelar a ativa.

## Operações e imutabilidade

- `validate` aceita branch, tag ou commit, resolve SHA de 40 caracteres, faz checkout destacado e valida sem publicar ou implantar.
- `deploy-preview` exige SHA minúsculo completo alcançável a partir de `origin/develop` e `confirmation=DEPLOY`; é integração, não release.
- `deploy` exige literalmente `ref=main`, confirma o HEAD atual de `origin/main` e `confirmation=DEPLOY`.

O SHA resolvido é a fonte única para checkout, tag `docker.io/pxs00/energiai-backend:sha-<full-sha>`, checkout OCI, digest, readiness, smoke e rollback. A validação executa Maven, Bash, ShellCheck, actionlint, fixtures, Compose, Caddyfile, whitespace e build externo `linux/amd64` sem publicação. Para deploy, a workflow reutiliza ou publica a imagem imutável Docker Hub, preservando labels OCI e digest.

## Ambiente e secrets

Somente o job de deploy usa o GitHub Environment `oci-production`: `OCI_COMPUTE_HOST`, `OCI_COMPUTE_USER`, `OCI_COMPUTE_SSH_PORT`, `OCI_COMPUTE_SSH_PRIVATE_KEY` e `OCI_COMPUTE_KNOWN_HOSTS`. Repository secrets: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN` (somente publicação) e `DOCKERHUB_DEPLOY_TOKEN` (somente pull OCI). O deploy também exige `OCI_SMOKE_USER_EMAIL` e `OCI_SMOKE_USER_PASSWORD` no Environment.

SSH usa `BatchMode`, `IdentitiesOnly`, `StrictHostKeyChecking=yes`, known_hosts controlado e timeout. A credencial de leitura é transferida temporariamente para `docker login --password-stdin`; o helper limpa `DOCKER_CONFIG`, arquivo e workspace. Nunca transfira `DOCKERHUB_TOKEN` à OCI ou ponha credenciais Docker/Oracle em `backend.env`.

## Deploy, rollback e limites

O runner self-hosted `Linux`, `X64`, `oci-deploy` transfere apenas o helper. Ele atualiza atomicamente apenas `BACKEND_IMAGE`, faz pull, confere `linux/amd64` e digest, recria somente `backend`, aguarda readiness local e executa `infra/tests/smoke/backend-oci-smoke.sh`. Falhas após a troca acionam tentativa de rollback da imagem e checkout anteriores; a workflow falha mesmo se rollback funcionar. Não reverte migrations compatíveis, Caddy, frontend ou FastAPI.

O Job Summary registra operação, ref, SHA, política, imagem/digest, plataforma, ambiente, timestamps, readiness, smoke e rollback, sem host, JDBC, chaves ou tokens. Diagnósticos pós-troca ficam restritos a `/opt/energiai/deploy-diagnostics/<commit-completo>.log`, modo `0600`, retenção de cinco; inspeção autorizada usa `sudo stat` e `sudo less --`. Execute `validate` antes de qualquer implantação.

## Procedimento de execução e diagnósticos

Execute `validate` primeiro. Para integração, informe em `deploy-preview` um SHA completo de `develop` e `confirmation=DEPLOY`. Para produção, selecione `deploy`, use `ref=main` e a mesma confirmação. A fonte esperada do smoke pode ser selecionada quando aplicável.

Antes de atualizar, o helper recusa estado não identificável: imagem em execução, `BACKEND_IMAGE` e checkout OCI devem representar a mesma tag `sha-<40-hex>`; `backend.env` precisa ser arquivo regular restrito e o checkout não pode ter alterações. Isso evita sobrescrever estado operacional. A migração inicial continua sendo manual.

Falha antes da troca de `BACKEND_IMAGE` não exige rollback. Em falha de pull, Compose, readiness ou smoke posterior, o helper tenta restaurar imagem e checkout anteriores e a job continua falha. Se o rollback falhar, o resumo informa `failed-rollback-failed`. Rollback de aplicação não reverte migrations Oracle compatíveis.

Quando o container candidato já foi substituído, a falha preserva diagnóstico best-effort em `/opt/energiai/deploy-diagnostics/<commit-completo>.log`; diretório usa modo `0700`, arquivos `0600` e apenas os cinco mais recentes permanecem. Implantações bem-sucedidas ou falhas anteriores à substituição não criam arquivo. Em SSH autorizado, revise somente o SHA conhecido:

~~~bash
sudo stat --format='mode=%a owner=%U group=%G' \
  /opt/energiai/deploy-diagnostics/<commit-completo>.log
sudo less -- /opt/energiai/deploy-diagnostics/<commit-completo>.log
~~~

Para remover diagnóstico antigo, revise SHA completo e remova somente o arquivo identificado. Para rotação ou revogação, revogue a credencial Docker Hub afetada, atualize somente o secret correspondente, execute `validate` e faça deploy aprovado.
