package com.trainbooking.it25102925.service;
import com.trainbooking.it25102327.repository.TrainRepository;
import lombok.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicitly enabled demonstration telemetry; never presented as railway hardware GPS. */
@Service @RequiredArgsConstructor
public class GpsSimulationService {
    private final TrainRepository trainRepository;
    private final NotificationService notificationService;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private ServiceStatusService statuses;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private TrainLocationService locations;
    @Getter @Setter @org.springframework.beans.factory.annotation.Value("${trainbook.demo.gps-delays:false}") private boolean simulationActive;
    private final AtomicInteger tickCount=new AtomicInteger();
    @Scheduled(fixedRate=60000) @Transactional public void runGpsTrackingCycle(){
        if(!simulationActive || statuses==null || locations==null)return;
        int tick=tickCount.incrementAndGet();
        var choices=statuses.board(LocalDate.now()).stream().filter(s->s.status().equals("ON_TIME") || s.status().equals("DELAYED")).toList();
        if(choices.isEmpty())return;var service=choices.get(tick%choices.size());
        String state=tick%3==0?"DELAYED":null;Integer delay=state==null?null:15+tick%20;
        locations.report(service.trainId(),service.scheduleId(),service.serviceDate(),6.9344+(tick%10)*0.01,79.85+(tick%10)*0.01,45.0,state,null,null,"OUTBOUND",null,
            (tick*10)%100,delay,null,true,"demo GPS simulator");
    }
}
