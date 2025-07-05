package io.github.yumika;

import java.util.List;
import java.util.Map;

public class YmkClass implements YmkCallable {
  final String name;
  final YmkClass superclass;
  final boolean isAbstract;
  final Token keyword;

  private final Map<String, YmkFunction> methods;

  public YmkClass(String name, YmkClass superclass,
                  Map<String, YmkFunction> methods, boolean isAbstract) {
    this.superclass = superclass;
    this.name = name;
    this.methods = methods;
    this.isAbstract = isAbstract;
    this.keyword = null;
  }

  public YmkClass(String name, Token keyword, YmkClass superclass,
                  Map<String, YmkFunction> methods, boolean isAbstract) {
    this.superclass = superclass;
    this.name = name;
    this.methods = methods;
    this.isAbstract = isAbstract;
    this.keyword = keyword;
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
                     List<Object> arguments,
                     Map<String, Object> kwargs) {
    if (isAbstract) {
      throw new RuntimeError(keyword, "Cannot instantiate abstract class "
      + name);
    }
    YmkInstance instance = new YmkInstance(this);
    YmkFunction initializer = findMethod("init");
    if (initializer != null) {
      initializer.bind(instance).call(interpreter, arguments, Map.of());
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
