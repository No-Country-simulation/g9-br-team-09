#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api/v1}"
curl_options=(
  --fail
  --silent
  --show-error
  --connect-timeout 10
  --max-time 30
)

for command in curl jq; do
  command -v "$command" >/dev/null || {
    echo "Dependência ausente: $command" >&2
    exit 1
  }
done

payload='{"consumo_kwh":420,"uso_horario_pico":true,"quantidade_equipamentos":10,"tipo_imovel":"CASA","horas_alto_consumo":8}'
response="$(
  curl "${curl_options[@]}" \
    --request POST \
    "$BASE_URL/analise-energetica" \
    --header 'Content-Type: application/json' \
    --data "$payload"
)"
created_id="$(jq --raw-output '.id // empty' <<<"$response")"

if [[ -z "$created_id" || ! "$created_id" =~ ^[0-9]+$ ]]; then
  echo "POST não retornou um ID válido." >&2
  exit 1
fi

read_response="$(
  curl "${curl_options[@]}" \
    "$BASE_URL/analise-energetica/$created_id"
)"

if ! jq --exit-status --argjson id "$created_id" '
  .id == $id
  and .consumo_kwh == 420
  and .uso_horario_pico == true
  and .quantidade_equipamentos == 10
  and .tipo_imovel == "CASA"
  and .horas_alto_consumo == 8
' <<<"$read_response" >/dev/null; then
  echo "GET retornou um payload inválido ou diferente da análise criada." >&2
  exit 1
fi

echo "Persistência verificada pela API. ID criado: $created_id"
