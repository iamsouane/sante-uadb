#!/usr/bin/env python3
"""Mesure les délais de détection et de rétablissement sur la pile Docker démarrée.

Python 3.9+, bibliothèque standard uniquement (fonctionne sous macOS, Linux et Windows).

    python3 scripts/mesurer.py rapide      # réglages du compose
    python3 scripts/mesurer.py defaut      # réglages d'usine d'Eureka / Spring Cloud
    python3 scripts/mesurer.py sans-sante  # eureka.client.healthcheck.enabled=false
    python3 scripts/mesurer.py rapide kill_9   # un seul scénario

Le profil redémarre les services Java concernés, puis joue les scénarios et écrit
docs/mesures/<profil>.json. Attention : des conteneurs de la pile sont arrêtés pendant l'essai.
"""
import base64
import json
import os
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
ENV = dict(l.split("=", 1) for l in (RACINE / ".env").read_text().splitlines() if "=" in l and not l.startswith("#"))
PROFILS = {
    "rapide": {},
    "sans-sante": {"EUREKA_HEALTHCHECK": "false"},
    # Valeurs par défaut documentées d'Eureka, Spring Cloud Netflix et Spring Cloud LoadBalancer
    "defaut": {"EUREKA_FETCH_S": "30", "EUREKA_REPLICATION_S": "30", "EUREKA_RENOUVELLEMENT_S": "30",
               "EUREKA_EXPIRATION_S": "90", "LB_CACHE": "35s", "EUREKA_EVICTION_MS": "60000",
               "EUREKA_CACHE_MS": "30000", "EUREKA_AUTO_PRESERVATION": "true"},
}
SERVICE = "catalogue-service"


