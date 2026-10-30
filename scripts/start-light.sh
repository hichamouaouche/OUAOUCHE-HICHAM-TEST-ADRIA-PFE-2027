#!/usr/bin/env bash
# Démarrage progressif pour machine limitée en mémoire : un service à la fois,
# sans l'assistant ni le consumer, et arrêt complet si la mémoire devient trop basse.
# Arrêt manuel : docker compose stop
set -u
cd "$(dirname "$0")/.." || exit 1

MIN_TO_START_MB=${MIN_TO_START_MB:-3000}
MIN_RUNNING_MB=${MIN_RUNNING_MB:-500}
export JAVA_HEAP_OPTS="-Xmx200m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

available() { free -m | awk '/^Mem:/ {print $7}'; }

abort() {
  echo "ARRÊT : $1"
  docker compose stop
  exit 1
}

if [ "$(available)" -lt "$MIN_TO_START_MB" ]; then
  echo "Mémoire disponible : $(available) Mo, il en faut $MIN_TO_START_MB."
  echo "Fermez le navigateur ou d'autres applications, puis relancez. Rien n'a été démarré."
  exit 1
fi

for service in postgres kafka keycloak discovery-service config-service account-service \
               transaction-service gateway-service bank-web; do
  echo "Démarrage de $service (mémoire disponible : $(available) Mo)"
  # Keycloak recréé pour réimporter le realm, client web reconstruit depuis bank-web/dist
  case "$service" in
    keycloak) options="--force-recreate" ;;
    bank-web) options="--build" ;;
    *) options="" ;;
  esac
  docker compose up -d --no-deps $options "$service" >/dev/null 2>&1 || abort "impossible de démarrer $service"
  for _ in $(seq 1 90); do
    [ "$(available)" -lt "$MIN_RUNNING_MB" ] && abort "mémoire sous $MIN_RUNNING_MB Mo"
    state=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
      "$(docker compose ps -q "$service")" 2>/dev/null)
    case "$state" in healthy|running) break ;; esac
    sleep 2
  done
  case "$state" in healthy|running) ;; *) abort "$service n'est pas prêt ($state)" ;; esac
done

echo "Prêt : http://localhost:4200 (mémoire disponible : $(available) Mo)"
echo "Patientez environ 45 secondes que la gateway découvre les services."
