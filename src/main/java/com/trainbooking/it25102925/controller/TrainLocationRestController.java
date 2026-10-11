package com.trainbooking.it25102925.controller;
import com.trainbooking.it25102925.dto.TrainLocationDto;
import com.trainbooking.it25102925.service.TrainLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.security.Principal;
import java.util.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/trains")
public class TrainLocationRestController {
    private final TrainLocationService trainLocationService;
    @PostMapping("/location/update") public ResponseEntity<Map<String,Object>> updateLocationPost(@RequestBody Map<String,Object> p,Principal principal) {
        try {
            if(p==null)throw new IllegalArgumentException("Enter a telemetry payload.");
            Long id=integer(p.get("trainId"));Long schedule=integer(p.get("scheduleId"));
            Double lat=number(p.containsKey("latitude")?p.get("latitude"):p.get("lat"));Double lng=number(p.containsKey("longitude")?p.get("longitude"):p.containsKey("lng")?p.get("lng"):p.get("lon"));
            Long rawProgress=integer(p.get("progress")),rawDelay=integer(p.get("delayMinutes"));
            if(rawProgress!=null && (rawProgress<0 || rawProgress>100) || rawDelay!=null && (rawDelay<0 || rawDelay>1440))throw new IllegalArgumentException("Use valid whole progress/delay values.");
            LocalDate date=p.get("serviceDate")==null?null:LocalDate.parse(text(p.get("serviceDate")));
            LocalDateTime eta=p.get("expectedArrival")==null?null:LocalDateTime.parse(text(p.get("expectedArrival")));
            var result=trainLocationService.report(id,schedule,date,lat,lng,number(p.get("speed")),text(p.get("status")),text(p.get("station")),text(p.get("nextStation")),
                text(p.containsKey("direction")?p.get("direction"):p.get("tripDirection")),text(p.get("route")),rawProgress==null?null:rawProgress.intValue(),rawDelay==null?null:rawDelay.intValue(),eta,Boolean.TRUE.equals(p.get("simulated")),principal==null?"position reporter":principal.getName());
            return ResponseEntity.ok(Map.of("success",true,"message","Reported telemetry recorded; physical GPS accuracy is not verified by this prototype.","data",result));
        } catch(IllegalArgumentException|IllegalStateException|java.time.DateTimeException invalid){return ResponseEntity.badRequest().body(Map.of("success",false,"message",invalid.getMessage()));}
    }
    @PostMapping("/{trainId}/location/clear") public ResponseEntity<Map<String,Object>> clear(@PathVariable Long trainId){trainLocationService.clear(trainId);return ResponseEntity.ok(Map.of("success",true,"message","Latest reported position cleared."));}
    @GetMapping("/locations") public ResponseEntity<List<TrainLocationDto>> getAllLocations(){return ResponseEntity.ok(trainLocationService.getAllLocations());}
    @GetMapping("/{trainId}/location") public ResponseEntity<TrainLocationDto> getTrainLocation(@PathVariable Long trainId){return trainLocationService.getLocation(trainId).map(ResponseEntity::ok).orElseGet(()->ResponseEntity.notFound().build());}
    private Long integer(Object value){if(value==null)return null;try{return new BigDecimal(value.toString()).longValueExact();}catch(Exception invalid){throw new IllegalArgumentException("Identifiers, progress and delay must be whole numbers.");}}
    private Double number(Object value){if(value==null)return null;try{double n=Double.parseDouble(value.toString());if(!Double.isFinite(n))throw new IllegalArgumentException();return n;}catch(Exception invalid){throw new IllegalArgumentException("Enter finite numeric coordinates and speed.");}}
    private String text(Object value){if(value==null)return null;if(!(value instanceof String))throw new IllegalArgumentException("Use text for status, station, route and dates.");return ((String)value).trim();}
}
