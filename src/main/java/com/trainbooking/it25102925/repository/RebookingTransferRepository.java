package com.trainbooking.it25102925.repository;
import com.trainbooking.it25102925.model.RebookingTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RebookingTransferRepository extends JpaRepository<RebookingTransfer,Long> {
    List<RebookingTransfer> findByBookingIdOrderByIdDesc(Long bookingId);
}
