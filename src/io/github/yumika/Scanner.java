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
    keywords.put("catch", CATCH);
    keywords.put("class", CLASS);
    keywords.put("else", ELSE);
    keywords.put("false", FALSE);
    keywords.put("fun", FUN);
    keywords.put("for", FOR);
    keywords.put("get", GET);
    keywords.put("if", IF);
    keywords.put("import", IMPORT);
    keywords.put("in", IN);
    keywords.put("interface", INTERFACE);
    keywords.put("or", OR);
    keywords.put("match", MATCH);
    keywords.put("new", NEW);
    keywords.put("not", NOT);
    keywords.put("null", NULL);
    keywords.put("print", PRINT);
    keywords.put("puts", PUTS);
    keywords.put("return", RETURN);
    keywords.put("set", SET);
    keywords.put("super", SUPER);
    keywords.put("this", THIS);
    keywords.put("throw", THROW);
    keywords.put("true", TRUE);
    keywords.put("try", TRY);
    keywords.put("type", TYPE);
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
      case '@': addToken(AT); break;
      case '[': addToken(LEFT_BRACKET); break;
      case ']': addToken(RIGHT_BRACKET); break;
      case '(': addToken(LEFT_PAREN); break;
      case ')': addToken(RIGHT_PAREN); break;
      case '{': addToken(LEFT_BRACE); break;
      case '}': addToken(RIGHT_BRACE); break;
      case ',': addToken(COMMA); break;
      case '.':
        if (match('.') && match('.')) {
          addToken(DOT_DOT_DOT);
        } else {
          addToken(DOT);
        }
        break;
      case '%': addToken(PERCENT); break;
      case '|': addToken(PIPE); break;
      case ':': addToken(COLON); break;
      case ';': addToken(SEMICOLON); break;
      case '*': addToken(match('*') ? STAR_STAR : STAR); break;
      // two-char-tokens
      case '-':
        if (match('=')) {
          addToken(MINUS_EQUAL);
        } else {
          addToken(match('-') ? MINUS_MINUS : MINUS);
        }
        break;
      case '+':
        if (match('=')) {
          addToken(PLUS_EQUAL);
        } else {
          addToken(match('+') ? PLUS_PLUS : PLUS);
        }
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
      case '?':
        if (match('.')) {
          addToken(QUESTION_DOT);
        } else if (match('?')) {
          addToken(QUESTION_QUESTION);
        } else {
          addToken(QUESTION);
        }
        break;
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
      case '\'':
      case '"': string(previous()); break;
      // < string start

      case '`': templateString(); break;

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

  private void string(char quote) {
    // Multi-line string
    StringBuilder value = new StringBuilder();
    while (peek() != quote && !isAtEnd()) {
      if (match('\\')) {
        if (isAtEnd()) break;
        char esc = advance();
        switch (esc) {
          case 'n' -> value.append('\n');
          case 't' -> value.append('\t');
          case 'r' -> value.append('\r');
          case '\\' -> value.append('\\');
          case '\'' -> value.append('\'');
          case '\"' -> value.append('\"');
          default -> value.append(esc);
        }
      } else {
        value.append(advance());
      }
      if (peek() == '\n') line++;
//      advance();
    }

    if (isAtEnd()) {
      YouMeKa.error(line, "Unterminated string.");
      return;
    }

    // The closing (").
    advance();

//    String value = source.substring(start + 1, current - 1);
    addToken(STRING, value.toString());
  }

  private void templateString() {
    int start = current;
    StringBuilder builder = new StringBuilder();
    List<Object> parts = new ArrayList<>();

    while (!isAtEnd()) {
      if (peek() == '`') {
        advance();
        break;
      }

      if (peek() == '$' && peekNext() == '{') {
        current += 2;
        if (builder.length() > 0) {
          parts.add(builder.toString());
          builder.setLength(0);
        }

        int exprStart = current;
        int depth = 1;
        while (!isAtEnd() && depth > 0) {
          char c = advance();
          if (c == '{') depth++;
          else if (c == '}') depth--;
        }

        String expr = source.substring(exprStart, current - 1);
        parts.add(new Token(TEMPLATE_STRING, expr, null, line));
      } else {
        builder.append(advance());
      }
    }

    if (builder.length() > 0) {
      parts.add(builder.toString());
    }

    addToken(TEMPLATE_STRING, parts);
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

  private char previous() {
    return source.charAt(current - 1);
  }

  private char advance() { return source.charAt(current++);}

  private void addToken(TokenType type) { addToken(type, null); }

  private void addToken(TokenType type, Object literal) {
    String text = source.substring(start, current);
    tokens.add(new Token(type, text, literal, line));
  }
}
