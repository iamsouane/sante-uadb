#!/usr/bin/env python3
"""Génère .env avec des mots de passe aléatoires (équivalent de init-env.sh, utilisable sous Windows).

    python scripts/init_env.py
"""
import secrets
from pathlib import Path

racine = Path(__file__).resolve().parent.parent
env = racine / ".env"
if env.exists():
    print(".env existe déjà, rien à faire.")
else:
    lignes = [l for l in (racine / ".env.example").read_text(encoding="utf-8").splitlines()
              if l.endswith("=") and not l.startswith("#")]
    # newline="\n" : fins de ligne Unix même sous Windows (Docker Compose lit ce fichier)
    with env.open("w", encoding="utf-8", newline="\n") as f:
        f.writelines(f"{l}{secrets.token_hex(16)}\n" for l in lignes)
    print(".env créé. Mots de passe utiles pour la démonstration :")
    print("".join(l + "\n" for l in env.read_text(encoding="utf-8").splitlines()
                  if l.split("=")[0] in ("EUREKA_PASSWORD", "SUPERVISION_PASSWORD", "ADMIN_PASSWORD", "GRAFANA_PASSWORD")), end="")
