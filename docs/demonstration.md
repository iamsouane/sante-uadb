# Démonstration (18 minutes)

À faire **avant** : `docker compose up -d --wait`, puis `python3 scripts/verifier.py` (doit finir par « Tout est conforme »). Attendre ensuite **une minute** : l'essai met une instance en panne, et tant qu'elle n'est pas revenue dans les caches, l'étape 5:30 ne montrerait qu'une seule instance.
Ouvrir trois onglets : tableau de bord (connecté `admin`), console Eureka, Grafana. Un terminal à côté.

Chargement des mots de passe dans le terminal : `set -a; . ./.env; set +a`

| Temps | Étape | À montrer | À expliquer |
|:-----:|-------|-----------|-------------|
| 0:00 | **Contexte** | Schéma du README | Une instance peut être *lancée* sans être *en bonne santé*. Qui le sait, et en combien de temps ? |
| 2:00 | **L'endpoint** | `curl -i localhost:8090/actuator/health` puis `curl -u supervision:$SUPERVISION_PASSWORD localhost:8090/actuator/health` | Anonyme : statut seul. Authentifié : chaque composant. 200 ou 503 selon l'état. |
| 4:00 | **Eureka** | Console 8761 | Enregistrement, battement de 5 s, bail de 15 s (30 s et 90 s par défaut). |
| 5:30 | **Répartition** | `for i in 1 2 3 4; do curl -s -o /dev/null -D- localhost:8080/api/produits \| grep -i x-inst; done` | La passerelle alterne entre deux instances trouvées par leur nom. |
| 7:00 | **DOWN** | Tableau de bord : mettre une instance du catalogue à DOWN, relancer la boucle | `/health` passe à 503, Eureka à DOWN, une seule instance répond, zéro erreur client. Remettre « Aucune ». |
| 9:30 | **Arrêt brutal** | `docker kill sante-uadb-catalogue-service-2` | Personne n'a prévenu Eureka : il dit encore UP, la supervision signale l'**écart**. Puis expulsion. `docker start sante-uadb-catalogue-service-2` |
| 12:00 | **Base coupée** | `docker compose stop postgres` | Le détail pointe `db`. Le conteneur reste *healthy* : vivacité et préparation sont deux questions différentes. `docker compose start postgres` |
| 14:30 | **DEGRADE** | `curl -XPOST -H 'content-type: application/json' -d '{"produitId":4,"quantite":3}' localhost:8080/api/commandes` (adapter la quantité au stock) | Rupture de stock : 200, toujours routé. Sans notre classe, Eureka dirait UNKNOWN et couperait le trafic. |
| 16:00 | **Alertes** | Prometheus `/alerts`, Grafana | Prometheus trouve ses cibles dans Eureka. `InstanceInstable` est normalement active : nous venons de provoquer plus de 6 changements d'état en 5 min. |
| 17:00 | **Bilan** | Tableau des mesures du rapport | Réglages d'usine contre réglages rapides, et leur prix. |

Remettre le stock : `curl -u admin:$ADMIN_PASSWORD -XPOST -H 'content-type: application/json' -d '{"quantite":3}' localhost:8080/api/produits/4/reapprovisionnements`

**Plan de secours** : si une étape échoue, `docker compose up -d --force-recreate --wait` (une minute) et poursuivre avec les captures du rapport.

## Questions probables

**Pourquoi Eureka disait-il UP alors que la base était coupée ?** Par défaut il ne connaît que les battements de cœur. Il faut `eureka.client.healthcheck.enabled=true`.

**Différence entre liveness et readiness ?** Liveness : faut-il redémarrer le processus ? Readiness : peut-on lui envoyer du trafic ? La base coupée rend la seconde fausse, pas la première : redémarrer ne réparerait rien.

**Pourquoi ne pas mettre des délais de 1 s partout ?** Plus de trafic vers Eureka, et des instances lentes expulsées à tort (flapping).

**Qu'est-ce que l'auto-préservation ?** Si trop de battements manquent d'un coup (moins de 85 % reçus), Eureka suppose une coupure réseau et n'expulse plus personne. Utile à grande échelle, gênante avec six instances.

**Si Eureka tombe ?** Chaque client garde une copie du registre : le trafic continue, mais plus aucun changement n'est vu.

**Pourquoi la supervision n'a-t-elle pas de base de données ?** Elle ne doit pas tomber avec ce qu'elle surveille. Contrepartie : l'historique est perdu au redémarrage, Prometheus garde la trace longue.

**Qui surveille le superviseur ?** Prometheus, règle `SupervisionAbsente`.

**Pourquoi Spring Boot Admin n'a-t-il pas été utilisé ?** Il affiche les états Actuator, mais nous voulions montrer l'écart avec Eureka, et comprendre le mécanisme en l'écrivant.

**Eureka est-il dépassé par Kubernetes ?** Kubernetes fournit découverte et sondes. Eureka reste pertinent hors Kubernetes, et les concepts (readiness, propagation, caches) sont les mêmes.
