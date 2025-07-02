package io.github.yumika.javainterop;

import java.lang.reflect.*;
import java.util.*;

public class JavaClassWrapper {
  private final Class<?> javaClass;

  public JavaClassWrapper(Class<?> cls) {
    this.javaClass = cls;
  }

  public Object call(List<Object> args) {
    for (Constructor<?> ctor : javaClass.getConstructors()) {
      if (ctor.getParameterCount() == args.size()) {
        try {
          return new JavaInstanceWrapper(ctor.newInstance(args.toArray()));
        } catch (Exception ignored) {}
      }
    }
    throw new RuntimeException("No suitable constructor for: " + javaClass.getName());
  }

  public Object get(String name) {
    try {
      Method method = javaClass.getMethod(name);
      return new JavaStaticMethod(javaClass, name);
    } catch (NoSuchMethodException e) {
      try {
        Field field = javaClass.getField(name);
        return field.get(null);
      } catch (Exception ex) {
        throw new RuntimeException("No such static member: " + name);
      }
    }
  }

  @Override
  public String toString() {
    return "<JavaClass " + javaClass.getName() + ">";
  }
}
