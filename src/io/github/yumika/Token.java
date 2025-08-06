package io.github.yumika;

public class Token {
  final TokenType type;
  final String lexeme;
  final Object literal;
  final int line;
  int column;

  public Token(TokenType type, String lexeme, Object literal, int line, int column) {
    this.type = type;
    this.lexeme = lexeme;
    this.literal = literal;
    this.line = line;
    this.column = column;
  }

  public String toString() { return type + " " + lexeme + " " + literal; }
}
