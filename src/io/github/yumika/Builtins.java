package io.github.yumika;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Builtins {

  static YmkInstance loadBuiltins(Interpreter interpreter) {
    Map<String, YmkFunction> methods = new HashMap<>();
    YmkInstance builtins = new YmkInstance(new YmkClass("__builtins__", null, methods)) {
      @Override
      public String toString() {
        return "__builtins__";
      }
    };
    builtins.set("env", new YmkCallable() {
      @Override
      public int arity() {
        return 0;
      }
      @Override
      public Object call(Interpreter interpreter,
                         List<Object> arguments,
                         Map<String, Object> kwargs) {
        return interpreter.getEnvironment();
      }

      @Override
      public String toString() { return "<native fn environment>"; }
    }, interpreter);
    builtins.set("typeof", new YmkCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, Map<String, Object> kwargs) {
        Object value = arguments.get(0);

        return interpreter.getTypeName(value);
      }

      @Override
      public String toString() {
        return "<native fn typeof>";
      }
    }, interpreter);
    builtins.set("isNumber", interpreter.isTypeOf(Double.class), interpreter);
    builtins.set("isString", interpreter.isTypeOf(String.class), interpreter);
    builtins.set("isBoolean", interpreter.isTypeOf(Boolean.class), interpreter);
    builtins.set("isArray", interpreter.isTypeOf(List.class), interpreter);
    builtins.set("isFunction", interpreter.isTypeOfFunction(), interpreter);
    builtins.set("isObject", interpreter.isTypeOf(YmkInstance.class), interpreter);
    builtins.set("str", new YmkCallable() {
      @Override
      public int arity() {
        return -2;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, Map<String, Object> kwargs) {
        StringBuilder strBuilder = new StringBuilder();
        boolean isEmpty = true;
        for (Object argument : arguments) {
          strBuilder.append(argument.toString()).append(" ");
          if (isEmpty) {
            isEmpty = false;
          }
        }
        if (!isEmpty) {
          strBuilder.deleteCharAt(strBuilder.length() - 1);
        }
        return strBuilder.toString();
      }

      @Override
      public String toString() { return "<native fn to-string>"; }
    }, interpreter);
    builtins.set("length", new YmkCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, Map<String, Object> kwargs) {
        if (arguments.get(0) instanceof List<?> list) {
          return list.size();
        } else if (arguments.get(0) instanceof String string) {
          return string.length();
        } else if (arguments.get(0) instanceof Map<?, ?> map) {
          return map.size();
        } else {
          throw new RuntimeError(null, "Argument doesn't have a length.");
        }
      }

      @Override
      public String toString() { return "<native fn length>"; }
    }, interpreter);
    builtins.set("clock", new YmkCallable() {
      @Override
      public int arity() { return 0; }

      @Override
      public Object call(Interpreter interpreter,
                         List<Object> arguments,
                         Map<String, Object> kwargs) {
        return (double)System.currentTimeMillis() / 1000.0;
      }

      @Override
      public String toString() { return "<native fn>"; }
    }, interpreter);
    builtins.set("setTimeout",
        new YmkNativeFunction("setTimeout", 2,
            (_interpreter, args) -> {

              Object fn = args.get(0);
              double delay = (double) args.get(1);

              if (!(fn instanceof YmkCallable)) {
                throw new RuntimeError(null,
                    "First argument to setTimeout must be callable.");
              }
              return _interpreter.setTimeout((YmkCallable) fn, delay);

            }
        ), interpreter);
    builtins.set("setInterval",
        new YmkNativeFunction("setInterval", 2,
            (_interpreter, args) -> {

              Object fn = args.get(0);
              double delay = (double) args.get(1);

              if (!(fn instanceof YmkCallable)) {
                throw new RuntimeError(null,
                    "First argument to setInterval must be callable.");
              }
              return _interpreter.setInterval((YmkCallable) fn, delay);
            }
        ), interpreter);
    builtins.set("clearTimeout",
        new YmkNativeFunction("clearTimeout", 1,
            (_interpreter, args) -> {
              int id = (Integer) args.get(0);
              _interpreter.clearTimer(id);
              return null;
            }
        ), interpreter);
    builtins.set("clearInterval",
        new YmkNativeFunction("clearInterval", 1,
            (_interpreter, args) -> {
              int id = (Integer) args.get(0);
              _interpreter.clearTimer(id);
              return null;
            }
        ), interpreter);

    builtins.set("exit", new YmkCallable() {
      @Override
      public int arity() { return 0;}

      @Override
      public Void call(Interpreter interpreter,
                       List<Object> arguments,
                       Map<String, Object> kwargs) {
        System.exit(0);
        return null;
      }

      @Override
      public String toString() { return "<native fn exit>"; }
    }, interpreter);

    return builtins;
  }
}
