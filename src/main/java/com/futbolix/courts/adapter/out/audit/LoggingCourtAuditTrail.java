package com.futbolix.courts.adapter.out.audit;

import com.futbolix.courts.application.port.out.CourtAuditTrail;
import com.futbolix.courts.domain.Court;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * FR-036 through the structured log (logger "audit"): who changed which court and the values
 * before and after. No personal data is involved: courts hold none.
 */
@Component
public class LoggingCourtAuditTrail implements CourtAuditTrail {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    @Override
    public void courtUpdated(String administratorId, Court before, Court after) {
        audit.atInfo()
                .addKeyValue("event", "court.updated")
                .addKeyValue("administratorId", administratorId)
                .addKeyValue("courtId", after.id())
                .addKeyValue("nameBefore", before.name()).addKeyValue("nameAfter", after.name())
                .addKeyValue("capacityBefore", before.capacity()).addKeyValue("capacityAfter", after.capacity())
                .addKeyValue("priceBefore", before.hourlyPrice().amount()).addKeyValue("priceAfter", after.hourlyPrice().amount())
                .addKeyValue("statusBefore", before.status()).addKeyValue("statusAfter", after.status())
                .log("court updated");
    }
}
