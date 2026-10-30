#!/usr/bin/env bash
# Test de bout en bout à travers la gateway, plateforme démarrée avec docker compose.
set -u
GW=http://localhost:8080
KC=http://localhost:8180/realms/bank/protocol/openid-connect/token

token() {
  curl -s "$KC" -d grant_type=password -d client_id=bank-web -d "username=$1" -d "password=$1" |
    python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])'
}
call() { # jeton méthode chemin [corps]
  curl -s -w '\n-> HTTP %{http_code}\n' -X "$2" "$GW$3" -H "Authorization: Bearer $1" \
    -H 'Content-Type: application/json' ${4:+-d "$4"}
}
field() { python3 -c "import json,sys; print(json.loads(sys.stdin.read().split('\n')[0])['$1'])"; }
title() { printf '\n=== %s\n' "$1"; }

ADMIN=$(token admin1)
USER=$(token user1)

title "Sans jeton : refus"
curl -s -o /dev/null -w '-> HTTP %{http_code}\n' "$GW/api/accounts"

title "Keycloak : un utilisateur standard ne peut pas créer de compte"
call "$USER" POST /api/accounts '{"customerReference":"X","currency":"EUR","initialBalance":1}'

title "REST : création de deux comptes EUR et d'un compte USD par un administrateur"
R=$(call "$ADMIN" POST /api/accounts '{"customerReference":"CUST-A","currency":"EUR","initialBalance":2000}'); echo "$R"
A=$(echo "$R" | field id)
R=$(call "$ADMIN" POST /api/accounts '{"customerReference":"CUST-B","currency":"EUR","initialBalance":100}'); echo "$R"
B=$(echo "$R" | field id)
R=$(call "$ADMIN" POST /api/accounts '{"customerReference":"CUST-C","currency":"USD","initialBalance":100}'); echo "$R"
C=$(echo "$R" | field id)

title "Transaction valide de 250 EUR : le solde passe de 2000 à 1750"
call "$USER" POST /api/transactions "{\"accountId\":\"$A\",\"amount\":250.00,\"currency\":\"EUR\",\"merchantCategory\":\"GROCERY\",\"country\":\"FR\"}"
call "$USER" GET "/api/accounts/$A"

title "Règle : montant négatif"
call "$USER" POST /api/transactions "{\"accountId\":\"$A\",\"amount\":-5,\"currency\":\"EUR\",\"merchantCategory\":\"GROCERY\",\"country\":\"FR\"}"
title "Règle : plafond de 1000 dépassé (valeur issue du config-service)"
call "$USER" POST /api/transactions "{\"accountId\":\"$A\",\"amount\":1000.01,\"currency\":\"EUR\",\"merchantCategory\":\"GROCERY\",\"country\":\"FR\"}"
title "Règle : devise différente de celle du compte"
call "$USER" POST /api/transactions "{\"accountId\":\"$A\",\"amount\":5,\"currency\":\"USD\",\"merchantCategory\":\"GROCERY\",\"country\":\"FR\"}"
title "Règle : compte inexistant"
call "$USER" POST /api/transactions "{\"accountId\":\"00000000-0000-0000-0000-000000000000\",\"amount\":5,\"currency\":\"EUR\",\"merchantCategory\":\"GROCERY\",\"country\":\"FR\"}"
title "Règle : solde insuffisant"
call "$USER" POST /api/transactions "{\"accountId\":\"$B\",\"amount\":500,\"currency\":\"EUR\",\"merchantCategory\":\"GROCERY\",\"country\":\"FR\"}"

title "Virement de 300 EUR de CUST-A vers CUST-B"
call "$USER" POST /api/transfers "{\"sourceAccountId\":\"$A\",\"targetAccountId\":\"$B\",\"amount\":300,\"currency\":\"EUR\",\"country\":\"FR\"}"
call "$USER" GET "/api/accounts/$A"
call "$USER" GET "/api/accounts/$B"
title "Virement vers un compte d'une autre devise : refus"
call "$USER" POST /api/transfers "{\"sourceAccountId\":\"$A\",\"targetAccountId\":\"$C\",\"amount\":10,\"currency\":\"EUR\",\"country\":\"FR\"}"

title "Règle : compte bloqué"
call "$ADMIN" PATCH "/api/accounts/$C/status" '{"status":"BLOCKED"}'
call "$USER" POST /api/transactions "{\"accountId\":\"$C\",\"amount\":5,\"currency\":\"USD\",\"merchantCategory\":\"GROCERY\",\"country\":\"FR\"}"

title "GraphQL account-service"
call "$USER" POST /accounts/graphql "{\"query\":\"{ account(id: \\\"$A\\\") { customerReference status balance currency } }\"}"
title "GraphQL transaction-service"
call "$USER" POST /transactions/graphql "{\"query\":\"{ transactions(accountId: \\\"$A\\\") { type amount merchantCategory createdAt } }\"}"
title "GraphQL : mutation de paiement"
call "$USER" POST /transactions/graphql "{\"query\":\"mutation { createTransaction(input: {accountId: \\\"$A\\\", amount: 12.5, currency: \\\"EUR\\\", merchantCategory: \\\"BOOKS\\\", country: \\\"FR\\\"}) { id amount type } }\"}"

title "Routes internes non exposées par la gateway"
call "$ADMIN" POST "/internal/accounts/$A/credit" '{"operationId":"00000000-0000-0000-0000-000000000001","amount":1000}'

title "Assistant IA via MCP : lecture"
call "$USER" POST /api/assistant/chat "{\"conversationId\":\"e2e\",\"message\":\"Quel est le solde et le statut du compte $B ?\"}"
title "Assistant IA via MCP : demande de virement (doit demander confirmation)"
call "$USER" POST /api/assistant/chat "{\"conversationId\":\"e2e\",\"message\":\"Fais un virement de 10 EUR du compte $A vers le compte $B, pays FR.\"}"
title "Assistant IA via MCP : confirmation"
call "$USER" POST /api/assistant/chat '{"conversationId":"e2e","message":"Oui, je confirme."}'
call "$USER" GET "/api/accounts/$B"

title "Kafka : événements journalisés par le consumer"
docker compose logs transaction-consumer-service --no-log-prefix 2>/dev/null | grep 'Événement reçu' | tail -4
