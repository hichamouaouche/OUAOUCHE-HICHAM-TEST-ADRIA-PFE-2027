# Plateforme bancaire : microservices, IA et full-stack

Épreuve technique ADRIA / ENSET 2027. La plateforme gère des comptes et des transactions, publie des événements Kafka, propose un assistant conversationnel relié aux services par MCP, et inclut une expérimentation de détection de fraude en Python.

Les diagrammes de la partie B (architecture, classes, flux d'une transaction) sont dans [docs/conception.md](docs/conception.md).

## Composants

| Module | Port | Rôle |
|---|---|---|
| `account-service` | interne (8081) | Comptes : REST, GraphQL, MCP |
| `transaction-service` | interne (8082) | Transactions et virements : REST, GraphQL, MCP, Feign, Resilience4J, producteur Kafka |
| `transaction-consumer-service` | interne (8084) | Journalise les événements du topic `bank.transactions.created` |
| `assistant-service` | interne (8083) | Agent Spring AI, client MCP, REST et Telegram |
| `gateway-service` | 8080 | Point d'entrée unique, contrôle du JWT |
| `discovery-service` | 8761 | Annuaire Eureka |
| `config-service` | interne (8888) | Configuration partagée, dont `bank.transaction.max-amount=1000` |
| `bank-web` | 4200 | Client Angular |
| Keycloak | 8180 | Authentification et rôles |
| `fraud-detection/` | - | Script Python de la partie H |

Pile technique : Java 21, Spring Boot 4.0, Spring Cloud 2025.1, Spring AI 2.0, Kafka 4.1, PostgreSQL 17, Keycloak 26, Angular 22.

Dans `account-service` et `transaction-service`, les packages suivent la séparation demandée : `domain`, `repository`, `service`, `web/rest`, `web/graphql`, `mcp`. Les trois connecteurs appellent la même classe de service.

## Démarrage

Prérequis : Java 21, Maven, Node 24, Docker.

```bash
cp .env.example .env              # renseigner LLM_API_KEY
mvn -B package -DskipTests
(cd bank-web && npm ci && npm run build)
docker compose up -d --build --wait
```

Attendre ensuite environ 45 secondes que la gateway découvre les services dans Eureka, puis :

- ouvrir le client web : http://localhost:4200
- lancer le test de bout en bout : `bash scripts/e2e.sh`
- lancer les tests automatisés : `mvn test`

Comptes de démonstration (mot de passe identique à l'identifiant) :

| Identifiant | Rôles | Droits |
|---|---|---|
| `user1` | `USER` | Consulter, payer, virer, utiliser l'assistant |
| `admin1` | `USER`, `ADMIN` | Également créer et bloquer des comptes |

Ces identifiants et les secrets de `keycloak/bank-realm.json` servent uniquement à la démonstration locale.

## API (via la gateway)

| Méthode et chemin | Description | Rôle |
|---|---|---|
| `GET /api/accounts`, `GET /api/accounts/{id}` | Consulter les comptes | USER |
| `POST /api/accounts` | Créer un compte | ADMIN |
| `PATCH /api/accounts/{id}/status` | Bloquer ou activer | ADMIN |
| `GET /api/transactions?accountId=`, `GET /api/transactions/{id}` | Consulter les transactions | USER |
| `POST /api/transactions` | Enregistrer un paiement | USER |
| `POST /api/transfers` | Effectuer un virement | USER |
| `POST /accounts/graphql`, `POST /transactions/graphql` | GraphQL | USER, ADMIN pour les mutations de compte |
| `POST /api/assistant/chat` | Parler à l'assistant | USER |

Outils MCP, joignables uniquement sur le réseau interne avec le rôle `MCP_CLIENT` :

- `account-service` : `list_accounts`, `get_account`
- `transaction-service` : `list_transactions`, `create_transaction`, `transfer`

## Choix de conception

- **Type d'une transaction.** Une transaction est un paiement qui débite le compte. Le modèle minimal est complété par `type` (`DEBIT`, `CREDIT`) et `transferId` pour représenter les deux écritures d'un virement.
- **Solde insuffisant.** Une transaction qui dépasse le solde est refusée. Le sujet impose de consulter le solde sans préciser la règle : c'est l'interprétation retenue.
- **Ordre des opérations.** La transaction est insérée, le solde est mis à jour, puis la base valide. Si le débit échoue, l'insertion est annulée et aucun événement n'est émis.
- **Rejeu sans double débit.** Les mouvements portent un `operationId` que `account-service` dédoublonne. Le Retry de Resilience4J peut donc les rejouer sans risque.
- **Concurrence.** Un verrou pessimiste sur le compte empêche deux débits simultanés de passer sous zéro.
- **Compensation.** Si le crédit d'un virement échoue après le débit, la source est recréditée.
- **Identités de service.** `transaction-service` et l'assistant utilisent leur propre jeton `client_credentials`. Les routes `/internal/**` exigent le rôle `INTERNAL` et ne sont pas routées par la gateway : un utilisateur ne peut pas créditer son compte lui-même.
- **Droits de l'assistant.** Ses outils ne donnent pas plus de droits qu'un `USER`. Il demande confirmation avant toute opération qui modifie un solde. Cette confirmation repose sur le LLM : elle ne remplace pas un contrôle côté serveur.
- **MCP sans état.** Les serveurs MCP sont en mode `STATELESS`, car en mode avec session l'assistant perdait ses outils au redémarrage d'un service.
- **Telegram.** Le bot ne répond qu'aux chats listés dans `TELEGRAM_ALLOWED_CHAT_IDS`.
- **Plafond.** `bank.transaction.max-amount` vient du `config-service` et peut être rechargé par `/actuator/refresh`.

## Limites connues

- **`balance` en `double`.** Ce type est imposé par le sujet mais expose à des erreurs d'arrondi. En production, il faudrait utiliser `BigDecimal`.
- **Cohérence entre services.** Si le débit expire côté client alors qu'il a réussi côté serveur et que les rejeux échouent, le solde est débité sans transaction enregistrée. Si la compensation d'un virement échoue, l'incident est seulement journalisé. Une saga serait nécessaire en production.
- **Événements Kafka.** La publication a lieu après la validation, sans outbox : une panne de Kafka à ce moment fait perdre l'événement, qui est alors seulement journalisé.
- **Isolation des clients.** Tout utilisateur `USER` voit tous les comptes, car aucun lien n'existe entre un utilisateur Keycloak et `customerReference`.
- **Mémoire de l'assistant.** Elle est conservée en mémoire vive et disparaît au redémarrage.
- **Client Telegram.** Il est écrit mais n'a pas été testé faute de token de bot. L'assistant a été testé par REST et depuis le client web.
- **Mode Keycloak.** `start-dev` et les secrets par défaut conviennent seulement à un usage local.

## Partie H : détection de fraude

```bash
cd fraud-detection
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python fraud_detection.py
```

Le script lit `bank_transactions.csv` et écrit `results/metrics.json`.

**Données.** 5 000 transactions, dont 257 fraudes (5,14 %). Valeurs manquantes : `country` 66, `merchant_category` 84, `average_amount_last_7d` 75.

**Méthode.** Découpage stratifié 60 % entraînement, 20 % validation, 20 % test. Les identifiants sont exclus. Le pipeline (imputation, encodage one-hot, normalisation) est ajusté uniquement sur l'entraînement. Le modèle est une régression logistique avec `class_weight="balanced"`. Le seuil est choisi sur la validation en maximisant le F1, jamais sur le test.

**Résultats sur le test (1 000 transactions, 51 fraudes).**

| Seuil | Accuracy | Précision fraude | Rappel fraude | F1 | Faux positifs | Fraudes manquées |
|---|---|---|---|---|---|---|
| 0,50 | 0,729 | 0,118 | 0,667 | 0,201 | 254 | 17 |
| 0,71 (validation) | 0,901 | 0,176 | 0,255 | 0,208 | 61 | 38 |

L'average precision est de 0,187, soit environ 3,6 fois le taux de base de 0,051. Le modèle apporte un signal réel mais reste faible : il ne suffirait pas à bloquer seul des transactions.

**1. Pourquoi l'accuracy seule est-elle insuffisante ?** Un modèle qui déclare tout légitime obtient ici 94,9 % d'accuracy sans détecter aucune fraude. Sur ce test, le seuil 0,50 a une accuracy plus basse (72,9 %) que ce modèle trivial, tout en détectant deux tiers des fraudes.

**2. Quel compromis entre faux positifs et fraudes non détectées ?** Un faux positif bloque un client légitime et mobilise des analystes. Une fraude manquée cause une perte financière directe. Réduire l'un augmente généralement l'autre : de 0,71 à 0,50, les fraudes manquées passent de 38 à 17, mais les faux positifs de 61 à 254. Le bon réglage dépend du coût relatif des deux erreurs.

**3. Quel est l'effet d'une diminution du seuil ?** Davantage de transactions sont signalées. Le rappel augmente et les fraudes manquées diminuent, mais les faux positifs augmentent et la précision baisse généralement.

**4. Pourquoi une validation temporelle sur des données réelles ?** Les comportements de fraude évoluent et le modèle sert toujours à prédire le futur. Une séparation aléatoire mélange passé et futur, ce qui donne une estimation trop optimiste. Le jeu fourni ne contient qu'une heure de la journée, sans date : la séparation temporelle n'y est pas applicable.

## Partie I : DevOps

Chaque service possède un `Dockerfile`, `docker-compose.yml` lance l'ensemble, et `.github/workflows/ci.yml` enchaîne compilation, tests, construction des images, publication sur GHCR et une étape de déploiement schématique.

**Secrets et configuration.** La configuration varie selon l'environnement sans être sensible (URL, plafond, délais) : elle peut être versionnée et servie par le `config-service`. Les secrets (clé LLM, mots de passe, secrets OAuth2) ne sont jamais versionnés : ici ils passent par un `.env` ignoré par Git et par les secrets GitHub, et en production par un coffre comme Vault ou les secrets Kubernetes, avec rotation et moindre privilège.

**Contrôles de santé.**
- *Démarrage (startup)* : l'application a-t-elle fini de démarrer ? Évite de la tuer pendant une initialisation lente.
- *Disponibilité (readiness)* : peut-elle recevoir du trafic maintenant ? Sinon, on la retire du routage sans la redémarrer.
- *Vitalité (liveness)* : est-elle bloquée ? Sinon, on la redémarre. Ce contrôle ne doit pas dépendre d'un service externe, pour éviter des redémarrages en cascade.

Actuator expose `/actuator/health/readiness` et `/actuator/health/liveness` ; Docker Compose utilise le premier.

**Logs, métriques, traces.** Les logs décrivent chaque événement et gagnent à être structurés, centralisés et sans donnée sensible. Les métriques (latence, taux d'erreur, état du circuit) servent aux tableaux de bord et aux alertes, avec Prometheus et Grafana par exemple. Les traces distribuées, avec OpenTelemetry par exemple, suivent une requête à travers les services et localisent le maillon lent. Dans ce projet, seuls les logs et les points d'accès Actuator sont en place : aucune chaîne de traces ni tableau de bord n'est déployé.

**Rollback.** Chaque image est taguée avec le SHA du commit, ce qui rend les versions immuables. Si les contrôles de santé échouent après un déploiement, on redéploie le SHA précédent. Un déploiement progressif (canary ou blue/green) limite l'impact. Les migrations de base doivent rester rétrocompatibles, sinon l'ancienne version ne pourrait plus lire le schéma.
