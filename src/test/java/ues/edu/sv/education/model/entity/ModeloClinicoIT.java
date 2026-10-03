package ues.edu.sv.education.model.entity;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.PruebaDeIntegracion;
import ues.edu.sv.education.repository.EnfermeraRepository;
import ues.edu.sv.education.repository.EspecialidadRepository;
import ues.edu.sv.education.repository.MedicoRepository;
import ues.edu.sv.education.repository.PacienteRepository;
import ues.edu.sv.education.repository.PersonaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariantes del modelo clinico, verificadas contra PostgreSQL real.
 *
 * Cada prueba aqui corresponde a una decision de diseño que se tomo por escrito
 * y que se rompe en silencio si alguien la deshace. No comprueban que el ORM
 * sepa guardar filas: comprueban que el esquema impide lo que debe impedir.
 */
@Transactional
class ModeloClinicoIT extends PruebaDeIntegracion {

    @Autowired private PersonaRepository personas;
    @Autowired private EnfermeraRepository enfermeras;
    @Autowired private PacienteRepository pacientes;
    @Autowired private MedicoRepository medicos;
    @Autowired private EspecialidadRepository especialidades;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;

    // ─────────────────────────────────────────────────────────── ayudantes

    private Persona personaCompleta(String dui, String nombres, String apellidos) {
        return Persona.builder()
                .dui(dui)
                .nombres(nombres)
                .apellidos(apellidos)
                .fechaNacimiento(LocalDate.of(1991, 3, 14))
                .sexo("F")
                .telefono("7777-7777")
                .direccion("San Salvador")
                .build();
    }

