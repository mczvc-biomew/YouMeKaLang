package io.github.yumika;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

class Environment {
  final Environment enclosing;
  private final Map<String, Object> values = new HashMap<>();

  Environment() { enclosing = null; }

  Environment(Environment enclosing) { this.enclosing = enclosing;}

  boolean contains(String name) {
    return values.containsKey(name);
  }
  int containsAt(String name, int distance) {
    int index = distance;
    while (!ancestor(distance, name).values.containsKey(name)) {
      index = containsAt(name, distance + 1);
      if (index >= 64) return -1;
    }
    return index;
  }

  boolean exists(String name) {
    if (values.containsKey(name)) return true;
    if (enclosing != null) return enclosing.exists(name);
    return false;
  }

  Object get(Token name) {
    return get(name.lexeme);
  }

  Object get(String name) {

    if (values.containsKey(name)) {
      return values.get(name);
    }

    if (enclosing != null) return enclosing.get(name);

    throw new RuntimeError.UndefinedException(null,
        "Undefined variable '" + name + "'.");
  }

  public void forEach(BiConsumer<String, Object> action) {
    for (var e : values.entrySet()) {
      action.accept(e.getKey(), e.getValue());
    }
  }

  void assign(Token name, Object value) {
    if (values.containsKey(name.lexeme)) {
      values.put(name.lexeme, value);
      return;
    }

    if (enclosing != null) {
      enclosing.assign(name, value);
      return;
    }

    throw new RuntimeError(name,
        "Undefined variable '" + name.lexeme + "'.");
  }

  void define(String name, Object value) { values.put(name, value); }

  Environment ancestor(int distance, String name) {
    Environment environment = this;
    for (int i = 0; i < distance; i++) {
      assert environment != null;
      if (environment.values.containsKey(name)) {
        return environment;
      }
      environment = environment.enclosing;
    }

    return environment;
  }

  Object getAt(int distance, String name) { return ancestor(distance, name).values.get(name); }

  void assignAt(int distance, Token name, Object value) { ancestor(distance, name.lexeme).values.put(name.lexeme, value); }
  void assignAt(int distance, String name, Object value) { ancestor(distance, name).values.put(name, value); }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder( Interpreter.stringify(values, 1) );
    result.append("\n");
    if (enclosing != null) {
      result.append(" -> ").append(Interpreter.stringify(0, enclosing));
    }

    return "Environment: " + result.toString() + "::\n";
  }
}