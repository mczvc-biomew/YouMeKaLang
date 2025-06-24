package io.github.yumika.javainterop;

import java.lang.reflect.*;
import java.util.*;

public class JavaInstanceWrapper {
  private final Object instance;

  public JavaInstanceWrapper(Object instance) {
    this.instance = instance;
  }

  public Object get(String name) {
    try {
//      Method method = instance.getClass().getMethod(name);
//      return new JavaInstanceMethod(instance, name);
      Method[] methods = instance.getClass().getMethods();
      List<Method> matching = new ArrayList<>();

      for (Method method : methods) {
        if (method.getName().equals(name)) {
          matching.add(method);
        }
      }
      if (matching.isEmpty()) {
        throw new NoSuchMethodException("No suitable method found for name: " + name);
//        throw new RuntimeException("No matching method found for: " + name);
      }
      return new JavaInstanceMethod(instance, matching);
    } catch (NoSuchMethodException e) {
      try {
        Field field = instance.getClass().getField(name);
        return field.get(instance);
      } catch (Exception ex) {
        throw new RuntimeException("No such member: " + name);
      }
    }
  }

  public void set(String name, Object value) {
    try {
      Field field = instance.getClass().getField(name);
      field.set(instance, value);
    } catch (Exception ex) {
      throw new RuntimeException("Cannot set member: " + name);
    }
  }

  @Override
  public String toString() {
    return "<JavaObject " + instance.toString() + ">";
  }
}
