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

import com.goide.intentions.GoMoveToStructAssignmentIntention;
import org.jetbrains.annotations.NotNull;

public class GoMoveToStructAssignmentIntentionTest extends GoQuickFixTestBase {
  @NotNull
  @Override
  protected String getBasePath() {
    return "intentions/move-to-struct-assignment";
  }

  private void doTest()                                     { doTest(GoMoveToStructAssignmentIntention.NAME); }
  private void doTestNoFix()                                { doTestNoFix(GoMoveToStructAssignmentIntention.NAME); }

  public void testSimple()                                  { doTest(); }
  public void testCaretAtValue()                            { doTest(); }
  public void testAnonymousField()                          { doTest(); }
  public void testStructAssignment()                        { doTest(); }
  public void testStructAssignmentMultipleAssignee()        { doTest(); }
  public void testMultipleFieldsSameStructureCaretAtValue() { doTest(); }
  public void testOneLineFieldDeclaration()                 { doTest(); }
  public void testMultipleFields()                          { doTest(); }
  public void testTwoSameStructures()                       { doTest(); }
  public void testTwoSameStructuresAssignment()             { doTest(); }
  public void testMultiReturnFunction()                     { doTestNoFix(); }
  public void testWrongStruct()                             { doTestNoFix(); }
  public void testExistingDeclaration()                     { doTestNoFix(); }
  public void testNotExistingField()                        { doTestNoFix(); }
  public void testInvalidAssignment()                       { doTestNoFix(); }
  public void testExistingField()                           { doTest(); }
  public void testMultipleAssignmentsLeftmost()             { doTest(); }
  public void testMultipleAssignmentsRightmost()            { doTest(); }
  public void testMultipleAssignmentsMiddle()               { doTest(); }
  public void testMultipleFieldsPartlyAssigned()            { doTest(); }
}

