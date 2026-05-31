package com.example.magrathea.objectstore.domain;

/**
 * Thrown when a state transition is attempted with invalid preconditions.
 * Pure domain exception — NO framework dependencies.
 */
public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(String message) {
        super(message);
    }
}
