package io.github.yumika;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.yumika.TokenType.*;

class Scanner {
  private static final Map<String, TokenType> keywords;

  static {
    keywords = new HashMap<>();
    keywords.put("and", AND);
    keywords.put("as", AS);
    keywords.put("case", CASE);
    keywords.put("class", CLASS);
    keywords.put("else", ELSE);
    keywords.put("false", FALSE);
    keywords.put("fun", FUN);
    keywords.put("for", FOR);
    keywords.put("if", IF);
    keywords.put("import", IMPORT);
    keywords.put("in", IN);
    keywords.put("or", OR);
    keywords.put("new", NEW);
    keywords.put("not", NOT);
    keywords.put("null", NULL);
    keywords.put("print", PRINT);
    keywords.put("puts", PUTS);
    keywords.put("return", RETURN);
    keywords.put("super", SUPER);
    keywords.put("this", THIS);
    keywords.put("true", TRUE);
    keywords.put("undefined", UNDEFINED);
    keywords.put("var", VAR);
    keywords.put("when", WHEN);
    keywords.put("while", WHILE);
  }

  private final String source;
  private final List<Token> tokens = new ArrayList<>();

  private int start = 0;
  private int current = 0;
  private int line = 1;

  Scanner(String source) { this.source = source;}

  List<Token> scanTokens() {
    while (!isAtEnd()) {
      start = current;
      scanToken();
    }

    tokens.add(new Token(EOF, "", null, line));
    return tokens;
  }

  private void scanToken() {
    char c = advance();
    switch (c) {
      case '[': addToken(LEFT_BRACKET); break;
      case ']': addToken(RIGHT_BRACKET); break;
      case '(': addToken(LEFT_PAREN); break;
      case ')': addToken(RIGHT_PAREN); break;
      case '{': addToken(LEFT_BRACE); break;
      case '}': addToken(RIGHT_BRACE); break;
      case ',': addToken(COMMA); break;
      case '.': addToken(DOT); break;
      case '|': addToken(PIPE); break;
      case ':': addToken(COLON); break;
      case ';': addToken(SEMICOLON); break;
      case '*': addToken(STAR); break;
      // two-char-tokens
      case '-':
        addToken(match('-') ? MINUS_MINUS : MINUS);
        break;
      case '+':
        addToken(match('+') ? PLUS_PLUS : PLUS);
        break;
      case '!':
        addToken(match('=') ? BANG_EQUAL : BANG);
        break;
      case '=':
        addToken(match('=') ? EQUAL_EQUAL
            : (match('>') ? ARROW : EQUAL));
        break;
      case '<':
        addToken(match('=') ? LESS_EQUAL : LESS);
        break;
      case '>':
        addToken(match('=') ? GREATER_EQUAL : GREATER);
        break;
      //< two-char-tokens
      // slash
      case '/':
        if (match('/')) {
          // TODO: add comment to AST
          while (peek() != '\n' && !isAtEnd()) advance();
        } else {
          addToken(SLASH);
        }
        break;
      // < slash
      // whitespace

      case ' ':
      case '\r':
      case '\t':
        // Ignore whitespace.
        break;

      case '\n':
        line++;
        break;
      // < whitespace

      // string start
      case '"': string(); break;
      // < string start

      // char-error
      default:
      /* Scanning char-error */
        // digit-start
        if (isDigit(c)) {
          number();
        // identifier-start
        } else if (isAlpha(c)) {
          identifier();
        } else {
          YouMeKa.error(line, "Unexpected character.");
        }
        // < digit-start
        break;
      // char-error
    }
  }

  private void identifier() {
    // Two task here: here to identify identifier;
    while (isAlphaNumeric(peek())) advance();

    String text = source.substring(start, current);
    // or to identify reserved keywords here:
    TokenType type = keywords.get(text);
    if (type == null) type = IDENTIFIER;
    addToken(type);
  }

  private void number() {
    while (isDigit(peek())) advance();

    // Look for a fractional part.
    if (peek() == '.' && isDigit(peekNext())) {
      // Consume the '.'
      advance();

      while (isDigit(peek())) advance();
    }

    addToken(NUMBER,
        Double.parseDouble(source.substring(start, current)));
  }

  private void string() {
    // Multi-line string
    while (peek() != '"' && !isAtEnd()) {
      if (peek() == '\n') line++;
      advance();
    }

    if (isAtEnd()) {
      YouMeKa.error(line, "Unterminated string.");
      return;
    }

    // The closing (").
    advance();

    String value = source.substring(start + 1, current - 1);
    addToken(STRING, value);
  }

  private boolean match(char expected) {
    if (isAtEnd()) return false;
    if (source.charAt(current) != expected) return false;

    current++;
    return true;
  }

  private char peek() {
    if (isAtEnd()) return '\0';
    return source.charAt(current);
  }

  private char peekNext() {
    if (current + 1 >= source.length()) return '\0';
    return source.charAt(current + 1);
  }

  private boolean isAlpha(char c) {
    return
        (c >= 'a' && c <= 'z') ||
        (c >= 'A' && c <= 'Z') ||
        c == '_';
  }

  private boolean isAlphaNumeric(char c) { return isAlpha(c) || isDigit(c); }

  private boolean isDigit(char c) { return c >= '0' && c <= '9'; }

  private boolean isAtEnd() { return current >= source.length(); }

  private char advance() { return source.charAt(current++);}

  private void addToken(TokenType type) { addToken(type, null); }

  private void addToken(TokenType type, Object literal) {
    String text = source.substring(start, current);
    tokens.add(new Token(type, text, literal, line));
  }
}
