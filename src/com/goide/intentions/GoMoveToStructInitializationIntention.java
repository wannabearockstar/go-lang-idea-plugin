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
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GoMoveToStructInitializationIntention extends BaseElementAtCaretIntentionAction {
  public static final String NAME = "Move field assignment to struct initialization";

  public GoMoveToStructInitializationIntention() {
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
    GoAssignmentStatement assignment = getValidAssignmentParent(element);
    GoReferenceExpression selectedFieldReference = assignment != null ? getFieldReference(element, assignment) : null;
    return selectedFieldReference != null && !getUninitializedSingleFieldReferences(assignment, selectedFieldReference).isEmpty();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!element.isValid() || !element.isWritable()) return;
    GoAssignmentStatement assignment = getValidAssignmentParent(element);
    if (assignment == null) return;
    GoReferenceExpression selectedFieldReference = getFieldReference(element, assignment);
    if (selectedFieldReference == null) return;
    GoCompositeLit structLiteral = getStructLiteralByReference(selectedFieldReference, assignment);
    if (structLiteral == null) return;
    moveFields(getUninitializedSingleFieldReferences(assignment, selectedFieldReference), structLiteral, project);
  }

  @Nullable
  private static GoAssignmentStatement getValidAssignmentParent(@Nullable PsiElement element) {
    return ObjectUtils.nullizeByCondition(PsiTreeUtil.getNonStrictParentOfType(element, GoAssignmentStatement.class),
                                          assignment -> assignment == null ||
                                                        !assignment.isValid() ||
                                                        !isAssignmentEqualsSized(assignment));
  }

  private static boolean isAssignmentEqualsSized(@NotNull GoAssignmentStatement assignment) {
    List<GoExpression> expressions = assignment.getExpressionList();
    return assignment.getLeftHandExprList().getExpressionList().size() == expressions.size() &&
           expressions.stream().allMatch(e -> GoInspectionUtil.getExpressionResultCount(e) == 1);
  }

  @Nullable
  private static GoReferenceExpression getFieldReference(@NotNull PsiElement selectedElement,
                                                         @NotNull GoAssignmentStatement assignment) {
    GoReferenceExpression selectedFieldReference = PsiTreeUtil.getTopmostParentOfType(selectedElement, GoReferenceExpression.class);
    if (isFieldReference(selectedFieldReference)) {
      return ObjectUtils.nullizeByCondition(selectedFieldReference, reference -> isValueAssignedInPreviousStatement(reference, assignment));
    }

    List<GoExpression> fieldReferences = ContainerUtil.filter(assignment.getLeftHandExprList().getExpressionList(),
                                                              GoMoveToStructInitializationIntention::isFieldReference);
    if (ContainerUtil.exists(fieldReferences, expression -> isValueAssignedInPreviousStatement(expression, assignment))) return null;

    long distinctStructsCount = fieldReferences.stream()
      .map(expression -> getQualifierName((GoReferenceExpression)expression))
      .distinct().count();
    return distinctStructsCount == 1 ? tryGetReference(ContainerUtil.getFirstItem(fieldReferences)) : null;
  }

  private static boolean isValueAssignedInPreviousStatement(@NotNull GoExpression expression,
                                                            @NotNull GoAssignmentStatement assignment) {
    GoReferenceExpression value = tryGetReference(GoPsiImplUtil.getExpressionValue(assignment, expression));
    if (value == null) return false;
    PsiElement previousElement = PsiTreeUtil.skipSiblingsBackward(assignment, PsiWhiteSpace.class);
    if (previousElement instanceof GoSimpleStatement) {
      GoShortVarDeclaration varDeclaration = ((GoSimpleStatement)previousElement).getShortVarDeclaration();
      return varDeclaration != null &&
             ContainerUtil.exists(varDeclaration.getVarDefinitionList(), definition -> isTextMatches(definition, value));
    }
    if (previousElement instanceof GoAssignmentStatement) {
      return ContainerUtil.exists(((GoAssignmentStatement)previousElement).getLeftHandExprList().getExpressionList(),
                                  leftExpression -> isTextMatches(leftExpression, value));
    }
    return false;
  }

  @NotNull
  private static List<GoExpression> getUninitializedSingleFieldReferences(@NotNull GoAssignmentStatement assignment,
                                                                          @NotNull GoReferenceExpression selectedFieldReference) {
    GoCompositeLit structLiteral = getStructLiteralByReference(selectedFieldReference, assignment);
    String qualifierName = getQualifierName(selectedFieldReference);
    if (structLiteral == null || qualifierName == null) return ContainerUtil.emptyList();
    List<GoExpression> uninitializedFieldsReferences =
      ContainerUtil.filter(assignment.getLeftHandExprList().getExpressionList(), expression ->
        isUninitializedFieldReferenceByQualifier(expression, structLiteral, qualifierName) &&
        !isValueAssignedInPreviousStatement(expression, assignment)
      );
    MultiMap<String, GoExpression> fieldReferencesByName =
      ContainerUtil.groupBy(uninitializedFieldsReferences, expression -> getFieldName((GoReferenceExpression)expression));
    return ContainerUtil.filter(uninitializedFieldsReferences,
                                reference -> fieldReferencesByName.get(getFieldName((GoReferenceExpression)reference)).size() == 1);
  }

  private static void moveFields(@NotNull List<GoExpression> fields,
                                 @NotNull GoCompositeLit structLiteral,
                                 @NotNull Project project) {
    GoAssignmentStatement assignment = getValidAssignmentParent(ContainerUtil.getFirstItem(fields));
    if (assignment == null) return;

    GoLiteralValue literalValue = structLiteral.getLiteralValue();
    if (literalValue == null) return;

    for (GoExpression expression : fields) {
      GoReferenceExpression fieldReference = tryGetReference(expression);
      if (!isFieldReference(fieldReference)) continue;

      GoExpression fieldValue = GoPsiImplUtil.getExpressionValue(assignment, fieldReference);
      if (fieldValue == null) continue;

      GoPsiImplUtil.deleteExpressionFromAssignment(assignment, fieldReference);
      addFieldDefinition(literalValue, getFieldName(fieldReference), fieldValue.getText(), project);
    }
  }

  @Nullable
  private static GoCompositeLit getStructLiteralByReference(@NotNull GoReferenceExpression fieldReference,
                                                            @NotNull GoAssignmentStatement assignment) {
    PsiElement previousElement = PsiTreeUtil.skipSiblingsBackward(assignment, PsiWhiteSpace.class);
    if (previousElement instanceof GoSimpleStatement) {
      return getStructLiteral(fieldReference, (GoSimpleStatement)previousElement);
    }
    if (previousElement instanceof GoAssignmentStatement) {
      return getStructLiteral(fieldReference, (GoAssignmentStatement)previousElement);
    }
    return null;
  }

  @Nullable
  private static GoCompositeLit getStructLiteral(@NotNull GoReferenceExpression fieldReference,
                                                 @NotNull GoSimpleStatement structDeclaration) {
    GoShortVarDeclaration varDeclaration = structDeclaration.getShortVarDeclaration();
    if (varDeclaration == null) return null;
    GoVarDefinition structVarDefinition =
      ContainerUtil.find(varDeclaration.getVarDefinitionList(),
                         definition -> GoPsiImplUtil.getName(definition).equals(getQualifierName(fieldReference)));
    return structVarDefinition != null && isStructWithField(fieldReference, structVarDefinition) ?
           ObjectUtils.tryCast(structVarDefinition.getValue(), GoCompositeLit.class) : null;
  }

  @Nullable
  private static GoCompositeLit getStructLiteral(@NotNull GoReferenceExpression fieldReference,
                                                 @NotNull GoAssignmentStatement structAssignment) {
    GoExpression struct = ContainerUtil.find(structAssignment.getLeftHandExprList().getExpressionList(),
                                             expression -> isStructByReference(tryGetReference(expression), fieldReference));
    return struct != null ? ObjectUtils.tryCast(GoPsiImplUtil.getExpressionValue(structAssignment, struct), GoCompositeLit.class) : null;
  }

  private static boolean isStructByReference(@Nullable GoReferenceExpression structReference,
                                             @NotNull GoReferenceExpression fieldReference) {
    PsiElement structDefinition = structReference != null ? structReference.resolve() : null;
    GoVarDefinition structVarDefinition = ObjectUtils.tryCast(structDefinition, GoVarDefinition.class);
    return structVarDefinition != null &&
           GoPsiImplUtil.getName(structVarDefinition).equals(getQualifierName(fieldReference)) &&
           isStructWithField(fieldReference, structVarDefinition);
  }

  @Contract("null, _, _ -> false")
  private static boolean isUninitializedFieldReferenceByQualifier(@Nullable GoExpression expression,
                                                                  @NotNull GoCompositeLit structLiteral,
                                                                  @NotNull String qualifierName) {
    GoReferenceExpression fieldReference = tryGetReference(expression);
    if (fieldReference == null) return false;
    GoLiteralValue literalValue = structLiteral.getLiteralValue();
    String fieldName = getFieldName(fieldReference);
    return literalValue != null &&
           isFieldDefinition(fieldReference.resolve()) && qualifierName.equals(getQualifierName(fieldReference)) &&
           !ContainerUtil.exists(literalValue.getElementList(), element -> hasFieldName(element, fieldName));
  }

  private static void addFieldDefinition(@NotNull GoLiteralValue literalValue,
                                         @NotNull String name,
                                         @NotNull String value,
                                         @NotNull Project project) {
    PsiElement newField = GoElementFactory.createNamedStructField(project, name, value);
    GoElement lastField = ContainerUtil.getLastItem(literalValue.getElementList());
    if (lastField == null) {
      literalValue.addAfter(newField, literalValue.getLbrace());
    }
    else {
      lastField.add(GoElementFactory.createComma(project));
      lastField.add(newField);
    }
  }

  @Nullable
  private static GoStructType getStructType(@NotNull GoVarDefinition structVarDefinition) {
    GoType varType = structVarDefinition.getGoType(null);
    GoType type = varType != null ? varType.getUnderlyingType() : null;
    return ObjectUtils.tryCast(type, GoStructType.class);
  }

  private static boolean hasFieldByName(@NotNull GoStructType struct, @NotNull String name) {
    return !GoPsiImplUtil.goTraverser().withRoot(struct).traverse()
      .filter(element -> isFieldDefinition(element) && name.equals(((GoNamedElement)element).getName()))
      .isEmpty();
  }

  private static boolean isStructWithField(@NotNull GoReferenceExpression fieldReference, @NotNull GoVarDefinition structVarDefinition) {
    GoStructType structType = getStructType(structVarDefinition);
    return structType != null && hasFieldByName(structType, getFieldName(fieldReference));
  }

  private static boolean hasFieldName(@NotNull GoElement field, @NotNull String name) {
    GoKey fieldName = field.getKey();
    return fieldName != null && fieldName.getFieldName() != null && name.equals(fieldName.getFieldName().getText());
  }

  @Nullable
  private static String getQualifierName(@NotNull GoReferenceExpression reference) {
    GoReferenceExpression qualifier = reference.getQualifier();
    return qualifier != null ? qualifier.getText() : null;
  }

  @Contract("null -> false")
  private static boolean isFieldReference(@Nullable GoExpression expression) {
    return expression instanceof GoReferenceExpression && isFieldDefinition(((GoReferenceExpression)expression).resolve());
  }

  @Contract("null -> false")
  private static boolean isFieldDefinition(@Nullable PsiElement element) {
    return element instanceof GoFieldDefinition || element instanceof GoAnonymousFieldDefinition;
  }

  @Contract("null -> null")
  private static GoReferenceExpression tryGetReference(@Nullable GoExpression expression) {
    return ObjectUtils.tryCast(expression, GoReferenceExpression.class);
  }

  private static boolean isTextMatches(@NotNull PsiElement first, @NotNull PsiElement second) {
    return first.getText().equals(second.getText());
  }

  @NotNull
  private static String getFieldName(@NotNull GoReferenceExpression fieldReference) {
    return fieldReference.getIdentifier().getText();
  }
}