#!/usr/bin/env python3
"""Crea las cuatro cuentas de demostración contra un despliegue vivo.

    python3 crear-cuentas-demo.py https://docrecordsv.duckdns.org correo-admin@ues.edu.sv
    # pide la contraseña sin eco

La cuenta que se le pase debe tener rol ADMIN: casi todo lo que hace vive
detrás de /user, que es hasRole('ADMIN') a nivel de clase.

Es IDEMPOTENTE: correrlo dos veces deja el mismo estado que correrlo una.
Lo que ya existe se reutiliza, no se duplica ni se pisa. Hace falta que lo sea
porque la instancia se reconstruye («desplegar.sh») y porque la IP del VPS
cambia cada vez que se apaga: quien lo corra no siempre va a saber si las
cuentas siguen ahí.

Al final inicia sesión con las cuatro. No basta con que las llamadas de alta
respondan 201: lo que la tutora va a hacer es entrar, y eso es lo que se
comprueba. Devuelve 0 si las cuatro entran, 1 si alguna no.

── Por qué cada cuenta se crea distinto ────────────────────────────────────
MEDICO no se puede crear con POST /user + POST /user/{id}/role/2. El rol se
asignaría, pero `medicos.especialidad_id` es NOT NULL y addRole no puede
inventar una especialidad, así que la cuenta quedaría sin fila en `medicos`:
pasa el hasRole('MEDICO') del controlador y el servicio la rechaza con 403
después (ver el comentario en UserService.addRole). La única alta que crea esa
fila es POST /auth/register, que sí pide la especialidad.

ENFERMERA sí puede: asignar el rol 3 crea su ficha en `enfermeras` sola, que es
justo lo que UserService.addRole hace a propósito.

ADMIN tiene un orden obligatorio -- contraseña ANTES de asignar el rol. Al
revés no se puede: UserService.asignarContrasena prohíbe que un administrador
toque la contraseña de otro administrador (escalada de privilegios), así que en
cuanto la cuenta tiene el rol, este script ya no puede habilitarla.

── Por qué se asigna la contraseña a mano si POST /user ya la recibe ───────
Porque la cuenta nace DESHABILITADA esperando el correo de confirmación, y en
el despliegue el envío está apagado (commit e14f4b7). POST /user/{id}/password
pone la contraseña y además hace enabled = true, que es la única forma de que
la cuenta sirva sin que llegue ningún correo.

── Por qué el personal lleva clínica y el paciente no ─────────────────────
A quien opera en una sede -- ADMIN, MEDICO, ENFERMERA -- el login lo manda a
/select-clinica, y esa pantalla se llena con GET /clinics/mias, que devuelve
las clínicas ASIGNADAS, también para un administrador. Sin una sede asignada,
la tutora entra y se queda en la puerta.

El paciente no pasa por ahí: no trabaja en ninguna clínica, así que el login lo
lleva directo a /mi-panel. Asignarle una sede no le habilitaría nada y afirmaría
algo falso sobre esa cuenta -- que atiende en esa clínica.
"""
import getpass
import json
import sys
import urllib.error
import urllib.request

BASE = ""

# Los ids del catálogo cerrado de roles (RolesEnum, y el CHECK de la V7 que no
# deja meter otros). Se escriben aquí porque no hay endpoint que los liste sin
# ser administrador, y son fijos por migración.
ROL_ADMIN = 1
ROL_MEDICO = 2
ROL_ENFERMERA = 3
ROL_PACIENTE = 4

# La sede de la demostración. Se busca por nombre antes de crearla: es lo que
# hace que correr esto dos veces no deje dos clínicas iguales.
CLINICA = {
    "name": "Clínica Demostración DSI215",
    "departamento": "San Salvador",
    "municipio": "San Salvador",
    "direccion": "Ciudad Universitaria, Final 25 Avenida Norte",
    "telefono": "2511-2000",
    "horario": "Lunes a viernes, 7:00 a 16:00",
    "latitud": 13.7185,
    "longitud": -89.2020,
}

