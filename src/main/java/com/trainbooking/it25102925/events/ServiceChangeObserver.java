package com.trainbooking.it25102925.events;
/** Observer contract: react only to a committed operational change. */
public interface ServiceChangeObserver { void onServiceChange(ServiceChange change); }
