package com.trainbooking.it25102925;
import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25102925.model.Notification;
import com.trainbooking.it25102925.repository.*;
import com.trainbooking.it25102925.service.*;
import com.trainbooking.it25102327.repository.*;
import com.trainbooking.it25103308.repository.BookingRepository;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    NotificationRepository repository;NotificationService service;Notification active,archived;
    @BeforeEach void setup(){repository=mock(NotificationRepository.class);service=new NotificationService(repository,mock(EmailService.class),mock(TrainRepository.class),mock(BookingRepository.class),mock(RebookingTokenRepository.class),mock(ScheduleRepository.class));
        active=Notification.builder().id(1L).subject("Current alert").isRead(false).archived(false).build();archived=Notification.builder().id(2L).subject("Archived alert").isRead(false).archived(true).build();}
    @Test void ownedListSeparatesActiveUnreadAndArchivedWithoutInventedTrainNumber(){when(repository.findOwned(10L)).thenReturn(List.of(archived,active));assertEquals(List.of(active),service.list(10L,"ALL"));assertEquals(List.of(active),service.list(10L,"UNREAD"));assertEquals(List.of(archived),service.list(10L,"ARCHIVED"));assertEquals("Not recorded",active.getTrainNumber());}
    @Test void markAllReadDoesNotChangeArchivedAlerts(){when(repository.findOwned(10L)).thenReturn(List.of(archived,active));service.markAllAsRead(10L);assertTrue(active.getIsRead());assertFalse(archived.getIsRead());verify(repository,never()).save(archived);}
    @Test void archiveAndRestoreKeepTheOwnedRecord(){when(repository.findByIdAndRecipientId(1L,10L)).thenReturn(Optional.of(active));service.archive(1L,10L,true);assertTrue(active.getArchived());service.archive(1L,10L,false);assertFalse(active.getArchived());verify(repository,times(2)).save(active);verify(repository,never()).delete(any());}
    @Test void anotherPassengersRecordCannotBeReadMarkedOrArchived(){assertThrows(ResourceNotFoundException.class,()->service.markRead(1L,99L));assertThrows(ResourceNotFoundException.class,()->service.archive(1L,99L,true));verify(repository,never()).save(any());}
    @Test void unsupportedFilterAndMissingAccountAreRejected(){assertThrows(IllegalArgumentException.class,()->service.list(10L,"INVALID"));assertThrows(ResourceNotFoundException.class,()->service.list(null,"ALL"));verifyNoInteractions(repository);}
    @Test void legacyRebookingApiCannotConfirmOrMoveAnUncheckedReservation(){assertThrows(IllegalStateException.class,()->service.claimRebooking("legacy",99L));verifyNoInteractions(repository);}
    @Test void singleOwnedReadUpdateDoesNotRemoveTheNotification(){when(repository.findByIdAndRecipientId(1L,10L)).thenReturn(Optional.of(active));service.markRead(1L,10L);assertTrue(active.getIsRead());verify(repository).save(active);verify(repository,never()).delete(any());}
}
