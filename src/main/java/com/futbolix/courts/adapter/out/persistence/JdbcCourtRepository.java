package com.futbolix.courts.adapter.out.persistence;

import com.futbolix.courts.application.CourtNameTakenException;
import com.futbolix.courts.application.port.out.CourtRepository;
import com.futbolix.courts.domain.Court;
import com.futbolix.courts.domain.CourtStatus;
import com.futbolix.courts.domain.Money;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * courts.courts of ftx-courts-db (table created by its V002). Connects as courts_app. This adapter
 * only reads and updates: it never inserts or deletes a court (BR-001), whatever the grants are.
 */
@Repository
public class JdbcCourtRepository implements CourtRepository {

    private static final String COLUMNS =
            "id, name, sport, capacity, hourly_price, status, created_at, updated_at";

    private final JdbcClient jdbc;

    public JdbcCourtRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Court> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM courts.courts").query(JdbcCourtRepository::toCourt).list();
    }

    @Override
    public Optional<Court> findById(UUID courtId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM courts.courts WHERE id = :id")
                .param("id", courtId)
                .query(JdbcCourtRepository::toCourt)
                .optional();
    }

    @Override
    public boolean isNameTakenByAnotherCourt(String name, UUID courtId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM courts.courts WHERE lower(name) = lower(:name) AND id <> :id)")
                .param("name", name)
                .param("id", courtId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public Court update(Court court) {
        try {
            return jdbc.sql("""
                    UPDATE courts.courts
                       SET name = :name, capacity = :capacity, hourly_price = :price, status = :status,
                           updated_at = now()
                     WHERE id = :id
                    RETURNING """ + " " + COLUMNS)
                    .param("name", court.name())
                    .param("capacity", court.capacity())
                    .param("price", BigDecimal.valueOf(court.hourlyPrice().amount()))
                    .param("status", court.status().name())
                    .param("id", court.id())
                    .query(JdbcCourtRepository::toCourt)
                    .single();
        } catch (DuplicateKeyException e) {
            // Two administrators renamed two courts to the same name at the same time:
            // the unique constraint on the name is the last guard.
            throw new CourtNameTakenException(court.name());
        }
    }

    private static Court toCourt(ResultSet rs, int rowNum) throws SQLException {
        return new Court(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("sport"),
                rs.getInt("capacity"),
                Money.cop(rs.getBigDecimal("hourly_price").longValueExact()),
                CourtStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
