package ues.edu.sv.education.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Integer> {

    /**
     * La cuenta con ESE correo, comparando sin distinguir mayusculas.
     *
     * ── Aqui decia `findByEmailIgnoreCase` ──────────────────────
     * Es decir, `LIKE '%correo%'`: una BUSQUEDA POR SUBCADENA usada para
     * autenticar. Un correo que no existe pero que es subcadena de uno real
     * devolvia esa cuenta, y con la contrasena correcta emitia su token. Se
     * comprobo ejecutandolo: `osa.portillo@ues.edu.sv` -inexistente- devolvia
     * un JWT valido de `rosa.portillo@ues.edu.sv`.
     *
     * El dano no se queda en eso. Como el metodo devuelve Optional, en cuanto
     * dos correos de la base son subcadena uno del otro Spring Data encuentra
     * dos filas y lanza IncorrectResultSizeDataAccessException: el login de
     * ambas cuentas pasa a responder 500 sin explicacion.
     *
     * Lo usaban el login (AuthService), el UserDetailsService de Spring
     * Security, el alta de enfermeria y el borrado por correo: en los cuatro
     * casos lo que se quiere es UNA cuenta concreta, nunca un parecido.
     *
     * IgnoreCase se conserva a proposito: HU-02 exige que quien se registro
     * escribiendo el correo con mayusculas pueda entrar igual. El otro lado de
     * esa regla -normalizar al GUARDAR- vive en V11 y en los servicios de alta.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    Page<User> findAll(Pageable pageable);

    // Usado por AdminBootstrap para decidir si ya existe un administrador
    // antes de crear el primero al arrancar.
    //
    // Con @Query explicito y no derivado (existsByRolesName): la entidad
    // declara su clave con el nombre de campo `UserID` (mayuscula, ver
    // User.java), y la version derivada de "exists" que genera Spring Data
    // proyecta esa clave como `u.userID` (decapitalizado a la JavaBean) para
    // optimizar el EXISTS, lo que Hibernate no puede resolver contra el
    // atributo real `UserID` del metamodelo -- revienta con
    // UnknownPathException en tiempo de arranque. Contando filas en vez de
    // proyectar el id se evita el problema por completo.
    @Query("select count(u) > 0 from User u join u.roles r where r.name = :name")
    boolean existsByRolesName(@Param("name") String name);

    // Usado por las pruebas de AdminBootstrap para verificar cuantos
    // usuarios tienen un rol dado, sin depender de cuantos administradores
    // haya creado el resto de la suite para sus propios casos.
    List<User> findByRolesName(String name);

    // La cuenta de acceso de una persona concreta. La usa EnfermeraService
    // para decir, en el listado de enfermeria, con que correo entra cada una.
    //
    // Existe para no resolverlo con findAll() y un filtro en memoria, que es
    // como estaba escrito primero: eso trae la tabla `users` entera por cada
    // enfermera del listado.
    Optional<User> findByPersona_PersonaId(Long personaId);
}
