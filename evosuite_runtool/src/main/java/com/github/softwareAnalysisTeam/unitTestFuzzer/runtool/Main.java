package com.github.softwareAnalysisTeam.unitTestFuzzer.runtool;

import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        Writer writer = new PrintWriter(System.out);
        Reader reader = new InputStreamReader(System.in);
        EvosuiteTool evosuiteTool = new EvosuiteTool();
        RunTool runTool = new RunTool(evosuiteTool, new InputStreamReader(System.in), new OutputStreamWriter(System.out));
        runTool.run();
    }
}