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
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.*;

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
    return getData(element) != null;
  }

  @Nullable
  private static Data getData(@NotNull PsiElement element) {
    if (!element.isValid() || !element.isWritable()) return null;
    GoAssignmentStatement assignment = getValidAssignmentParent(element);
    GoStatement previousStatement = assignment != null ? PsiTreeUtil.getPrevSiblingOfType(assignment, GoStatement.class) : null;
    GoReferenceExpression selectedFieldReferenceExpression =
      previousStatement != null ? getFieldReferenceExpression(element, assignment, previousStatement) : null;
    if (selectedFieldReferenceExpression == null) return null;

    GoVarDefinition structDefinition = getDefinition(selectedFieldReferenceExpression);
    boolean needReplaceDeclarationWithShortVar = isUnassigned(getSingleVarSpecByDefinition(previousStatement, structDefinition));

    GoCompositeLit compositeLit = structDefinition != null ? getStructLiteralByDefinition(structDefinition, previousStatement) : null;
    GoNamedElement field = ObjectUtils.tryCast(selectedFieldReferenceExpression.resolve(), GoNamedElement.class);
    if (compositeLit == null && !needReplaceDeclarationWithShortVar || !hasStructTypeWithField(structDefinition, field)) return null;

    List<GoReferenceExpression> references =
      getUninitializedSingleFieldReferences(assignment, previousStatement, structDefinition, compositeLit);
    return !references.isEmpty() ? new Data(assignment, compositeLit, references, previousStatement, structDefinition) : null;
  }

  @Nullable
  private static GoAssignmentStatement getValidAssignmentParent(@Nullable PsiElement element) {
    GoAssignmentStatement assignment = PsiTreeUtil.getNonStrictParentOfType(element, GoAssignmentStatement.class);
    return assignment != null && assignment.isValid() && getLeftHandElements(assignment).size() == assignment.getExpressionList().size()
           ? assignment : null;
  }

  @Nullable
  private static GoReferenceExpression getFieldReferenceExpression(@NotNull PsiElement selectedElement,
                                                                   @NotNull GoAssignmentStatement assignment,
                                                                   @NotNull GoStatement previousStatement) {
    GoReferenceExpression selectedReferenceExpression = PsiTreeUtil.getTopmostParentOfType(selectedElement, GoReferenceExpression.class);
    if (isFieldReferenceExpression(selectedReferenceExpression)) {
      return !isAssignedInStatement(getRightExpression(selectedReferenceExpression, assignment), previousStatement)
             ? selectedReferenceExpression : null;
    }

    List<GoReferenceExpression> fieldReferenceExpressions = getFieldReferenceExpressions(assignment);
    if (exists(fieldReferenceExpressions,
               expression -> isAssignedInStatement(getRightExpression(expression, assignment), previousStatement))) {
      return null;
    }

    Set<GoVarDefinition> resolvedDefinition = map2Set(fieldReferenceExpressions, GoMoveToStructInitializationIntention::getDefinition);
    return resolvedDefinition.size() == 1 ? getFirstItem(fieldReferenceExpressions) : null;
  }

  @Nullable
  @Contract("null -> null")
  private static GoVarDefinition getDefinition(@Nullable GoReferenceExpression referenceExpressions) {
    GoReferenceExpression qualifier = referenceExpressions != null ? referenceExpressions.getQualifier() : null;
    return qualifier != null ? ObjectUtils.tryCast(qualifier.resolve(), GoVarDefinition.class) : null;
  }

  @NotNull
  private static List<GoReferenceExpression> getFieldReferenceExpressions(@NotNull GoAssignmentStatement assignment) {
    return filter(map(getLeftHandElements(assignment), GoMoveToStructInitializationIntention::unwrapParensAndCast),
                  GoMoveToStructInitializationIntention::isFieldReferenceExpression);
  }

  @Nullable
  private static GoReferenceExpression unwrapParensAndCast(@Nullable PsiElement element) {
    while (element instanceof GoParenthesesExpr) {
      element = ((GoParenthesesExpr)element).getExpression();
    }
    return ObjectUtils.tryCast(element, GoReferenceExpression.class);
  }

  @Nullable
  @Contract("_, null -> null; null, _ -> null")
  private static GoVarSpec getSingleVarSpecByDefinition(@Nullable GoStatement statement,
                                                        @Nullable GoVarDefinition definition) {
    GoVarDeclaration declaration = statement != null ? statement.getVarDeclaration() : null;
    List<GoVarSpec> varSpecs = declaration != null ? declaration.getVarSpecList() : emptyList();
    GoVarSpec singleVarSpec = varSpecs.size() == 1 ? getFirstItem(varSpecs) : null;
    List<GoVarDefinition> varDefinitions = singleVarSpec != null ? singleVarSpec.getVarDefinitionList() : emptyList();
    return varDefinitions.size() == 1 && definition == getFirstItem(varDefinitions) ? singleVarSpec : null;
  }

  @Contract("null -> false")
  private static boolean isUnassigned(@Nullable GoVarSpec varSpec) {
    return varSpec != null && varSpec.getExpressionList().isEmpty();
  }

  @Contract("null -> false")
  private static boolean isFieldReferenceExpression(@Nullable PsiElement element) {
    return element instanceof GoReferenceExpression && isFieldDefinition(((GoReferenceExpression)element).resolve());
  }

  @Contract("null -> false")
  private static boolean isFieldDefinition(@Nullable PsiElement element) {
    return element instanceof GoFieldDefinition || element instanceof GoAnonymousFieldDefinition;
  }

  private static boolean isAssignedInStatement(@Nullable GoReferenceExpression referenceExpression,
                                               @NotNull GoStatement statement) {
    PsiElement resolve = referenceExpression != null ? referenceExpression.resolve() : null;
    return exists(getLeftHandElements(statement), element -> isResolvedTo(element, resolve));
  }

  @Nullable
  private static GoReferenceExpression getRightExpression(@NotNull GoExpression expression,
                                                          @NotNull GoAssignmentStatement assignment) {
    return unwrapParensAndCast(GoPsiImplUtil.getRightExpression(assignment, getTopmostExpression(expression)));
  }

  @NotNull
  private static GoExpression getTopmostExpression(@NotNull GoExpression expression) {
    return ObjectUtils.notNull(PsiTreeUtil.getTopmostParentOfType(expression, GoExpression.class), expression);
  }

  private static boolean isResolvedTo(@Nullable PsiElement element, @Nullable PsiElement resolve) {
    if (element instanceof GoVarDefinition) return resolve == element;

    GoReferenceExpression refExpression = unwrapParensAndCast(element);
    return refExpression != null && refExpression.resolve() == resolve;
  }

  @NotNull
  private static List<GoReferenceExpression> getUninitializedSingleFieldReferences(@NotNull GoAssignmentStatement assignment,
                                                                                   @NotNull GoStatement previousStatement,
                                                                                   @Nullable GoVarDefinition definition,
                                                                                   @Nullable GoCompositeLit compositeLit) {
    List<GoReferenceExpression> uninitializedFieldReferencesByQualifier =
      filter(getUninitializedFieldReferenceExpressions(assignment, compositeLit, previousStatement),
             element -> isResolvedTo(element.getQualifier(), definition));
    MultiMap<PsiElement, GoReferenceExpression> resolved = groupBy(uninitializedFieldReferencesByQualifier, GoReferenceExpression::resolve);
    return map(filter(resolved.entrySet(), set -> set.getValue().size() == 1), set -> getFirstItem(set.getValue()));
  }

  @Nullable
  private static GoCompositeLit getStructLiteralByDefinition(@NotNull GoVarDefinition definition, @NotNull GoStatement statement) {
    if (statement instanceof GoSimpleStatement) {
      return getStructLiteral(definition, (GoSimpleStatement)statement);
    }
    if (statement instanceof GoAssignmentStatement) {
      return getStructLiteral(definition, (GoAssignmentStatement)statement);
    }
    return getStructLiteral(definition, statement);
  }

  @Nullable
  @Contract("null, _ -> null")
  private static GoCompositeLit getStructLiteral(@Nullable GoVarDefinition definition, @NotNull GoSimpleStatement declaration) {
    GoShortVarDeclaration varDeclaration = definition != null ? declaration.getShortVarDeclaration() : null;
    return varDeclaration != null && containsIdentity(varDeclaration.getVarDefinitionList(), definition)
           ? ObjectUtils.tryCast(definition.getValue(), GoCompositeLit.class) : null;
  }

  @Nullable
  private static GoCompositeLit getStructLiteral(@NotNull GoVarDefinition definition, @NotNull GoAssignmentStatement structAssignment) {
    GoExpression structReferenceExpression = find(structAssignment.getLeftHandExprList().getExpressionList(),
                                                  expression -> isResolvedTo(expression, definition));
    GoExpression compositeLit =
      structReferenceExpression != null ? GoPsiImplUtil.getRightExpression(structAssignment, structReferenceExpression) : null;
    return ObjectUtils.tryCast(compositeLit, GoCompositeLit.class);
  }

  @Nullable
  @Contract("null, _ -> null")
  private static GoCompositeLit getStructLiteral(@Nullable GoVarDefinition definition, @NotNull GoStatement statement) {
    GoVarSpec varSpec = definition != null ? getSingleVarSpecByDefinition(statement, definition) : null;
    return varSpec != null ? ObjectUtils.tryCast(getFirstItem(varSpec.getRightExpressionsList()), GoCompositeLit.class) : null;
  }

  @Contract("_, null -> false; null, !null -> false")
  private static boolean hasStructTypeWithField(@Nullable GoVarDefinition definition, @Nullable GoNamedElement field) {
    GoType type = field != null && definition != null ? definition.getGoType(null) : null;
    GoStructType structType = type != null ? ObjectUtils.tryCast(type.getUnderlyingType(), GoStructType.class) : null;
    return structType != null && PsiTreeUtil.isAncestor(structType, field, true);
  }

  private static boolean isFieldInitialization(@NotNull GoElement element, @NotNull PsiElement field) {
    GoKey key = element.getKey();
    GoFieldName fieldName = key != null ? key.getFieldName() : null;
    return fieldName != null && fieldName.resolve() == field;
  }

  @NotNull
  private static List<GoReferenceExpression> getUninitializedFieldReferenceExpressions(@NotNull GoAssignmentStatement assignment,
                                                                                       @Nullable GoCompositeLit structLiteral,
                                                                                       @NotNull GoStatement previousStatement) {
    return filter(getFieldReferenceExpressions(assignment),
                  expression -> isUninitializedFieldReferenceExpression(expression, structLiteral) &&
                                !isAssignedInStatement(getRightExpression(expression, assignment), previousStatement));
  }

  @Contract("null, _-> false; !null, null -> true")
  private static boolean isUninitializedFieldReferenceExpression(@Nullable GoReferenceExpression fieldReferenceExpression,
                                                                 @Nullable GoCompositeLit structLiteral) {
    if (fieldReferenceExpression == null) return false;
    if (structLiteral == null) return true;

    GoLiteralValue literalValue = structLiteral.getLiteralValue();
    PsiElement resolve = fieldReferenceExpression.resolve();
    return literalValue != null && isFieldDefinition(resolve) &&
           !exists(literalValue.getElementList(), element -> isFieldInitialization(element, resolve));
  }

  @NotNull
  private static List<? extends PsiElement> getLeftHandElements(@NotNull GoStatement statement) {
    if (statement instanceof GoSimpleStatement) {
      GoShortVarDeclaration varDeclaration = ((GoSimpleStatement)statement).getShortVarDeclaration();
      return varDeclaration != null ? varDeclaration.getVarDefinitionList() : emptyList();
    }
    if (statement instanceof GoAssignmentStatement) {
      return ((GoAssignmentStatement)statement).getLeftHandExprList().getExpressionList();
    }
    return emptyList();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    Data data = getData(element);
    if (data == null) return;

    boolean needReplaceDeclarationWithShortVar = data.getCompositeLit() == null;
    GoCompositeLit compositeLit =
      needReplaceDeclarationWithShortVar ? createStructLiteral(data.getStructDefinition(), project) : data.getCompositeLit();
    if (compositeLit == null || needReplaceDeclarationWithShortVar && data.getStructDeclaration() == null) return;

    moveFieldReferenceExpressions(data.getReferenceExpressions(), compositeLit.getLiteralValue(), data.getAssignment());
    if (!needReplaceDeclarationWithShortVar) return;
    GoStatement shortVarStatement =
      GoElementFactory.createShortVarDeclarationStatement(project, data.getStructDefinition().getText(), compositeLit.getText());
    data.getStructDeclaration().replace(shortVarStatement);
  }


  @Nullable
  private static GoCompositeLit createStructLiteral(@NotNull GoVarDefinition definition, @NotNull Project project) {
    GoType type = definition.getGoType(null);
    return type != null ? GoElementFactory.createCompositeLit(project, type) : null;
  }

  private static void moveFieldReferenceExpressions(@NotNull List<GoReferenceExpression> referenceExpressions,
                                                    @Nullable GoLiteralValue literalValue,
                                                    @NotNull GoAssignmentStatement parentAssignment) {
    if (literalValue == null) return;
    for (GoReferenceExpression expression : referenceExpressions) {
      GoExpression anchor = getTopmostExpression(expression);
      GoExpression fieldValue = GoPsiImplUtil.getRightExpression(parentAssignment, anchor);
      if (fieldValue == null) continue;

      GoPsiImplUtil.deleteExpressionFromAssignment(parentAssignment, anchor);
      addFieldDefinition(literalValue, expression.getIdentifier().getText(), fieldValue.getText());
    }
  }

  private static void addFieldDefinition(@NotNull GoLiteralValue literalValue, @NotNull String name, @NotNull String value) {
    Project project = literalValue.getProject();
    PsiElement newField = GoElementFactory.createLiteralValueElement(project, name, value);
    GoElement lastElement = getLastItem(literalValue.getElementList());
    if (lastElement == null) {
      literalValue.addAfter(newField, literalValue.getLbrace());
    }
    else {
      lastElement.add(GoElementFactory.createComma(project));
      lastElement.add(newField);
    }
  }

  private static class Data {
    private final GoCompositeLit myCompositeLit;
    private final GoAssignmentStatement myAssignment;
    private final List<GoReferenceExpression> myReferenceExpressions;
    private final GoStatement myStructDeclaration;
    private final GoVarDefinition myStructDefinition;

    public Data(@NotNull GoAssignmentStatement assignment,
                @Nullable GoCompositeLit compositeLit,
                @NotNull List<GoReferenceExpression> referenceExpressions,
                @Nullable GoStatement structDeclaration,
                @NotNull GoVarDefinition structDefinition) {
      myCompositeLit = compositeLit;
      myAssignment = assignment;
      myReferenceExpressions = referenceExpressions;
      myStructDeclaration = structDeclaration;
      myStructDefinition = structDefinition;
    }

    @Nullable
    public GoCompositeLit getCompositeLit() {
      return myCompositeLit;
    }

    @NotNull
    public GoAssignmentStatement getAssignment() {
      return myAssignment;
    }

    @NotNull
    public List<GoReferenceExpression> getReferenceExpressions() {
      return myReferenceExpressions;
    }

    @Nullable
    public GoStatement getStructDeclaration() {
      return myStructDeclaration;
    }

    @NotNull
    public GoVarDefinition getStructDefinition() {
      return myStructDefinition;
    }
  }
}


