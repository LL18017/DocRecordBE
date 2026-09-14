# Despliegue

Todo lo que hace falta para levantar DocRecord Sv en un servidor. Lo único que
**no** está aquí, y nunca debe estarlo, es `.env`: los secretos se generan
dentro de la instancia y no salen de ella.

## 🔗 En producción

| | URL |
|---|---|
| Aplicación | https://docrecordsv.duckdns.org |
| API | https://api-docrecordsv.duckdns.org |
| Swagger | https://api-docrecordsv.duckdns.org/swagger-ui/index.html |

## Qué hay aquí

| Archivo | Para qué |
|---|---|
| `docker-compose.prod.yml` | Los cuatro servicios: PostgreSQL, backend, frontend y el proxy |
| `Caddyfile` | El proxy, con HTTPS automático de Let's Encrypt |
| `desplegar.sh` | Trae el código, reconstruye y levanta |
| `cambiar-clave-admin.sh` | Cambia la contraseña del administrador sin que pase por el historial del shell |
| `nginx.conf` | La configuración equivalente con nginx, **sin usar**. Se conserva por si se vuelve atrás desde Caddy |

## Desplegar

```bash
ssh -i <tu-llave>.pem ubuntu@<ip>
cd /opt/docrecord && ./desplegar.sh dev-naun
```

Lo hace también GitHub Actions en cada push a `dev-naun`, pero solo si las
pruebas pasan: el job de despliegue depende del workflow CI.

## Levantar un servidor desde cero

Suponiendo Ubuntu con Docker instalado y el usuario en el grupo `docker`:

```bash
sudo install -d -o ubuntu -g ubuntu /opt/docrecord
cd /opt/docrecord
git clone --depth 1 --branch dev-naun https://github.com/LL18017/DocRecordBE.git
cp DocRecordBE/despliegue/desplegar.sh .
chmod +x desplegar.sh
```

Después el `.env`, que se genera **en el servidor** para que los secretos no
viajen por ningún canal:

```bash
umask 077
{
  echo "DB_NAME=datadoc"
  echo "DB_USER=docrecord"
  echo "DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-28)"
  # base64URL, no base64 estándar: jjwt rechaza los caracteres + y / con
  # "Illegal base64url character" y el login responde 500.
  echo "JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n' | tr '+/' '-_' | tr -d '=')"
  echo "ADMIN_EMAIL=<correo del primer administrador>"
  echo "ADMIN_PASSWORD=<contraseña del primer administrador>"
  echo "MAIL_USERNAME=<cuenta de correo>"
  echo 'MAIL_PASSWORD="<clave de aplicación>"'   # entre comillas: lleva espacios
  echo "DOMINIO=<dominio de la aplicación>"
  echo "DOMINIO_API=<dominio de la API>"
  echo "PUBLIC_URL=https://<dominio de la aplicación>"
  echo 'JPA_SHOW_SQL="false"'
} > .env
chmod 600 .env

./desplegar.sh dev-naun
```

`ADMIN_EMAIL` y `ADMIN_PASSWORD` los usa `AdminBootstrap` para crear el primer
administrador, y **solo si todavía no existe ninguno**: cambiarlos después y
reiniciar no toca la cuenta ya creada. Para eso está `cambiar-clave-admin.sh`.

## Requisitos del servidor

| | Mínimo | Por qué |
|---|---|---|
| RAM | 2 GB + 4 GB de swap | El build de Next.js hace picos de 1.5–2 GB. Sin swap, el OOM killer corta el build a la mitad y el error no dice que faltó memoria |
| Disco | 30 GB | Las etapas de compilación —`.m2`, `node_modules`, `.next`— superan los 3 GB antes de producir las imágenes finales |
| Puertos | 80 y 443 | El 80 no es opcional aunque todo vaya por HTTPS: Let's Encrypt lo usa para validar el dominio, y de ahí sale la redirección |

Solo el proxy publica puertos. El backend, el frontend y PostgreSQL viven en la
red interna de Docker y no son alcanzables desde internet.

## Dos cosas que conviene saber antes de tocar esto

**Los certificados están en un volumen, y no es opcional.** Sin
`docrecord-caddy-data`, cada recreación del contenedor los pierde y vuelve a
pedirlos. Let's Encrypt admite cinco certificados idénticos por semana, así que
un par de despliegues seguidos agotan la cuota y el sitio se queda sin HTTPS
durante días.

**La URL de la API es relativa a propósito.** El frontend se compila con
`NEXT_PUBLIC_API_URL=/api`, y Next.js hornea esa variable en el bundle del
navegador en tiempo de build. Con una URL absoluta, la dirección del servidor
quedaría grabada en el código y cada cambio de IP o de dominio obligaría a
reconstruir la imagen. Con una ruta relativa, el navegador la resuelve contra
el host que le sirvió la página — por eso este despliegue pasó de una IP a otra
y de HTTP a HTTPS sin reconstruir nada.

## Rollback

`./desplegar.sh <rama-o-etiqueta>` reconstruye desde cualquier referencia de
git.

Las migraciones **no** se revierten: Flyway solo avanza, y una que deba
deshacerse se deshace con otra migración. Bajar de versión el esquema con la
aplicación viva es la forma más rápida de perder datos.

Antes de cualquier operación sobre datos:

```bash
docker exec docrecord-db pg_dump -U "$DB_USER" -d "$DB_NAME" \
  --no-owner --no-privileges > respaldo-$(date +%Y%m%d-%H%M).sql
```
