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

    Optional<User> findByEmailContainingIgnoreCase(String email);

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
}
