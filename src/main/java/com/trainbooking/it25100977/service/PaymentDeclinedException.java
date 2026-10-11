package com.trainbooking.it25100977.service;

/** A simulated decline records its outcome while leaving the owned hold pending. */
public class PaymentDeclinedException extends IllegalArgumentException {
    public PaymentDeclinedException(String message) { super(message); }
}
