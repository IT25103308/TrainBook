package com.trainbooking.it25100228.repository;

import com.trainbooking.it25100228.model.BoardingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link BoardingLog} audit trail and real-time station metrics.
 *
 * @author SLIIT Software Engineering Team (IT25100228)
 * @version 1.0.0
 */
@Repository
public interface BoardingLogRepository extends JpaRepository<BoardingLog, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select l from BoardingLog l where l.id = :id")
    java.util.Optional<BoardingLog> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    List<BoardingLog> findTop100ByOrderByIdDesc();

    /**
     * Counts boarding scans by result type.
     *
     * @param scanResult result status (VALID, DUPLICATE, etc.)
     * @return count of matching scans
     */
    long countByScanResult(BoardingLog.ScanResult scanResult);

    /**
     * Retrieves the latest 20 scans for live gate activity monitors.
     *
     * @return recent boarding log records
     */
    List<BoardingLog> findTop20ByOrderByScannedAtDesc();

    /**
     * Finds boarding logs associated with a specific ticket ID.
     *
     * @param ticketId ticket ID
     * @return list of boarding logs
     */
    List<BoardingLog> findByTicketId(Long ticketId);

    /**
     * Deletes all boarding logs referencing a ticket ID.
     *
     * @param ticketId ticket ID
     */
    void deleteByTicketId(Long ticketId);
}
