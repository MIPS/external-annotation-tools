package annotator.find;

import java.util.ArrayList;
import java.util.List;

import annotations.el.InnerTypeLocation;

import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;

public class ReturnTypeCriterion implements Criterion {

  private String methodName;
  private Criterion inMethodCriterion;

  public ReturnTypeCriterion(String methodName) {
    this.methodName = methodName; // substring(0, name.indexOf(")") + 1);
    this.inMethodCriterion = Criteria.inMethod(methodName);
  }

  public boolean isSatisfiedBy(TreePath path) {
    if (path == null) {
      return false;
    }

    if (Criteria.debug) {
      System.err.println("ReturnTypeCriterion.isSatisfiedBy deferring to inMethodCriterion");
    }
    return inMethodCriterion.isSatisfiedBy(path);
  }

  public Kind getKind() {
    return Kind.RETURN_TYPE;
  }

  public String toString() {
    return "ReturnTypeCriterion for method: " + methodName;
  }
}
