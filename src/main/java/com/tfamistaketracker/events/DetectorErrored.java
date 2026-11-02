package com.tfamistaketracker.events;

import lombok.Value;

/**
 * An event denoting that a detector or tracker threw an exception, which was caught so the others keep running.
 */
@Value
public class DetectorErrored {

    int tick;
    String source;
    RuntimeException exception;
}
