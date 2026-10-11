package com.trainbooking.it25102327.service;
import java.util.Map;
/** Field errors can be shown beside a retained server-bound operational form. */
public class OperationValidation extends IllegalArgumentException {
    private final Map<String,String> fields;
    public OperationValidation(String field,String message) { super(message); fields=Map.of(field,message); }
    public Map<String,String> fields() { return fields; }
}
