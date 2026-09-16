# Convenciones de este repositorio

## Los commits no llevan atribución de herramientas

Ningún mensaje de commit lleva `Co-Authored-By:` de un asistente, ni ninguna
otra línea que anuncie con qué herramienta se escribió el código.

El historial responde **qué cambió y por qué**, y quién de las tres personas
del equipo se hace responsable de ese cambio. Con qué editor, plantilla o
asistente se tecleó no es parte de esa respuesta: no cambia el diseño, no
ayuda a revisarlo y no le dice nada a quien lo lea dentro de seis meses
buscando por qué una decisión es como es.

Además, este es un proyecto evaluado. El autor del commit es quien responde
por él ante la cátedra y ante el resto del equipo, y ese nombre ya está en
`%an`. Un segundo autor en el pie no añade responsabilidad, la diluye.

**Si un commit ya se hizo con el pie puesto**, se quita antes de publicarlo
(`git commit --amend`) o, si ya se empujó, reescribiendo el mensaje y
empujando con `--force-with-lease`. Conviene avisar al equipo: `dev-naun` es
la rama desde la que se despliega, y quien la tenga clonada necesita
`git fetch origin && git reset --hard origin/dev-naun` —un `git pull` normal
crearía un merge que devuelve los commits viejos—.

# Cómo correr las pruebas

Las pruebas de integración levantan Spring contra un PostgreSQL **real**, que
recrean desde cero en cada ejecución (ver `PruebaDeIntegracion`). Hace falta
que el contenedor del `docker-compose.yml` esté arriba.

En este proyecto ese contenedor publica el **5433**, no el 5432 —el 5432 suele
estar ocupado por otra base—, así que el puerto hay que decirlo:

```bash
docker compose up -d
./mvnw test -Dtest.db.port=5433
```

Sin `-Dtest.db.port` la suite entera falla con `NoClassDefFoundError: Could not
initialize class PruebaDeIntegracion`, que no menciona la base por ningún lado
y manda a buscar el problema al sitio equivocado.
