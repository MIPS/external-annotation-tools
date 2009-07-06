package annotator.find;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;

public class IsSigMethodCriterion implements Criterion {

  // The context is used for determining the fully qualified name of methods.
  private static class Context {
    public String packageName;
    public List<String> imports;
    public Context(String packageName, List<String> imports) {
      this.packageName = packageName;
      this.imports = imports;
    }
  }
  private static Map<CompilationUnitTree, Context> contextCache = new HashMap<CompilationUnitTree, Context>();

  private String fullMethodName;
  private String simpleMethodName;
  private List<String> fullyQualifiedParams;

  public IsSigMethodCriterion(String methodName) {
    this.fullMethodName = methodName.substring(0, methodName.indexOf(")") + 1);
    this.simpleMethodName = methodName.substring(0, methodName.indexOf("("));
//    this.fullyQualifiedParams = new ArrayList<String>();
//    for (String s : methodName.substring(
//        methodName.indexOf("(") + 1, methodName.indexOf(")")).split(",")) {
//      if (s.length() > 0) {
//        fullyQualifiedParams.add(s);
//      }
//    }
    this.fullyQualifiedParams = new ArrayList<String>();
    try {
      parseParams(
        methodName.substring(methodName.indexOf("(") + 1,
            methodName.indexOf(")")));
    } catch(Exception e) {
      throw new RuntimeException("Caught exception while parsing method: " +
          methodName, e);
    }
  }

  private void parseParams(String params) {
    while (params.length() != 0) {
      String nextParam = readNext(params);
      fullyQualifiedParams.add(nextParam);
      params = params.substring(nextParam.length());
    }
  }

  private String readNext(String restOfParams) {
    String firstChar = restOfParams.substring(0, 1);
    if (isPrimitiveLetter(firstChar)) {
      return firstChar;
    } else if (firstChar.equals("[")) {
      return "[" + readNext(restOfParams.substring(1));
    } else if (firstChar.equals("L")) {
      return "L" + restOfParams.substring(1, restOfParams.indexOf(";") + 1);
    } else {
      throw new RuntimeException("Unknown method params: " + fullMethodName);
    }
  }

  // called by isSatisfiedBy(TreePath), will get compilation unit on its own
  private static Context initImports(TreePath path) {
    CompilationUnitTree topLevel = path.getCompilationUnit();
    Context result = contextCache.get(topLevel);
    if (result != null) {
      return result;
    }

    ExpressionTree packageTree = topLevel.getPackageName();
    String packageName;
    if (packageTree == null) {
      packageName = ""; // the default package
    } else {
      packageName = packageTree.toString();
    }

    List<String> imports = new ArrayList<String>();
    for (ImportTree i : topLevel.getImports()) {
      String imported = i.getQualifiedIdentifier().toString();
      imports.add(imported);
    }

    result = new Context(packageName, imports);
    contextCache.put(topLevel, result);
    return result;
  }


  private boolean matchTypeParams(List<? extends VariableTree> sourceParams,
                                  Map<String, String> typeToClassMap,
                                  Context context) {
    assert sourceParams.size() == fullyQualifiedParams.size();
    for (int i = 0; i < sourceParams.size(); i++) {
      String fullType = fullyQualifiedParams.get(i);
      VariableTree vt = sourceParams.get(i);
      String simpleType = vt.getType().toString();

      boolean haveMatch = matchSimpleType(fullType, simpleType, context);
      if (!haveMatch) {
        if (typeToClassMap.containsKey(simpleType)) {
          simpleType = typeToClassMap.get(simpleType);
          haveMatch = matchSimpleType(fullType, simpleType, context);
        }
      }
      if (!haveMatch) {
        return false;
      }
    }
    return true;
  }


