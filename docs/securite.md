# Rapport de sécurité

Périmètre : services Java, tableau de bord, configuration Docker Compose, exécutés sur un poste local.

## 1. Pourquoi Actuator est une cible

Mal configuré, Actuator livre des secrets (`env`, `heapdump`), modifie l'application (`loggers`, `shutdown`) ou décrit l'architecture (`mappings`, `health` détaillé).

| Endpoint | Anonyme | SUPERVISION | ADMIN |
|---|:---:|:---:|:---:|
| `/actuator/health` (+ liveness, readiness) | statut seul | détails | détails |
| `/actuator/info`, `metrics`, `prometheus` | 401 | oui | oui |
| `/actuator/panne` en lecture | 401 | oui | oui |
| `/actuator/panne` en écriture | 401 | 403 | oui |
| `/actuator/env`, `heapdump`, autres (non exposés) | 401 ou 404 selon le service | non exposé | non exposé |

Preuves : `SanteCatalogueTests`, `SupervisionApiTests`, `EurekaServerApplicationTests`, `scripts/verifier.py`.

## 2. Menaces et réponses

| Menace | Réponse | OWASP 2021 |
|---|---|:---:|
| Enregistrer une fausse instance dans Eureka pour détourner le trafic | Registre protégé par mot de passe | A01, A07 |
| Utiliser la supervision pour atteindre une URL interne | Seul un identifiant d'instance connu est accepté | A10 |
| Couper un service par l'injection de panne | Rôle ADMIN, inactive sauf `PANNE_ACTIVE=true`, latence bornée à 10 s. **Le Compose de démonstration l'active.** | A01, A05 |
| Atteindre l'Actuator d'un service par la passerelle | Seuls `/api/produits/**` et `/api/commandes/**` sont routés | A05 |
| Voler les identifiants dans le navigateur | Ni cookie ni stockage local ; CSP `default-src 'self'` | A03 |
| CSRF | Pas de session ni de cookie ; 401 sans `WWW-Authenticate` | A01 |
| Injection SQL | JPA et requêtes paramétrées | A03 |
| Survente par concurrence | Décrément conditionnel en une requête SQL | A04 |
| Secrets dans Git | `.env` ignoré ; génération aléatoire ; démarrage refusé si une variable manque | A02, A05 |
| Compromission d'un conteneur | Non root, `read_only` (services Java), `cap_drop: ALL`, `no-new-privileges`, mémoire limitée | A05 |
| Accès réseau à la base | Réseau Docker interne, une base et un compte par service | A05 |

## 3. Analyse des dépendances

Outil : `scripts/scanner.py` (base OSV). Périmètre : dépendances Maven d'exécution et paquets Alpine de deux images, `catalogue-service` (même base que les autres services Java) et `tableau-de-bord`. `npm audit` : 0 vulnérabilité lors de l'installation.

| | Avant | Après |
|---|:---:|:---:|
| Dépendances Java | 5 | **0** |
| Paquets Alpine | 42 | **3** |

Corrections :

| Composant | Avant | Après | Origine |
|---|---|---|---|
| guava | 33.0.0 | 33.7.2 | Eureka serveur |
| httpclient (4.x) | 4.5.3 | 4.5.14 | Eureka client |
| httpclient5 | 5.5.2 | 5.6.5 | Spring Cloud |
| httpcore5, httpcore5-h2 | 5.3.6 | 5.4.4 | Spring Cloud |
| curl, openssl, libxslt, musl… | divers | à jour | image nginx, par `apk upgrade` |

Restent trois CVE sur `expat` 2.10.0 dans l'image Java (CVE-2026-124317, 128241, 134703). Notre code n'utilise pas cette bibliothèque. La version 2.11.0, présente dans l'image nginx, n'est plus signalée : reconstruire avec `docker compose build --no-cache` devrait la récupérer.

**Trivy** a été exécuté en local pour la configuration et les secrets (`--scanners misconfig,secret`) : 0 problème de gravité haute ou critique dans les Dockerfiles, aucun secret. Son analyse de vulnérabilités n'a pas pu tourner en local, faute de pouvoir télécharger sa base. La CI l'exécute sur les fichiers et sur nos six images. Les images PostgreSQL, Prometheus et Grafana ne sont analysées ni par OSV ni par Trivy.

Note : appliqué aux `pom.xml`, Trivy interroge Maven Central pour chaque dépendance, au point de faire bloquer l'adresse IP (429). La CI exclut donc les POM du scan de fichiers ; les JAR sont analysés dans les images. Données brutes : `docs/mesures/osv-avant.json` et `osv.json`.

## 4. Risques résiduels

| Risque | Niveau en local | À faire en production |
|---|:---:|---|
| HTTP sans chiffrement entre services | faible | TLS mutuel ou maillage de services |
| Authentification Basic sans limite d'essais | moyen | OAuth2, verrouillage, rate limiting |
| API métier sans authentification | moyen | OAuth2 à la passerelle |
| Prometheus sans mot de passe | faible (127.0.0.1) | Proxy authentifié |
| Mots de passe en variables d'environnement | faible | Gestionnaire de secrets |
| Mot de passe Eureka dans une URL | faible | Certificat client |
| Documentation OpenAPI publique | faible | La fermer |
| Versions d'images par étiquette et non par empreinte | faible | Épingler par `@sha256` |
