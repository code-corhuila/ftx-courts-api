package com.futbolix.courts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.futbolix.courts.application.port.in.UpdateCourtCommand;
import com.futbolix.courts.application.port.out.CourtAuditTrail;
import com.futbolix.courts.application.port.out.CourtRepository;
import com.futbolix.courts.domain.Court;
import com.futbolix.courts.domain.CourtStatus;
import com.futbolix.courts.domain.InvalidCourtStatusTransitionException;
import com.futbolix.courts.domain.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CourtServiceTest {

    private static final UUID COURT_1 = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID COURT_2 = UUID.fromString("c0000000-0000-4000-8000-000000000002");

    private final InMemoryCourts courts = new InMemoryCourts();
    private final List<String> auditLines = new ArrayList<>();
    private final CourtAuditTrail audit = (admin, before, after) ->
            auditLines.add(admin + " " + before.status() + "->" + after.status() + " "
                    + before.hourlyPrice().amount() + "->" + after.hourlyPrice().amount());
    private final CourtService service = new CourtService(courts, audit);

    @BeforeEach
    void seed() {
        Instant t = Instant.parse("2026-11-10T12:00:00Z");
        courts.put(new Court(COURT_2, "Cancha 2", Court.FOOTBALL, 10, Money.cop(60000), CourtStatus.ACTIVE, t, t));
        courts.put(new Court(COURT_1, "Cancha 1", Court.FOOTBALL, 10, Money.cop(60000), CourtStatus.ACTIVE, t, t));
    }

    private static UpdateCourtCommand change(UUID id, Optional<String> name, Optional<Money> price,
                                             Optional<CourtStatus> status) {
        return new UpdateCourtCommand(id, "admin-1", name, Optional.empty(), price, status);
    }

    @Test
    void listsCourtsByName() {
        assertThat(service.listCourts()).extracting(Court::name).containsExactly("Cancha 1", "Cancha 2");
    }

    @Test
    void unknownCourtIsNotFound() {
        assertThatThrownBy(() -> service.getCourt(UUID.randomUUID())).isInstanceOf(CourtNotFoundException.class);
    }

    @Test
    void updatesAndAuditsWhoChangedWhat() {
        Court updated = service.updateCourt(change(COURT_1, Optional.empty(), Optional.of(Money.cop(70000)),
                Optional.of(CourtStatus.UNDER_MAINTENANCE)));

        assertThat(updated.hourlyPrice().amount()).isEqualTo(70000);
        assertThat(courts.findById(COURT_1)).get().extracting(Court::status).isEqualTo(CourtStatus.UNDER_MAINTENANCE);
        assertThat(auditLines).containsExactly("admin-1 ACTIVE->UNDER_MAINTENANCE 60000->70000");
    }

    @Test
    void anEmptyChangeIsRejected() {
        assertThatThrownBy(() -> service.updateCourt(change(COURT_1, Optional.empty(), Optional.empty(), Optional.empty())))
                .isInstanceOf(EmptyCourtUpdateException.class);
        assertThat(auditLines).isEmpty();
    }

    @Test
    void aNameUsedByAnotherCourtIsRejectedWhateverTheCase() {
        assertThatThrownBy(() -> service.updateCourt(change(COURT_1, Optional.of("CANCHA 2"), Optional.empty(), Optional.empty())))
                .isInstanceOf(CourtNameTakenException.class);
        assertThat(courts.findById(COURT_1)).get().extracting(Court::name).isEqualTo("Cancha 1");
    }

    @Test
    void keepingItsOwnNameIsNotAConflict() {
        Court updated = service.updateCourt(change(COURT_1, Optional.of("cancha 1"), Optional.empty(), Optional.empty()));
        assertThat(updated.name()).isEqualTo("cancha 1");
    }

    @Test
    void aForbiddenTransitionChangesNothing() {
        service.updateCourt(change(COURT_1, Optional.empty(), Optional.empty(), Optional.of(CourtStatus.INACTIVE)));
        auditLines.clear();

        assertThatThrownBy(() -> service.updateCourt(change(COURT_1, Optional.empty(), Optional.of(Money.cop(1000)),
                Optional.of(CourtStatus.UNDER_MAINTENANCE))))
                .isInstanceOf(InvalidCourtStatusTransitionException.class);
        assertThat(courts.findById(COURT_1)).get().satisfies(c -> {
            assertThat(c.status()).isEqualTo(CourtStatus.INACTIVE);
            assertThat(c.hourlyPrice().amount()).isEqualTo(60000);
        });
        assertThat(auditLines).isEmpty();
    }

    /** Stores copies, like a database: a change only lands through update(). */
    private static final class InMemoryCourts implements CourtRepository {
        private final Map<UUID, Court> rows = new LinkedHashMap<>();

        void put(Court court) {
            rows.put(court.id(), court.snapshot());
        }

        @Override
        public List<Court> findAll() {
            return rows.values().stream().map(Court::snapshot).toList();
        }

        @Override
        public Optional<Court> findById(UUID courtId) {
            return Optional.ofNullable(rows.get(courtId)).map(Court::snapshot);
        }

        @Override
        public boolean isNameTakenByAnotherCourt(String name, UUID courtId) {
            return rows.values().stream().anyMatch(c -> !c.id().equals(courtId) && c.name().equalsIgnoreCase(name));
        }

        @Override
        public Court update(Court court) {
            put(court);
            return court.snapshot();
        }
    }
}
