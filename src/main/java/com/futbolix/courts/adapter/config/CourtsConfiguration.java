package com.futbolix.courts.adapter.config;

import com.futbolix.courts.application.CourtService;
import com.futbolix.courts.application.port.in.GetCourt;
import com.futbolix.courts.application.port.in.ListCourts;
import com.futbolix.courts.application.port.in.UpdateCourt;
import com.futbolix.courts.application.port.in.UpdateCourtCommand;
import com.futbolix.courts.application.port.out.CourtAuditTrail;
import com.futbolix.courts.application.port.out.CourtRepository;
import com.futbolix.courts.domain.Court;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

/** Wires the framework-free CourtService as the single bean behind the three input ports. */
@Configuration
public class CourtsConfiguration {

    @Bean
    CourtUseCases courtUseCases(CourtRepository courts, CourtAuditTrail audit, TransactionTemplate transactions) {
        return new CourtUseCases(new CourtService(courts, audit), transactions);
    }

    /**
     * An update reads, checks the name and writes in one transaction, so the uniqueness check
     * and the write see the same data.
     */
    static final class CourtUseCases implements ListCourts, GetCourt, UpdateCourt {

        private final CourtService service;
        private final TransactionTemplate transactions;

        CourtUseCases(CourtService service, TransactionTemplate transactions) {
            this.service = service;
            this.transactions = transactions;
        }

        @Override
        public List<Court> listCourts() {
            return service.listCourts();
        }

        @Override
        public Court getCourt(UUID courtId) {
            return service.getCourt(courtId);
        }

        @Override
        public Court updateCourt(UpdateCourtCommand command) {
            return transactions.execute(status -> service.updateCourt(command));
        }
    }
}
