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

import com.goide.SdkAware;
import com.goide.inspections.GoStringIndexIsByteInspection;
import org.jetbrains.annotations.NotNull;

@SdkAware
public class GoStringIndexIsByteQuickFixTest extends GoQuickFixTestBase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(GoStringIndexIsByteInspection.class);
  }

  @NotNull
  @Override
  protected String getBasePath() {
    return "quickfixes/string-index-is-byte";
  }

  public void testEqualsCondition()         { doTest(GoStringIndexIsByteQuickFix.NAME); }
  public void testNotEqualsCondition()      { doTest(GoStringIndexIsByteQuickFix.NAME); }
  public void testGreaterCondition()        { doTest(GoStringIndexIsByteQuickFix.NAME); }
  public void testGreaterOrEqualsCondition(){ doTest(GoStringIndexIsByteQuickFix.NAME); }
  public void testLessCondition()           { doTest(GoStringIndexIsByteQuickFix.NAME); }
  public void testLessOrEqualsCondition()   { doTest(GoStringIndexIsByteQuickFix.NAME); }
  public void testReverse()                 { doTest(GoStringIndexIsByteQuickFix.NAME); }
  public void testLiterals()                { doTest(GoStringIndexIsByteQuickFix.NAME); }
  public void testLongLiteral()             { doTest(GoStringIndexIsByteQuickFix.NAME); }
  public void testSliceFromLeft()           { doTestNoFix(GoStringIndexIsByteQuickFix.NAME); }
  public void testSliceFromRight()          { doTestNoFix(GoStringIndexIsByteQuickFix.NAME); }
  public void testSliceUnbound()            { doTestNoFix(GoStringIndexIsByteQuickFix.NAME); }
  public void testMoreThanOneCharInString() { doTestNoFix(GoStringIndexIsByteQuickFix.NAME, true); }
}
