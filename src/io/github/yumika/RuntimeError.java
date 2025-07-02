package io.github.yumika;

public class RuntimeError extends RuntimeException {
  final Token token;

  static class UndefinedException extends RuntimeError {
    UndefinedException(Token name, String message) {
      super(name, message);
    }
    UndefinedException(UndefinedException undefEx) {
      super(undefEx.token, undefEx.getMessage());
    }
  }

  static class ReferenceError extends RuntimeError {
    ReferenceError(Token name, String message) {
      super(name, message);
    }
  }

  RuntimeError(Token token, String message) {
    super(message);
    this.token = token;
  }
}
