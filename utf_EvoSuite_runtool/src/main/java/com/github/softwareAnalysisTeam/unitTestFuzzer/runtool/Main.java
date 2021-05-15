package com.github.softwareAnalysisTeam.unitTestFuzzer.runtool;

import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        Writer writer = new PrintWriter(System.out);
        Reader reader = new InputStreamReader(System.in);
        String generationTool = args[0];
        String jqfDir = args[1];
        UTF utf = new UTF(generationTool, jqfDir);
        RunTool runTool = new RunTool(utf, new InputStreamReader(System.in), new OutputStreamWriter(System.out));
        runTool.run();
    }
}