#!/usr/bin/env bash

set -euo pipefail

core=false
notification=false
worker=false

selecionar_todos() {
  core=true
  notification=true
  worker=true
}

if (( $# > 0 )); then
  arquivos_alterados=("$@")
elif [[ "${GITHUB_EVENT_NAME:-}" != "pull_request" || "${BASE_REF:-}" == "main" ]]; then
  selecionar_todos
  arquivos_alterados=()
else
  diferencas=$(git diff --name-only "${BASE_SHA:?BASE_SHA não informado}" "${HEAD_SHA:?HEAD_SHA não informado}" --)
  if [[ -n "$diferencas" ]]; then
    mapfile -t arquivos_alterados <<< "$diferencas"
  else
    arquivos_alterados=()
  fi
fi

for arquivo in "${arquivos_alterados[@]}"; do
  case "$arquivo" in
    docs/**|README.md|AGENTS.md|*.md)
      ;;

    pom.xml|mvnw|mvnw.cmd|.mvn/**|.github/workflows/**|.github/scripts/**)
      selecionar_todos
      ;;

    infra/keycloak/**)
      core=true
      ;;

    worker/src/test/resources/contratos/**)
      core=true
      worker=true
      ;;

    core/src/main/java/br/edu/ifrn/wonder/core/messaging/**)
      selecionar_todos
      ;;

    core/src/main/java/br/edu/ifrn/wonder/core/dto/AgendamentoNotificacaoEvent.java|\
    core/src/main/java/br/edu/ifrn/wonder/core/dto/ProviderRequestCreated.java|\
    core/src/main/java/br/edu/ifrn/wonder/core/dto/ProviderRequestReviewed.java)
      core=true
      notification=true
      ;;

    core/src/main/java/br/edu/ifrn/wonder/core/dto/ExportacaoSolicitada.java|\
    core/src/main/java/br/edu/ifrn/wonder/core/dto/UsuariosAdminExportacaoResponse.java|\
    core/src/main/java/br/edu/ifrn/wonder/core/dto/PrestadoresAdminExportacaoResponse.java|\
    core/src/main/java/br/edu/ifrn/wonder/core/dto/DashboardAgendamentosResponse.java)
      core=true
      worker=true
      ;;

    worker/src/main/java/br/edu/ifrn/wonder/worker/dto/ExportacaoConcluida.java|\
    worker/src/main/java/br/edu/ifrn/wonder/worker/messaging/ExportacaoPublisher.java)
      notification=true
      worker=true
      ;;

    core/**)
      core=true
      ;;

    notification/**)
      notification=true
      ;;

    worker/**)
      worker=true
      ;;

    *)
      selecionar_todos
      ;;
  esac
done

saida="${GITHUB_OUTPUT:-/dev/stdout}"
{
  echo "core=$core"
  echo "notification=$notification"
  echo "worker=$worker"
} >> "$saida"

echo "Módulos selecionados: core=$core notification=$notification worker=$worker"
