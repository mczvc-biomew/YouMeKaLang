package io.github.yumika;

import java.util.List;
import java.util.Map;

public class CUtils {
  static boolean isMap(Object value) {
    return value instanceof Map<?, ?>;
  }

  static boolean isYmkInstance(Object value) {
    return value instanceof YmkInstance;
  }

  static boolean isPair(Object value) {
    return isMap(value) || isYmkInstance(value);
  }

  static String repeatString(String str, int times) {
    if (times < 0) return "";
    StringBuilder builder = new StringBuilder(str.length() * times);
    for (int i = 0; i < times; i++) {
      builder.append(str);
    }
    return builder.toString();
  }

  static String stringify(List<?> list, int depth) {

    StringBuilder sb = new StringBuilder();
    boolean notEmpty = false;

    if (list.isEmpty()) return "\n\n" + list.toString() + "\n\n";
    var first = list.get(0);
    int matchCount = 0;
    for (Object item : list) {
      if (item.toString().equals(first.toString())) {
        matchCount++;
      }
    };
    if (list.size() > 9 && matchCount == list.size()) {
      sb.append(repeatString(" ", depth * 2)).append('[');
      for (int i = 0; i < 10; i++) {
        sb.append(first.toString()).append(", ");
      }
      return sb.append("(...").append(list.size() - 10).append(" more times) ]").toString();
    }

    sb.append(repeatString(" ", (depth - 1) * 2)).append("[");
    for (Object element : list) {
      if (element instanceof Double) {
        sb.append(String.format("%s, ", element));
      } else {
        sb.append(String.format("\"%s\", ", element.toString()));
      }
      if (!notEmpty) {
        notEmpty = true;
      }
    }
    if (notEmpty) {
      sb.delete(sb.length() - 2, sb.length());
    }
    sb.append("]");
    return sb.toString();
  }

  static String stringify(int depth, Object ...args) {
    StringBuilder builder = new StringBuilder();
    boolean removeTrailingSpace = false;
    for (Object arg : args) {
      builder.append(stringify(arg, depth)).append(" ");
      removeTrailingSpace = true;
    }
    if (removeTrailingSpace)
      builder.delete(builder.length() - 1, builder.length());
    return builder.toString();
  }

  static StringBuilder indentedBrace(StringBuilder sb, int level, int depth) {
    sb.append(stringify(repeatString(" ", level * 2) + "}", depth));
    return sb;
  }

  static StringBuilder stringifyPairWithIndent(Object key, String value, int level, String indent,
                                                         String connector, String separator, StringBuilder pairBuilder) {
    pairBuilder.append(repeatString(indent, level * 2));
    pairBuilder.append(stringify(key, level + 1)).append(" ").append(connector).append(" ");
    pairBuilder.append(value).append(separator);
    return pairBuilder;
  }

  static String stringify(Object object, int depth) {
    if (object == null) return "null";

    if (object instanceof Double) {
      String text = object.toString();
      if (text.endsWith(".0")) {
        text = text.substring(0, text.length() - 2);
      }

      return text;

    } else if (object instanceof List<?> list) {
      return stringify(list, depth + 1);

    } else if (object instanceof Expr.ObjectLiteral objLiteral) {
      StringBuilder pairBuilder = new StringBuilder();
      pairBuilder.append("{");
      for (Expr.ObjectLiteral.Property prop : objLiteral.properties) {
        if (prop instanceof Expr.ObjectLiteral.Pair pair) {
          pairBuilder.append("\n");
          if (pair.value instanceof Expr.Variable var) {
            stringifyPairWithIndent(
                pair.key.lexeme, var.name.lexeme,
                depth + 1, "", "->", ";",
                pairBuilder);
          } else {
            pairBuilder.append(stringify(pair.value, depth));
          }
        }
      }
      pairBuilder.append("\n");
      indentedBrace(pairBuilder, depth - 1, depth + 1);
      return pairBuilder.toString();

    } else if (object instanceof YmkInstance inst) {
      StringBuilder pairBuilder = new StringBuilder();
      boolean removeTrailingComma = false;
      pairBuilder.append("{");
      for ( Map.Entry<?, ?> entry : inst.getFields().entrySet() ) {
        pairBuilder.append("\n");
        stringifyPairWithIndent(entry.getKey(),
            stringify(entry.getValue(),
                CUtils.isPair(entry.getValue()) ? depth+1 : depth),
            depth, "", "->", ",",
            pairBuilder);
        removeTrailingComma = true;
      }
      if (removeTrailingComma)
        pairBuilder.delete(pairBuilder.length() - 1, pairBuilder.length());
      pairBuilder.append("\n");
      indentedBrace(pairBuilder, depth - 1, depth);
      return pairBuilder.toString();
    } else if (object instanceof Map<?, ?> objMap) {
      StringBuilder pairBuilder = new StringBuilder("{");
      boolean removeTrailingComma = false;
      for (Map.Entry<?, ?> entry : objMap.entrySet()) {
        pairBuilder.append("\n");

        stringifyPairWithIndent(entry.getKey(),
            stringify(entry.getValue(),
                CUtils.isPair(entry.getValue()) ? depth+1 : 0),
            depth, "", "->", ",",
            pairBuilder);
        removeTrailingComma = true;
      }
      if (removeTrailingComma)
        pairBuilder.delete(pairBuilder.length() - 1, pairBuilder.length());
      pairBuilder.append("\n");
      indentedBrace(pairBuilder, depth - 1, depth);
      return pairBuilder.toString();
    }

    return repeatString(" ", depth * 2) + object.toString();
  }
}
