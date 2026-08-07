# Evidências históricas de produção

Este documento registra somente evidências já coletadas; não substitui os procedimentos de [configuração](configuration.md), [implantação](manual-deployment.md) ou [operação](operations.md).

## Validação da Issue #110

Na implantação concluída da Issue #110, foi validado o frontend https://energiai.vercel.app integrado à API com prefixo https://147.15.30.0.sslip.io/api/v1. Foram publicados e utilizados [Swagger UI](https://147.15.30.0.sslip.io/api/v1/swagger-ui/index.html) e [OpenAPI](https://147.15.30.0.sslip.io/api/v1/v3/api-docs).

Foi observado que Caddy serviu HTTPS com certificado confiável e redirecionou HTTP para HTTPS; a porta 8080 permaneceu restrita a loopback. O health local retornou HTTP 200 e status UP. Durante a recriação apenas do backend, foi observado HTTP 502 transitório pelo Caddy até o health local retornar UP; após esse estado, a rota pública voltou a responder.

Foi configurada e validada a origem exata https://energiai.vercel.app. O preflight para POST /analise-energetica retornou HTTP 200, Access-Control-Allow-Origin: https://energiai.vercel.app, credenciais e cabeçalhos authorization, content-type; origem https://example.com retornou HTTP 403 e Invalid CORS request. O frontend publicado executou POST /analise-energetica com HTTP 200 e renderizou categoria, score, probabilidade, custo estimado e recomendações.

O diagnóstico de SSH foi atribuído à regra OCI TCP/22 restrita para IP administrativo /32 desatualizado: portas 80/443 continuaram disponíveis e SSH expirava antes de autenticação. A regra foi corrigida para o IP administrativo atual; foi mantida a restrição de SSH, sem 0.0.0.0/0.

## Limitações registradas

Foi utilizado hostname sslip.io baseado em IP público efêmero; por isso, os links publicados correspondiam ao endereço da instância validada. A validação relatada não comprovou futuras execuções, DNS, ACME, Oracle TLS, comportamento em todos os navegadores ou novos deploys. Cookies SameSite=None; Secure podem ser bloqueados por políticas de terceiros. A implantação da Vercel pertence à Issue #119; Caddy/HTTPS e estas evidências são rastreáveis à Issue #110.
