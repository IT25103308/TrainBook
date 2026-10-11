package com.trainbooking.it25102327;
import com.trainbooking.it25102327.controller.TrainController;
import com.trainbooking.it25102327.dto.TrainDto;
import com.trainbooking.it25102327.model.Train;
import com.trainbooking.it25102327.service.*;
import com.trainbooking.it25102925.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.mock.web.*;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FleetStatusTest {
 @Test void editingATrainIntoCancellationDispatchesTheStatusAlertOnce() {
  var service=mock(TrainService.class);var notifications=mock(NotificationService.class);
  when(service.getTrainById(1L)).thenReturn(Train.builder().status(Train.TrainStatus.ON_TIME).build());
  var dto=TrainDto.builder().status("CANCELLED").build();
  when(service.updateTrain(1L,dto)).thenReturn(Train.builder().status(Train.TrainStatus.CANCELLED).build());
  var controller=new TrainController(service,mock(ScheduleService.class),notifications);
  assertEquals("redirect:/trains/fleet",controller.updateTrain(1L,dto,new RedirectAttributesModelMap()));
  verify(notifications).notifyAffectedPassengers(1L,"CANCELLED");
 }
 @Test void unsupportedStatusReturnsUsefulFeedbackWithoutDispatchingAnAlert() {
  var service=mock(TrainService.class);var notifications=mock(NotificationService.class);
  when(service.getTrainById(1L)).thenReturn(Train.builder().status(Train.TrainStatus.ON_TIME).build());
  when(service.updateTrainStatus(1L,"BOGUS")).thenThrow(new IllegalArgumentException("Choose a valid train status."));
  var flash=new RedirectAttributesModelMap();
  var controller=new TrainController(service,mock(ScheduleService.class),notifications);
  assertEquals("redirect:/trains/fleet",controller.updateStatus(1L,"BOGUS",flash));
  assertEquals("Choose a valid train status.",flash.getFlashAttributes().get("errorMessage"));verifyNoInteractions(notifications);
 }
}
