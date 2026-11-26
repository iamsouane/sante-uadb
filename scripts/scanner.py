#!/usr/bin/env python3
"""Cherche les vulnérabilités connues des dépendances Java et des paquets Alpine (base OSV).

N'envoie à api.osv.dev que des noms et versions de paquets publics. Complète le scan Trivy de la CI.
Usage : python3 scripts/scanner.py   (Maven et Docker requis)
"""
import json
import re
import subprocess
import tempfile
import urllib.request
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent


def appel(chemin, corps=None):
    requete = urllib.request.Request("https://api.osv.dev/v1/" + chemin,
                                     data=json.dumps(corps).encode() if corps else None,
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(requete, timeout=60) as r:
        return json.load(r)


def maven():
    with tempfile.TemporaryDirectory() as d:
        f = Path(d) / "deps.txt"
        subprocess.run(["mvn", "-q", "-B", "compile", "dependency:list", "-DincludeScope=runtime", f"-DoutputFile={f}",
                        "-DappendOutput=true"], cwd=RACINE, check=True)
        trouves = re.findall(r"^\s+([\w.\-]+):([\w.\-]+):jar:(?:[\w.\-]+:)?([\w.\-]+):(?:compile|runtime)", f.read_text(), re.M)
    return sorted({("Maven", f"{g}:{a}", v) for g, a, v in trouves if g != "sn.uadb.supervision"})


def alpine(image):
    def lire(commande):
        return subprocess.run(["docker", "run", "--rm", "--network", "none", "--entrypoint", "sh", image, "-c", commande],
                              check=True, capture_output=True, text=True).stdout
    branche = ".".join(lire("cat /etc/alpine-release").strip().split(".")[:2])
    base = lire("cat /lib/apk/db/installed")
    paquets = set()
    for bloc in base.split("\n\n"):
        champs = dict(l.split(":", 1) for l in bloc.splitlines() if ":" in l)
        if "P" in champs:
            paquets.add((f"Alpine:v{branche}", champs.get("o", champs["P"]), champs["V"]))
    return sorted(paquets)


def note(d):
    """Gravité publiée (GitHub) ou vecteur CVSS (Alpine)."""
    return d.get("database_specific", {}).get("severity") or (d.get("severity") or [{}])[0].get("score", "?")


def main():
    paquets = sorted(set(maven() + alpine("sante-uadb/catalogue-service") + alpine("sante-uadb/tableau-de-bord")))
    print(f"{len(paquets)} paquets analysés ({sum(p[0] == 'Maven' for p in paquets)} Maven)")
    requetes = [{"package": {"ecosystem": e, "name": n}, "version": v} for e, n, v in paquets]
    resultats = []
    for i in range(0, len(requetes), 500):
        resultats += appel("querybatch", {"queries": requetes[i:i + 500]})["results"]
    rapport = []
    for (eco, nom, version), r in zip(paquets, resultats):
        for v in r.get("vulns", []):
            d = appel("vulns/" + v["id"])
            gravite = note(d)
            rapport.append({"paquet": nom, "version": version, "ecosysteme": eco, "id": v["id"],
                            "gravite": gravite, "resume": d.get("summary") or d.get("details", "")[:120]})
            print(f"{v['id']:<24} {gravite[:44]:<45} {nom} {version} : {rapport[-1]['resume'][:90]}")
    sortie = RACINE / "docs" / "mesures" / "osv.json"
    sortie.write_text(json.dumps({"paquets": len(paquets), "vulnerabilites": rapport}, indent=2, ensure_ascii=False) + "\n")
    print(f"{len(rapport)} vulnérabilité(s) connue(s)")


main()