  private boolean matchSimpleType(String fullType, String simpleType, Context context) {

    // must strip off generics, is all of this necessary, though?
    // do you ever have generics anywhere but at the end?
    while (simpleType.contains("<")) {
      String beforeBracket = simpleType.substring(0, simpleType.indexOf("<"));
      String afterBracket = simpleType.substring(simpleType.indexOf(">") + 1);
      simpleType = beforeBracket + afterBracket;
    }


    // TODO: arrays?

    // first try quantifying simpleType with this package name,
    // then with java.lang
    // then with default package
    // then with all of the imports

    boolean matchable = false;

    if (!matchable) {
      // match with this package name
      String packagePrefix = context.packageName;
      if (packagePrefix.length() > 0) {
        packagePrefix = packagePrefix + ".";
      }
      if (matchWithPrefix(fullType, simpleType, packagePrefix)) {
        matchable = true;
      }
    }

    if (!matchable) {
      // match with java.lang
      if (matchWithPrefix(fullType, simpleType, "java.lang.")) {
        matchable = true;
      }
    }

    if (!matchable) {
      // match with default package
      if (matchWithPrefix(fullType, simpleType, "")) {
        matchable = true;
      }
    }

    if (!matchable) {
      // match with any of the imports
      for (String someImport : context.imports) {
        String importPrefix = null;
        if (someImport.contains("*")) {
          // don't include the * in the prefix, should end in .
          // TODO: this is a real bug due to nonnull, though I discovered it manually
          //importPrefix = someImport.substring(0, importPrefix.indexOf("*"));
          importPrefix = someImport.substring(0, someImport.indexOf("*"));
        } else {
          // if you imported a specific class, you can only use that import
          // if the last part matches the simple type
          String importSimpleType =
            someImport.substring(someImport.lastIndexOf(".") + 1);

          if (!simpleType.equals(importSimpleType)) {
            continue;
          }

          importPrefix = someImport.substring(0, someImport.lastIndexOf(".") + 1);
        }

        if (matchWithPrefix(fullType, simpleType, importPrefix)) {
          matchable = true;
          break; // out of for loop
        }
      }
    }

    return matchable;
  }

  private boolean matchWithPrefix(String fullType, String simpleType, String prefix) {
    String possibleFullType = turnTypeToString(prefix, simpleType);
    possibleFullType = possibleFullType.replace(".", "/");
    boolean b = fullType.equals(possibleFullType);
    return b;
  }

  private String turnTypeToString(String prefix, String simpleType) {
    if (simpleType.contains("[")) {
      return "[" + turnTypeToString(prefix,
          simpleType.substring(0, simpleType.lastIndexOf("[")));
    } else if (isPrimitive(simpleType)) {
      return primitiveLetter(simpleType);
    } else {
      return "L" + prefix + simpleType + ";";
    }
  }

  public boolean isSatisfiedBy(TreePath path) {
    if (path == null) {
      return false;
    }

    Context context = initImports(path);

    Tree leaf = path.getLeaf();

    if (leaf.getKind() == Tree.Kind.METHOD) {
      MethodTree mt = (MethodTree) leaf;

      if (simpleMethodName.equals(mt.getName().toString())) {

        List<? extends VariableTree> sourceParams = mt.getParameters();
        if (fullyQualifiedParams.size() == sourceParams.size()) {

          // now go through all type parameters declared by method
          // and for each one, create a mapping from the type to the
          // first declared extended class, defaulting to Object
          // for example,
          // <T extends Date> void foo(T t)
          //  creates mapping: T -> Date
          // <T extends Date & List> void foo(Object o)
          //  creates mapping: T -> Date
          // <T extends Date, U extends List> foo(Object o)
          //  creates mappings: T -> Date, U -> List
          // <T> void foo(T t)
          //  creates mapping: T -> Object

          Map<String, String> typeToClassMap = new HashMap<String, String>();
          for (TypeParameterTree param : mt.getTypeParameters()) {
            String paramName = param.getName().toString();
            String paramClass = "Object";
            List<? extends Tree> paramBounds = param.getBounds();
            if (paramBounds != null && paramBounds.size() >= 1) {
              paramClass = paramBounds.get(0).toString();
            }
            typeToClassMap.put(paramName, paramClass);
          }

          if (matchTypeParams(sourceParams, typeToClassMap, context)) {
            debug("IsSigMethodCriterion => true");
            return true;
          }
        }
      }
    } else {
      debug("IsSigMethodCriterion: not a METHOD tree");
    }

    debug("IsSigMethodCriterion => false");
    return false;
  }

