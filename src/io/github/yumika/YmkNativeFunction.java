package io.github.yumika;

import java.util.List;

// Define NativeFunction adapter that wraps a lambda as a YmkFunction
public class YmkNativeFunction implements YmkCallable {
  private final String name;
  private final int arity;
  private final NativeImpl impl;

  @FunctionalInterface
  public interface NativeImpl {
    Object call(Interpreter interpreter, List<Object> args);
  }

  public YmkNativeFunction(String name, int arity, NativeImpl impl) {
    this.name = name;
    this.arity = arity;
    this.impl = impl;
  }

  @Override
  public int arity() {
    return arity;
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    return impl.call(interpreter, arguments);
  }

  @Override
  public String toString() {
    return "<native fn>";
  }
}
