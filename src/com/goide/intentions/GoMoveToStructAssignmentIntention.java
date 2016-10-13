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

package com.goide.intentions;

import com.goide.inspections.GoInspectionUtil;
import com.goide.psi.*;
import com.goide.psi.impl.GoElementFactory;
import com.goide.psi.impl.GoPsiImplUtil;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GoMoveToStructAssignmentIntention extends BaseElementAtCaretIntentionAction {
  public static final String NAME = "Move field assignment to struct initialization";

  public GoMoveToStructAssignmentIntention() {
    setText(NAME);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return NAME;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!element.isValid() || !element.isWritable()) return false;
    GoAssignmentStatement assignment = PsiTreeUtil.getNonStrictParentOfType(element, GoAssignmentStatement.class);
    if (assignment == null || !assignment.isValid() || !isAssignmentEqualsSized(assignment)) return false;
    GoReferenceExpression selectedField = getFieldReference(element, assignment);
    if (selectedField == null) return false;
    GoCompositeLit structDefinition = getStructDefinitionByField(selectedField, assignment);
    String qualifierName = getQualifierName(selectedField);
    return structDefinition != null && qualifierName != null &&
           !getUninitializedFieldsByQualifier(assignment, structDefinition, qualifierName).isEmpty();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!element.isValid() || !element.isWritable()) return;
    GoAssignmentStatement assignment = PsiTreeUtil.getNonStrictParentOfType(element, GoAssignmentStatement.class);
    if (assignment == null || !assignment.isValid() || !isAssignmentEqualsSized(assignment)) return;
    GoReferenceExpression selectedField = getFieldReference(element, assignment);
    if (selectedField == null) return;
    GoCompositeLit structDefinition = getStructDefinitionByField(selectedField, assignment);
    String qualifierName = getQualifierName(selectedField);
    if (structDefinition == null || qualifierName == null) return;
    List<GoExpression> fields = getUninitializedFieldsByQualifier(assignment, structDefinition, qualifierName);
    if (fields.isEmpty()) return;
    moveFields(fields, assignment, structDefinition, project);
  }

  @NotNull
  private static List<GoExpression> getUninitializedFieldsByQualifier(@NotNull GoAssignmentStatement assignment,
                                                                      @NotNull GoCompositeLit definition,
                                                                      @NotNull String qualifier) {
    return ContainerUtil.filter(assignment.getLeftHandExprList().getExpressionList(),
                                e -> isFieldDefinitionReference(e) &&
                                     qualifier.equals(getQualifierName((GoReferenceExpression)e)) &&
                                     isFieldUninitialized((GoReferenceExpression)e, definition));
  }

  private static void moveFields(@NotNull List<GoExpression> fields,
                                 @NotNull GoAssignmentStatement fromAssignment,
                                 @NotNull GoCompositeLit toStructDefinition,
                                 @NotNull Project project) {
    GoLiteralValue structFieldDefinitions = toStructDefinition.getLiteralValue();
    if (structFieldDefinitions == null) return;
    for (GoExpression expr : fields) {
      if (!(expr instanceof GoReferenceExpression)) continue;
      GoReferenceExpression field = (GoReferenceExpression)expr;
      if (!isFieldDefinitionReference(field)) continue;

      GoExpression fieldValue = GoPsiImplUtil.getExpressionValue(fromAssignment, field);
      if (fieldValue == null) continue;

      GoPsiImplUtil.deleteExpressionFromAssignment(fromAssignment, field);
      addFieldDefinition(structFieldDefinitions, getFieldName(field), fieldValue.getText(), project);
    }
  }

  @Nullable
  private static String getQualifierName(@NotNull GoReferenceExpression fieldReference) {
    GoReferenceExpression qualifier = fieldReference.getQualifier();
    return qualifier != null ? qualifier.getText() : null;
  }

  private static boolean isAssignmentEqualsSized(@NotNull GoAssignmentStatement assignment) {
    List<GoExpression> expressions = assignment.getExpressionList();
    return assignment.getLeftHandExprList().getExpressionList().size() == expressions.size() &&
           expressions.stream()
             .allMatch(e -> !(e instanceof GoCallExpr) || GoInspectionUtil.getFunctionResultCount((GoCallExpr)e) == 1);
  }

  @Nullable
  private static GoReferenceExpression getFieldReference(@NotNull PsiElement selectedElement,
                                                         @NotNull GoAssignmentStatement assignment) {
    List<GoExpression> structFields = ContainerUtil
      .filter(assignment.getLeftHandExprList().getExpressionList(), GoMoveToStructAssignmentIntention::isFieldDefinitionReference);
    if (structFields.isEmpty()) return null;

    GoReferenceExpression selectedFieldReference = PsiTreeUtil.getTopmostParentOfType(selectedElement, GoReferenceExpression.class);
    if (selectedFieldReference != null && isFieldDefinitionReference(selectedFieldReference)) return selectedFieldReference;

    long differentStructsQualifiersCount = structFields.stream()
      .map(e -> getQualifierName((GoReferenceExpression)e))
      .distinct().count();
    if (differentStructsQualifiersCount != 1) return null;
    GoExpression onlyFieldReference = ContainerUtil.getFirstItem(structFields);
    return onlyFieldReference instanceof GoReferenceExpression ? (GoReferenceExpression)onlyFieldReference : null;
  }

  @Contract("null -> false")
  private static boolean isFieldDefinitionReference(@Nullable GoExpression expression) {
    return expression instanceof GoReferenceExpression && isFieldDefinition(((GoReferenceExpression)expression).resolve());
  }

  @Contract(value = "null -> false", pure = true)
  private static boolean isFieldDefinition(@Nullable PsiElement element) {
    return element instanceof GoFieldDefinition || element instanceof GoAnonymousFieldDefinition;
  }

  @Nullable
  private static GoCompositeLit getStructDefinitionByField(@NotNull GoReferenceExpression field,
                                                           @NotNull GoAssignmentStatement assignment) {
    PsiElement previousElement = PsiTreeUtil.skipSiblingsBackward(assignment, PsiWhiteSpace.class);
    if (previousElement instanceof GoSimpleStatement) {
      return getStructDefinition(field, (GoSimpleStatement)previousElement);
    }
    if (previousElement instanceof GoAssignmentStatement) {
      return getStructDefinition(field, (GoAssignmentStatement)previousElement);
    }
    return null;
  }

  @Nullable
  private static GoCompositeLit getStructDefinition(@NotNull GoReferenceExpression field, @NotNull GoSimpleStatement structDeclaration) {
    GoShortVarDeclaration varDeclaration = structDeclaration.getShortVarDeclaration();
    if (varDeclaration == null) return null;
    GoVarDefinition structVarDefinition =
      ContainerUtil.find(varDeclaration.getVarDefinitionList(),
                         struct -> GoPsiImplUtil.getName(struct).equals(getQualifierName(field)) && isStructWithField(field, struct));
    if (structVarDefinition == null) return null;
    GoExpression structDefinition = structVarDefinition.getValue();
    return structDefinition instanceof GoCompositeLit ? (GoCompositeLit)structDefinition : null;
  }

  @Nullable
  private static GoCompositeLit getStructDefinition(@NotNull GoReferenceExpression field, @NotNull GoAssignmentStatement structAssignment) {
    GoExpression struct = ContainerUtil.find(structAssignment.getLeftHandExprList().getExpressionList(), expression -> {
      if (!(expression instanceof GoReferenceExpression)) return false;
      PsiElement structDefinition = ((GoReferenceExpression)expression).resolve();
      return structDefinition instanceof GoVarDefinition &&
             GoPsiImplUtil.getName((GoVarDefinition)structDefinition).equals(getQualifierName(field)) &&
             isStructWithField(field, (GoVarDefinition)structDefinition);
    });
    if (struct == null) return null;
    GoExpression structDefinition = GoPsiImplUtil.getExpressionValue(structAssignment, struct);
    return structDefinition instanceof GoCompositeLit ? (GoCompositeLit)structDefinition : null;
  }

  private static boolean isStructWithField(@NotNull GoReferenceExpression field, @NotNull GoVarDefinition structVar) {
    GoStructType structType = getStructType(structVar);
    return structType != null && hasFieldByName(structType, getFieldName(field));
  }

  @Nullable
  private static GoStructType getStructType(@NotNull GoVarDefinition structVar) {
    GoType varType = structVar.getGoType(null);
    GoType type = varType != null ? varType.getUnderlyingType() : null;
    return type instanceof GoStructType ? (GoStructType)type : null;
  }

  private static boolean hasFieldByName(@NotNull GoStructType struct, @NotNull String name) {
    return !GoPsiImplUtil.goTraverser().withRoot(struct).traverse()
      .filter(PsiNamedElement.class)
      .filter(d -> isFieldDefinition(d) && name.equals(d.getName()))
      .isEmpty();
  }

  private static boolean isFieldUninitialized(@NotNull GoReferenceExpression field, @NotNull GoCompositeLit structDefinition) {
    GoLiteralValue fieldDefinitions = structDefinition.getLiteralValue();
    return fieldDefinitions != null && !ContainerUtil.exists(fieldDefinitions.getElementList(), e -> hasFieldName(e, getFieldName(field)));
  }

  private static boolean hasFieldName(@NotNull GoElement fieldDefinition, @NotNull String fieldName) {
    GoKey field = fieldDefinition.getKey();
    return field != null && field.getFieldName() != null && fieldName.equals(field.getFieldName().getText());
  }

  @NotNull
  private static String getFieldName(@NotNull GoReferenceExpression field) {
    return field.getIdentifier().getText();
  }

  private static void addFieldDefinition(@NotNull GoLiteralValue structFieldDefinitions,
                                         @NotNull String name,
                                         @NotNull String value,
                                         @NotNull Project project) {
    PsiElement field = GoElementFactory.createNamedStructField(project, name, value);
    GoElement lastFieldDefinition = ContainerUtil.getLastItem(structFieldDefinitions.getElementList());
    if (lastFieldDefinition == null) {
      structFieldDefinitions.addAfter(field, structFieldDefinitions.getLbrace());
    }
    else {
      lastFieldDefinition.add(GoElementFactory.createComma(project));
      lastFieldDefinition.add(field);
    }
  }
}
