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

import com.goide.psi.*;
import com.goide.psi.impl.GoElementFactory;
import com.goide.psi.impl.GoPsiImplUtil;
import com.goide.util.GoUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.emptyList;
import static com.intellij.util.containers.ContainerUtil.list;
import static java.lang.Math.min;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;

public class GoStructInitializationInspection extends GoInspectionBase {
  public static final String REPLACE_WITH_NAMED_STRUCT_FIELD_FIX_NAME = "Replace with named struct fields";
  private static final GoReplaceWithNamedStructFieldQuickFix QUICK_FIX = new GoReplaceWithNamedStructFieldQuickFix();
  public boolean reportLocalStructs;
  /**
   * @deprecated use {@link #reportLocalStructs}
   */
  @SuppressWarnings("WeakerAccess") public Boolean reportImportedStructs;

  @NotNull
  @Override
  protected GoVisitor buildGoVisitor(@NotNull ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
    return new GoVisitor() {
      @Override
      public void visitLiteralValue(@NotNull GoLiteralValue literalValue) {
        GoStructType structType = getLiteralStructType(literalValue);
        if (structType == null || !isStructImportedOrLocalAllowed(structType, literalValue)) return;

        List<GoElement> elements = literalValue.getElementList();
        List<GoNamedElement> definitions = getFieldDefinitions(structType);

        if (!areElementsKeysMatchesDefinitions(elements, definitions)) return;
        registerProblemsForElementsWithoutKeys(elements, definitions.size());
      }

      private void registerProblemsForElementsWithoutKeys(@NotNull List<GoElement> elements, int definitionsCount) {
        for (int i = 0; i < min(elements.size(), definitionsCount); i++) {
          if (elements.get(i).getKey() != null) continue;
          holder.registerProblem(elements.get(i), "Unnamed field initialization", ProblemHighlightType.WEAK_WARNING, QUICK_FIX);
        }
      }
    };
  }

  @Contract("null -> null")
  private static GoStructType getLiteralStructType(@Nullable GoLiteralValue literalValue) {
    GoCompositeLit parentLit = GoPsiTreeUtil.getDirectParentOfType(literalValue, GoCompositeLit.class);
    if (parentLit != null && !isStructLit(parentLit)) return null;

    GoStructType litType = ObjectUtils.tryCast(GoPsiImplUtil.getLiteralType(literalValue, parentLit == null), GoStructType.class);
    GoNamedElement definition = getFieldDefinition(GoPsiTreeUtil.getDirectParentOfType(literalValue, GoValue.class));
    return definition != null && litType != null ? getUnderlyingStructType(definition.getGoType(null)) : litType;
  }

  @Nullable
  private static GoNamedElement getFieldDefinition(@Nullable GoValue value) {
    GoKey key = PsiTreeUtil.getPrevSiblingOfType(value, GoKey.class);
    GoFieldName fieldName = key != null ? key.getFieldName() : null;
    PsiElement field = fieldName != null ? fieldName.resolve() : null;
    return GoPsiImplUtil.isFieldDefinition(field) ? ObjectUtils.tryCast(field, GoNamedElement.class) : null;
  }

  @Nullable
  @Contract("null -> null")
  private static GoStructType getUnderlyingStructType(@Nullable GoType type) {
    return type != null ? ObjectUtils.tryCast(type.getUnderlyingType(), GoStructType.class) : null;
  }

  private static boolean isStructLit(@NotNull GoCompositeLit compositeLit) {
    return getUnderlyingStructType(compositeLit.getGoType(null)) != null;
  }

  private boolean isStructImportedOrLocalAllowed(@NotNull GoStructType structType, @NotNull GoLiteralValue literalValue) {
    return reportLocalStructs || !GoUtil.inSamePackage(structType.getContainingFile(), literalValue.getContainingFile());
  }

  private static boolean areElementsKeysMatchesDefinitions(@NotNull List<GoElement> elements, @NotNull List<GoNamedElement> definitions) {
    return range(0, elements.size()).allMatch(i -> isNullOrNamesEqual(elements.get(i).getKey(), GoPsiImplUtil.getByIndex(definitions, i)));
  }

  @Contract("null, _ -> true; !null, null -> false")
  private static boolean isNullOrNamesEqual(@Nullable GoKey key, @Nullable GoNamedElement elementToCompare) {
    return key == null || elementToCompare != null && Comparing.equal(key.getText(), elementToCompare.getName());
  }

  @NotNull
  private static List<GoNamedElement> getFieldDefinitions(@Nullable GoStructType type) {
    return type != null ? type.getFieldDeclarationList().stream()
      .flatMap(declaration -> getFieldDefinitions(declaration).stream())
      .collect(toList()) : emptyList();
  }

  @NotNull
  private static List<? extends GoNamedElement> getFieldDefinitions(@NotNull GoFieldDeclaration declaration) {
    GoAnonymousFieldDefinition anonymousDefinition = declaration.getAnonymousFieldDefinition();
    return anonymousDefinition != null ? list(anonymousDefinition) : declaration.getFieldDefinitionList();
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Report for local type definitions as well", this, "reportLocalStructs");
  }

  private static class GoReplaceWithNamedStructFieldQuickFix extends LocalQuickFixBase {

    public GoReplaceWithNamedStructFieldQuickFix() {
      super(REPLACE_WITH_NAMED_STRUCT_FIELD_FIX_NAME);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = ObjectUtils.tryCast(descriptor.getStartElement(), GoElement.class);
      GoLiteralValue literal = element != null && element.isValid() ? PsiTreeUtil.getParentOfType(element, GoLiteralValue.class) : null;

      List<GoElement> elements = literal != null ? literal.getElementList() : emptyList();
      List<GoNamedElement> definitions = getFieldDefinitions(getLiteralStructType(literal));
      if (!areElementsKeysMatchesDefinitions(elements, definitions)) return;
      addKeysToElements(project, elements, definitions);
    }
  }

  private static void addKeysToElements(@NotNull Project project,
                                        @NotNull List<GoElement> elements,
                                        @NotNull List<GoNamedElement> definitions) {
    for (int i = 0; i < min(elements.size(), definitions.size()); i++) {
      GoElement element = elements.get(i);
      String fieldDefinitionName = definitions.get(i).getName();
      GoValue value = fieldDefinitionName != null && element.getKey() == null ? element.getValue() : null;
      if (value != null) element.replace(GoElementFactory.createLiteralValueElement(project, fieldDefinitionName, value.getText()));
    }
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    if (reportImportedStructs != null) {
      reportLocalStructs = reportImportedStructs;
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    reportImportedStructs = null;
    super.writeSettings(node);
  }
}
