package io.github.yumika.javainterop;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public class JavaStaticMethod {
  private final Class<?> cls;
  private final String methodName;

  public JavaStaticMethod(Class<?> cls, String methodName) {
    this.cls = cls;
    this.methodName = methodName;
  }

  public Object call(List<Object> args) {
    for (Method method : cls.getMethods()) {
      if (method.getName().equals(methodName)
          && method.getParameterCount() == args.size()
          && Modifier.isStatic(method.getModifiers())) {
        try {
          return method.invoke(null, args.toArray());
        } catch (Exception ignored) {}
      }
    }
    throw new RuntimeException("Static method not found: " + methodName);
  }
}

