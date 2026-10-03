#!/usr/bin/env bash
# ----------------------------------------------------------------------------
# Cambia la contrasena del administrador.
#
#   ./cambiar-clave-admin.sh
#
# Se hace por la API y no tocando el .env porque AdminBootstrap sale temprano
# en cuanto existe algun administrador: cambiar ADMIN_PASSWORD alli y reiniciar
# no tiene ningun efecto sobre la cuenta ya creada.
#
# La contrasena nueva se escribe sin eco y nunca se pasa como argumento, para
# que no quede en el historial del shell ni en la lista de procesos.
# ----------------------------------------------------------------------------
set -euo pipefail

ENVF=/opt/docrecord/.env
[ -f "$ENVF" ] || { echo "Falta $ENVF"; exit 1; }
set -a; . "$ENVF"; set +a

read -rsp "Contrasena NUEVA: " NUEVA; echo
read -rsp "Repetila:         " OTRA;  echo
[ "$NUEVA" = "$OTRA" ] || { echo "No coinciden. No se cambio nada."; exit 1; }
[ ${#NUEVA} -ge 8 ]    || { echo "Muy corta (minimo 8). No se cambio nada."; exit 1; }

echo "-- iniciando sesion con la contrasena actual"
TOKEN=$(curl -s -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  --data-binary "$(printf '{"email":"%s","password":"%s"}' "$ADMIN_EMAIL" "$ADMIN_PASSWORD")" \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

[ -n "$TOKEN" ] || { echo "No se pudo iniciar sesion con la contrasena que hay en .env."; exit 1; }

echo "-- asignando la nueva"
CODE=$(curl -s -o /tmp/cambio.json -w '%{http_code}' \
  -X POST "http://localhost/api/user/1/password" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary "$(printf '{"password":"%s"}' "$NUEVA")")

if [ "$CODE" != "200" ]; then
  echo "Fallo con HTTP $CODE:"; cat /tmp/cambio.json; rm -f /tmp/cambio.json; exit 1
fi
rm -f /tmp/cambio.json

# El .env queda al dia: si no, la proxima vez este mismo script no podria
# iniciar sesion, y el archivo afirmaria una contrasena que ya no es.
python3 - "$NUEVA" <<'PY'
import sys, pathlib
nueva = sys.argv[1]
p = pathlib.Path("/opt/docrecord/.env")
lineas = [
    f'ADMIN_PASSWORD="{nueva}"' if l.startswith("ADMIN_PASSWORD=") else l
    for l in p.read_text().splitlines()
]
p.write_text("\n".join(lineas) + "\n")
PY
chmod 600 "$ENVF"

echo "-- comprobando que la nueva funciona"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  --data-binary "$(printf '{"email":"%s","password":"%s"}' "$ADMIN_EMAIL" "$NUEVA")")
[ "$CODE" = "200" ] && echo "Listo. Entra con $ADMIN_EMAIL y la contrasena nueva." \
                    || { echo "La nueva no autentica (HTTP $CODE). Revisar."; exit 1; }