def compose(*args, env=None):
    subprocess.run(["docker", "compose", *args], cwd=RACINE, check=True, env={**os.environ, **(env or {})},
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def docker(*args):
    subprocess.run(["docker", *args], check=True, stdout=subprocess.DEVNULL)


def http(url, compte=None, methode="GET", corps=None, delai=5):
    entetes = {"Accept": "application/json"}
    if compte:
        entetes["Authorization"] = "Basic " + base64.b64encode(":".join(compte).encode()).decode()
    donnees = None
    if corps is not None:
        donnees = json.dumps(corps).encode()
        entetes["Content-Type"] = "application/json"
    requete = urllib.request.Request(url, data=donnees, headers=entetes, method=methode)
    try:
        with urllib.request.urlopen(requete, timeout=delai) as r:
            return r.status, r.read(), r.headers.get("X-Instance")
    except urllib.error.HTTPError as e:
        return e.code, e.read(), e.headers.get("X-Instance")
    except OSError:
        return 0, b"", None


SUP = ("supervision", ENV["SUPERVISION_PASSWORD"])
ADMIN = ("admin", ENV["ADMIN_PASSWORD"])
EUREKA = ("eureka", ENV["EUREKA_PASSWORD"])


def etat_service():
    code, corps, _ = http(f"http://localhost:8090/api/supervision/services/{SERVICE}", SUP)
    return json.loads(corps) if code == 200 else {"etat": "?", "instances": []}


def sante(instance):
    return next((i["sante"] for i in etat_service()["instances"] if i["id"] == instance), "ABSENTE")


def eureka():
    """Statut de chaque instance du catalogue, lu directement sur le serveur Eureka."""
    code, corps, _ = http(f"http://localhost:8761/eureka/apps/{SERVICE.upper()}", EUREKA)
    if code != 200:
        return {}
    instances = json.loads(corps)["application"]["instance"]
    instances = instances if isinstance(instances, list) else [instances]
    return {i["instanceId"]: i["status"] for i in instances}


class Trafic:
    """Environ 10 requêtes/s vers le catalogue à travers la passerelle."""

    def __init__(self):
        self.requetes, self.actif = [], True
        self.fil = threading.Thread(target=self.boucle, daemon=True)
        self.fil.start()

    def boucle(self):
        while self.actif:
            t = time.monotonic()
            code, _, instance = http("http://localhost:8080/api/produits")
            self.requetes.append((t, code, instance))
            time.sleep(0.1)

    def arreter(self):
        self.actif = False
        self.fil.join()


def attendre(condition, limite):
    debut = time.monotonic()
    while time.monotonic() - debut < limite:
        if condition():
            return round(time.monotonic() - debut, 1)
        time.sleep(0.25)
    return None


def suivre(conditions, debut, limite):
    """Attend plusieurs conditions à la fois ; renvoie le délai de chacune depuis « debut »."""
    delais = {nom: None for nom in conditions}
    while time.monotonic() - debut < limite and None in delais.values():
        for nom, condition in conditions.items():
            if delais[nom] is None and condition():
                delais[nom] = round(time.monotonic() - debut, 1)
        time.sleep(0.25)
    return delais


def stabiliser():
    """Attend deux instances UP partout, puis laisse les caches se remplir."""
    if attendre(lambda: list(eureka().values()).count("UP") >= 2
                and etat_service().get("instancesDisponibles", 0) >= 2, 400) is None:
        sys.exit("La pile ne se stabilise pas, voir docker compose ps")
    codes = attendre(lambda: all(http("http://localhost:8080/api/produits")[0] == 200 for _ in range(6)), 200)
    if codes is None:
        sys.exit("La passerelle ne répond pas")
    time.sleep(10)


def bilan(trafic, debut, victime):
    """Erreurs vues par le client, et dernier instant où la victime a été sollicitée."""
    apres = [r for r in trafic.requetes if r[0] >= debut]
    erreurs = [r for r in apres if r[1] != 200]
    servies = [r for r in apres if r[2] == victime]
    fin = trafic.requetes[-1][0]
    return {"requetes": len(apres), "erreurs": len(erreurs),
            # Vrai si le client échouait encore à la fin : le délai mesuré n'est qu'un minimum
            "erreurs_jusqu_a_la_fin": bool(erreurs) and fin - erreurs[-1][0] < 2,
            "observation_s": round(fin - debut, 1),
            "derniere_erreur_s": round(erreurs[-1][0] - debut, 1) if erreurs else None,
            "derniere_reponse_de_la_victime_s": round(servies[-1][0] - debut, 1) if servies else None}


def scenario_down_injecte(limite):
    victime = sorted(eureka())[0]
    trafic = Trafic()
    time.sleep(2)
    debut = time.monotonic()
    http(f"http://localhost:8090/api/supervision/instances/{victime}/panne", ADMIN, "PUT", {"statut": "DOWN"})
    r = suivre({"supervision_s": lambda: sante(victime) == "DOWN",
                "eureka_s": lambda: eureka().get(victime) == "DOWN"}, debut, limite)
    time.sleep(max(10, limite / 6))
    trafic.arreter()
    r.update(bilan(trafic, debut, victime))
    http(f"http://localhost:8090/api/supervision/instances/{victime}/panne", ADMIN, "DELETE")
    debut = time.monotonic()
    r["retour_up_eureka_s"] = suivre({"x": lambda: eureka().get(victime) == "UP"}, debut, limite)["x"]
    return r


def scenario_arret(commande, limite):
    """commande = kill (SIGKILL) ou stop (SIGTERM, arrêt propre)."""
    victime = sorted(eureka())[0]
    conteneur = victime.split(":")[1]
    trafic = Trafic()
    time.sleep(2)
    debut = time.monotonic()
    fil = threading.Thread(target=docker, args=(commande, conteneur))
    fil.start()
    r = suivre({"supervision_s": lambda: sante(victime) in ("INJOIGNABLE", "ABSENTE"),
                "eureka_s": lambda: victime not in eureka()}, debut, limite)
    # Laisse expirer les caches clients avant de conclure sur la dernière erreur
    time.sleep(max(10, limite / 4))
    trafic.arreter()
    fil.join()
    r.update(bilan(trafic, debut, victime))
    docker("start", conteneur)
    debut = time.monotonic()
    r["retour_up_eureka_s"] = suivre({"x": lambda: eureka().get(victime) == "UP"}, debut, 200)["x"]
    return r


def scenario_base(limite):
    debut = time.monotonic()
    fil = threading.Thread(target=compose, args=("stop", "-t", "2", "postgres"))
    fil.start()
    r = suivre({"supervision_s": lambda: etat_service()["etat"] == "DOWN",
                "eureka_s": lambda: eureka() and set(eureka().values()) == {"DOWN"}}, debut, limite)
    fil.join()
    service = etat_service()
    r["etat_supervision"] = service["etat"]
    r["statuts_eureka"] = sorted(eureka().values())
    r["divergences"] = sum(i["divergence"] for i in service["instances"])
    compose("start", "postgres")
    debut = time.monotonic()
    r.update(suivre({"retour_up_supervision_s": lambda: etat_service()["etat"] == "UP",
                     "retour_up_eureka_s": lambda: list(eureka().values()).count("UP") == 2}, debut, 200))
    return r


def main():
    profil = sys.argv[1] if len(sys.argv) > 1 else "rapide"
    reglages = PROFILS[profil]
    limite = 330 if profil == "defaut" else 60
    print(f"Profil {profil} : démarrage…", flush=True)
    compose("up", "-d", "--force-recreate", "eureka-server", "catalogue-service", "commande-service", "api-gateway",
            "supervision-service", env=reglages)
    scenarios = {"base_arretee": lambda: scenario_base(limite)}
    if profil != "sans-sante":
        scenarios = {"down_injecte": lambda: scenario_down_injecte(limite),
                     "arret_propre": lambda: scenario_arret("stop", limite),
                     "kill_9": lambda: scenario_arret("kill", limite), **scenarios}
    if len(sys.argv) > 2:  # rejouer un seul scénario
        scenarios = {sys.argv[2]: scenarios[sys.argv[2]]}
    sortie = RACINE / "docs" / "mesures" / f"{profil}.json"
    resultats = json.loads(sortie.read_text())["scenarios"] if len(sys.argv) > 2 and sortie.exists() else {}
    for nom, jouer in scenarios.items():
        stabiliser()
        resultats[nom] = jouer()
        print(nom, json.dumps(resultats[nom], ensure_ascii=False), flush=True)
    sortie.parent.mkdir(parents=True, exist_ok=True)
    sortie.write_text(json.dumps({"profil": profil, "reglages": reglages, "limite_s": limite,
                                  "scenarios": resultats}, indent=2, ensure_ascii=False) + "\n")
    print("Écrit :", sortie)


if __name__ == "__main__":
    main()
