package com.trainbooking.it25102925.events;
import com.trainbooking.it25100977.model.Payment;
import com.trainbooking.it25100977.repository.*;
import com.trainbooking.it25102925.model.*;
import com.trainbooking.it25102925.repository.*;
import com.trainbooking.it25102925.service.EmailService;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;

@Component @Order(10) @RequiredArgsConstructor @Slf4j
public class PassengerNotificationObserver implements ServiceChangeObserver {
    private final NotificationRepository notifications;
    private final BookingRepository bookings;
    private final RebookingTokenRepository tokens;
    private final PaymentRepository payments;
    private final TicketRepository tickets;
    private final EmailService email;
    private final jakarta.persistence.EntityManager entityManager;

    @Override @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public void onServiceChange(ServiceChange event) {
        LocalDateTime now=LocalDateTime.now();
        for(Booking initial:bookings.findByScheduleId(event.scheduleId()).stream().sorted(Comparator.comparing(Booking::getId)).toList()) {
            Booking booking=bookings.findByIdForUpdate(initial.getId()).orElse(null);
            if(booking==null) continue;
            if(entityManager!=null) entityManager.refresh(booking);
            if(booking.getSchedule()==null || !Objects.equals(booking.getSchedule().getId(),event.scheduleId()) || !Objects.equals(booking.getTravelDate(),event.serviceDate()) || booking.getStatus()==Booking.BookingStatus.CANCELLED
                || booking.getPassenger()==null || booking.getStatus()==Booking.BookingStatus.PENDING && (booking.getLockExpiresAt()==null || !booking.getLockExpiresAt().isAfter(now))) continue;
            if(booking.getTravelDate().isBefore(LocalDate.now().minusDays(1))) continue;
            if(!event.status().equals(com.trainbooking.it25102327.service.ServicePlan.resolve(booking.getSchedule(),event.serviceDate()).status())) continue;
            var passenger=booking.getPassenger();
            int threshold=passenger.getDelayAlertThresholdMinutes()==null?15:passenger.getDelayAlertThresholdMinutes();
            if(event.status().equals("DELAYED") && event.delayMinutes()<threshold) continue;
            if(notifications.existsByEventIdAndBookingId(event.eventId(),booking.getId())) continue;
            var train=booking.getSchedule().getTrain();
            String subject=booking.getTrainName()+" ("+train.getTrainNumber()+") · "+event.serviceDate()+" · "+event.status();
            String message="Your departure from "+booking.getOrigin()+" to "+booking.getDestination()+" on "+event.serviceDate()+" is "+event.status().toLowerCase(Locale.ROOT).replace('_',' ')
                +(event.delayMinutes()>0?" by "+event.delayMinutes()+" minutes":"")+". "+event.reason();
            String action=null;
            boolean paid=booking.getStatus()==Booking.BookingStatus.CONFIRMED && payments.findByBookingId(booking.getId()).filter(p->p.getStatus()==Payment.PaymentStatus.COMPLETED).isPresent();
            boolean unboarded=tickets.findByBookingId(booking.getId()).filter(t->!Boolean.TRUE.equals(t.getIsBoarded())).isPresent();
            if(event.status().equals("CANCELLED") && paid && unboarded) {
                var token=tokens.findByBookingIdAndIsRedeemedFalse(booking.getId()).stream().filter(t->!t.isExpired() && Objects.equals(t.getSourceScheduleId(),event.scheduleId()) && Objects.equals(t.getSourceDate(),event.serviceDate())).findFirst().orElse(null);
                if(token==null) token=tokens.save(RebookingToken.builder().booking(booking).token(UUID.randomUUID().toString()).isRedeemed(false)
                    .sourceScheduleId(event.scheduleId()).sourceDate(event.serviceDate()).sourceClass(booking.getSeatClass().name()).expiresAt(now.plusHours(24)).build());
                action="/rebooking/claim/"+token.getToken();message+=" Choose a compatible available replacement within 24 hours of your original departure. Your paid amount remains unchanged.";
            }
            boolean shouldEmail=!Boolean.FALSE.equals(passenger.getNotifyByEmail());
            boolean shouldSms=!Boolean.FALSE.equals(passenger.getNotifyBySms());
            Notification saved=notifications.save(Notification.builder().recipient(passenger).subject(subject).message(message).type(Notification.NotificationType.IN_APP)
                .isRead(false).archived(false).eventId(event.eventId()).bookingId(booking.getId()).serviceDate(event.serviceDate()).recordedTrainNumber(train.getTrainNumber())
                .actionUrl(action).emailResult(shouldEmail?"PENDING":"NOT_REQUESTED").smsSimulated(shouldSms && passenger.getPhone()!=null).build());
            final String body=message+(action==null?"":"\nOpen your signed-in TrainBook account: "+action);
            if(shouldSms && passenger.getPhone()!=null) log.info("SMS simulation recorded for notification #{}; no SMS gateway delivery is claimed",saved.getId());
            if(shouldEmail) {
                Runnable dispatch=()->{
                    String result="FAILED";
                    try {var sent=email.sendSimpleEmail(passenger.getEmail(),subject,body);result=sent==null?"FAILED":sent.name();}
                    catch(Exception failure){log.warn("Notification email failed for #{}",saved.getId());}
                    try{notifications.updateEmailResult(saved.getId(),result);}catch(Exception failedStatus){log.warn("Notification delivery status could not be recorded for #{}",saved.getId());}
                };
                if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){dispatch.run();}});
                else dispatch.run();
            }
        }
    }
}
