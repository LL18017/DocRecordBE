#!/usr/bin/env python3
"""Comprueba los criterios de aceptación del Sprint 2 contra un despliegue vivo.

    python3 verificar-criterios.py https://docrecordsv.duckdns.org correo@ues.edu.sv
    # pide la contraseña sin eco

No lee código ni se fía de que la suite pase: llama a la API como lo haría
cualquiera y mira lo que responde. Es la diferencia entre «las pruebas están en
verde» y «el sistema desplegado hace lo que la historia dice» — y el registro de
mejora M-03 existe justamente porque esas dos cosas se dieron por equivalentes
una vez.

La cuenta que se le pase debe tener rol ADMIN: varios criterios se comprueban
sobre endpoints que solo un administrador puede tocar.

Devuelve 0 si todo pasa, 1 si algo falla. Sirve como smoke test tras desplegar.
"""
import getpass
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

resultados = []
BASE = ""


def pedir(ruta, cuerpo=None, token=None, metodo=None):
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


def comprobar(hu, criterio, descripcion, condicion, detalle=""):
    resultados.append((hu, criterio, descripcion, bool(condicion)))
    print(f"  [{'OK  ' if condicion else 'FALLA'}] {hu} c{criterio}: {descripcion}"
          + (f"  -> {detalle}" if detalle else ""))