# Tabla 6 del documento de entrega. Si cambia allí, cambia aquí: son las
# credenciales que la tutora va a teclear.
CUENTAS = [
    {
        "email": "admin.demo@docrecordsv.sv",
        "password": "DemoAdmin2026",
        "nombres": "Admin",
        "apellidos": "Demo",
        "rol_id": ROL_ADMIN,
        "rol": "ADMIN",
    },
    {
        "email": "medico.demo@docrecordsv.sv",
        "password": "DemoMedico2026",
        "nombres": "Médico",
        "apellidos": "Demo",
        "rol_id": ROL_MEDICO,
        "rol": "MEDICO",
    },
    {
        "email": "enfermera.demo@docrecordsv.sv",
        "password": "DemoEnfermera2026",
        "nombres": "Enfermera",
        "apellidos": "Demo",
        "rol_id": ROL_ENFERMERA,
        "rol": "ENFERMERA",
    },
    {
        "email": "paciente.demo@docrecordsv.sv",
        "password": "DemoPaciente2026",
        "nombres": "Paciente",
        "apellidos": "Demo",
        "rol_id": ROL_PACIENTE,
        "rol": "PACIENTE",
    },
]


def pedir(ruta, cuerpo=None, token=None, metodo=None):
    """Misma forma que en verificar-criterios.py: devuelve (código, json)."""
    datos = json.dumps(cuerpo, ensure_ascii=False).encode("utf-8") if cuerpo is not None else None
    req = urllib.request.Request(BASE + ruta, data=datos,
                                 method=metodo or ("POST" if datos is not None else "GET"))
    req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            texto = r.read().decode("utf-8")
            return r.status, (json.loads(texto) if texto else None)
    except urllib.error.HTTPError as e:
        bruto = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(bruto)
        except Exception:
            return e.code, bruto
    except urllib.error.URLError as e:
        return 0, str(e.reason)


def resolver_base(url):
    """La base de la API, se le pase el dominio de la app o el de la API.

    Hay dos válidas en el despliegue -- https://docrecordsv.duckdns.org/api y
    https://api-docrecordsv.duckdns.org sin prefijo -- y acertar de memoria cuál
    lleva /api no es razonable. Equivocarse tampoco avisa: el proxy responde
    «Debe enviar token Bearer» con 401 a TODO lo que no sabe enrutar, incluido
    /auth/login, así que el error parece de credenciales y no de URL.

    Se decide probando, no adivinando: GET /especialidades es público
    (BasicConfiguration), así que contesta 200 en la base correcta sin necesidad
    de haber iniciado sesión todavía.
    """
    global BASE
    limpia = url.rstrip("/")
    for candidata in (limpia, limpia + "/api"):
        BASE = candidata
        cod, _ = pedir("/especialidades")
        if cod == 200:
            return candidata
    return None


def detalle(cuerpo):
    """El mensaje del backend, para que un fallo diga qué pasó y no solo un número."""
    if isinstance(cuerpo, dict):
        return cuerpo.get("message") or cuerpo.get("error") or json.dumps(cuerpo, ensure_ascii=False)
    return str(cuerpo)


def buscar_usuario(token, email):
    """El usuario con ese correo, o None.

    Pagina de verdad en vez de pedir una página enorme: GET /user/all recibe
    `inicio` como NÚMERO DE PÁGINA (PageRequest.of(inicio, fin)), no como
    desplazamiento, y en la base de la demostración ya hubo cientos de usuarios.
    Un `fin` gigante funciona hasta que deja de hacerlo, y falla en silencio:
    el correo simplemente «no está» y el script crearía una cuenta duplicada.
    """
    pagina = 0
    while True:
        cod, cuerpo = pedir(f"/user/all?inicio={pagina}&fin=100", token=token)
        if cod != 200 or not isinstance(cuerpo, list):
            raise SystemExit(f"No se pudo listar usuarios (HTTP {cod}): {detalle(cuerpo)}")
        for u in cuerpo:
            if (u.get("email") or "").lower() == email.lower():
                return u
        if len(cuerpo) < 100:
            return None
        pagina += 1


def tiene_rol(usuario, nombre):
    return any((r.get("name") or "").upper().replace("ROLE_", "") == nombre
               for r in usuario.get("roles") or [])


