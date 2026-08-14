package io.github.rsweet.esdm2opencqrs.feel;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser for the FEEL subset (proposal 0002). Precedence: or &lt; and &lt;
 * comparison &lt; primary.
 *
 * <p>Supported: comparisons ({@code = != < <= > >=}), and/or/not(...), membership
 * ({@code x in [a, b]}), parentheses, string/number/boolean literals, identifiers (field references)
 * and the niladic functions {@code today()}/{@code now()}.
 */
public final class Parser {

    private final List<Token> tokens;
    private int index;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static FeelNode parse(String source) {
        Parser parser = new Parser(Lexer.tokenize(source));
        FeelNode ast = parser.parseOr();
        parser.expectType(Token.Type.EOF);
        return ast;
    }

    private Token peek() {
        return tokens.get(index);
    }

    private void advance() {
        index++;
    }

    private boolean at(String value) {
        return peek().value().equals(value);
    }

    private boolean isKeyword(String keyword) {
        Token token = peek();
        return token.type() == Token.Type.NAME && token.value().toLowerCase().equals(keyword);
    }

    private void eat(String value) {
        if (!at(value)) {
            throw new FeelException("Expected \"" + value + "\", got \"" + peek().value() + "\"");
        }
        advance();
    }

    private void expectType(Token.Type type) {
        if (peek().type() != type) {
            throw new FeelException("Expected " + type + ", got \"" + peek().value() + "\"");
        }
    }

    private FeelNode parseOr() {
        FeelNode left = parseAnd();
        while (isKeyword("or")) {
            advance();
            left = new FeelNode.Or(left, parseAnd());
        }
        return left;
    }

    private FeelNode parseAnd() {
        FeelNode left = parseComparison();
        while (isKeyword("and")) {
            advance();
            left = new FeelNode.And(left, parseComparison());
        }
        return left;
    }

    private static final List<String> COMPARISONS = List.of("=", "!=", "<", "<=", ">", ">=");

    private FeelNode parseComparison() {
        FeelNode left = parsePrimary();
        Token token = peek();

        if (token.type() == Token.Type.OP && COMPARISONS.contains(token.value())) {
            advance();
            return new FeelNode.Binary(token.value(), left, parsePrimary());
        }

        if (isKeyword("in")) {
            advance();
            return parseMembership(left);
        }

        // `x between a and b` is sugar for two comparisons; desugaring here keeps every
        // compiler in the family unaware that it exists.
        if (isKeyword("between")) {
            advance();
            FeelNode low = parsePrimary();
            if (!isKeyword("and")) {
                throw new FeelException("Expected \"and\" in a between expression");
            }
            advance();
            return range(left, low, parsePrimary());
        }

        return left;
    }

    /** {@code x in [a, b]} stays a membership test; {@code x in [1..10]} desugars to a range. */
    private FeelNode parseMembership(FeelNode left) {
        eat("[");
        if (at("]")) {
            eat("]");
            return new FeelNode.In(left, List.of());
        }

        FeelNode first = parsePrimary();
        if (at("..")) {
            advance();
            FeelNode high = parsePrimary();
            eat("]");
            return range(left, first, high);
        }

        List<FeelNode> items = new ArrayList<>();
        items.add(first);
        while (at(",")) {
            advance();
            items.add(parsePrimary());
        }
        eat("]");
        return new FeelNode.In(left, List.copyOf(items));
    }

    private static FeelNode range(FeelNode value, FeelNode low, FeelNode high) {
        return new FeelNode.And(
                new FeelNode.Binary(">=", value, low), new FeelNode.Binary("<=", value, high));
    }

    private FeelNode parsePrimary() {
        Token token = peek();

        if (at("-")) {
            advance();
            if (peek().type() == Token.Type.NUM) {
                String value = peek().value();
                advance();
                return new FeelNode.Num(-Double.parseDouble(value));
            }
            return new FeelNode.Negate(parsePrimary());
        }

        if (at("(")) {
            advance();
            FeelNode expression = parseOr();
            eat(")");
            return expression;
        }

        if (isKeyword("not")) {
            advance();
            eat("(");
            FeelNode expression = parseOr();
            eat(")");
            return new FeelNode.Not(expression);
        }

        if (token.type() == Token.Type.NUM) {
            advance();
            return new FeelNode.Num(Double.parseDouble(token.value()));
        }

        if (token.type() == Token.Type.STR) {
            advance();
            return new FeelNode.Str(token.value().substring(1, token.value().length() - 1));
        }

        if (token.type() == Token.Type.NAME) {
            String name = token.value();
            String lower = name.toLowerCase();

            if (lower.equals("true") || lower.equals("false")) {
                advance();
                return new FeelNode.Bool(lower.equals("true"));
            }

            if (lower.equals("null")) {
                advance();
                return new FeelNode.NullLiteral();
            }

            if (lower.equals("today") || lower.equals("now")) {
                advance();
                eat("(");
                eat(")");
                return new FeelNode.Call(lower);
            }

            advance();
            return new FeelNode.Id(name);
        }

        throw new FeelException("Unexpected token \"" + token.value() + "\"");
    }
}
