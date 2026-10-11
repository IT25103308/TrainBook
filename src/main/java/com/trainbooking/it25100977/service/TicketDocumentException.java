package com.trainbooking.it25100977.service;

/** A document-generation failure must never masquerade as a valid PDF or QR image. */
public class TicketDocumentException extends RuntimeException {
    public TicketDocumentException(String message, Throwable cause) { super(message, cause); }
}