  public Kind getKind() {
    return Kind.SIG_METHOD;
  }

//  public static String getSignature(MethodTree mt) {
//    String sig = mt.getName().toString().trim(); // method name, no parameters
//    sig += "(";
//    boolean first = true;
//    for (VariableTree vt : mt.getParameters()) {
//      if (!first) {
//        sig += ",";
//      }
//      sig += getType(vt.getType());
//      first = false;
//    }
//    sig += ")";
//
//    return sig;
//  }
//
//  private static String getType(Tree t) {
//    if (t.getKind() == Tree.Kind.PRIMITIVE_TYPE) {
//      return getPrimitiveType((PrimitiveTypeTree) t);
//    } else if (t.getKind() == Tree.Kind.IDENTIFIER) {
//      return "L" + ((IdentifierTree) t).getName().toString();
//    } else if (t.getKind() == Tree.Kind.PARAMETERIZED_TYPE) {
//      // don't care about generics due to erasure
//      return getType(((ParameterizedTypeTree) t).getType());
//    }
//    throw new RuntimeException("unable to get type of: " + t);
//  }
//
//  private static String getPrimitiveType(PrimitiveTypeTree pt) {
//    TypeKind tk = pt.getPrimitiveTypeKind();
//    if (tk == TypeKind.ARRAY) {
//      return "[";
//    } else if (tk == TypeKind.BOOLEAN) {
//      return "Z";
//    } else if (tk == TypeKind.BYTE) {
//      return "B";
//    } else if (tk == TypeKind.CHAR) {
//      return "C";
//    } else if (tk == TypeKind.DOUBLE) {
//      return "D";
//    } else if (tk == TypeKind.FLOAT) {
//      return "F";
//    } else if (tk == TypeKind.INT) {
//      return "I";
//    } else if (tk == TypeKind.LONG) {
//      return "J";
//    } else if (tk == TypeKind.SHORT) {
//      return "S";
//    }
//
//    throw new RuntimeException("Invalid TypeKind: " + tk);
//  }

  private boolean isPrimitive(String s) {
    return
      s.equals("boolean") ||
      s.equals("byte") ||
      s.equals("char") ||
      s.equals("double") ||
      s.equals("float") ||
      s.equals("int") ||
      s.equals("long") ||
      s.equals("short");
  }

  private boolean isPrimitiveLetter(String s) {
    return
      s.equals("Z") ||
      s.equals("B") ||
      s.equals("C") ||
      s.equals("D") ||
      s.equals("F") ||
      s.equals("I") ||
      s.equals("J") ||
      s.equals("S");
  }

  private String primitiveLetter(String s) {
    if (s.equals("boolean")) {
      return "Z";
    } else if (s.equals("byte")) {
      return "B";
    } else if (s.equals("char")) {
      return "C";
    } else if (s.equals("double")) {
      return "D";
    } else if (s.equals("float")) {
      return "F";
    } else if (s.equals("int")) {
      return "I";
    } else if (s.equals("long")) {
      return "J";
    } else if (s.equals("short")) {
      return "S";
    } else {
      throw new RuntimeException("IsSigMethodCriterion: unknown primitive: " + s);
    }
  }

  public String toString() {
    return "IsSigMethodCriterion: " + fullMethodName;
  }

  private static void debug(String s) {
    if (Criteria.debug) {
      System.out.println(s);
    }
  }

}