def asegurar_clinica(token):
    """El id de la sede de demostración, creándola si no está."""
    cod, cuerpo = pedir("/clinics", token=token)
    if cod == 200 and isinstance(cuerpo, list):
        for c in cuerpo:
            if (c.get("name") or "").strip().lower() == CLINICA["name"].lower():
                print(f"  sede: ya existe (id {c['clinicaId']})")
                return c["clinicaId"]
    elif cod != 200:
        raise SystemExit(f"No se pudo listar clínicas (HTTP {cod}): {detalle(cuerpo)}")

    cod, cuerpo = pedir("/clinics", CLINICA, token=token)
    if cod not in (200, 201):
        raise SystemExit(f"No se pudo crear la sede (HTTP {cod}): {detalle(cuerpo)}")
    print(f"  sede: creada (id {cuerpo['clinicaId']})")
    return cuerpo["clinicaId"]


def especialidad_por_defecto(token):
    """El id de 'Medicina General', para el alta del médico.

    Se busca por NOMBRE y no se escribe el 1 a mano: los ids de
    `especialidades` salen de un SERIAL de la V2 y nada garantiza que el
    primero siga siendo ese si mañana alguien reordena el catálogo.
    """
    cod, cuerpo = pedir("/especialidades", token=token)
    if cod != 200 or not cuerpo:
        raise SystemExit(f"No se pudo leer el catálogo de especialidades (HTTP {cod}): {detalle(cuerpo)}")
    for e in cuerpo:
        if (e.get("nombre") or "").strip().lower() == "medicina general":
            return e["especialidadId"]
    return cuerpo[0]["especialidadId"]


def crear_cuenta(token, cuenta, especialidad_id):
    """Da de alta la cuenta y devuelve su userId. Si ya existía, la reutiliza."""
    existente = buscar_usuario(token, cuenta["email"])
    if existente:
        print(f"  {cuenta['email']}: ya existe (id {existente['userId']})")
        return existente["userId"], existente

    if cuenta["rol"] == "MEDICO":
        # La única alta que crea la fila en `medicos`. Ver la cabecera.
        cod, cuerpo = pedir("/auth/register", {
            "nombres": cuenta["nombres"],
            "apellidos": cuenta["apellidos"],
            "email": cuenta["email"],
            "password": cuenta["password"],
            "especialidadId": especialidad_id,
        })
    else:
        # `userName` se parte por el primer espacio en nombres/apellidos
        # (UserService.dividirNombreCompleto), así que va el nombre completo.
        cod, cuerpo = pedir("/user", {
            "email": cuenta["email"],
            "userName": f"{cuenta['nombres']} {cuenta['apellidos']}",
            "password": cuenta["password"],
        }, token=token)

    if cod not in (200, 201):
        raise SystemExit(f"No se pudo crear {cuenta['email']} (HTTP {cod}): {detalle(cuerpo)}")
    print(f"  {cuenta['email']}: creada (id {cuerpo['userId']})")
    return cuerpo["userId"], None


def habilitar(token, user_id, cuenta, existente):
    """Pone la contraseña documentada y habilita la cuenta.

    Se salta solo en un caso: la cuenta ya es ADMIN. Ahí el backend responde
    403 a propósito -- un administrador no puede tocar la contraseña de otro --
    y no es un fallo del script sino la regla funcionando. Pasa en la segunda
    corrida, cuando admin.demo ya quedó creada y promovida.
    """
    if existente and tiene_rol(existente, "ADMIN"):
        print(f"  {cuenta['email']}: ya es ADMIN, no se le puede reasignar la contraseña "
              f"desde otra cuenta admin (correcto). Se deja como está.")
        return True

    cod, cuerpo = pedir(f"/user/{user_id}/password", {"password": cuenta["password"]}, token=token)
    if cod != 200:
        print(f"  AVISO {cuenta['email']}: no se pudo asignar contraseña (HTTP {cod}): {detalle(cuerpo)}")
        return False
    print(f"  {cuenta['email']}: contraseña asignada y cuenta habilitada")
    return True


