# Backlog et User Stories

Sujet A du binôme 3 : **Supervision et endpoint `/health` : Eureka, Spring Boot Actuator, états UP/DOWN**.
Priorité MoSCoW : M (Must), S (Should), C (Could). Effort en points.

| ID | User story | Prio. | Pts | Critères d'acceptation | Vérifié par |
|----|-----------|:-----:|:---:|------------------------|-------------|
| US01 | En tant qu'exploitant, je veux qu'un service expose `/actuator/health` pour connaître son état. | M | 2 | 200 + `UP` si sain ; 503 + `DOWN` sinon | `SanteCatalogueTests` |
| US02 | En tant que service, je veux m'enregistrer dans Eureka pour être trouvé sans adresse en dur. | M | 3 | L'instance apparaît avec son statut ; la passerelle route par nom | `verifier.py` |
| US03 | En tant qu'exploitant, je veux qu'Eureka reflète la santé réelle et pas seulement le battement de cœur. | M | 3 | Base coupée : Eureka passe à DOWN | `mesurer.py` (profils `rapide` et `sans-sante`) |
| US04 | En tant que client, je ne veux plus être dirigé vers une instance DOWN. | M | 3 | Aucune requête servie par l'instance DOWN après propagation | `verifier.py` |
| US05 | En tant qu'exploitant, je veux distinguer vivacité et préparation. | M | 2 | Panne : `/health` 503, `/health/liveness` 200 | `SanteCatalogueTests` |
| US06 | En tant qu'exploitant, je veux un état DEGRADE pour les pannes non bloquantes. | S | 3 | Rupture de stock : HTTP 200, `DEGRADE`, instance toujours routée | `StatutsTests`, `SanteCatalogueTests` |
| US07 | En tant qu'exploitant, je veux une vue unique croisant Eureka et Actuator. | M | 5 | Écart signalé quand les deux sources se contredisent | `SuperviseurTests` |
| US08 | En tant qu'exploitant, je veux l'historique des changements d'état. | M | 3 | Un événement par transition, historique borné | `SuperviseurTests` |
| US09 | En tant qu'exploitant, je veux voir un service attendu qui n'a jamais démarré. | S | 2 | Service listé DOWN sans instance | `SuperviseurTests` |
| US10 | En tant qu'exploitant, je veux éviter les fausses alertes. | S | 2 | INJOIGNABLE après 2 échecs consécutifs | `SuperviseurTests` |
| US11 | En tant qu'exploitant, je veux un tableau de bord web. | S | 5 | Rafraîchi toutes les 2 s, lisible sans la couleur | `App.test.jsx` |
| US12 | En tant qu'exploitant, je veux des métriques et des alertes. | S | 3 | Prometheus découvre les cibles via Eureka ; 5 règles ; tableau Grafana | Démonstration |
| US13 | En tant que responsable sécurité, je veux qu'Actuator ne divulgue rien. | M | 3 | Détails réservés ; `env` et `heapdump` fermés ; Eureka authentifié | `SanteCatalogueTests`, `EurekaServerApplicationTests` |
| US14 | En tant que démonstrateur, je veux provoquer une panne à la demande. | S | 3 | Rôle ADMIN exigé ; désactivé par défaut | `CommandeTests`, `SupervisionApiTests` |
| US15 | En tant que service, je ne veux pas tomber parce qu'une dépendance tombe. | S | 2 | Catalogue absent : commande `DEGRADE`, readiness 200 | `CommandeTests` |
| US16 | En tant que développeur, je veux démarrer toute la pile en deux commandes. | M | 3 | `init-env.sh` puis `docker compose up` | README, CI |
| US17 | En tant que développeur, je veux une intégration continue sur nos deux OS. | S | 3 | Tests sous Linux, macOS, Windows ; essai de bout en bout | `.github/workflows/ci.yml` |

## Écarté du périmètre

| Idée | Raison |
|------|--------|
| Cluster Eureka à plusieurs nœuds | Mémoire du poste ; limite assumée dans le rapport |
| Centralisation des logs (ELK) | C'est notre sujet B |
| Tracing distribué, rate limiting | Sujets d'autres binômes |
| Envoi d'alertes (courriel, SMS) | Les règles Prometheus existent ; brancher Alertmanager est la suite logique |
