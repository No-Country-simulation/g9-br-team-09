# Provisionamento do container-host OCI Compute — Issue #106

Este diretório prepara a instância OCI Compute provisionada pela Issue #104 para futuros containers do EnergiAI. O objetivo é apenas instalar e configurar o Docker Engine; ele não faz deploy, pull, build ou execução de nenhum serviço de aplicação do EnergiAI.

## Host suportado e pré-requisitos

A Issue #104 é a fonte da verdade para a dependência e a arquitetura. Execute este script apenas na instância Canonical Ubuntu 24.04 LTS provisionada por ela, com arquitetura de kernel `x86_64`, arquitetura do APT `amd64` e o usuário administrativo padrão `ubuntu`. Hosts ARM e arquiteturas `aarch64` são rejeitados intencionalmente.

Conecte-se através do acesso SSH criado pela Issue #104 e copie este repositório ou este diretório de provisionamento para a instância. O comando exige privilégios de root, acesso à internet para alcançar os repositórios APT do Ubuntu e do Docker, e um host executando o systemd. O script não reinicia a instância; ele apenas reporta se o Ubuntu marcou uma reinicialização como necessária (reboot required).

```bash
sudo bash infra/provisioning/oci-compute/install-docker.sh
```

Para usar uma conta administrativa já existente diferente de `ubuntu`:

```bash
sudo ENERGIAI_ADMIN_USER=<existing-user> \
  bash infra/provisioning/oci-compute/install-docker.sh
```

Este override não pode ser vazio, `root` ou um usuário inexistente. O script nunca cria usuários, senhas, logins de registry, arquivos `.env` ou configurações de aplicação.

## O que o script altera

Ele atualiza os pacotes do Ubuntu sem reinicializar o sistema automaticamente, instala os pacotes `ca-certificates`, `curl`, `iproute2` (para validação de listeners) e `jq`, e remove apenas os pacotes conflitantes documentados pelo Docker se estiverem instalados. Ele não remove `/var/lib/docker` ou `/var/lib/containerd`.

O Docker é instalado a partir do repositório oficial APT HTTPS do Docker para o Ubuntu:

- key: `/etc/apt/keyrings/docker.asc`
- source: `/etc/apt/sources.list.d/docker.sources`
- repositório: `https://download.docker.com/linux/ubuntu`, usando a versão do Ubuntu (suite) e a arquitetura APT detectadas no host, no componente `stable`

Os pacotes instalados são `docker-ce`, `docker-ce-cli`, `containerd.io`, `docker-buildx-plugin` e `docker-compose-plugin`. Ele habilita e inicia o serviço docker do systemd.

O script adiciona o usuário administrativo selecionado ao grupo `docker` apenas se necessário. **O grupo `docker` concede privilégios equivalentes ao root.** Faça logout e reconecte-se após a primeira execução (ou execute `newgrp docker`) antes de testar o Docker como usuário não-root.

O Docker permanece configurado para o seu Unix socket padrão. Este provisionamento não configura a API TCP remota do Docker, não abre portas (incluindo 80, 443, 8080, 2375 ou 2376) e não altera a rede OCI, UFW, AppArmor, iptables, SSH ou autenticação de registry.

## Logging e futuros diretórios

O arquivo `/etc/docker/daemon.json` é criado ou mesclado (merged) de forma segura para utilizar o driver de log `json-file` com as opções `max-size` em `10m` e `max-file` em `3`. Configurações válidas preexistentes no nível superior e em `log-opts` são preservadas. Um driver de log existente diferente de `json-file`, um JSON inválido, opções de `log-opts` inválidas ou uma API Docker TCP serão rejeitados em vez de sobrescritos. Caso o arquivo preexistente seja alterado, um backup dele é criado no mesmo local com o padrão `daemon.json.backup.<timestamp UTC>.<pid>`. A configuração candidata é validada via `dockerd --validate` antes da substituição; o Docker só é reiniciado quando a configuração final do daemon for de fato alterada. Os objetos JSON são comparados de forma normalizada e ordenada, portanto, diferenças inofensivas de formatação ou ordem de chaves preservarão o conteúdo em bytes do arquivo existente e não gerarão backup nem reinicialização do Docker.