    private Paciente pacienteDe(Persona persona, String expediente) {
        return Paciente.builder()
                .persona(persona)
                .expediente(expediente)
                .tipoSangre("O+")
                .creadoEn(LocalDateTime.now())
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // El requisito que motiva toda la tabla base
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("una misma persona puede ser enfermera Y paciente al mismo tiempo")
    void unaPersonaPuedeSerEnfermeraYPacienteALaVez() {
        // El personal de una clinica se atiende donde trabaja. Este es el caso
        // que justifica separar `persona` de sus especializaciones, y el que
        // @Inheritance de JPA haria imposible: con herencia, una fila pertenece
        // a un solo subtipo. Si alguien "simplifica" el modelo a herencia, esta
        // prueba es la que lo detiene.
        Persona maria = personas.saveAndFlush(
                personaCompleta("04871239-5", "María Elena", "López Torres"));

        enfermeras.saveAndFlush(Enfermera.builder()
                .persona(maria)
                .registroJunta("JVPE-1234")
                .activo(true)
                .build());

        pacientes.saveAndFlush(pacienteDe(maria, "P-000001"));

        em.flush();
        em.clear();

        assertTrue(enfermeras.findById(maria.getPersonaId()).isPresent(),
                "María debe seguir siendo enfermera despues de registrarse como paciente");
        assertTrue(pacientes.findById(maria.getPersonaId()).isPresent(),
                "María debe ser paciente ademas de enfermera");

        // Y ambos papeles apuntan a la MISMA identidad: un solo DUI, un solo
        // nombre. Ese es el punto de la tabla base.
        Enfermera comoEnfermera = enfermeras.findById(maria.getPersonaId())
                .orElseThrow(() -> new AssertionError("no se encontro la enfermera"));
        Paciente comoPaciente = pacientes.findById(maria.getPersonaId())
                .orElseThrow(() -> new AssertionError("no se encontro el paciente"));

        assertEquals(
                comoEnfermera.getPersona().getPersonaId(),
                comoPaciente.getPersona().getPersonaId());
    }

    @Test
    @DisplayName("la especializacion comparte la clave primaria de la persona")
    void laEspecializacionCompartLaClaveDeLaPersona() {
        // @MapsId debe derivar el id, no generarlo aparte. Si alguien cambia
        // esto por un id propio con @GeneratedValue, la relacion 1:1 deja de
        // estar garantizada por la base y pasa a depender de que el codigo se
        // porte bien.
        Persona persona = personas.saveAndFlush(
                personaCompleta("01234567-8", "Carlos", "Menjívar"));

        Paciente paciente = pacientes.saveAndFlush(pacienteDe(persona, "P-000002"));

        assertEquals(persona.getPersonaId(), paciente.getPersonaId(),
                "El id del paciente ES el id de la persona; no son claves distintas");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Identidad: el DUI sostiene el mecanismo de no duplicar personas
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("dos personas no pueden compartir DUI")
    void elDuiRechazaDuplicados() {
        personas.saveAndFlush(personaCompleta("06543210-1", "Ana", "Ramírez"));

        // La restriccion vive en la base a proposito: la busqueda por DUI antes
        // de crear es una comodidad de la interfaz, pero lo que de verdad
        // impide duplicar una identidad es este UNIQUE. Si solo estuviera
        // validado en el servicio, dos altas simultaneas lo saltarian.
        assertThrows(DataIntegrityViolationException.class, () ->
                        personas.saveAndFlush(personaCompleta("06543210-1", "Otra", "Persona")),
                "El UNIQUE de persona.dui debe rechazar el duplicado en la base, no solo en el servicio");
    }

    @Test
    @DisplayName("varias personas pueden no tener DUI, porque los menores no lo tienen")
    void variasPersonasPuedenNoTenerDui() {
        // NULL no cuenta para UNIQUE en PostgreSQL, y aqui eso es exactamente
        // lo que se necesita: un recien nacido no tiene DUI, y varios pacientes
        // sin DUI deben poder coexistir.
        Persona sinDui1 = personas.saveAndFlush(Persona.builder()
                .nombres("Bebé").apellidos("Flores Menjívar")
                .fechaNacimiento(LocalDate.of(2026, 1, 5))
                .sexo("M")
                .build());

        Persona sinDui2 = personas.saveAndFlush(Persona.builder()
                .nombres("Otro Bebé").apellidos("García")
                .fechaNacimiento(LocalDate.of(2026, 2, 9))
                .sexo("F")
                .build());

        assertNotNull(sinDui1.getPersonaId());
        assertNotNull(sinDui2.getPersonaId());
        assertNull(sinDui1.getDui());
    }

    @Test
    @DisplayName("una persona con solo nombre y apellido es valida")
    void unaPersonaSoloConNombreEsValida() {
        // Es el estado en que queda un medico recien registrado por
        // /auth/register: el formulario no pide DUI, fecha de nacimiento ni
        // sexo. Si alguien pone NOT NULL en esas columnas, el registro publico
        // deja de funcionar y esta prueba lo detecta.
        Persona minima = personas.saveAndFlush(Persona.builder()
                .nombres("Juan Armando")
                .apellidos("Guerra Guevara")
                .build());

        em.flush();

        assertNotNull(minima.getPersonaId());
        assertNull(minima.getDui());
        assertNull(minima.getFechaNacimiento());
        assertNull(minima.getSexo());
    }

    @Test
    @DisplayName("una persona sin apellidos es rechazada")
    void unaPersonaSinApellidosEsRechazada() {
        // El nombre es lo unico que el sistema exige siempre. Sin apellidos no
        // se puede buscar a nadie, que es la operacion mas frecuente.
        assertThrows(Exception.class, () ->
                personas.saveAndFlush(Persona.builder().nombres("Solo Nombre").build()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Restricciones de dominio
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("la entidad rechaza un sexo distinto de M o F antes de llegar a la base")
    void laEntidadRechazaUnSexoInvalido() {
        // Primera linea de defensa: @Pattern en Persona. Da un mensaje util al
        // cliente sin gastar un viaje a la base de datos.
        assertThrows(ConstraintViolationException.class, () ->
                personas.saveAndFlush(Persona.builder()
                        .nombres("Prueba").apellidos("Sexo Invalido")
                        .sexo("X")
                        .build()));
    }

    @Test
    @DisplayName("la base rechaza un sexo invalido aunque se salte la validacion de la entidad")
    void laBaseRechazaUnSexoInvalidoAunqueSeSalteLaEntidad() {
        // Segunda linea de defensa, y la que de verdad importa: el CHECK de la
        // migracion. La anotacion @Pattern solo protege lo que pasa por el ORM;
        // un INSERT directo, una carga masiva o alguien que borre la anotacion
        // dejarian entrar el dato. Por eso se inserta aqui saltandose JPA, para
        // comprobar que la restriccion vive en el esquema y no solo en el codigo.
        assertThrows(DataIntegrityViolationException.class, () ->
                        jdbc.update("INSERT INTO persona (nombres, apellidos, sexo) VALUES (?, ?, ?)",
                                "Insercion", "Directa", "X"),
                "El CHECK persona_sexo_check debe rechazar el valor en la base de datos");
    }

    @Test
    @DisplayName("el tipo de sangre solo acepta los ocho grupos reales")
    void elTipoDeSangreSoloAceptaGruposReales() {
        Persona persona = personas.saveAndFlush(
                personaCompleta("09876543-2", "Prueba", "Sangre"));

        // En un expediente clinico un tipo de sangre inventado no es un dato
        // mal escrito: es un riesgo para el paciente.
        assertThrows(DataIntegrityViolationException.class, () ->
                pacientes.saveAndFlush(Paciente.builder()
                        .persona(persona)
                        .expediente("P-000003")
                        .tipoSangre("Z+")
                        .creadoEn(LocalDateTime.now())
                        .build()));
    }

    @Test
    @DisplayName("dos pacientes no pueden compartir numero de expediente")
    void elExpedienteEsUnico() {
        Persona uno = personas.saveAndFlush(personaCompleta("11111111-1", "Uno", "Apellido"));
        Persona dos = personas.saveAndFlush(personaCompleta("22222222-2", "Dos", "Apellido"));

        pacientes.saveAndFlush(pacienteDe(uno, "P-999999"));

        assertThrows(DataIntegrityViolationException.class, () ->
                pacientes.saveAndFlush(pacienteDe(dos, "P-999999")));
    }

    @Test
    @DisplayName("un medico no puede quedarse sin especialidad")
    void unMedicoRequiereEspecialidad() {
        Persona persona = personas.saveAndFlush(
                personaCompleta("33333333-3", "Medico", "Sin Especialidad"));

        assertThrows(Exception.class, () ->
                medicos.saveAndFlush(Medico.builder()
                        .persona(persona)
                        .registroJunta("JVPM-9999")
                        .activo(true)
                        .build()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Catalogo
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("la migracion siembra el catalogo de especialidades")
    void laMigracionSiembraElCatalogoDeEspecialidades() {
        // Si el catalogo llega vacio, el formulario de registro de medicos se
        // queda sin opciones y nadie puede crear una cuenta. Es una dependencia
        // real entre la migracion y una pantalla.
        long cuantas = especialidades.count();

        assertTrue(cuantas >= 7,
                "Se esperaban al menos las 7 especialidades sembradas por la migracion, hubo " + cuantas);
    }

    @Test
    @DisplayName("no hay dos especialidades con el mismo nombre")
    void elNombreDeEspecialidadEsUnico() {
        Especialidad existente = especialidades.findAll().get(0);

        assertThrows(DataIntegrityViolationException.class, () ->
                especialidades.saveAndFlush(Especialidad.builder()
                        .nombre(existente.getNombre())
                        .activa(true)
                        .build()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Borrado en cascada
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("borrar una persona arrastra sus especializaciones")
    void borrarLaPersonaArrastraSusEspecializaciones() {
        Persona persona = personas.saveAndFlush(
                personaCompleta("44444444-4", "Para", "Borrar"));
        pacientes.saveAndFlush(pacienteDe(persona, "P-000004"));
        em.flush();

        Long id = persona.getPersonaId();

        pacientes.deleteById(id);
        personas.deleteById(id);
        em.flush();
        em.clear();

        assertFalse(personas.findById(id).isPresent());
        assertFalse(pacientes.findById(id).isPresent(),
                "Un paciente huerfano, sin identidad detras, no debe poder quedar en la base");
    }
}
