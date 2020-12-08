package com.github.softwareAnalysisTeam.unitTestFuzzer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals

object TestCreatorTest : Spek({

    describe("calling replaceWithNewValue") {
        // todo
    }

    describe("calling collectAndDeleteAsserts() function") {
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
        val asserts = TestCreator.collectAndDeleteAsserts(cu)

        it("should collect 2 asserts") {
            assert(asserts.size == 2)
        }

        it("should remove asserts from cu") {
            assertEquals(
                "@FixMethodOrder(MethodSorters.NAME_ASCENDING)\n" +
                        "public class RegressionTest0 {\n" +
                        "\n" +
                        "    @Test\n" +
                        "    public void test002() throws Throwable {\n" +
                        "        if (debug)\n" +
                        "            System.out.format(\"%n%s%n\", \"RegressionTest0.test002\");\n" +
                        "        MyInteger myInteger1 = new MyInteger(0);\n" +
                        "        int int2 = myInteger1.getIntValue();\n" +
                        "        java.lang.Class<?> wildcardClass3 = myInteger1.getClass();\n" +
                        "    }\n" +
                        "}\n",
                cu.toString()
            )
        }
    }
})