Os diretórios gerenciados pelo Docker (`/etc/apt/keyrings`, `/etc/apt/sources.list.d` e `/etc/docker`) têm sua propriedade forçada como `root:root` e modo `0755`. Seus respectivos arquivos gerenciados são forçados como `root:root` e modo `0644`. Metadados incorretos são corrigidos diretamente (in place) sem a necessidade de reescrever conteúdos inalterados; uma correção que mude apenas metadados no `daemon.json` não cria backup nem reinicia o Docker. Nenhuma operação de propriedade ou permissão recursiva é executada.

As configurações de log do daemon se aplicam automaticamente apenas aos containers criados após a mudança. Containers existentes devem ser recriados através de uma operação posterior de deployment explicitamente autorizada para receber as novas configurações de log.

Os seguintes diretórios vazios para uso futuro são criados com o proprietário e grupo do usuário administrativo selecionado e modo `0750`:

| Diretório | Objetivo futuro |
| --- | --- |
| `/opt/energiai` | diretório pai restrito para futuros recursos de deployment |
| `/opt/energiai/config` | configurações de runtime (não sensíveis) fornecidas posteriormente |
| `/opt/energiai/logs` | logs de aplicação gerenciados pelo host |
| `/opt/energiai/data` | dados persistentes futuros da aplicação |

Nenhuma configuração, segredo (secret), dataset, modelo, artefato ou credencial provisória (placeholder) é colocada nestes diretórios. Um deployment posterior pode refinar a propriedade dos diretórios para UIDs/GIDs específicos de containers. Diretórios existentes não vazios que possuam propriedade ou permissões incompatíveis são intencionalmente ignorados e fazem o script parar.

## Validação

O script valida a plataforma, as versões exatas de pacotes instalados contra os metadados do repositório Docker, a configuração do daemon, o estado do serviço, CLI, Compose, Buildx, a segurança dos listeners TCP, o estado dos diretórios e o container `hello-world` executado como root. Ele rejeita hosts TCP em `daemon.json`, padrões legados do Docker, configurações de drop-in/unit do systemd para o Docker, propriedades ativas do systemd e a linha de comando de execução do `dockerd`. Ele também falha se um listener TCP do `dockerd` for encontrado, incluindo listeners em portas não padrão; as portas 2375 e 2376 são verificadas explicitamente como portas proibidas para listeners do Docker.

Na instância, estes comandos são úteis para verificação:

```bash
cat /etc/os-release
uname -m
dpkg --print-architecture
docker version
docker compose version
docker buildx version
docker info
systemctl is-active docker
systemctl is-enabled docker
sudo dockerd --validate --config-file=/etc/docker/daemon.json
docker run --rm hello-world
```

Após reconectar-se como usuário administrativo, realize o teste rápido (smoke test) sem root:

```bash
docker run --rm hello-world
```

Execute o script de provisionamento uma segunda vez para verificar a idempotência. Ele deve manter apenas uma fonte APT, não duplicar a associação de grupos, manter configurações válidas preexistentes do daemon que não tenham relação com logs, não alterar diretórios que já contenham arquivos e evitar a regravação do daemon ou reinicialização do Docker quando a configuração efetiva estiver inalterada.

## Upgrade

Revise as alterações de pacotes disponíveis e utilize a manutenção padrão do APT no host:

```bash
sudo apt-get update
apt-cache policy docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo apt-get upgrade
```

Execute novamente o script de provisionamento em seguida para confirmar o repositório, configuração, permissões e testes de validação. Revise o marcador de reinicialização do Ubuntu e agende um reboot na janela de manutenção se necessário; o script nunca reinicia o sistema de forma automática.

