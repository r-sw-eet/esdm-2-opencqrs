package io.github.rsweet.esdm2opencqrs.feel;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tokenizes the supported FEEL subset. */
public final class Lexer {

    // Anchored, ordered alternation; longest operators first.
    private static final Pattern PATTERN =
            Pattern.compile("(\\s+)|(\\d+(?:\\.\\d+)?)|(\"[^\"]*\")|(<=|>=|!=|=|<|>)|([()\\[\\],])|([A-Za-z_][A-Za-z0-9_]*)");

    private Lexer() {}

    public static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = PATTERN.matcher(source);
        int offset = 0;

        while (offset < source.length()) {
            matcher.region(offset, source.length());
            if (!matcher.lookingAt()) {
                throw new FeelException("Unexpected character at " + offset + ": \"" + source.charAt(offset) + "\"");
            }
            String value = matcher.group();
            offset += value.length();

            if (value.isBlank()) {
                continue;
            }

            char first = value.charAt(0);
            Token.Type type;
            if (Character.isDigit(first)) {
                type = Token.Type.NUM;
            } else if (first == '"') {
                type = Token.Type.STR;
            } else if (Character.isLetter(first) || first == '_') {
                type = Token.Type.NAME;
            } else if ("()[],".indexOf(first) >= 0) {
                type = Token.Type.PUNC;
            } else {
                type = Token.Type.OP;
            }

            tokens.add(new Token(type, value));
        }

        tokens.add(new Token(Token.Type.EOF, ""));
        return tokens;
    }
}
