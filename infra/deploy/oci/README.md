# Implantação e operação do backend na OCI

Esta documentação descreve a implantação do backend Spring Boot do EnergiAI em uma instância OCI Compute. O Terraform provisiona rede e host separadamente; este diretório não instala Docker, não implanta a FastAPI e não expõe o backend diretamente à internet.

## Visão geral

A imagem é construída fora da VM para `linux/amd64` e identificada por tag imutável. Na OCI, `backend` atende em `127.0.0.1:8080`, enquanto `caddy` publica somente TCP 80 e 443 e encaminha para `backend:8080` pela bridge `energiai-oci`. Os volumes `energiai-caddy-data` e `energiai-caddy-config` mantêm o estado do Caddy. Oracle é obrigatório para readiness; FastAPI é opcional e pode acionar `RULE_BASED_FALLBACK`.

Os componentes são `compose.yaml`, `Caddyfile`, `/opt/energiai/config/backend.env`, a imagem `docker.io/pxs00/energiai-backend` e os [smoke tests](../../tests/smoke/README.md).

## Pré-requisitos e fluxo

São necessários host OCI Ubuntu 24.04 `x86_64/amd64` preparado, Docker Engine com Compose, SSH autorizado, Oracle configurado e, para construção manual, Docker Buildx com `linux/amd64`.

1. Preparar checkout e ambiente externo seguro.
2. Validar e publicar/carregar uma imagem imutável.
3. Iniciar ou atualizar o Compose sem build.
4. Aguardar readiness e executar verificações ou smoke test.
5. Preservar a imagem anterior até o fim da janela de rollback.

## Invariantes de segurança

- `backend.env` permanece fora do Git, regular e modo `0600`; não o imprima.
- `BACKEND_IMAGE` usa `sha-<SHA-completo>`; nunca `latest`, `develop` ou SHA abreviado.
- A porta `8080` permanece em loopback; Caddy é a única entrada pública.
- Não use CORS wildcard com credenciais, nem exponha segredos, wallets, JDBC, chaves SSH ou tokens.
- Não execute `up --build`, `flyway clean`, `ddl-auto=update` ou limpezas amplas na OCI.

## Guias especializados

- [Configuração do ambiente](docs/configuration.md)
- [Implantação manual](docs/manual-deployment.md)
- [Implantação via GitHub Actions](docs/github-actions-deployment.md)
- [Operação e diagnóstico](docs/operations.md)
- [Evidências históricas de produção](docs/production-validation.md)

## Rastreabilidade

Esta organização consolida as Issues #104 (host/rede), #105 (health), #106 (Docker/diretórios), #107 (Compose e operação), #108 (smoke tests), #109/#139 (automação), #110 (HTTPS público) e #152. Consulte também o [provisionamento do host](../../provisioning/oci-compute/README.md), o [guia do Oracle](../../../docs/oracle-autonomous-database.md) e o README principal.
