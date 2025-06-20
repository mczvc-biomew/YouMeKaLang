package io.github.yumika;

import java.util.HashMap;
import java.util.Map;

class YmkInstance {
  private YmkClass klass;
  private final Map<String, Object> fields = new HashMap<>();

  YmkInstance(YmkClass klass) { this.klass = klass; }

  boolean containsField(String name) {
    return fields.containsKey(name);
  }

  Object get(String name) {
    if (fields.containsKey(name)) {
      return fields.get(name);
    }

    YmkFunction method = klass.findMethod(name);

    if (method != null) return method.bind(this);

    throw new RuntimeError(null,
        "Undefined property '" + name + "'.");
  }

  Object get(Token name) {
    return get(name.lexeme);
  }

  Map<String, Object> getFields() { return fields; }

  void set(Token name, Object value) { fields.put(name.lexeme, value); }
  void set(String name, Object value) { fields.put(name, value); }
  void putAll(Map<String, Object> map) { fields.putAll(map); }

  @Override
  public String toString() { return klass.name + " instance = " + fields; }
}
