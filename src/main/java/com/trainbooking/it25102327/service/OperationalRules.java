package com.trainbooking.it25102327.service;
import com.trainbooking.it25102327.dto.*;
import com.trainbooking.it25102327.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class OperationalRules {
    private OperationalRules() { }
    public static void fail(String field,String message) { throw new OperationValidation(field,message); }
    public static String name(String value,String field,int maximum) {
        String text=value==null ? "" : value.strip().replaceAll(" +"," ");
        if(text.isEmpty() || text.length()>maximum || !text.matches("[\\p{L}\\p{M}\\p{N} .’'()&-]+") || text.codePoints().noneMatch(Character::isLetter))
            fail(field,"Enter a name using letters, spaces or ordinary name punctuation (maximum "+maximum+" characters).");
        return text;
    }
    public static String platform(String value,String field) {
        String text=value==null ? "" : value.strip();
        if(!text.matches("(?i)(?:Platform\\s+)?(?:[1-9]|1[0-9]|20)")) fail(field,"Choose a platform number from 1 to 20.");
        return "Platform "+text.replaceFirst("(?i)^Platform\\s+","");
    }
    public static String note(String value,String field,int maximum) {
        String text=value==null ? "" : value.strip();
        if(text.isBlank() || text.length()>maximum || text.matches("(?s).*[<>\\p{Cntrl}].*")) fail(field,"Enter a short plain-text description (maximum "+maximum+" characters).");
        return text;
    }
    public static int capacity(Integer value,String field) {
        if(value==null || value<0 || value>1000) fail(field,"Enter a whole-number capacity from 0 to 1000.");
        return value;
    }
    public static TrainDto train(TrainDto dto) {
        if(dto==null) throw new IllegalArgumentException("Enter train details.");
        String number=dto.getTrainNumber()==null ? "" : dto.getTrainNumber().strip().toUpperCase(Locale.ROOT);
        if(!number.matches("[A-Z0-9][A-Z0-9-]{0,19}") || number.chars().noneMatch(Character::isDigit)) fail("trainNumber","Use 1–20 letters/digits/hyphens, including at least one digit.");
        dto.setTrainNumber(number); dto.setTrainName(name(dto.getTrainName(),"trainName",100));
        String type=dto.getTrainType()==null || dto.getTrainType().isBlank() ? "Express Intercity" : dto.getTrainType().strip();
        if(type.equals("Commuter / Slow")) type="Commuter";
        if(type.equals("LUXURY_TOURIST")) type="Special Service";
        if(!Set.of("Express Intercity","Intercity","Commuter","Night Mail","Special Service").contains(type)) fail("trainType","Choose an offered train type.");
        dto.setTrainType(type);
        int first=capacity(dto.getFirstClassSeats(),"firstClassSeats"),second=capacity(dto.getSecondClassSeats(),"secondClassSeats");
        if(first+second==0) fail("firstClassSeats","Provide seats in at least one supported class.");
        dto.setTotalSeats(first+second); return dto;
    }
    public static int distance(Integer value) {
        if(value==null || value<1 || value>2000) fail("distanceKm","Enter a distance from 1 to 2000 km."); return value;
    }
    public static RouteDto route(RouteDto dto) {
        dto.setOrigin(name(dto.getOrigin(),"origin",100));dto.setDestination(name(dto.getDestination(),"destination",100));
        if(dto.getOrigin().equalsIgnoreCase(dto.getDestination())) fail("destination","Choose different origin and destination stations.");
        distance(dto.getDistanceKm());dto.setDefaultPlatform(platform(dto.getDefaultPlatform(),"defaultPlatform"));
        dto.setDestinationPlatform(platform(dto.getDestinationPlatform()==null ? "Platform 1" : dto.getDestinationPlatform(),"destinationPlatform"));return dto;
    }
    public static LocalTime time(String value,String field) {
        if(value==null || !value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) fail(field,"Enter a valid time in HH:mm format.");
        return LocalTime.parse(value);
    }
    public static int duration(LocalTime departure,LocalTime arrival,Integer offset,String field) {
        if(offset==null || offset<0 || offset>1) fail("arrivalDayOffset","Choose same-day or next-day arrival.");
        int minutes=arrival.toSecondOfDay()/60+offset*1440-departure.toSecondOfDay()/60;
        if(minutes<1 || minutes>1440) fail(field,"Arrival must be after departure, within 24 hours. Select next day for overnight services.");return minutes;
    }
    public static BigDecimal fare(BigDecimal value,String field,boolean required) {
        if(value==null && !required) return null;
        if(value==null || value.signum()<=0 || value.stripTrailingZeros().scale()>2 || value.compareTo(new BigDecimal("999999.99"))>0) fail(field,"Enter a positive fare, up to 999999.99, with at most two decimal places.");return value.setScale(2);
    }
    public static List<TimetableStop> stops(List<StopDto> rows,Schedule s) {
        List<TimetableStop> result=new ArrayList<>();if(rows==null) return result;
        if(rows.size()>20) fail("stops","Up to 20 intermediate stops are supported.");
        int previous=0,duration=duration(s.getDepartureTime(),s.getArrivalTime(),s.getArrivalDayOffset(),"arrivalTime");
        Set<String> seen=new HashSet<>();seen.add(s.getRoute().getOrigin().toLowerCase(Locale.ROOT));seen.add(s.getRoute().getDestination().toLowerCase(Locale.ROOT));
        for(int i=0;i<rows.size();i++) {
            StopDto row=rows.get(i);if(row==null) continue;
            if((row.getStation()==null || row.getStation().isBlank()) && (row.getArrivalTime()==null || row.getArrivalTime().isBlank()) && (row.getDepartureTime()==null || row.getDepartureTime().isBlank())) continue;
            String prefix="stops["+i+"]";String station=name(row.getStation(),prefix+".station",100);
            if(!seen.add(station.toLowerCase(Locale.ROOT))) fail(prefix+".station","Each stop must be distinct from the endpoints and other stops.");
            LocalTime arrival=time(row.getArrivalTime(),prefix+".arrivalTime"),departure=time(row.getDepartureTime(),prefix+".departureTime");
            if(row.getDayOffset()==null || row.getDayOffset()<0 || row.getDayOffset()>1) fail(prefix+".dayOffset","Choose same-day or next-day stop times.");
            int arr=arrival.toSecondOfDay()/60+row.getDayOffset()*1440-s.getDepartureTime().toSecondOfDay()/60;
            int dep=departure.toSecondOfDay()/60+row.getDayOffset()*1440-s.getDepartureTime().toSecondOfDay()/60;
            if(departure.isBefore(arrival)) dep+=1440;
            if(arr<=previous || dep<arr || dep>=duration) fail(prefix+".arrivalTime","Put stops in journey order; departure cannot precede arrival or reach the destination time.");
            TimetableStop stop=new TimetableStop();stop.setSchedule(s);stop.setPosition(result.size());stop.setStation(station);stop.setArrivalOffset(arr);stop.setDepartureOffset(dep);
            stop.setPlatform(platform(row.getPlatform(),prefix+".platform"));result.add(stop);previous=dep;
        }
        return result;
    }
}
