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

package com.goide.quickfix;

import com.goide.psi.GoConditionalExpr;
import com.goide.psi.GoExpression;
import com.goide.psi.GoIndexOrSliceExpr;
import com.goide.psi.GoStringLiteral;
import com.intellij.codeInspection.LocalQuickFixBase;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.goide.inspections.GoStringIndexIsByteInspection.isSingleCharLiteral;
import static com.goide.psi.impl.GoElementFactory.createComparison;
import static java.lang.String.format;

public class GoStringIndexIsByteQuickFix extends LocalQuickFixBase {

  public static final String NAME = "Convert string to byte";

  public GoStringIndexIsByteQuickFix() {
    super(NAME);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof GoConditionalExpr)) {
      return;
    }

    GoConditionalExpr expr = (GoConditionalExpr)element;
    if (expr.getEq() == null) {
      return;
    }

    GoExpression left = expr.getLeft();
    GoExpression right = expr.getRight();

    if (left instanceof GoIndexOrSliceExpr && right instanceof GoStringLiteral) {
      GoStringLiteral literal = (GoStringLiteral)right;
      if (!isSingleCharLiteral(literal)) {
        return;
      }
      expr.replace(createComparison(project, left.getText() + " == " + extractSingleCharFromText(literal)));
    }
    else if (left instanceof GoStringLiteral && right instanceof GoIndexOrSliceExpr) {
      GoStringLiteral literal = (GoStringLiteral)left;
      if (!isSingleCharLiteral(literal)) {
        return;
      }
      expr.replace(createComparison(project, extractSingleCharFromText(literal) + " == " + right.getText()));
    }
  }

  private static String extractSingleCharFromText(@NotNull GoStringLiteral element) {
    return format("'%c'", element.getText().charAt(1));
  }
}
