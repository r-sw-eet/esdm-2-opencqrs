package io.github.rsweet.esdm2opencqrs.feel;

public record Token(Type type, String value) {

    public enum Type {
        NUM,
        STR,
        NAME,
        PUNC,
        OP,
        EOF
    }
}
