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

import com.goide.GoTypes;
import com.goide.psi.GoStringLiteral;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.lang.ASTFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.GeneratedMarkerVisitor.markGenerated;
import static com.intellij.psi.impl.source.DummyHolderFactory.createHolder;

public class GoStringIndexIsByteQuickFix extends LocalQuickFixOnPsiElement {

  public static final String NAME = "Convert string to byte";

  public GoStringIndexIsByteQuickFix(@NotNull GoStringLiteral element) {
    super(element);
  }

  @NotNull
  @Override
  public String getText() {
    return NAME;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    if (startElement.getTextLength() < 2) {
      return;
    }
    startElement.replace(createChar(startElement));
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return getName();
  }

  private static PsiElement createChar(PsiElement element) throws IncorrectOperationException {
    FileElement holderElement = createHolder(element.getContainingFile().getManager(), null).getTreeElement();
    LeafElement newElement = ASTFactory.leaf(GoTypes.CHAR, holderElement.getCharTable().intern("'" + element.getText().charAt(1) + "'"));
    holderElement.rawAddChildren(newElement);
    markGenerated(newElement.getPsi());
    return newElement.getPsi();
  }
}
