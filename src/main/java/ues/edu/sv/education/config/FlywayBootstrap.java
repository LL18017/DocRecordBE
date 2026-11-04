package ues.edu.sv.education.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/*
 * Este build de Spring Boot 4.1.0-SNAPSHOT (spring-boot-autoconfigure del
 * 2026-05-30, la ultima snapshot publicada) no trae FlywayAutoConfiguration
 * todavia, asi que tener flyway-core + flyway-database-postgresql en el
 * classpath no alcanza: Flyway nunca se dispara solo.
 *
 * Se ejecuta aqui a mano contra el propio bean DataSource. Al ser un
 * BeanPostProcessor, Spring garantiza que corre antes de que cualquier otro
 * bean -incluido el EntityManagerFactory de Hibernate- reciba una referencia
 * al DataSource, asi que la migracion siempre queda aplicada antes de que
 * ddl-auto=validate compare el esquema.
 *
 * Si una futura snapshot de Boot 4 agrega la autoconfiguracion oficial, esta
 * clase se puede borrar sin mas cambios (pasando outOfOrder a
 * spring.flyway.out-of-order=true).
 *
 * ── Por que outOfOrder ────────────────────────────────────────────────────
 * Los numeros de migracion se reparten por persona (V33 y V34 de Mario, V35
 * de Naun) y cada uno integra cuando termina. Sin outOfOrder, si V35 llega a
 * produccion antes que V33, la V33 que llega despues tumba el arranque con
 * "Detected resolved migration not applied to database", que es lo que paso
 * el 24 de octubre. Con outOfOrder la aplica igual, porque las migraciones
 * reservadas tocan tablas distintas y el orden entre ellas no importa.
 */
@Component
public class FlywayBootstrap implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource) {
            Flyway.configure()
                    .dataSource(dataSource)
                    .baselineOnMigrate(true)
                    .outOfOrder(true)
                    .load()
                    .migrate();

        }
        return bean;
    }
}
