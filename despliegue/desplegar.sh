#!/usr/bin/env bash
# ----------------------------------------------------------------------------
# Despliegue de DocRecord Sv. Idempotente: se puede correr cuantas veces haga
# falta.
#
#   ./desplegar.sh            despliega la rama por defecto (dev-naun)
#   ./desplegar.sh otra-rama  despliega otra rama
#
# Trae el codigo de GitHub, actualiza la configuracion de despliegue,
# reconstruye las imagenes y levanta los servicios. La unica fuente es el
# repositorio: no recibe nada de ninguna maquina de desarrollo.
#
# ── Como queda el servidor ──────────────────────────────────────────────────
#   /opt/docrecord/
#     .env                     secretos, permisos 600, NUNCA versionado
#     docker-compose.prod.yml  copiado desde el repositorio en cada despliegue
#     Caddyfile                idem
#     desplegar.sh             este archivo (ver la nota de abajo)
#     DocRecordBE/             clon
#     DocRecordFE/             clon
# ----------------------------------------------------------------------------
set -euo pipefail

RAMA="${1:-dev-naun}"
RAIZ=/opt/docrecord
COMPOSE="docker compose -f docker-compose.prod.yml"
cd "$RAIZ"

# ----------------------------------------------------------------------------
# Un despliegue a la vez, venga del repositorio que venga.
#
# Los dos repositorios despliegan en esta misma maquina y corren ESTE mismo
# script, que reconstruye AMBOS. El `concurrency` de GitHub Actions serializa
# las ejecuciones dentro de un repositorio, pero no cruza de uno a otro: si se
# empuja al backend y al frontend con pocos minutos de diferencia, las dos
# corridas se pisan sobre el mismo /opt/docrecord y una muere.
#
# Cuando eso pasa, el despliegue que gana reconstruye los dos repositorios de
# todos modos -- asi que el codigo SI queda publicado -- y el que pierde
# reporta fallo. Esa combinacion es la peligrosa: el 16 de septiembre hizo que
# un arreglo de SEGURIDAD apareciera como fallido en Actions estando vivo en
# produccion. Quien mirara la insignia en vez de la API habria concluido lo
# contrario de lo que pasaba.
#
# `flock` sin -n a proposito: el segundo despliegue ESPERA su turno en vez de
# rendirse. El timeout evita que un proceso muerto deje el lock tomado para
# siempre; es holgado porque una reconstruccion completa de las imagenes puede
# pasar de los diez minutos.
#
# El descriptor 9 no tiene nada de especial: es uno alto y libre, elegido para
# no chocar con 0, 1 y 2. Se mantiene abierto mientras dure el script, y el
# kernel suelta el lock solo cuando el proceso termina, aunque muera de golpe.
# ----------------------------------------------------------------------------
exec 9>/var/lock/docrecord-despliegue.lock
if ! flock --wait 900 9; then
  echo "Otro despliegue lleva mas de 15 minutos en curso. Abortado sin tocar nada."
  exit 1
fi
echo "== lock de despliegue tomado"

[ -f .env ] || { echo "Falta $RAIZ/.env (los secretos). Abortado."; exit 1; }

for repo in DocRecordBE DocRecordFE; do
  url="https://github.com/LL18017/$repo.git"
  if [ -d "$repo/.git" ]; then
    echo "== $repo: actualizando a $RAMA"
    git -C "$repo" fetch --depth 1 origin "$RAMA"
    # reset --hard y no merge: este clon no es un area de trabajo, es un
    # reflejo de lo que hay en GitHub. Si alguien edito un archivo aqui a mano
    # se pierde, y eso es lo correcto: lo contrario seria desplegar algo que no
    # esta en ningun commit.
    git -C "$repo" reset --hard "origin/$RAMA"
  else
    echo "== $repo: clonando $RAMA"
    # --depth 1: en esta maquina el historial completo no aporta nada y estos
    # repositorios ya pasan de los 300 commits.
    git clone --depth 1 --branch "$RAMA" "$url" "$repo"
  fi
  echo "   $(git -C "$repo" log --oneline -1)"
done

# La configuracion de despliegue tambien viene del repositorio, no se edita a
# mano en el servidor. Antes vivia SOLO aqui: si la instancia moria, se perdia
# con ella, y nadie mas podia reproducir el despliegue.
#
# Este script NO se copia a si mismo a proposito. Bash lee el archivo a medida
# que lo ejecuta, asi que reescribirlo mientras corre hace que siga leyendo
# desde un desplazamiento que ya no significa lo mismo. Para actualizarlo:
#   cp DocRecordBE/despliegue/desplegar.sh . && chmod +x desplegar.sh
echo "== actualizando la configuracion de despliegue"
cp DocRecordBE/despliegue/docker-compose.prod.yml .
cp DocRecordBE/despliegue/Caddyfile .
cp DocRecordBE/despliegue/cambiar-clave-admin.sh .
chmod +x cambiar-clave-admin.sh

# De a uno y no en paralelo: `compose build` por defecto construye todos los
# servicios a la vez, y las dos etapas pesadas -- Maven compilando el backend y
# Turbopack compilando el frontend -- hacen picos de mas de 1.5 GB cada una.
# Juntas no caben en los 2 GB de esta maquina, y lo que se ve cuando no caben
# no es un error claro sino al proceso desapareciendo a mitad del build.
echo "== construyendo backend"
$COMPOSE build backend
echo "== construyendo frontend"
$COMPOSE build frontend

echo "== levantando servicios"
$COMPOSE up -d

# Las etapas intermedias de un build multi-stage quedan como imagenes huerfanas
# y se acumulan en cada despliegue.
echo "== limpiando capas huerfanas"
docker image prune -f >/dev/null

echo "== estado"
$COMPOSE ps
df -h / | tail -1
