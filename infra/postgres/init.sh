#!/bin/sh
# Une base et un compte par service : catalogue ne peut pas lire commande.
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<SQL
create user catalogue with password '$CATALOGUE_DB_PASSWORD';
create user commande with password '$COMMANDE_DB_PASSWORD';
create database catalogue owner catalogue;
create database commande owner commande;
revoke connect on database catalogue from public;
revoke connect on database commande from public;
SQL
