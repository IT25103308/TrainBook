package com.trainbooking.it25102925.service;
import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25102327.repository.*;
import com.trainbooking.it25102925.events.*;
import com.trainbooking.it25102925.model.Notification;
import com.trainbooking.it25102925.repository.*;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final TrainRepository trainRepository;
    private final BookingRepository bookingRepository;
    private final RebookingTokenRepository rebookingTokenRepository;
    private final ScheduleRepository scheduleRepository;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private ServiceChangeSubject subject;

    /** A fleet stop may affect multiple dated bookings; emit a distinct scope for each. */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void notifyAffectedPassengers(Long trainId,String status) {
        var train=trainRepository.findById(trainId).orElseThrow(()->new ResourceNotFoundException("Train not found."));
        if(status==null || !java.util.Set.of("ON_TIME","DELAYED","CANCELLED","MAINTENANCE","INACTIVE").contains(status)) throw new IllegalArgumentException("Choose a supported operational status.");
        if(subject==null) throw new IllegalStateException("The committed service-change publisher is unavailable.");
        var pairs=new LinkedHashSet<String>();LocalDateTime now=LocalDateTime.now();
        for(Booking booking:bookingRepository.findAll()) {
            if(booking.getSchedule()==null || booking.getSchedule().getTrain()==null || !Objects.equals(booking.getSchedule().getTrain().getId(),trainId)
                || booking.getTravelDate()==null || booking.getTravelDate().isBefore(LocalDate.now()) || booking.getStatus()==Booking.BookingStatus.CANCELLED
                || booking.getStatus()==Booking.BookingStatus.PENDING && (booking.getLockExpiresAt()==null || !booking.getLockExpiresAt().isAfter(now))) continue;
            String key=booking.getSchedule().getId()+":"+booking.getTravelDate();
            if(pairs.add(key)) subject.publishAfterCommit(ServiceChange.of(booking.getSchedule().getId(),booking.getTravelDate(),"FLEET",status,status.equals("DELAYED")?15:0,
                "Fleet operational status changed to "+status,"fleet operator"));
        }
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) @org.springframework.scheduling.annotation.Async public void notifyAffectedPassengersAsync(Long trainId,String status){notifyAffectedPassengers(trainId,status);}
    /** The old transfer API cannot bypass the owned, dated allocation transaction. */
    @Deprecated public Booking claimRebooking(String token,Long scheduleId){throw new IllegalStateException("Use the owned replacement flow with its selected travel date.");}
    public void sendEmail(String to,String subject,String body){emailService.sendSimpleEmail(to,subject,body);}
    @Transactional(readOnly=true) public List<Notification> getNotificationsForUser(Long userId){return list(userId,"ALL");}
    @Transactional(readOnly=true) public List<Notification> list(Long userId,String filter) {
        if(userId==null) throw new ResourceNotFoundException("Account not found.");
        String value=filter==null?"ALL":filter.toUpperCase(Locale.ROOT);
        if(!Set.of("ALL","UNREAD","ARCHIVED").contains(value)) throw new IllegalArgumentException("Choose All, Unread or Archived alerts.");
        return notificationRepository.findOwned(userId).stream().filter(n->value.equals("ARCHIVED")?Boolean.TRUE.equals(n.getArchived()):!Boolean.TRUE.equals(n.getArchived()))
            .filter(n->!value.equals("UNREAD") || !Boolean.TRUE.equals(n.getIsRead())).toList();
    }
    @Transactional public void markAllAsRead(Long userId){for(var n:list(userId,"ALL")){n.setIsRead(true);notificationRepository.save(n);}}
    @Transactional public void markRead(Long id,Long userId){var n=owned(id,userId);n.setIsRead(true);notificationRepository.save(n);}
    @Transactional public void archive(Long id,Long userId,boolean archived){var n=owned(id,userId);n.setArchived(archived);notificationRepository.save(n);}
    private Notification owned(Long id,Long userId){return notificationRepository.findByIdAndRecipientId(id,userId).orElseThrow(()->new ResourceNotFoundException("Notification not found."));}
}
