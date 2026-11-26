#!/usr/bin/env bash
# Génère .env avec des mots de passe aléatoires. Ne remplace jamais un .env existant.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -e .env ]; then
  echo ".env existe déjà, rien à faire."
  exit 0
fi
umask 077
grep -E '^[A-Z_]+=$' .env.example | while IFS= read -r ligne; do
  echo "${ligne}$(openssl rand -hex 16)"
done > .env
echo ".env créé. Mots de passe utiles pour la démonstration :"
grep -E '^(EUREKA|SUPERVISION|ADMIN|GRAFANA)_PASSWORD' .env
