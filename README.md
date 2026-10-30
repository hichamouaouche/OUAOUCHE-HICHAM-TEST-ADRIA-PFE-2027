# Plateforme bancaire : microservices, IA et full-stack

**Réalisé par :** Hicham OUAOUCHE  
**Master :** 2-SDIA

Épreuve technique ADRIA / ENSET 2027. La plateforme gère des comptes et des transactions, publie des événements Kafka, propose un assistant conversationnel relié aux services par MCP, et inclut une expérimentation de détection de fraude en Python.

## Sommaire

- [Synthèse](#synthèse)
- [Démarrage](#démarrage)
- [Partie A : préparation et consignes](#partie-a--préparation-et-consignes)
- [Partie B : conception et architecture](#partie-b--conception-et-architecture)
- [Partie C : micro-services fonctionnels](#partie-c--micro-services-fonctionnels)
- [Partie D : micro-services techniques](#partie-d--micro-services-techniques)
- [Partie E : chatbot agentique](#partie-e--chatbot-agentique)
- [Partie F : communication asynchrone avec Kafka](#partie-f--communication-asynchrone-avec-kafka)
- [Partie G : client web](#partie-g--client-web)
- [Partie H : détection de fraude](#partie-h--détection-de-fraude)
- [Partie I : DevOps](#partie-i--devops)
- [Choix de conception](#choix-de-conception)
- [Extraits du code source](#extraits-du-code-source)
- [Limites connues](#limites-connues)
- [Conclusion](#conclusion)

## Synthèse

| Partie | Résultat |
|---|---|
| B - Conception | Schéma d'architecture, diagramme de classes, flux d'une transaction |
| C - Services métier | REST, GraphQL et MCP sur la même couche service ; OpenFeign et Resilience4J testés en panne réelle |
| D - Services techniques | Gateway, Eureka et Config (`bank.transaction.max-amount=1000`) opérationnels |
| E - Chatbot agentique | Agent Spring AI relié aux serveurs MCP, testé par REST et depuis le web. Client Telegram écrit, **non testé** |
| F - Kafka | Événements publiés après validation, clé `accountId`, consumer de journalisation |
| G - Client | Client Angular connecté à Keycloak, avec tableau de bord |
| H - Fraude | Script exécutable : average precision de 0,187 pour un taux de base de 0,051 |
| I - DevOps | Dockerfiles, Docker Compose et pipeline GitHub Actions |

Les 22 tests automatisés passent, et la plateforme a été testée de bout en bout à travers la gateway avec Docker Compose.

### Composants

| Module | Port | Rôle |
|---|---|---|
| `account-service` | interne (8081) | Comptes : REST, GraphQL, MCP |
| `transaction-service` | interne (8082) | Transactions et virements : REST, GraphQL, MCP, Feign, Resilience4J, producteur Kafka |
| `transaction-consumer-service` | interne (8084) | Journalise les événements du topic `bank.transactions.created` |
| `assistant-service` | interne (8083) | Agent Spring AI, client MCP, REST et Telegram |
| `gateway-service` | 8080 | Point d'entrée unique, contrôle du JWT |
| `discovery-service` | 8761 | Annuaire Eureka |
| `config-service` | interne (8888) | Configuration partagée |
| `bank-web` | 4200 | Client Angular |
| Keycloak | 8180 | Authentification et rôles |
| `fraud-detection/` | - | Script Python de la partie H |

Pile technique : Java 21, Spring Boot 4.0, Spring Cloud 2025.1, Spring AI 2.0, Kafka 4.1, PostgreSQL 17, Keycloak 26, Angular 22.

## Démarrage

Prérequis : Java 21, Maven, Node 24, Docker. La plateforme complète demande beaucoup de mémoire : prévoyez environ 5 Go libres.

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

Sur une machine limitée en mémoire, `bash scripts/start-light.sh` démarre les services un par un, sans l'assistant ni le consumer, et arrête tout si la mémoire devient trop basse. Dans ce mode, la page Assistant ne répond pas. Arrêt : `docker compose stop`.

Comptes de démonstration (mot de passe identique à l'identifiant) :

| Identifiant | Rôles | Droits |
|---|---|---|
| `user1` | `USER` | Consulter, payer, virer, utiliser l'assistant |
| `admin1` | `USER`, `ADMIN` | Également créer et bloquer des comptes |
| `hicham.ouaouche` | `USER`, `ADMIN` | Comme `admin1` |

Ces identifiants et les secrets de `keycloak/bank-realm.json` servent uniquement à la démonstration locale.

## Partie A : préparation et consignes

| Consigne | Mise en œuvre |
|---|---|
| Dépôt GitHub | `OUAOUCHE-HICHAM-TEST-ADRIA-PFE-2027` |
| Projet Maven avec un module par micro-service | Parent `bank-platform` et 7 modules |
| Commits réguliers | Un commit par partie, à intervalles d'environ 10 minutes |
| Captures des tests | Dossier [docs/captures](docs/captures), reprises ci-dessous |

## Partie B : conception et architecture

Les sources Mermaid des diagrammes sont dans [docs/conception.md](docs/conception.md).

### Schéma d'architecture

Le navigateur et les clients REST passent par la gateway. Chaque service métier a sa base. L'assistant accède aux données uniquement par MCP.

![Schéma d'architecture](docs/captures/00-architecture.png)

### Diagramme de classes

![Diagramme de classes](docs/captures/00-classes.png)

### Flux d'une nouvelle transaction

La transaction est insérée, le solde est mis à jour, puis la base valide. Un refus ou une panne annule l'insertion et aucun événement n'est émis.

![Flux d'une nouvelle transaction](docs/captures/00-sequence.png)

## Partie C : micro-services fonctionnels

`account-service` et `transaction-service` suivent les packages `domain`, `repository`, `service`, `web/rest`, `web/graphql` et `mcp`. Les trois connecteurs appellent la même classe de service, donc les règles métier s'appliquent partout.

### API via la gateway

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

### 1. Account-Service

Il gère la consultation, la création et le blocage des comptes. Les mouvements de solde sont réservés au rôle `INTERNAL`, sur des routes que la gateway n'expose pas.

![Contrôle des accès et création de comptes](docs/captures/06-securite-rest.png)

### 2. Transaction-Service

Il enregistre et consulte les transactions. Toutes les règles métier ont été vérifiées :

| Cas testé | Résultat attendu | HTTP obtenu |
|---|---|---|
| Transaction valide de 250 EUR | Solde de 2000 à 1750 | 201 |
| Montant négatif | Refus | 422 |
| Montant de 1000,01 (plafond de 1000) | Refus | 422 |
| Devise différente de celle du compte | Refus | 422 |
| Compte inexistant | Refus | 404 |
| Solde insuffisant | Refus | 422 |
| Compte bloqué | Refus | 422 |
| Virement de 300 EUR | Source débitée, destination créditée | 201 |
| Virement vers une autre devise | Refus | 422 |

![Transaction valide et règles métier](docs/captures/07-regles-metier.png)

![Virement entre deux comptes](docs/captures/08-virement.png)

### Connecteurs GraphQL et MCP

![Requêtes et mutation GraphQL](docs/captures/09-graphql.png)

Les serveurs MCP exposent cinq outils, réservés au rôle `MCP_CLIENT` et joignables uniquement sur le réseau interne. Une requête sans jeton reçoit un 401.

![Outils exposés par les serveurs MCP](docs/captures/10-mcp.png)

### 3. OpenFeign et Resilience4J

`transaction-service` appelle `account-service` par OpenFeign, avec un Retry de 3 tentatives et un CircuitBreaker. Les refus métier ne comptent pas comme des pannes.

Test réalisé en arrêtant réellement `account-service` :

- les premières requêtes répondent 503 après les tentatives, en 6,7 s puis 4,7 s ;
- le circuit passe à `OPEN` et les requêtes suivantes répondent 503 en 19 ms, sans appel réseau ;
- après le redémarrage du service, une transaction est de nouveau acceptée au bout de 60 s.

![Comportement pendant et après la panne](docs/captures/12-resilience.png)

## Partie D : micro-services techniques

### 1. Spring Cloud Gateway

La gateway est le point d'entrée unique. Elle contrôle le JWT Keycloak et route vers les services par leur nom Eureka. Les tests de la partie C passent tous par elle.

### 2. Eureka Discovery

![Services enregistrés dans Eureka](docs/captures/05-eureka.png)

### 3. Config-Service

Le paramètre `bank.transaction.max-amount=1000` est servi par le config-service. Le refus d'un montant de 1000,01 montre que `transaction-service` l'applique bien.

![Paramètre servi par le config-service](docs/captures/13-config.png)

## Partie E : chatbot agentique

`assistant-service` utilise Spring AI avec le modèle `gpt-oss:20b` d'Ollama Cloud, par son API compatible OpenAI. Les outils sont découverts sur les serveurs MCP des deux services métier :

- `account-service` : `list_accounts`, `get_account`
- `transaction-service` : `list_transactions`, `create_transaction`, `transfer`

L'agent conserve le contexte de chaque conversation et demande confirmation avant de modifier un solde. Le test couvre une lecture de solde, une demande de virement, puis sa confirmation : le solde du compte destinataire passe de 400 à 410.

![Assistant testé avec un client REST](docs/captures/11-assistant-rest.png)

![Assistant utilisé depuis le client web](docs/captures/04-web-assistant.png)

Le client Telegram est écrit et limité à une liste de chats autorisés, mais il n'a pas été testé faute de token de bot.

## Partie F : communication asynchrone avec Kafka

- Après validation de la transaction en base, un événement est publié dans `bank.transactions.created`, au format demandé.
- `accountId` sert de clé : les événements d'un même compte arrivent dans l'ordre.
- `transaction-consumer-service` consomme le topic et journalise chaque événement, sans accès aux données bancaires.

![Événements journalisés par le consumer](docs/captures/14-kafka.png)

## Partie G : client web

Le client Angular se connecte à Keycloak (flux PKCE) et ajoute le jeton aux appels vers la gateway. Il couvre un tableau de bord, les comptes, les paiements, les virements et l'assistant. La création et le blocage de comptes n'apparaissent que pour un administrateur.

Le tableau de bord est la page d'accueil. Il affiche le nombre de comptes (total, actifs, bloqués), le nombre de transactions, le solde total par devise, les transactions par catégorie et les dernières transactions.

![Connexion par Keycloak](docs/captures/01-keycloak-login.png)

![Tableau de bord](docs/captures/1.png)

![Gestion des comptes](docs/captures/2.png)

![Paiements, virements et historique](docs/captures/3.png)

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

![Sortie du script de détection de fraude](docs/captures/15-fraude.png)

### Questions d'analyse

**1. Pourquoi l'accuracy seule est-elle insuffisante ?** Un modèle qui déclare tout légitime obtient ici 94,9 % d'accuracy sans détecter aucune fraude. Le seuil 0,50 a une accuracy plus basse (72,9 %) tout en détectant deux tiers des fraudes.

**2. Quel compromis entre faux positifs et fraudes non détectées ?** Un faux positif bloque un client légitime et mobilise des analystes. Une fraude manquée cause une perte financière directe. De 0,71 à 0,50, les fraudes manquées passent de 38 à 17, mais les faux positifs de 61 à 254. Le bon réglage dépend du coût relatif des deux erreurs.

**3. Quel est l'effet d'une diminution du seuil ?** Davantage de transactions sont signalées. Le rappel augmente et les fraudes manquées diminuent, mais les faux positifs augmentent et la précision baisse généralement.

**4. Pourquoi une validation temporelle sur des données réelles ?** Les comportements de fraude évoluent et le modèle sert toujours à prédire le futur. Une séparation aléatoire mélange passé et futur, ce qui donne une estimation trop optimiste. Le jeu fourni ne contient qu'une heure de la journée, sans date : la séparation temporelle n'y est pas applicable.

## Partie I : DevOps

Chaque service possède un `Dockerfile`, et `docker-compose.yml` lance l'ensemble avec des contrôles de santé et un ordre de démarrage.

![Conteneurs en fonctionnement](docs/captures/16-docker-compose.png)

`.github/workflows/ci.yml` enchaîne compilation, tests, construction des images, publication sur GHCR et une étape de déploiement schématique.

![Tests automatisés : 22 tests réussis](docs/captures/17-tests-maven.png)

**Secrets et configuration.** La configuration varie selon l'environnement sans être sensible (URL, plafond, délais) : elle peut être versionnée et servie par le `config-service`. Les secrets (clé LLM, mots de passe, secrets OAuth2) ne sont jamais versionnés : ici ils passent par un `.env` ignoré par Git et par les secrets GitHub, et en production par un coffre comme Vault ou les secrets Kubernetes, avec rotation et moindre privilège.

**Contrôles de santé.**

- *Démarrage (startup)* : l'application a-t-elle fini de démarrer ? Évite de la tuer pendant une initialisation lente.
- *Disponibilité (readiness)* : peut-elle recevoir du trafic maintenant ? Sinon, on la retire du routage sans la redémarrer.
- *Vitalité (liveness)* : est-elle bloquée ? Sinon, on la redémarre. Ce contrôle ne doit pas dépendre d'un service externe, pour éviter des redémarrages en cascade.

Actuator expose `/actuator/health/readiness` et `/actuator/health/liveness` ; Docker Compose utilise le premier.

**Logs, métriques, traces.** Les logs décrivent chaque événement et gagnent à être structurés, centralisés et sans donnée sensible. Les métriques (latence, taux d'erreur, état du circuit) servent aux tableaux de bord et aux alertes, avec Prometheus et Grafana par exemple. Les traces distribuées, avec OpenTelemetry par exemple, suivent une requête à travers les services et localisent le maillon lent. Dans ce projet, seuls les logs et les points d'accès Actuator sont en place.

**Rollback.** Chaque image est taguée avec le SHA du commit, ce qui rend les versions immuables. Si les contrôles de santé échouent après un déploiement, on redéploie le SHA précédent. Un déploiement progressif (canary ou blue/green) limite l'impact. Les migrations de base doivent rester rétrocompatibles, sinon l'ancienne version ne pourrait plus lire le schéma.

## Choix de conception

- **Type d'une transaction.** Une transaction est un paiement qui débite le compte. Le modèle minimal est complété par `type` (`DEBIT`, `CREDIT`) et `transferId` pour représenter les deux écritures d'un virement.
- **Solde insuffisant.** Une transaction qui dépasse le solde est refusée. Le sujet impose de consulter le solde sans préciser la règle : c'est l'interprétation retenue.
- **Rejeu sans double débit.** Les mouvements portent un `operationId` que `account-service` dédoublonne. Le Retry de Resilience4J peut donc les rejouer sans risque.
- **Concurrence.** Un verrou pessimiste sur le compte empêche deux débits simultanés de passer sous zéro.
- **Compensation.** Si le crédit d'un virement échoue après le débit, la source est recréditée.
- **Identités de service.** `transaction-service` et l'assistant utilisent leur propre jeton `client_credentials`. Les routes `/internal/**` exigent le rôle `INTERNAL` et ne sont pas routées par la gateway : un utilisateur ne peut pas créditer son compte lui-même.
- **Droits de l'assistant.** Ses outils ne donnent pas plus de droits qu'un `USER`. Sa demande de confirmation repose sur le LLM : elle ne remplace pas un contrôle côté serveur.
- **MCP sans état.** Les serveurs MCP sont en mode `STATELESS`, car en mode avec session l'assistant perdait ses outils au redémarrage d'un service.
- **Telegram.** Le bot ne répond qu'aux chats listés dans `TELEGRAM_ALLOWED_CHAT_IDS`.

## Extraits du code source

Connecteur MCP de `account-service`, qui appelle la même couche service que REST et GraphQL :

![Connecteur MCP de account-service, qui appelle la même couche service que REST et GraphQL](docs/captures/code-mcp.png)

Enregistrement d'une transaction : contrôles, insertion, débit, puis événement :

![Enregistrement d'une transaction : contrôles, insertion, débit, puis événement](docs/captures/code-transaction.png)

Mise à jour du solde : le verrou et le contrôle de l'`operationId` empêchent un double débit :

![Mise à jour du solde : le verrou et le contrôle de l'operationId empêchent un double débit](docs/captures/code-solde.png)

Appels à `account-service` protégés par Retry et CircuitBreaker :

![Appels à account-service protégés par Retry et CircuitBreaker](docs/captures/code-resilience.png)

Publication Kafka après validation, avec `accountId` comme clé :

![Publication Kafka après validation, avec accountId comme clé](docs/captures/code-kafka.png)

Agent : consignes, mémoire de conversation et outils découverts par MCP :

![Agent : consignes, mémoire de conversation et outils découverts par MCP](docs/captures/code-agent.png)

Pipeline de prétraitement et modèle de détection de fraude :

![Pipeline de prétraitement et modèle de détection de fraude](docs/captures/code-fraude.png)

## Limites connues

- **`balance` en `double`.** Ce type est imposé par le sujet mais expose à des erreurs d'arrondi. En production, il faudrait utiliser `BigDecimal`.
- **Cohérence entre services.** Si le débit réussit côté serveur alors que le client constate un échec après ses rejeux, le solde est débité sans transaction enregistrée. Si la compensation d'un virement échoue, l'incident est seulement journalisé. Une saga serait nécessaire en production.
- **Événements Kafka.** La publication a lieu après la validation, sans outbox : une panne de Kafka à ce moment fait perdre l'événement.
- **Isolation des clients.** Tout utilisateur `USER` voit tous les comptes, car aucun lien n'existe entre un utilisateur Keycloak et `customerReference`.
- **Mémoire de l'assistant.** Elle est conservée en mémoire vive et disparaît au redémarrage.
- **Client Telegram.** Il est écrit mais n'a pas été testé faute de token de bot.
- **Rechargement du plafond.** `/actuator/refresh` n'a pas été testé.
- **Pipeline GitHub Actions.** Son exécution sur GitHub n'a pas été vérifiée.
- **Mode Keycloak.** `start-dev` et les secrets par défaut conviennent seulement à un usage local.

## Conclusion

La plateforme répond aux parties B à I de l'épreuve : les deux services métier exposent REST, GraphQL et MCP sur une même couche service, la communication résiste à une panne réelle, les événements Kafka sont publiés après validation et l'agent IA réalise des opérations par MCP.

Trois évolutions seraient prioritaires pour une mise en production :

- fiabiliser la cohérence entre services avec une saga et un outbox Kafka ;
- lier chaque utilisateur Keycloak à ses comptes pour isoler les clients ;
- améliorer le modèle de fraude avec une validation temporelle sur des données datées.
