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

import com.goide.SdkAware;
import com.goide.quickfix.GoQuickFixTestBase;
import org.jetbrains.annotations.NotNull;

@SdkAware
public class GoStructInitializationInspectionTest extends GoQuickFixTestBase {
  private GoStructInitializationInspection myInspectionTool = new GoStructInitializationInspection();
  private boolean myDefaultReportLocalStructs;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(myInspectionTool);
    myDefaultReportLocalStructs = myInspectionTool.reportLocalStructs;
  }

  @Override
  protected void tearDown() throws Exception {
    myInspectionTool.reportLocalStructs = myDefaultReportLocalStructs;
    super.tearDown();
  }

  public void testUninitializedStructWithLocal()    { doHighlightTest(true); }
  public void testMultipleFieldsOneline()           { doHighlightTest(true); }
  public void testWrongOrder()                      { doHighlightTest(true); }
  public void testExceedElementsCountWithNamed()    { doHighlightTest(true); }
  public void testInvalidKeysNumber()               { doHighlightTest(true); }
  public void testUninitializedStructImportedOnly() { doHighlightTest(false); }

  public void testLiteralValue()                    { doQuickfixTest(); }
  public void testLiteralValueWithoutCompositeLit() { doQuickfixTest(); }
  public void testExceedElementsCountOnlyUnnamed()  { doQuickfixTest(); }
  public void testInnerAnonStruct()                 { doQuickfixTest(); }
  public void testAnonField()                       { doQuickfixTest(); }
  public void testQuickFix()                        { doQuickfixTest(); }
  public void testInnerStruct()                     { doQuickfixTest(); }
  public void testOnelineQuickfix()                 { doQuickfixTest(); }
  public void testVarDeclaration()                  { doQuickfixTest(); }
  public void testNestedLiteral()                   { doQuickfixTest(); }
  public void testInnerLiteral()                    { doQuickfixTest(); }
  public void testInnerAnonLiteral()                { doQuickfixTest(); }
  public void testInnerLiteralMap()                 { doQuickfixTest(); }
  public void testInnerLiteralFieldWithKey()        { doQuickfixTest(); }
  public void testMultipleInnerLiterals()           { doQuickfixTest(); }
  public void testInnerLiteralWithoutType()         { doQuickfixTest(); }



  private long doHighlightTest(boolean allowLocalStructs) {
    myInspectionTool.reportLocalStructs = allowLocalStructs;
    return myFixture.testHighlighting(true, false, true, getTestName(true) + ".go");
  }

  private void doQuickfixTest() {
    myInspectionTool.reportLocalStructs = true;
    doTest(GoStructInitializationInspection.REPLACE_WITH_NAMED_STRUCT_FIELD_FIX_NAME, true);
  }

  @NotNull
  @Override
  protected String getBasePath() {
    return "inspections/struct-initialization";
  }
}
