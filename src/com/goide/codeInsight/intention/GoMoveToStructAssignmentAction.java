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

package com.goide.codeInsight.intention;

import com.goide.psi.*;
import com.goide.psi.impl.GoElementFactory;
import com.goide.psi.impl.GoPsiImplUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GoMoveToStructAssignmentAction extends PsiElementBaseIntentionAction {
  public static final String NAME = "Move field initialization to struct assignment";

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!element.isValid() || !element.isWritable()) return false;
    GoAssignmentStatement assignment = PsiTreeUtil.getParentOfType(element, GoAssignmentStatement.class, false);
    if (assignment == null || !assignment.isValid()) return false;
    GoReferenceExpression field = getFieldReference(element, assignment);
    if (field == null) return false;
    GoCompositeLit structDefinition = getStructDefinitionByField(field, assignment);
    if (structDefinition == null) return false;
    return isFieldUninitialized(field, structDefinition);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!element.isValid() || !element.isWritable()) return;
    GoAssignmentStatement assignment = PsiTreeUtil.getParentOfType(element, GoAssignmentStatement.class, false);
    if (assignment == null || !assignment.isValid()) return;
    GoReferenceExpression field = getFieldReference(element, assignment);
    if (field == null) return;
    GoCompositeLit structDefinition = getStructDefinitionByField(field, assignment);
    if (structDefinition == null || !isFieldUninitialized(field, structDefinition)) return;
    GoExpression fieldValue = GoPsiImplUtil.getExpressionValue(assignment, field);
    GoLiteralValue structFieldDefinitions = structDefinition.getLiteralValue();
    if (fieldValue == null || structFieldDefinitions == null) return;
    WriteCommandAction.runWriteCommandAction(project, () -> {
      GoPsiImplUtil.deleteExpressionFromAssignment(assignment, field);
      addFieldDefinition(structFieldDefinitions, getFieldName(field), fieldValue.getText(), project);
    });
  }

  @Nullable
  private static GoReferenceExpression getFieldReference(@NotNull PsiElement selectedElement,
                                                         @NotNull GoAssignmentStatement assignment) {
    List<GoExpression> expressions = assignment.getLeftHandExprList().getExpressionList();
    List<GoExpression> structFields = ContainerUtil.filter(expressions, GoMoveToStructAssignmentAction::isFieldDefinition);
    if (structFields.isEmpty()) return null;
    if (structFields.size() == 1) {
      GoExpression onlyFieldReference = ContainerUtil.getFirstItem(structFields);
      return onlyFieldReference instanceof GoReferenceExpression ? (GoReferenceExpression)onlyFieldReference : null;
    }
    GoReferenceExpression selectedFieldReference = PsiTreeUtil.getTopmostParentOfType(selectedElement, GoReferenceExpression.class);
    if (selectedFieldReference == null || !isFieldDefinition(selectedFieldReference)) return null;
    return selectedFieldReference;
  }

  @Contract("null -> false")
  private static boolean isFieldDefinition(@Nullable GoExpression expression) {
    if (!(expression instanceof GoReferenceExpression)) return false;
    return ((GoReferenceExpression)expression).resolve() instanceof GoFieldDefinition;
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
      ContainerUtil.find(varDeclaration.getVarDefinitionList(), struct -> isStructWithField(field, struct));
    if (structVarDefinition == null) return null;
    GoExpression structDefinition = structVarDefinition.getValue();
    if (!(structDefinition instanceof GoCompositeLit)) return null;
    return (GoCompositeLit)structDefinition;
  }

  @Nullable
  private static GoCompositeLit getStructDefinition(@NotNull GoReferenceExpression field, @NotNull GoAssignmentStatement structAssignment) {
    List<GoExpression> expressions = structAssignment.getLeftHandExprList().getExpressionList();
    int structIndex = ContainerUtil.indexOf(expressions, new Condition<GoExpression>() {
      @Override
      public boolean value(GoExpression expression) {
        if (!(expression instanceof GoReferenceExpression)) return false;
        PsiElement structDefinition = ((GoReferenceExpression)expression).resolve();
        if (!(structDefinition instanceof GoVarDefinition)) return false;
        return isStructWithField(field, (GoVarDefinition)structDefinition);
      }
    });
    if (structIndex < 0 || !(structAssignment.getExpressionList().get(structIndex) instanceof GoCompositeLit)) return null;
    return (GoCompositeLit)structAssignment.getExpressionList().get(structIndex);
  }

  private static boolean isStructWithField(@NotNull GoReferenceExpression field, @NotNull GoVarDefinition structVar) {
    GoStructType structType = getStruct(structVar);
    if (structType == null) return false;
    return hasFieldByName(structType, getFieldName(field));
  }

  @Nullable
  private static GoStructType getStruct(@NotNull GoVarDefinition structVar) {
    GoType specType = structVar.getGoType(null);
    GoType type = specType == null ? null : specType.getUnderlyingType();
    return type instanceof GoStructType ? (GoStructType)type : null;
  }

  private static boolean hasFieldByName(@NotNull GoStructType struct, @NotNull String name) {
    return struct.getFieldDeclarationList().stream()
             .flatMap(def -> def.getFieldDefinitionList().stream())
             .filter(d -> name.equals(d.getIdentifier().getText()))
             .count() > 0;
  }

  private static boolean isFieldUninitialized(@NotNull GoReferenceExpression field, @NotNull GoCompositeLit structDefinition) {
    GoLiteralValue fieldDefinitions = structDefinition.getLiteralValue();
    return fieldDefinitions != null && !ContainerUtil.exists(fieldDefinitions.getElementList(), e -> hasFieldName(e, getFieldName(field)));
  }

  private static boolean hasFieldName(@NotNull GoElement fieldInitializer, @NotNull String fieldName) {
    GoKey field = fieldInitializer.getKey();
    if (field == null) return false;
    return field.getFieldName() != null && fieldName.equals(field.getFieldName().getText());
  }

  @NotNull
  private static String getFieldName(@NotNull GoReferenceExpression field) {
    return field.getIdentifier().getText();
  }

  private static void addFieldDefinition(@NotNull GoLiteralValue structFieldDefinitions,
                                         @NotNull String name,
                                         @NotNull String value,
                                         @NotNull Project project
  ) {
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

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return NAME;
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }
}
