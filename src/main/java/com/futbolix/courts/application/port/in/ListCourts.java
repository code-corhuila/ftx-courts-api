package com.futbolix.courts.application.port.in;

import com.futbolix.courts.domain.Court;
import java.util.List;

/** FR-001: the four courts, ordered by name. */
public interface ListCourts {
    List<Court> listCourts();
}
