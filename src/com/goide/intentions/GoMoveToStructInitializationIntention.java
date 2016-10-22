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

import com.goide.psi.*;
import com.goide.psi.impl.GoElementFactory;
import com.goide.psi.impl.GoPsiImplUtil;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    Pair<List<GoReferenceExpression>, GoCompositeLit> pair = getFieldReferencesAndStructLiteral(element);
    return !pair.first.isEmpty() && pair.second != null;
  }

  @NotNull
  private static Pair<List<GoReferenceExpression>, GoCompositeLit> getFieldReferencesAndStructLiteral(@NotNull PsiElement element) {
    if (!element.isValid() || !element.isWritable()) return Pair.create(ContainerUtil.emptyList(), null);
    GoAssignmentStatement assignment = getValidAssignmentParent(element);
    GoReferenceExpression selectedFieldReference = assignment != null ? getFieldReferenceExpression(element, assignment) : null;
    if (selectedFieldReference == null) return Pair.create(ContainerUtil.emptyList(), null);
    return Pair.create(getUninitializedSingleFieldReferences(assignment, selectedFieldReference),
                       getStructLiteralByReference(selectedFieldReference, assignment));
  }

  @Nullable
  private static GoAssignmentStatement getValidAssignmentParent(@Nullable PsiElement element) {
    GoAssignmentStatement assignment = PsiTreeUtil.getNonStrictParentOfType(element, GoAssignmentStatement.class);
    return assignment != null && assignment.isValid() && isAssignmentEqualsSized(assignment) ? assignment : null;
  }

  private static boolean isAssignmentEqualsSized(@NotNull GoAssignmentStatement assignment) {
    return getLeftHandElements(assignment).size() == assignment.getExpressionList().size();
  }

  @Nullable
  private static GoReferenceExpression getFieldReferenceExpression(@NotNull PsiElement selectedElement,
                                                                   @NotNull GoAssignmentStatement assignment) {
    GoReferenceExpression selectedFieldReference = PsiTreeUtil.getTopmostParentOfType(selectedElement, GoReferenceExpression.class);
    if (isFieldReferenceExpression(selectedFieldReference)) {
      return ObjectUtils.nullizeByCondition(selectedFieldReference, reference -> isAssignedInPreviousStatement(reference, assignment));
    }
    List<GoReferenceExpression> fieldReferenceExpressions = getFieldReferenceExpressions(assignment);
    if (ContainerUtil.exists(fieldReferenceExpressions, expression -> isAssignedInPreviousStatement(expression, assignment))) return null;
    long distinctStructsCount =
      ContainerUtil.map2Set(fieldReferenceExpressions, GoMoveToStructInitializationIntention::getQualifierName).size();
    return distinctStructsCount == 1 ? ContainerUtil.getFirstItem(fieldReferenceExpressions) : null;
  }

  @NotNull
  private static List<GoReferenceExpression> getFieldReferenceExpressions(@NotNull GoAssignmentStatement assignment) {
    return getLeftHandElements(assignment).stream()
      .filter(GoMoveToStructInitializationIntention::isFieldReferenceExpression)
      .map(GoReferenceExpression.class::cast)
      .collect(Collectors.toList());
  }

  @Contract("null -> false")
  private static boolean isFieldReferenceExpression(@Nullable PsiElement element) {
    return element instanceof GoReferenceExpression && isFieldDefinition(((GoReferenceExpression)element).resolve());
  }

  @Contract("null -> false")
  private static boolean isFieldDefinition(@Nullable PsiElement element) {
    return element instanceof GoFieldDefinition || element instanceof GoAnonymousFieldDefinition;
  }

  private static boolean isAssignedInPreviousStatement(@NotNull GoExpression referenceExpression,
                                                       @NotNull GoAssignmentStatement assignment) {
    GoReferenceExpression rightExpression = tryGetReferenceExpression(GoPsiImplUtil.getRightExpression(referenceExpression, assignment));
    GoStatement previousElement = rightExpression != null ? PsiTreeUtil.getPrevSiblingOfType(assignment, GoStatement.class) : null;
    return previousElement != null &&
           ContainerUtil.exists(getLeftHandElements(previousElement), element -> isTextMatches(element, rightExpression));
  }

  private static boolean isTextMatches(@NotNull PsiElement first, @NotNull PsiElement second) {
    return first.getText().equals(second.getText());
  }

  @Contract("null -> null")
  private static GoReferenceExpression tryGetReferenceExpression(@Nullable GoExpression expression) {
    return ObjectUtils.tryCast(expression, GoReferenceExpression.class);
  }

  @NotNull
  private static List<GoReferenceExpression> getUninitializedSingleFieldReferences(@NotNull GoAssignmentStatement assignment,
                                                                                   @NotNull GoReferenceExpression fieldReferenceExpression) {
    GoCompositeLit structLiteral = getStructLiteralByReference(fieldReferenceExpression, assignment);
    String qualifierName = getQualifierName(fieldReferenceExpression);
    if (structLiteral == null || qualifierName == null) return ContainerUtil.emptyList();

    List<GoReferenceExpression> uninitializedFieldReferencesByQualifier =
      ContainerUtil.filter(getUninitializedFieldReferenceExpressions(assignment, structLiteral),
                           expression -> qualifierName.equals(getQualifierName(expression)));
    Map<String, Integer> identifierNameCounts = getIdentifierNameCounts(uninitializedFieldReferencesByQualifier);
    return ContainerUtil
      .filter(uninitializedFieldReferencesByQualifier, reference -> identifierNameCounts.get(getIdentifierName(reference)) == 1);
  }

  @Nullable
  private static String getQualifierName(@NotNull GoReferenceExpression reference) {
    GoReferenceExpression qualifier = reference.getQualifier();
    return qualifier != null ? qualifier.getText() : null;
  }

  @Nullable
  private static GoCompositeLit getStructLiteralByReference(@NotNull GoReferenceExpression fieldReferenceExpression,
                                                            @NotNull GoAssignmentStatement assignment) {
    GoStatement previousStatement = PsiTreeUtil.getPrevSiblingOfType(assignment, GoStatement.class);
    if (previousStatement instanceof GoSimpleStatement) {
      return getStructLiteral(fieldReferenceExpression, (GoSimpleStatement)previousStatement);
    }
    if (previousStatement instanceof GoAssignmentStatement) {
      return getStructLiteral(fieldReferenceExpression, (GoAssignmentStatement)previousStatement);
    }
    return null;
  }


  @Nullable
  private static GoCompositeLit getStructLiteral(@NotNull GoReferenceExpression fieldReferenceExpression,
                                                 @NotNull GoSimpleStatement structDeclaration) {
    GoShortVarDeclaration varDeclaration = structDeclaration.getShortVarDeclaration();
    if (varDeclaration == null) return null;
    GoVarDefinition structVarDefinition = ContainerUtil
      .find(varDeclaration.getVarDefinitionList(), definition -> equalsByQualifiers(fieldReferenceExpression, definition));
    return structVarDefinition != null && hasStructTypeWithField(structVarDefinition, fieldReferenceExpression) ?
           ObjectUtils.tryCast(structVarDefinition.getValue(), GoCompositeLit.class) : null;
  }

  private static boolean equalsByQualifiers(@NotNull GoReferenceExpression referenceExpression,
                                            @NotNull GoVarDefinition definition) {
    return Comparing.equal(definition.getIdentifier().getText(), getQualifierName(referenceExpression));
  }

  @Nullable
  private static GoCompositeLit getStructLiteral(@NotNull GoReferenceExpression fieldReferenceExpression,
                                                 @NotNull GoAssignmentStatement structAssignment) {
    GoExpression struct = ContainerUtil.find(structAssignment.getLeftHandExprList().getExpressionList(),
                                             expression -> isStructByReferenceExpression(tryGetReferenceExpression(expression),
                                                                                         fieldReferenceExpression));
    return struct != null ? ObjectUtils.tryCast(GoPsiImplUtil.getRightExpression(struct, structAssignment), GoCompositeLit.class) : null;
  }

  private static boolean isStructByReferenceExpression(@Nullable GoReferenceExpression structReferenceExpression,
                                                       @NotNull GoReferenceExpression fieldReference) {
    PsiElement structDefinition = structReferenceExpression != null ? structReferenceExpression.resolve() : null;
    GoVarDefinition structVarDefinition = ObjectUtils.tryCast(structDefinition, GoVarDefinition.class);
    return structVarDefinition != null &&
           equalsByQualifiers(fieldReference, structVarDefinition) &&
           hasStructTypeWithField(structVarDefinition, fieldReference);
  }

  private static boolean hasStructTypeWithField(@NotNull GoVarDefinition structVarDefinition,
                                                @NotNull GoReferenceExpression fieldReferenceExpression) {
    GoStructType structType = getStructType(structVarDefinition);
    return structType != null && hasFieldByName(structType, getIdentifierName(fieldReferenceExpression));
  }

  private static boolean hasFieldByName(@NotNull GoStructType struct, @NotNull String name) {
    return !GoPsiImplUtil.goTraverser().withRoot(struct).traverse()
      .filter(element -> isFieldDefinition(element) && name.equals(((GoNamedElement)element).getName()))
      .isEmpty();
  }

  @Nullable
  private static GoStructType getStructType(@NotNull GoVarDefinition structVarDefinition) {
    GoType varType = structVarDefinition.getGoType(null);
    GoType type = varType != null ? varType.getUnderlyingType() : null;
    return ObjectUtils.tryCast(type, GoStructType.class);
  }

  private static boolean hasFieldName(@NotNull GoElement field, @NotNull String name) {
    GoKey fieldName = field.getKey();
    return fieldName != null && fieldName.getFieldName() != null && name.equals(fieldName.getFieldName().getText());
  }

  @NotNull
  private static List<GoReferenceExpression> getUninitializedFieldReferenceExpressions(@NotNull GoAssignmentStatement assignment,
                                                                                       @NotNull GoCompositeLit structLiteral) {
    return ContainerUtil.filter(getFieldReferenceExpressions(assignment),
                                expression -> isUninitializedFieldReferenceExpression(expression, structLiteral) &&
                                              !isAssignedInPreviousStatement(expression, assignment));
  }

  @Contract("null, _-> false")
  private static boolean isUninitializedFieldReferenceExpression(@Nullable GoReferenceExpression fieldReferenceExpression,
                                                                 @NotNull GoCompositeLit structLiteral) {
    if (fieldReferenceExpression == null) return false;
    GoLiteralValue literalValue = structLiteral.getLiteralValue();
    String fieldName = getIdentifierName(fieldReferenceExpression);
    return literalValue != null &&
           isFieldDefinition(fieldReferenceExpression.resolve()) &&
           !ContainerUtil.exists(literalValue.getElementList(), element -> hasFieldName(element, fieldName));
  }

  @NotNull
  private static Map<String, Integer> getIdentifierNameCounts(@NotNull List<GoReferenceExpression> fieldReferenceExpressions) {
    return ContainerUtil.groupBy(fieldReferenceExpressions, GoMoveToStructInitializationIntention::getIdentifierName).entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));
  }

  @NotNull
  private static String getIdentifierName(@NotNull GoReferenceExpression fieldReferenceExpression) {
    return fieldReferenceExpression.getIdentifier().getText();
  }

  @NotNull
  private static Collection<? extends PsiElement> getLeftHandElements(@NotNull GoStatement statement) {
    if (statement instanceof GoSimpleStatement) {
      GoShortVarDeclaration varDeclaration = ((GoSimpleStatement)statement).getShortVarDeclaration();
      return varDeclaration != null ? varDeclaration.getVarDefinitionList() : ContainerUtil.emptyList();
    }
    if (statement instanceof GoAssignmentStatement) {
      return ((GoAssignmentStatement)statement).getLeftHandExprList().getExpressionList();
    }
    return ContainerUtil.emptyList();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    Pair<List<GoReferenceExpression>, GoCompositeLit> pair = getFieldReferencesAndStructLiteral(element);
    if (pair.first == null || pair.first.isEmpty()) return;
    moveFieldReferenceExpressions(pair.first, pair.second, project);
  }

  private static void moveFieldReferenceExpressions(@NotNull List<GoReferenceExpression> fieldReferenceExpressions,
                                                    @NotNull GoCompositeLit structLiteral,
                                                    @NotNull Project project) {
    GoAssignmentStatement assignment = getValidAssignmentParent(ContainerUtil.getFirstItem(fieldReferenceExpressions));
    if (assignment == null) return;

    GoLiteralValue literalValue = structLiteral.getLiteralValue();
    if (literalValue == null) return;

    for (GoExpression expression : fieldReferenceExpressions) {
      GoReferenceExpression fieldReferenceExpression = tryGetReferenceExpression(expression);
      if (!isFieldReferenceExpression(fieldReferenceExpression)) continue;

      GoExpression fieldValue = GoPsiImplUtil.getRightExpression(fieldReferenceExpression, assignment);
      if (fieldValue == null) continue;

      GoPsiImplUtil.deleteExpressionFromAssignment(assignment, fieldReferenceExpression);
      addFieldDefinition(literalValue, getIdentifierName(fieldReferenceExpression), fieldValue.getText(), project);
    }
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
}