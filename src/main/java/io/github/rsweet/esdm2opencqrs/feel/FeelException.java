package io.github.rsweet.esdm2opencqrs.feel;

/** A FEEL expression failed to lex, parse or bind. */
public class FeelException extends RuntimeException {

    public FeelException(String message) {
        super(message);
    }
}
