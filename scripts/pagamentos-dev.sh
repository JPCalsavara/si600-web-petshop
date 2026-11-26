#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .envpagamentos ]]; then
  echo 'Copie .envpagamentos.example para .envpagamentos e preencha os campos obrigatórios.' >&2
  exit 1
fi
if command -v podman >/dev/null; then
  compose=(podman compose -f docker-compose.yml)
else
  compose=(docker compose -f docker-compose.yml)
fi
"${compose[@]}" up -d postgres
ready=false
for attempt in {1..30}; do
  if "${compose[@]}" exec -T postgres pg_isready -U eventos_user -d eventos_db >/dev/null 2>&1; then ready=true; break; fi
  sleep 2
done
if [[ "$ready" != true ]]; then echo 'PostgreSQL não ficou pronto em 60 segundos.' >&2; exit 1; fi
# Apenas PostgreSQL é gerenciado pelo Compose. Nenhum volume é apagado.
pids=()
cleanup() { for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done; }
trap cleanup EXIT
trap 'exit 130' INT TERM
(cd backend && exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--server.address=127.0.0.1) &
pids+=("$!")
(cd frontend && exec npm run dev -- --host 127.0.0.1) &
pids+=("$!")
echo 'Pagamentos: http://localhost:5173/dev/pagamentos (aguarde a inicialização).'
wait -n "${pids[@]}"
