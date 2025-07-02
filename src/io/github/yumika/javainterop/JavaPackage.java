package io.github.yumika.javainterop;

import java.util.HashMap;
import java.util.Map;

public class JavaPackage {
  private final String packageName;
  private final Map<String, Object> cache = new HashMap<>();

  public JavaPackage(String packageName) {
    this.packageName = packageName;
  }

  public Object get(String name) {
    if (cache.containsKey(name)) return cache.get(name);

    String fullName = packageName + "." + name;
    try {
      Class<?> cls = Class.forName(fullName);
      JavaClassWrapper wrapper = new JavaClassWrapper(cls);
      cache.put(name, wrapper);
      return wrapper;
    } catch (ClassNotFoundException e) {
      // Could be a subpackage
      JavaPackage subpackage = new JavaPackage(fullName);
      cache.put(name, subpackage);
      return subpackage;
    }
  }

  @Override
  public String toString() {
    return "<JavaPackage " + packageName + ">";
  }
}
