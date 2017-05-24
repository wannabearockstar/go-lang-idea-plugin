/*
 * Copyright 2013-2017 Sergey Ignatov, Alexander Zolotov, Florin Patan
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

package com.goide.inspections.unresolved;

import com.goide.psi.GoFieldDeclaration;
import com.goide.psi.GoStructType;
import com.goide.psi.impl.GoElementFactory;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GoAddStructFieldFix extends LocalQuickFixOnPsiElement {
  public static final String QUICK_FIX_NAME = "Add missing field";
  private final String myFieldText;
  private final String myTypeText;

  protected GoAddStructFieldFix(String fieldText, String typeText, @NotNull GoStructType element) {
    super(element);
    myFieldText = fieldText;
    myTypeText = typeText;
  }

  @NotNull
  @Override
  public String getText() {
    return QUICK_FIX_NAME;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    GoStructType structType = ObjectUtils.tryCast(startElement, GoStructType.class);
    if (structType == null) return;
    List<GoFieldDeclaration> declarations = structType.getFieldDeclarationList();
    PsiElement anchor = !declarations.isEmpty() ? ContainerUtil.getLastItem(declarations) : structType.getLbrace();
    if (anchor != null) structType.addAfter(GoElementFactory.createFieldDeclaration(project, myFieldText, myTypeText), anchor);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QUICK_FIX_NAME;
  }
}
