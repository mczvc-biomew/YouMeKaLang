package io.github.yumika.javainterop;

import java.lang.reflect.Method;
import java.util.List;

public class JavaInstanceMethod {
  private final Object instance;
//  private final String methodName;
  private final List<Method> overloads;


  public JavaInstanceMethod(Object instance, List<Method> overloads) {
    this.instance = instance;
    this.overloads = overloads;
//    this.methodName = methodName;
  }

  public Object call(List<Object> args) {
    for (Method method : overloads) {
      Class<?>[] paramTypes = method.getParameterTypes();
      if (paramTypes.length != args.size()) continue;

      try {
        Object[] coercedArgs = new Object[args.size()];
        boolean match = true;
        for (int i = 0; i < args.size(); i++) {
          Object arg = args.get(i);
          Class<?> paramType = paramTypes[i];

          Object coerced = coerceType(arg, paramType);
          if (coerced == null && arg !=null ) {
            match = false;
            break;
          }
          coercedArgs[i] = coerced;
        }

        if (match) {
          return method.invoke(instance, coercedArgs);
        }
      } catch(Exception e) {

      }
//      if (method.getParameterCount() == args.size()) {
//        try {
//          return method.invoke(instance, args.toArray());
//        } catch (Exception ignored) {
//          continue;
//        }
//      }
    }
    throw new RuntimeException("No suitable method found for args: " + args);
  }

  private Object coerceType(Object arg, Class<?> targetType) {
    if (arg == null) return null;

    if (targetType.isInstance(arg)) return arg;

    if (arg instanceof Double) {
      double d = (Double) arg;

      if (targetType == int.class || targetType == Integer.class) return (int) d;
      if (targetType == long.class || targetType == Long.class) return (long) d;
      if (targetType == float.class || targetType == Float.class) return (float) d;
      if (targetType == double.class || targetType == Double.class) return d;
    }

    if (arg instanceof String && targetType == char.class && ((String) arg).length() == 1)
      return ((String) arg).charAt(0);

    // fallback: try generic conversion
    try {
      return targetType.cast(arg);
    } catch (Exception e) {
      return null;
    }
  }

}
