# DocRecordBE

[![CI](https://github.com/LL18017/DocRecordBE/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/LL18017/DocRecordBE/actions/workflows/ci.yml)

## Configuración

Todo valor sensible se lee de variables de entorno; el repositorio no contiene credenciales de producción (ver `.env.example`).

| Variable | Para qué sirve | Por defecto |
|---|---|---|
| `DB_PASSWORD` | Clave de PostgreSQL | `9902`, **solo desarrollo local** (la del PostgreSQL desechable de `docker-compose.yml`). En un despliegue real se define siempre. |
| `JWT_SECRET` | Firma de los tokens (Base64URL, mínimo 32 bytes) | Ninguno: sin ella la aplicación no arranca. En local se usa el perfil `dev`, que trae una clave marcada *SOLO DESARROLLO*. |
| `MAIL_PASSWORD` | Contraseña de aplicación del correo | Vacía: la aplicación arranca, pero no se envían correos. |
| `CORS_ORIGENES` | Orígenes del navegador permitidos, separados por comas | `http://localhost:3000`, `https://docrecordsv.duckdns.org`, `https://api-docrecordsv.duckdns.org` |

Arranque local:

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

El perfil `dev` (`application-dev.properties`) existe únicamente para desarrollo: su clave JWT es pública y nunca debe usarse en producción.

## Formato de errores

Toda respuesta de error trae `error` (categoría), `message` (motivo legible), `codigo` (estado HTTP), `timestamp` (ISO-8601) y `ruta`. Un error 500 agrega `idError`, que también queda en el registro del servidor junto con la traza. Las validaciones (400) agregan además una entrada por campo rechazado.
