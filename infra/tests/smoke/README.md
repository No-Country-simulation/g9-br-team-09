# Smoke tests do backend na OCI

Este diretório contém um smoke test reproduzível para validar, exclusivamente pela API HTTP pública, o backend Spring Boot implantado na OCI Compute. O teste verifica probes, processamento, persistência consultável, contrato de erros e a fonte pública de classificação.

O procedimento depende da implantação da issue #107, ou de um ambiente equivalente. O cenário `ML_MODEL` também depende da integração e da disponibilidade previstas na issue #71; ele é complementar e não bloqueia a validação inicial obrigatória com `RULE_BASED_FALLBACK`.

O teste não provisiona infraestrutura, não altera o deploy, não consulta o Oracle Autonomous Database diretamente e não chama a FastAPI diretamente.

## Estrutura

```text
infra/tests/smoke/
├── backend-oci-smoke.sh
├── payload-invalid.json
├── payload-valid.json
└── README.md
```

O payload inválido preserva `"tipo_imovel": "CASA"` e viola somente restrições numéricas determinísticas. Assim, o cenário exercita Bean Validation e o erro público `VALIDATION_ERROR`, não a desserialização de enum nem `ENUM_TYPE_ERROR`.

## Pré-requisitos

- Bash;
- `curl`;
- `jq`;
- `mktemp` e `date`;
- backend OCI ativo conforme a issue #107;
- acesso SSH à instância para criar o túnel;
- uma janela controlada, sem outras gravações na API durante a execução.

O script pode ser iniciado de qualquer diretório, pois resolve os fixtures em relação ao próprio arquivo. Ele não lê arquivos `.env` e não recebe credenciais.

## Túnel SSH

Como a porta do backend está vinculada ao loopback da instância, estabeleça o túnel em um terminal:

```bash
ssh -N \
  -L 8080:127.0.0.1:8080 \
  ubuntu@<IP_PUBLICO>
```

Use o método de autenticação SSH já aprovado para o ambiente. Não copie caminhos de chaves, endereços reais nem a saída detalhada do SSH para evidências públicas.

## Configuração

| Variável | Obrigatória | Padrão | Uso |
|---|---:|---|---|
| `BASE_URL` | sim | — | URL pública do backend, incluindo `/api/v1`. Uma ou mais barras finais são removidas. |
| `REQUEST_TIMEOUT` | não | `15` | Inteiro positivo, em segundos, aplicado aos limites de conexão e total de cada requisição. |
| `EXPECTED_CLASSIFICATION_SOURCE` | não | vazio | Vazio, `RULE_BASED_FALLBACK` ou `ML_MODEL`. Vazio aceita qualquer fonte prevista no contrato público. |
| `NON_EXISTENT_ID` | não | `9223372036854775807` | Inteiro positivo usado no cenário determinístico de recurso inexistente. |
| `VALIDATED_ARTIFACT` | não | vazio | Commit, tag imutável ou digest sanitizado exibido com o timestamp da evidência. |

Valores explícitos diferentes de `RULE_BASED_FALLBACK` e `ML_MODEL` são rejeitados em `EXPECTED_CLASSIFICATION_SOURCE`. O modo vazio também aceita `RULE_BASED`, mas a execução oficial inicial desta issue deve exigir `RULE_BASED_FALLBACK`.

`VALIDATED_ARTIFACT` deve ser curto, sanitizado e identificar somente o artefato validado. Não informe OCID, hostname interno, credencial ou endereço privado nessa variável.

## Execução

### Validação genérica do contrato

Use este modo apenas quando não for necessário exigir uma fonte de classificação específica:

```bash
BASE_URL=http://localhost:8080/api/v1 \
REQUEST_TIMEOUT=15 \
./infra/tests/smoke/backend-oci-smoke.sh
```

### Cenário inicial obrigatório: fallback

Antes do teste, o operador deve preparar uma janela controlada no deploy da issue #107 em que a integração de ML esteja indisponível. A configuração documentada para essa janela é `ML_API_BASE_URL=http://127.0.0.1:9`. Aplique-a pelo procedimento operacional do deploy, sem imprimir o arquivo de ambiente, e restaure a configuração anterior ao terminar.

O script não altera essa configuração, não reinicia containers e não tenta acessar a FastAPI. Ele apenas comprova pela resposta pública que o backend utilizou `RULE_BASED_FALLBACK` e permaneceu ready.

```bash
BASE_URL=http://localhost:8080/api/v1 \
REQUEST_TIMEOUT=15 \
EXPECTED_CLASSIFICATION_SOURCE=RULE_BASED_FALLBACK \
VALIDATED_ARTIFACT=<COMMIT_OU_IMAGEM_IMUTAVEL> \
./infra/tests/smoke/backend-oci-smoke.sh
```

### Cenário complementar: ML_MODEL

Execute este modo somente quando a FastAPI estiver implantada, a integração da issue #71 estiver disponível e o backend tiver sido preparado pelo processo controlado de deploy:

```bash
BASE_URL=http://localhost:8080/api/v1 \
REQUEST_TIMEOUT=15 \
EXPECTED_CLASSIFICATION_SOURCE=ML_MODEL \
VALIDATED_ARTIFACT=<COMMIT_OU_IMAGEM_IMUTAVEL> \
./infra/tests/smoke/backend-oci-smoke.sh
```

