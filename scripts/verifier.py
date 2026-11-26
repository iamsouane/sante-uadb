#!/usr/bin/env python3
"""Essai de bout en bout sur la pile démarrée : sécurité, état UP, panne, rétablissement.

Utilisé par la CI et avant une démonstration. Code de sortie 1 au premier échec.
"""
import sys
import time

import mesurer as m


def exiger(nom, condition, limite=60):
    delai = m.attendre(condition, limite)
    if delai is None:
        sys.exit(f"ÉCHEC  {nom} (après {limite} s)")
    print(f"ok     {nom} ({delai} s)", flush=True)


BASE = "http://localhost:8090"
GW = "http://localhost:8080"

exiger("passerelle : le catalogue répond", lambda: m.http(f"{GW}/api/produits")[0] == 200, 120)
exiger("deux instances du catalogue UP", lambda: list(m.eureka().values()).count("UP") == 2, 120)
exiger("état global UP", lambda: b'"global":"UP"' in m.http(f"{BASE}/api/supervision/etat", m.SUP)[1], 120)
exiger("une commande traverse la chaîne",
       lambda: m.http(f"{GW}/api/commandes", methode="POST", corps={"produitId": 3, "quantite": 1})[0] == 201, 30)

exiger("API de supervision : 401 sans compte", lambda: m.http(f"{BASE}/api/supervision/etat")[0] == 401, 1)
exiger("/actuator/prometheus : 401 sans compte", lambda: m.http(f"{BASE}/actuator/prometheus")[0] == 401, 1)
exiger("/actuator/health : public, sans détails",
       lambda: b"components" not in m.http(f"{BASE}/actuator/health")[1], 1)
exiger("Eureka : 401 sans compte", lambda: m.http("http://localhost:8761/eureka/apps")[0] == 401, 1)
exiger("passerelle : aucun actuator des services",
       lambda: m.http(f"{GW}/api/produits/actuator/env")[0] in (400, 404), 1)

victime = sorted(m.eureka())[0]
panne = f"{BASE}/api/supervision/instances/{victime}/panne"
exiger("panne refusée au compte supervision",
       lambda: m.http(panne, m.SUP, "PUT", {"statut": "DOWN"})[0] == 403, 1)
exiger("panne acceptée du compte admin",
       lambda: m.http(panne, m.ADMIN, "PUT", {"statut": "DOWN"})[0] == 204, 1)
exiger("supervision : instance DOWN", lambda: m.sante(victime) == "DOWN")
exiger("Eureka : instance DOWN", lambda: m.eureka().get(victime) == "DOWN")
exiger("supervision : service dégradé, pas coupé", lambda: m.etat_service()["etat"] == "DEGRADE")
time.sleep(15)  # caches du client Eureka et du répartiteur de charge
reponses = [m.http(f"{GW}/api/produits") for _ in range(20)]
exiger("20 requêtes : aucune erreur", lambda: all(r[0] == 200 for r in reponses), 1)
exiger("20 requêtes : aucune vers l'instance DOWN", lambda: all(r[2] != victime for r in reponses), 1)

exiger("rétablissement accepté", lambda: m.http(panne, m.ADMIN, "DELETE")[0] == 204, 1)
exiger("retour à UP dans Eureka", lambda: m.eureka().get(victime) == "UP")
exiger("service de nouveau UP", lambda: m.etat_service()["etat"] == "UP")
print("Tout est conforme.")
