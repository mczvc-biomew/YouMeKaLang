package io.github.yumika;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YmkInstance {
  private YmkClass klass;
  private final Map<String, Object> fields = new HashMap<>();
  private final Map<String, YmkFunction> getters = new HashMap<>();
  private final Map<String, YmkFunction> setters = new HashMap<>();

  public YmkInstance(YmkClass klass) { this.klass = klass; }

  boolean containsField(String name) {
    return fields.containsKey(name);
  }

  Object get(String name, Interpreter interpreter) {
    if (fields.containsKey(name)) {
      return fields.get(name);
    }
    if (getters.containsKey(name)) return getters.get(name).call(interpreter, List.of());

    YmkFunction method = klass.findMethod(name);

    if (method != null) return method.bind(this);

    throw new RuntimeError(null,
        "Undefined property '" + name + "'.");
  }

  Object get(Token name, Interpreter interpreter) {
    return get(name.lexeme, interpreter);
  }

  Map<String, Object> getFields() { return fields; }

  protected void set(Token name, Object value, Interpreter interpreter) { set(name.lexeme, value, interpreter); }
  void set(String name, Object value, Interpreter interpreter) {
    if (setters.containsKey(name)) {
      setters.get(name).call(interpreter, List.of(value));
    } else {
      fields.put(name, value);
    }
  }
  void putAll(Map<String, Object> map) { fields.putAll(map); }

  void defineGetter(String name, YmkFunction fn) {
    getters.put(name, fn);
  }

  void defineSetter(String name, YmkFunction fn) {
    setters.put(name, fn);
  }

  @Override
  public String toString() { return klass.name + " instance = " + fields; }
}
