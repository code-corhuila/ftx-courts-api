package com.futbolix.courts.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.futbolix.courts.application.CourtNameTakenException;
import com.futbolix.courts.domain.Court;
import com.futbolix.courts.domain.CourtStatus;
import com.futbolix.courts.domain.Money;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Runs against a real PostgreSQL with the real migrations of the sibling ftx-courts-db clone
 * (system property courts.db.dir), connected as courts_app with only the rights ftx-courts-db
 * grants it. Database: FTX_IT_JDBC_URL / FTX_IT_ADMIN_USER / FTX_IT_ADMIN_PASSWORD when set,
 * otherwise a Testcontainers PostgreSQL 16 (CI).
 */
public class JdbcCourtRepositoryIntegrationTest {

    static final UUID COURT_1 = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    static final UUID COURT_2 = UUID.fromString("c0000000-0000-4000-8000-000000000002");

    static PostgreSQLContainer<?> container;
    static JdbcClient admin;
    static JdbcClient app;
    static JdbcCourtRepository repository;
    /** Shared with CourtsApiApplicationTest, which reuses this database preparation. */
    public static String url;
    public static String appPassword;

    /** Fresh courts schema from the real ftx-courts-db migrations and a courts_app user. */
    @BeforeAll
    public static void migrateRealSchema() {
        Path migrations = Path.of(System.getProperty("courts.db.dir", "../ftx-courts-db"));
        Assumptions.assumeTrue(Files.isDirectory(migrations.resolve("01_ddl")),
                "ftx-courts-db not found at " + migrations.toAbsolutePath());

        url = System.getenv("FTX_IT_JDBC_URL");
        String adminUser = System.getenv("FTX_IT_ADMIN_USER");
        String adminPassword = System.getenv("FTX_IT_ADMIN_PASSWORD");
        if (url == null) {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                    "no FTX_IT_JDBC_URL and no Docker: skipped");
            container = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("ftx");
            container.start();
            url = container.getJdbcUrl();
            adminUser = container.getUsername();
            adminPassword = container.getPassword();
        }

        admin = JdbcClient.create(dataSource(url, adminUser, adminPassword));
        admin.sql("DROP SCHEMA IF EXISTS courts CASCADE").update();
        admin.sql("DROP ROLE IF EXISTS courts_writer").update();
        admin.sql("DROP ROLE IF EXISTS courts_reader").update();
        appPassword = UUID.randomUUID().toString();
        boolean exists = admin.sql("SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'courts_app')")
                .query(Boolean.class).single();
        admin.sql((exists ? "ALTER" : "CREATE") + " ROLE courts_app LOGIN PASSWORD '" + appPassword + "'").update();
        admin.sql("SELECT format('GRANT CONNECT ON DATABASE %I TO courts_app', current_database())")
                .query(String.class).optional().ifPresent(grant -> admin.sql(grant).update());

        Flyway.configure()
                .dataSource(url, adminUser, adminPassword)
                .locations("filesystem:" + migrations.resolve("01_ddl"),
                        "filesystem:" + migrations.resolve("02_dml"),
                        "filesystem:" + migrations.resolve("03_dcl"))
                .schemas("courts").defaultSchema("courts")
                .load().migrate();

        app = JdbcClient.create(dataSource(url, "courts_app", appPassword));
        repository = new JdbcCourtRepository(app);
    }

    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
    }

    static PGSimpleDataSource dataSource(String url, String user, String password) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(url);
        ds.setUser(user);
        ds.setPassword(password);
        return ds;
    }

    @Test
    void readsTheFourSeededCourts() {
        List<Court> courts = repository.findAll();
        assertThat(courts).hasSize(4);
        assertThat(courts).extracting(Court::id).contains(COURT_1, COURT_2);
        Court one = repository.findById(COURT_1).orElseThrow();
        assertThat(one.name()).isEqualTo("Cancha 1");
        assertThat(one.hourlyPrice()).isEqualTo(Money.cop(60000));
        assertThat(one.status()).isEqualTo(CourtStatus.ACTIVE);
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void namesAreComparedWhateverTheCase() {
        assertThat(repository.isNameTakenByAnotherCourt("CANCHA 2", COURT_1)).isTrue();
        assertThat(repository.isNameTakenByAnotherCourt("cancha 1", COURT_1)).isFalse();
    }

    @Test
    void updatePersistsAndTheTriggerRefreshesUpdatedAt() {
        Court court = repository.findById(COURT_2).orElseThrow();
        court.changeHourlyPrice(Money.cop(65000));
        court.changeStatus(CourtStatus.UNDER_MAINTENANCE);

        Court saved = repository.update(court);

        assertThat(saved.hourlyPrice()).isEqualTo(Money.cop(65000));
        assertThat(saved.status()).isEqualTo(CourtStatus.UNDER_MAINTENANCE);
        assertThat(saved.updatedAt()).isAfter(court.updatedAt());
        assertThat(repository.findById(COURT_2).orElseThrow().hourlyPrice()).isEqualTo(Money.cop(65000));
    }

    @Test
    void theUniqueIndexIsTheLastGuardAgainstDuplicateNames() {
        Court court = repository.findById(COURT_1).orElseThrow();
        court.rename("cancha 2");
        assertThatThrownBy(() -> repository.update(court)).isInstanceOf(CourtNameTakenException.class);
    }

    @Test
    void theApplicationUserCannotCreateOrDeleteCourts() {
        assertThatThrownBy(() -> app.sql(
                "INSERT INTO courts.court (name, capacity, hourly_price) VALUES ('Cancha 5', 10, 60000)").update())
                .isInstanceOf(DataAccessException.class)
                .rootCause().isInstanceOf(java.sql.SQLException.class)
                .extracting(e -> ((java.sql.SQLException) e).getSQLState()).isEqualTo("42501"); // insufficient_privilege, any server language
        assertThatThrownBy(() -> app.sql("DELETE FROM courts.court").update())
                .isInstanceOf(DataAccessException.class)
                .rootCause().isInstanceOf(java.sql.SQLException.class)
                .extracting(e -> ((java.sql.SQLException) e).getSQLState()).isEqualTo("42501"); // insufficient_privilege, any server language
        assertThat(admin.sql("SELECT count(*) FROM courts.court").query(Long.class).single()).isEqualTo(4L);
    }
}
