package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object SeedFinderTest : Spek({
    val className = "MyInteger"
    val checkingNodeTypeDescription = "collected node should be"

    describe("calling visit() to test collection of values from constructor") {
        describe("with cast") {
            val testClass = "public class Test0 {\n" +
                    "    @Test\n" +
                    "    public void test() throws Throwable {\n" +
                    "        MyInteger myInteger1 = new MyInteger((int) (byte) 13);\n" +
                    "    }\n " +
                    "}"

            val cu: CompilationUnit = StaticJavaParser.parse(testClass)
            val seeds = SeedFinder.getSeeds(className, cu)
            val value = seeds["test"]?.get(0)

            it("should collect value 13") {
                assertEquals("13", value.toString())
            }

            it("$checkingNodeTypeDescription IntegerLiteralExpr") {
                if (value != null) {
                    assertTrue(value.isIntegerLiteralExpr)
                }
            }
        }

        describe("with unary minus") {
            val testClass = "public class Test1 {\n" +
                    "    @Test\n" +
                    "    public void test() throws Throwable {\n" +
                    "        MyInteger myInteger1 = new MyInteger((-23.6));\n" +
                    "    }\n " +
                    "}"

            val cu: CompilationUnit = StaticJavaParser.parse(testClass)
            val seeds = SeedFinder.getSeeds(className, cu)
            val value = seeds["test"]?.get(0)


            it("should collect value -23.6") {
                assertEquals("-23.6", value.toString())
            }

            it("$checkingNodeTypeDescription DoubleLiteralExpr") {
                if (value != null) {
                    assertTrue(value.isDoubleLiteralExpr)
                }
            }
        }

        describe("with several arguments") {
            val testClass = "public class Test2 {\n" +
                    "\n" +
                    "    @Test\n" +
                    "    public void test() throws Throwable {\n" +
                    "        String str = new MyInteger(false, \"hi\");\n" +
                    "    }\n " +
                    "}\n"

            val cu: CompilationUnit = StaticJavaParser.parse(testClass)
            val seeds = SeedFinder.getSeeds(className, cu)
            val value1 = seeds["test"]?.get(0)
            val value2 = seeds["test"]?.get(1)


            it("should collect values false and hi") {
                assertEquals("false", value1.toString())
                assertEquals("\"hi\"", value2.toString())
            }

            it("$checkingNodeTypeDescription BooleanLiteralExpr") {
                if (value1 != null) {
                    assertTrue(value1.isBooleanLiteralExpr)
                }
            }

            it("$checkingNodeTypeDescription StringLiteralExpr") {
                if (value2 != null) {
                    assertTrue(value2.isStringLiteralExpr)
                }
            }
        }

        describe("calling constructor that is not our CUT") {
            val testClass = "public class Test2 {\n" +
                    "\n" +
                    "    @Test\n" +
                    "    public void test() throws Throwable {\n" +
                    "        String str = new Object(13);\n" +
                    "    }\n" +
                    "}\n"

            val cu: CompilationUnit = StaticJavaParser.parse(testClass)
            val seeds = SeedFinder.getSeeds(className, cu)

            it("should collect nothing") {
                assertTrue(seeds["test"]?.isEmpty()!!)
            }

            it("should replace nothing") {
                assertEquals(testClass, cu.toString())
            }
        }
    }

    describe("calling visit() to test collection of values from method args") {
        describe("without variable declaration") {
            val testClass = "public class Test3 {\n" +
                    "    @Test\n" +
                    "    public void test() throws Throwable {\n" +
                    "        MyInteger.fun(true, \"hi!\");\n" +
                    "    }\n " +
                    "}"

            val cu: CompilationUnit = StaticJavaParser.parse(testClass)
            val seeds = SeedFinder.getSeeds(className, cu)
            val value1 = seeds["test"]?.get(0)
            val value2 = seeds["test"]?.get(1)

            it("should collect values true and hi") {
                assertEquals("true", value1.toString())
                assertEquals("\"hi!\"", value2.toString())
            }
        }

        describe("with variable declaration") {
            val testClass = "public class Test4 {\n" +
                    "    @Test\n" +
                    "    public void test() throws Throwable {\n" +
                    "        int int1 = MyInteger.returnIntAndAdd((String) null);\n" +
                    "        int int2 = myInteger1.getIntValue();\n" +
                    "    }\n " +
                    "}"

            val cu: CompilationUnit = StaticJavaParser.parse(testClass)
            val seeds = SeedFinder.getSeeds(className, cu)
            val value = seeds["test"]?.get(0)

            it("should collect value null") {
                assertEquals("null", value.toString())
            }
        }

        describe("with objects as argument") {
            val testClass = "public class Test5 {\n" +
                    "\n" +
                    "    @Test\n" +
                    "    public void test() throws Throwable {\n" +
                    "        MyInteger myInteger1 = new MyInteger();\n" +
                    "        MyInteger myInteger2 = myInteger1.add(myInteger1);\n" +
                    "        myInteger1.add(myInteger1);\n" +
                    "    }\n" +
                    "}\n"

            val cu: CompilationUnit = StaticJavaParser.parse(testClass)
            val seeds = SeedFinder.getSeeds(className, cu)

            it("should collect nothing") {
                assertTrue(seeds["test"]?.isEmpty()!!)
            }

            it("should replace nothing") {
                assertEquals(testClass, cu.toString())
            }
        }
    }

    describe("calling on the real generated test cases") {
        describe("Evosuite test") {
            val testClass = "public class MyInteger_ESTest extends MyInteger_ESTest_scaffolding {\n" +
                    "\n" +
                    "    @Test(timeout = 4000)\n" +
                    "    public void test3() throws Throwable {\n" +
                    "        MyInteger myInteger0 = new MyInteger(650);\n" +
                    "        MyInteger myInteger1 = myInteger0.add(myInteger0);\n" +
                    "        assertEquals(1300, myInteger1.getIntValue());\n" +
                    "        assertEquals(650, myInteger0.getIntValue());\n" +
                    "    }\n" +
                    "}\n"

            val cu: CompilationUnit = StaticJavaParser.parse(testClass)
            val seeds = SeedFinder.getSeeds(className, cu)
            val value = seeds["test3"]?.get(0)

            it("should collect 650") {
                assertEquals("650", value.toString())
            }
        }

        describe("Randoop test") {
            val testClass = "@FixMethodOrder(MethodSorters.NAME_ASCENDING)\n" +
                    "public class RegressionTest0 {\n" +
                    "\n" +
                    "    @Test\n" +
                    "    public void test002() throws Throwable {\n" +
                    "        if (debug)\n" +
                    "            System.out.format(\"%n%s%n\", \"RegressionTest0.test002\");\n" +
                    "        MyInteger myInteger1 = new MyInteger(0);\n" +
                    "        int int2 = myInteger1.getIntValue();\n" +
                    "        java.lang.Class<?> wildcardClass3 = myInteger1.getClass();\n" +
                    "        org.junit.Assert.assertTrue(\"'\" + int2 + \"' != '\" + 0 + \"'\", int2 == 0);\n" +
                    "        org.junit.Assert.assertNotNull(wildcardClass3);\n" +
                    "    }\n" +
                    "}\n"

            val cu: CompilationUnit = StaticJavaParser.parse(testClass)
            val seeds = SeedFinder.getSeeds(className, cu)
            val value = seeds["test002"]?.get(0)


            it("should collect 0") {
                assertEquals("0", value.toString())
            }
        }
    }
})