## Troubleshooting

- **SO ou arquitetura não suportada:** utilize a instância Ubuntu 24.04 `x86_64/amd64` da Issue #104. Não altere o Terraform para selecionar ARM.
- **Erro de APT ou repositório:** verifique a conectividade de DNS/HTTPS, o relógio do sistema, os caminhos das chaves e fontes do repositório, e execute `sudo apt-get update`. Não utilize `curl | sh` ou o pacote legado `docker.io` do Ubuntu.
- **Docker falha ao iniciar:** inspecione `systemctl status docker` e `journalctl -u docker --no-pager`; valide o arquivo do daemon com o comando citado anteriormente e restaure um backup conhecido apenas após revisão.
- **Permissão negada (Permission denied) como usuário não-root:** reconecte-se após as alterações de associação de grupo ou utilize `newgrp docker`; verifique com `id -nG`.
- **`daemon.json` inválido ou driver de log incompatível:** corrija o JSON ou decida explicitamente como um driver preexistente diferente de `json-file` deve ser migrado. O script não o substituirá de forma automática.
- **Reboot necessário (Reboot required):** inspecione `/var/run/reboot-required` e agende uma reinicialização deliberada. Não presuma que o provisionamento do Docker reiniciou o host.

## Rollback e desinstalação

Revise cada comando e backup antes de executá-los. Defina `admin_user` com o nome do usuário administrativo selecionado durante o provisionamento. O rollback de pacotes seguro e não destrutivo a seguir preserva as imagens, volumes, containers do Docker, o estado do containerd e todo o conteúdo em `/opt/energiai`. Cada verificação condicional (guard) pula apenas recursos sabidamente ausentes; falhas inesperadas de remoção ainda serão reportadas.

```bash
admin_user=ubuntu

if systemctl cat docker >/dev/null 2>&1; then
  sudo systemctl disable --now docker
fi
if id -nG "${admin_user}" | tr ' ' '\n' | grep -Fxq docker; then
  sudo gpasswd -d "${admin_user}" docker
fi
sudo apt-get purge docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin docker-ce-rootless-extras

for managed_file in /etc/apt/sources.list.d/docker.sources /etc/apt/keyrings/docker.asc; do
  if [[ -L "${managed_file}" || ( -e "${managed_file}" && ! -f "${managed_file}" ) ]]; then
    printf 'Recusando remover tipo de caminho inesperado: %s\n' "${managed_file}" >&2
    exit 1
  fi
  if [[ -f "${managed_file}" ]]; then
    sudo rm -- "${managed_file}"
  fi
done

# Se existir um backup regular revisado, restaure-o deliberadamente, por exemplo:
# sudo install -o root -g root -m 0644 /etc/docker/daemon.json.backup.<timestamp>.<pid> /etc/docker/daemon.json
sudo apt-get update
```

Se nenhum backup do daemon estiver disponível, mantenha a configuração atual do daemon em execução e revise-a manualmente; não crie ou remova um arquivo de configuração sem critério. A remoção da fonte/chave do APT e a restauração do backup do daemon são operações manuais revisadas, pois alteram a configuração do host.

**Exclusão destrutiva de dados (manual e normalmente desnecessária):** deletar `/var/lib/docker`, `/var/lib/containerd` ou `/opt/energiai` remove permanentemente imagens, containers, volumes, estado de runtime ou futuros dados de aplicação. Esta issue nunca realiza essa deleção; execute qualquer ação desse tipo apenas após backup explícito e revisão de perda de dados.

## Fora de escopo (Out of scope)

Este trabalho não modifica o Terraform, OCI Compute/VCN/subnet/NSG/security lists, rotas, regras de firewall, autenticação SSH, código da aplicação, CI, bancos de dados, registries, DNS, TLS, proxies reversos ou observabilidade. Ele não faz deploy nem executa o backend, frontend, FastAPI, banco de dados ou qualquer container de aplicação do EnergiAI.
