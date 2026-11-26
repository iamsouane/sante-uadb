#!/usr/bin/env python3
"""Affiche l'état de la pile dans le terminal (bibliothèque standard uniquement)."""
import base64
import json
import urllib.request
from pathlib import Path

lignes = (Path(__file__).resolve().parent.parent / ".env").read_text().splitlines()
env = dict(l.split("=", 1) for l in lignes if "=" in l and not l.startswith("#"))
jeton = base64.b64encode(f"supervision:{env['SUPERVISION_PASSWORD']}".encode()).decode()
requete = urllib.request.Request("http://localhost:8090/api/supervision/etat", headers={"Authorization": f"Basic {jeton}"})
etat = json.load(urllib.request.urlopen(requete, timeout=5))
print(f"GLOBAL {etat['global']}   EUREKA {etat['eureka']}")
for s in etat["services"]:
    print(f"\n{s['nom']:<22}{s['etat']:<13}{s['instancesDisponibles']}/{s['minimum']}  dispo {s['disponibilite']} %")
    for i in s["instances"]:
        print(f"   {i['id']:<42} eureka={i['statutEureka']:<15} actuator={i['sante']:<13} {i['detail']}"
              + ("  <-- ÉCART" if i["divergence"] else ""))
