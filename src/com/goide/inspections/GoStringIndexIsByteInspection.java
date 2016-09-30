/*
 * Copyright 2013-2016 Sergey Ignatov, Alexander Zolotov, Florin Patan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.goide.inspections;

import com.goide.GoTypes;
import com.goide.psi.*;
import com.goide.psi.impl.GoTypeUtil;
import com.goide.quickfix.GoStringIndexIsByteQuickFix;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class GoStringIndexIsByteInspection extends GoInspectionBase {

  private static final String TEXT_HINT = "Mismatched types: byte and string";

  @NotNull
  @Override
  protected GoVisitor buildGoVisitor(@NotNull ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
    return new GoVisitor() {

      @Override
      public void visitConditionalExpr(@NotNull GoConditionalExpr o) {
        if (o.getEq() == null) {
          return;
        }

        if (o.getLeft() instanceof GoIndexOrSliceExpr && o.getRight() instanceof GoStringLiteral) {
          GoIndexOrSliceExpr left = (GoIndexOrSliceExpr)o.getLeft();
          GoStringLiteral right = (GoStringLiteral)o.getRight();
          checkTypeMismatch(o, holder, left, right, new GoStringIndexIsByteQuickFix(right));
        }
        else if (o.getLeft() instanceof GoStringLiteral && o.getRight() instanceof GoIndexOrSliceExpr) {
          GoIndexOrSliceExpr right = (GoIndexOrSliceExpr)o.getRight();
          GoStringLiteral left = (GoStringLiteral)o.getLeft();
          checkTypeMismatch(o, holder, right, left, new GoStringIndexIsByteQuickFix(left));
        }
      }
    };
  }

  private static void checkTypeMismatch(@NotNull GoConditionalExpr o,
                                        @NotNull ProblemsHolder holder,
                                        GoIndexOrSliceExpr expr,
                                        GoStringLiteral stringLiteral,
                                        GoStringIndexIsByteQuickFix fix) {
    if (isStringIndexExpression(expr)) {
      if (isSingleCharLiteral(stringLiteral)) {
        holder.registerProblem(o, TEXT_HINT, ProblemHighlightType.GENERIC_ERROR, fix);
      }
      else {
        holder.registerProblem(o, TEXT_HINT, ProblemHighlightType.GENERIC_ERROR);
      }
    }
  }

  private static boolean isStringIndexExpression(GoIndexOrSliceExpr expr) {
    GoType type = Optional.of(expr)
      .map(GoIndexOrSliceExpr::getExpression)
      .map(e -> e.getGoType(null))
      .orElse(null);

    if (!GoTypeUtil.isString(type)) {
      return false;
    }

    Trinity<GoExpression, GoExpression, GoExpression> indices = expr.getIndices();
    PsiElement colonCandidate = Optional.ofNullable(expr.getRbrack())
      .map(PsiElement::getPrevSibling)
      .orElse(null);

    boolean isSliceExpr = indices.getSecond() != null
                          || indices.getThird() != null
                          || isColon(colonCandidate);

    return !isSliceExpr;
  }

  private static boolean isColon(PsiElement element) {
    if (!(element instanceof TreeElement)) {
      return false;
    }
    return ((TreeElement)element).getElementType().equals(GoTypes.COLON);
  }

  private static boolean isSingleCharLiteral(GoStringLiteral literal) {
    return literal.getTextLength() == 3
           && literal.getText().charAt(0) == '\"'
           && literal.getText().charAt(2) == '\"';
  }
}
