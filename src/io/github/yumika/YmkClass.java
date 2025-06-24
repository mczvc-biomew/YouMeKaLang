package io.github.yumika;

import java.util.List;
import java.util.Map;

public class YmkClass implements YmkCallable {
  final String name;
  final YmkClass superclass;

  private final Map<String, YmkFunction> methods;

  public YmkClass(String name, YmkClass superclass,
                  Map<String, YmkFunction> methods) {
    this.superclass = superclass;
    this.name = name;
    this.methods = methods;
  }

  YmkFunction findMethod(String name) {
    if (methods.containsKey(name)) {
      return methods.get(name);
    }

    if (superclass != null) {
      return superclass.findMethod(name);
    }

    return null;
  }

  @Override
  public String toString() { return name; }

  @Override
  public Object call(Interpreter interpreter,
                     List<Object> arguments) {
    YmkInstance instance = new YmkInstance(this);
    YmkFunction initializer = findMethod("init");
    if (initializer != null) {
      initializer.bind(instance).call(interpreter, arguments);
    }

    return instance;
  }

  @Override
  public int arity() {
    YmkFunction initializer = findMethod("init");
    if (initializer == null) return 0;
    return initializer.arity();
  }
}