def asignar_rol(token, user_id, cuenta):
    """El rol. Un 409 significa que ya lo tiene, que es el estado buscado."""
    if cuenta["rol"] == "MEDICO":
        # /auth/register ya lo asignó junto con la fila en `medicos`.
        return
    cod, cuerpo = pedir(f"/user/{user_id}/role/{cuenta['rol_id']}", token=token, metodo="POST")
    if cod in (200, 201):
        extra = " (y su ficha en enfermería)" if cuenta["rol"] == "ENFERMERA" else ""
        print(f"  {cuenta['email']}: rol {cuenta['rol']} asignado{extra}")
    elif cod == 409:
        print(f"  {cuenta['email']}: ya tenía el rol {cuenta['rol']}")
    else:
        print(f"  AVISO {cuenta['email']}: no se pudo asignar {cuenta['rol']} "
              f"(HTTP {cod}): {detalle(cuerpo)}")


def asignar_clinica(token, user_id, cuenta, clinica_id):
    # Solo a quien opera en una sede. Ver la cabecera.
    if cuenta["rol"] == "PACIENTE":
        print(f"  {cuenta['email']}: sin sede (no opera en ninguna clínica)")
        return
    cod, cuerpo = pedir(f"/user/{user_id}/clinica/{clinica_id}", token=token, metodo="POST")
    if cod in (200, 201):
        print(f"  {cuenta['email']}: sede asignada")
    elif cod == 409:
        print(f"  {cuenta['email']}: ya tenía la sede")
    else:
        print(f"  AVISO {cuenta['email']}: no se pudo asignar la sede "
              f"(HTTP {cod}): {detalle(cuerpo)}")


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2

    if resolver_base(sys.argv[1]) is None:
        print(f"No hay una API de DocRecord en {sys.argv[1]} (ni ahí ni en /api).")
        return 1
    correo_admin = sys.argv[2]
    clave_admin = getpass.getpass(f"Contraseña de {correo_admin}: ")

    print(f"\n== Sesión de administrador en {BASE}")
    cod, sesion = pedir("/auth/login", {"email": correo_admin, "password": clave_admin})
    if cod != 200 or not isinstance(sesion, dict) or not sesion.get("token"):
        print(f"No se pudo iniciar sesión (HTTP {cod}): {detalle(sesion)}")
        return 1
    token = sesion["token"]
    if not any((r.get("name") or "").upper().replace("ROLE_", "") == "ADMIN"
               for r in sesion.get("roles") or []):
        print("Esa cuenta no tiene rol ADMIN. Casi todo lo que sigue responderá 403.")
        return 1
    print("  sesión iniciada")

    print("\n== Sede de demostración")
    clinica_id = asegurar_clinica(token)
    especialidad_id = especialidad_por_defecto(token)

    print("\n== Cuentas")
    for cuenta in CUENTAS:
        user_id, existente = crear_cuenta(token, cuenta, especialidad_id)
        habilitar(token, user_id, cuenta, existente)
        asignar_rol(token, user_id, cuenta)
        asignar_clinica(token, user_id, cuenta, clinica_id)

    # Lo único que demuestra que quedó utilizable. Las altas pueden responder
    # 201 y la cuenta seguir sin poder entrar -- deshabilitada, sin rol, sin
    # sede -- y eso no se ve hasta que alguien lo intenta. Que lo intente el
    # script y no la tutora.
    print("\n== Comprobación: inicio de sesión de las cuatro")
    fallos = 0
    for cuenta in CUENTAS:
        cod, cuerpo = pedir("/auth/login", {"email": cuenta["email"], "password": cuenta["password"]})
        if cod == 200 and isinstance(cuerpo, dict) and cuerpo.get("token"):
            roles = ", ".join(sorted((r.get("name") or "") for r in cuerpo.get("roles") or [])) or "sin roles"
            print(f"  [OK   ] {cuenta['email']}  ->  {roles}")
        else:
            fallos += 1
            print(f"  [FALLA] {cuenta['email']}  ->  HTTP {cod}: {detalle(cuerpo)}")

    print()
    if fallos:
        print(f"{fallos} de {len(CUENTAS)} cuentas no pueden iniciar sesión.")
        return 1
    print(f"Las {len(CUENTAS)} cuentas entran. Sede asignada: {CLINICA['name']}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