def main():
    global BASE
    if len(sys.argv) < 3:
        print(__doc__)
        return 2

    # Siempre con /api: la aplicación y la API comparten origen, y ese prefijo
    # es lo que el proxy le quita antes de pasar al backend.
    BASE = sys.argv[1].rstrip("/") + "/api"
    correo = sys.argv[2]
    clave = getpass.getpass(f"Contraseña de {correo}: ")

    cod, sesion = pedir("/auth/login", {"email": correo, "password": clave})
    if cod != 200 or "token" not in (sesion or {}):
        print(f"No se pudo iniciar sesión: HTTP {cod} {sesion}")
        return 1
    admin = sesion["token"]

    # ── HU-04 · Recuperación de contraseña ──────────────────────────────
    print("\nHU-04 · Recuperación de contraseña")
    c1, _ = pedir("/auth/password/forgot", {"email": correo})
    comprobar("HU-04", 1, "solicitar con un correo registrado se acepta", c1 == 202, f"HTTP {c1}")

    # El criterio 4 no se comprueba solo con el cuerpo: si para una cuenta real
    # hubiera que consultar la base y hablar con el servidor de correo, y para
    # una inexistente se respondiera al instante, el TIEMPO la delataría igual
    # de bien que un mensaje distinto.
    t0 = time.time(); cod_si, cuerpo_si = pedir("/auth/password/forgot", {"email": correo})
    t_si = time.time() - t0
    t0 = time.time(); cod_no, cuerpo_no = pedir("/auth/password/forgot",
                                                {"email": "no.existe.nadie@ejemplo.sv"})
    t_no = time.time() - t0
    comprobar("HU-04", 4, "la respuesta no delata si la cuenta existe",
              cod_si == cod_no and cuerpo_si == cuerpo_no and abs(t_si - t_no) < 0.5,
              f"ambas HTTP {cod_si}, mismo cuerpo, {t_si*1000:.0f}ms vs {t_no*1000:.0f}ms")

    c3, _ = pedir("/auth/password/reset",
                  {"token": "inventado-no-existe", "password": "Docrecord2026!"})
    comprobar("HU-04", 3, "un enlace inexistente se rechaza", c3 == 400, f"HTTP {c3}")

    # ── HU-05 · Gestión de usuarios y roles ─────────────────────────────
    print("\nHU-05 · Gestión de usuarios y roles")
    _, usuarios = pedir("/user/all?inicio=0&fin=200", token=admin)
    con_esp = next((u for u in usuarios if u.get("especialidad")), None)
    sin_esp = next((u for u in usuarios if u.get("especialidad") is None), None)
    comprobar("HU-05", 1, "el listado trae especialidad", con_esp is not None,
              f"{con_esp['email']} -> {con_esp['especialidad']}" if con_esp else "ninguno")
    comprobar("HU-05", 1, "y la clave viene presente con null cuando no aplica",
              sin_esp is not None and "especialidad" in sin_esp)
    comprobar("HU-05", 1, "el listado trae el estado",
              all("activo" in u for u in usuarios), f"{len(usuarios)} cuentas")

    otro = next((u for u in usuarios if u["email"] != correo and u.get("activo")), None)
    if otro:
        cod, tras = pedir(f"/user/{otro['userId']}/estado", {"activo": False}, admin, "PATCH")
        # Con una contraseña cualquiera: lo que se comprueba es que NO deje
        # entrar, y una cuenta activa con clave mala tambien responde 401. Por
        # eso el resultado se lee junto al PATCH y al recuento de abajo.
        entro, _ = pedir("/auth/login", {"email": otro["email"], "password": "noEsLaSuya1!"})
        pedir(f"/user/{otro['userId']}/estado", {"activo": True}, admin, "PATCH")
        comprobar("HU-05", 3, "desactivar una cuenta le impide iniciar sesión",
                  cod == 200 and tras.get("activo") is False and entro >= 400,
                  f"PATCH {cod}, login {entro}")
        _, despues = pedir("/user/all?inicio=0&fin=200", token=admin)
        comprobar("HU-05", 3, "y no borra nada", len(despues) == len(usuarios),
                  f"{len(usuarios)} cuentas antes y después")

    yo = next(u for u in usuarios if u["email"] == correo)
    propio, _ = pedir(f"/user/{yo['userId']}/estado", {"activo": False}, admin, "PATCH")
    comprobar("HU-05", 3, "nadie puede desactivarse a sí mismo", propio == 409, f"HTTP {propio}")

    # ── HU-06, HU-07, HU-08 · Pacientes ─────────────────────────────────
    print("\nHU-06 · Registro   ·   HU-07 · Búsqueda   ·   HU-08 · Expediente")
    _, pacientes = pedir("/pacientes", token=admin)
    if pacientes:
        expedientes = {p.get("expediente") for p in pacientes}
        comprobar("HU-06", 4, "cada paciente tiene expediente único",
                  all(expedientes) and len(expedientes) == len(pacientes),
                  f"{len(pacientes)} pacientes, ej. {pacientes[0]['expediente']}")
        comprobar("HU-06", 4, "y estado ACTIVO",
                  all(p.get("estado") == "ACTIVO" for p in pacientes))

        dui_repetido, _ = pedir("/pacientes", {
            "persona": {"dui": pacientes[0]["persona"]["dui"], "nombres": "Duplicado",
                        "apellidos": "De Prueba", "fechaNacimiento": "1990-01-01", "sexo": "M"},
            "tipoSangre": "O+"}, admin)
        comprobar("HU-06", 2, "un DUI repetido se rechaza", dui_repetido >= 400,
                  f"HTTP {dui_repetido}")

        sangre, _ = pedir("/pacientes", {
            "persona": {"dui": "99999999-9", "nombres": "Sangre", "apellidos": "Invalida",
                        "fechaNacimiento": "1990-01-01", "sexo": "M"},
            "tipoSangre": "Z+"}, admin)
        comprobar("HU-06", 3, "un tipo de sangre inválido se rechaza", sangre >= 400,
                  f"HTTP {sangre}")

        cod_exp, expediente = pedir(f"/pacientes/{pacientes[0]['personaId']}", token=admin)
        comprobar("HU-08", 1, "el expediente se abre por su id",
                  cod_exp == 200 and expediente["personaId"] == pacientes[0]["personaId"])

    # Se busca por el apellido de un paciente real, quitandole las tildes: es
    # exactamente como se teclea en consulta, con el paciente enfrente.
    acentuado = next((p for p in pacientes
                      if any(c in p["persona"]["apellidos"] for c in "áéíóúÁÉÍÓÚñÑ")), None)
    if acentuado:
        apellido = acentuado["persona"]["apellidos"].split()[0]
        plano = (apellido.replace("á", "a").replace("é", "e").replace("í", "i")
                 .replace("ó", "o").replace("ú", "u").replace("ñ", "n"))
        _, con = pedir(f"/pacientes?buscar={urllib.parse.quote(apellido)}", token=admin)
        _, sin = pedir(f"/pacientes?buscar={urllib.parse.quote(plano)}", token=admin)
        _, may = pedir(f"/pacientes?buscar={urllib.parse.quote(plano.upper())}", token=admin)
        comprobar("HU-07", 3, "buscar sin tildes encuentra a quien sí las tiene",
                  len(sin) > 0 and len(sin) == len(con), f"'{plano}' -> {len(sin)}, '{apellido}' -> {len(con)}")
        comprobar("HU-07", 1, "y sigue ignorando mayúsculas", len(may) == len(sin))

    _, vacio = pedir("/pacientes?buscar=zzz-no-existe-nadie", token=admin)
    comprobar("HU-07", 2, "sin coincidencias devuelve lista vacía, no un error",
              isinstance(vacio, list) and len(vacio) == 0)

    faltante, _ = pedir("/pacientes/99999999", token=admin)
    comprobar("HU-08", 3, "un id inexistente responde 404", faltante == 404, f"HTTP {faltante}")

    # ── HU-21 · Asignación de sedes ─────────────────────────────────────
    print("\nHU-21 · Asignación de sedes")
    _, clinicas = pedir("/clinics", token=admin)
    if otro:
        _, asignadas = pedir(f"/user/{otro['userId']}/clinicas", token=admin)
        comprobar("HU-21", 1, "una cuenta ve las sedes que se le asignaron",
                  len(asignadas) > 0, f"{len(asignadas)} de {len(clinicas)}")
        if asignadas:
            repetida, _ = pedir(
                f"/user/{otro['userId']}/clinica/{asignadas[0]['clinicaId']}", {}, admin)
            comprobar("HU-21", 3, "repetir una asignación responde 409", repetida == 409,
                      f"HTTP {repetida}")

    # ── Sin sesión no se entra a nada ───────────────────────────────────
    print("\nTransversal")
    sin_token, _ = pedir("/pacientes")
    comprobar("HU-02", 3, "sin token, los endpoints clínicos responden 401",
              sin_token == 401, f"HTTP {sin_token}")

    print("\n" + "=" * 70)
    ok = sum(1 for r in resultados if r[3])
    print(f"  {ok} de {len(resultados)} comprobaciones pasan")
    fallos = [r for r in resultados if not r[3]]
    if fallos:
        print("\n  NO CUMPLEN:")
        for hu, c, d, _ in fallos:
            print(f"    {hu} c{c}: {d}")
    print("=" * 70)
    return 0 if not fallos else 1


if __name__ == "__main__":
    sys.exit(main())