O teste exige `fonte_classificacao: ML_MODEL`, mas não exige `modelo_versao`, pois esse campo não faz parte do contrato público atual.

## Cenários executados

O script interrompe no primeiro erro obrigatório e executa, nesta ordem:

1. health geral com `status: UP`;
2. liveness com `status: UP`;
3. readiness com `status: UP` antes de criar dados;
4. captura de `total_analises` pela API de resumo;
5. criação de uma análise válida e validação dos tipos do contrato público;
6. localização do registro no histórico pelo `id` devolvido no `POST`, com paginação, `snake_case` e ordenação decrescente;
7. consulta do detalhe pelo mesmo `id`, comparando entradas e campos calculados;
8. confirmação de que o total aumentou exatamente em uma unidade;
9. rejeição do payload inválido com HTTP `400` e `VALIDATION_ERROR`;
10. confirmação, pelo total público, de que o payload inválido não foi persistido;
11. consulta de ID inexistente com HTTP `404` e `NOT_FOUND_ERROR`;
12. pós-condição da classificação: nova readiness no modo fallback ou confirmação de `ML_MODEL` no modo complementar.

Todas as respostas são verificadas como JSON e inspecionadas contra indicadores óbvios de vazamento interno. O registro válido criado permanece persistido, pois a API pública não oferece uma operação de exclusão.

## Exit codes e falhas

- `0`: todos os cenários obrigatórios do modo selecionado passaram;
- diferente de `0`: configuração, dependência, transporte, timeout, status HTTP, JSON ou asserção de contrato falhou.

As mensagens usam somente os prefixos `[INFO]`, `[PASS]` e `[FAIL]`. Em falhas HTTP ou de contrato, o corpo completo não é exibido: o script mostra apenas metadados limitados e redige o restante. Erros de transporte também não reproduzem a saída detalhada do `curl`, para evitar revelar o destino.

## Evidências e segurança

Para uma evidência revisável, registre somente a saída resumida do script, que contém timestamp UTC e, quando fornecido, `VALIDATED_ARTIFACT`. Antes de publicar, revise e remova qualquer endereço, hostname interno, OCID ou dado operacional sensível acrescentado fora do script.

Nunca publique ou passe ao teste:

- credenciais ou headers de autenticação;
- URL JDBC, usuário ou senha do banco;
- conteúdo de arquivos de ambiente;
- chaves ou caminhos privados de SSH;
- IPs reais, hostnames internos ou OCIDs;
- respostas completas do backend ou logs detalhados do deploy.

Os corpos de resposta ficam em um diretório temporário privado, criado com `mktemp -d`, e são removidos automaticamente ao final, inclusive em falha. O script não habilita `set -x` e não usa modo verboso do `curl`.

## Diagnóstico

- **Pré-requisitos/configuração:** confirme que as ferramentas estão no `PATH`, que os fixtures são JSON válido e que as variáveis seguem a tabela acima.
- **Falha de transporte ou timeout:** confirme o túnel em outro terminal, o estado do deploy e o valor numérico de `REQUEST_TIMEOUT`. Não publique a linha SSH real nem arquivos de configuração.
- **Health/liveness/readiness:** use o procedimento operacional da issue #107 para inspecionar o backend. Uma falha de readiness pode indicar indisponibilidade do banco obrigatório; o script não tenta diagnosticar o Oracle diretamente.
- **Incremento ou não persistência:** repita em uma janela isolada. Escritas concorrentes tornam inválida a comparação exata dos totais.
- **Histórico:** o teste solicita os 100 registros mais recentes. Evite uma janela com volume concorrente que possa deslocar o registro recém-criado dessa página.
- **Fonte inesperada:** confirme qual modo foi preparado no backend. No fallback, mantenha a FastAPI indisponível somente durante a janela controlada e restaure a configuração depois.
- **Erro de contrato:** preserve apenas a saída sanitizada e compare a versão implantada com `VALIDATED_ARTIFACT`; não publique dumps de resposta nem logs com configuração interna.

## Limitações

- O teste comprova comportamento pela API pública; não executa SQL nem valida o Oracle isoladamente.
- Readiness positiva, criação e consulta pelo ID são a evidência indireta de persistência no caminho implantado.
- O teste não comprova provisionamento, arquitetura da instância, configuração do container, TLS público, carga, desempenho ou monitoramento.
- A disponibilidade ou indisponibilidade da FastAPI é responsabilidade do operador; o script apenas valida a fonte observada na resposta do backend.
- A comparação exata de totais pressupõe ausência de gravações concorrentes.
- A análise válida criada não é removida ao final.
- `ML_MODEL` é opcional e sua ausência não invalida o smoke test inicial com fallback.

## Validação dos arquivos

Na raiz do repositório:

```bash
bash -n infra/tests/smoke/backend-oci-smoke.sh
jq empty infra/tests/smoke/payload-valid.json
jq empty infra/tests/smoke/payload-invalid.json
git diff --check
```

Quando ShellCheck estiver instalado:

```bash
shellcheck infra/tests/smoke/backend-oci-smoke.sh
```

Execute também a verificação completa do backend:

```bash
cd backend
./mvnw --batch-mode verify
cd ..
```
