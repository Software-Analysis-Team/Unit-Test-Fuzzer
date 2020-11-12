package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object SeedFinderTest : Spek({
    val seedFinder = SeedFinder()
    val className = "MyInteger"

    describe("calling visit() to test collection of values from constructor") {
        describe("with cast") {
            val testClass = "public class Test0 {\n" +
                    "    @Test\n" +
                    "    public void test() throws Throwable {\n" +
                    "        MyInteger myInteger1 = new MyInteger((int) (byte) 13);\n" +
                    "    }\n " +
                    "}"

            val cu: CompilationUnit = StaticJavaParser.parse(testClass)
            val seeds = seedFinder.getSeeds(className, cu)

            it("should collect value 13") {
                assertEquals("13", seeds[0].toString())
            }

            it("should replace 13 with ###") {
                assertEquals(
                    "public class Test0 {\n" +
                            "\n" +
                            "    @Test\n" +
                            "    public void test() throws Throwable {\n" +
                            "        MyInteger myInteger1 = new MyInteger((int) (byte) \"###\");\n" +
                            "    }\n" +
                            "}\n",
                    cu.toString()
                )
            }
        }

        describe("with unary minus") {
            val testClass = "public class Test1 {\n" +
                    "    @Test\n" +
                    "    public void test() throws Throwable {\n" +
                    "        MyInteger myInteger1 = new MyInteger((-23));\n" +
                    "    }\n " +
                    "}"

            val cu: CompilationUnit = StaticJavaParser.parse(testClass)
            val seeds = seedFinder.getSeeds(className, cu)

            it("should collect value -23") {
                assertEquals("-23", seeds[0].toString())
            }

            it("should replace -23 with ###") {
                assertEquals(
                    "public class Test1 {\n" +
                            "\n" +
                            "    @Test\n" +
                            "    public void test() throws Throwable {\n" +
                            "        MyInteger myInteger1 = new MyInteger((\"###\"));\n" +
                            "    }\n" +
                            "}\n",
                    cu.toString()
                )
            }
        }

        describe("with several arguments") {
            val testClass = "public class Test2 {\n" +
                    "\n" +
                    "    @Test\n" +
                    "    public void test() throws Throwable {\n" +
                    "        String str = new Object(false, \"hi\");\n" +
                    "    }\n " +
                    "}\n"

            val cu: CompilationUnit = StaticJavaParser.parse(testClass)
            val seeds = seedFinder.getSeeds(className, cu)

            it("should collect values false and hi") {
                assertEquals("false", seeds[0].toString())
                assertEquals("\"hi\"", seeds[1].toString())
            }

            it("should replace false and hi with ###") {
                assertEquals(
                    "public class Test2 {\n" +
                            "\n" +
                            "    @Test\n" +
                            "    public void test() throws Throwable {\n" +
                            "        String str = new Object(\"###\", \"###\");\n" +
                            "    }\n" +
                            "}\n",
                    cu.toString()
                )
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
            val seeds = seedFinder.getSeeds(className, cu)

            it("should collect values true and hi") {
                assertEquals("true", seeds[0].toString())
                assertEquals("\"hi!\"", seeds[1].toString())
            }

            it("should replace true and hi with ###") {
                assertEquals(
                    "public class Test3 {\n" +
                            "\n" +
                            "    @Test\n" +
                            "    public void test() throws Throwable {\n" +
                            "        MyInteger.fun(\"###\", \"###\");\n" +
                            "    }\n" +
                            "}\n",
                    cu.toString()
                )
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
            val seeds = seedFinder.getSeeds(className, cu)

            it("should collect value null") {
                assertEquals("null", seeds[0].toString())
            }

            it("should replace null with ###") {
                assertEquals(
                    "public class Test4 {\n" +
                            "\n" +
                            "    @Test\n" +
                            "    public void test() throws Throwable {\n" +
                            "        int int1 = MyInteger.returnIntAndAdd((String) \"###\");\n" +
                            "        int int2 = myInteger1.getIntValue();\n" +
                            "    }\n" +
                            "}\n",
                    cu.toString()
                )
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
                    "    }\n " +
                    "}"

            val cu: CompilationUnit = StaticJavaParser.parse(testClass)
            val seeds = seedFinder.getSeeds(className, cu)

            it("should collect nothing") {
                assertTrue(seeds.isEmpty())
            }

            it("should replace nothing") {
                assertEquals(
                    "public class Test5 {\n" +
                            "\n" +
                            "    @Test\n" +
                            "    public void test() throws Throwable {\n" +
                            "        MyInteger myInteger1 = new MyInteger();\n" +
                            "        MyInteger myInteger2 = myInteger1.add(myInteger1);\n" +
                            "        myInteger1.add(myInteger1);\n" +
                            "    }\n" +
                            "}\n",
                    cu.toString()
                )
            }
        }
    }

    describe("calling on the real generated test cases") {
        // todo
    }
})