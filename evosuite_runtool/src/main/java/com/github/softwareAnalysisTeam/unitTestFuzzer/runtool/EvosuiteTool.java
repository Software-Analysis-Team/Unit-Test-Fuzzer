package com.github.softwareAnalysisTeam.unitTestFuzzer.runtool;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class EvosuiteTool implements ITestingTool {

    private PrintStream logOut = null;

    public EvosuiteTool() {
        super();
    }

    private boolean useClassList = true;

    public void setUseClassList(boolean isMandatory) {
        this.useClassList = isMandatory;
    }

    private File binFile;
    private List<File> classPathList;

    public List<File> getExtraClassPath() {
        return new ArrayList<File>();
    }

    public void initialize(File src, File bin, List<File> classPath) {
        this.binFile = bin;
        this.classPathList = classPath;
    }

    public void run(String cName, long timeBudget) {

        this.cleanLatestRandoopRetVal();

        final String homeDirName = "."; // current folder
        new File(homeDirName + "/temp").mkdir();

        final String tempDirName = String.join(File.separator, homeDirName, "temp");
        File tempDir = new File(tempDirName);
        if (!tempDir.exists()) {
            throw new RuntimeException("Expected Temporary folder " + tempDirName + " was not found");
        }

        if (logOut == null) {
            final String logUtfFileName = String.join(File.separator, tempDirName, "log_evosuite.txt");
            PrintStream outStream;
            try {
                outStream = new PrintStream(new FileOutputStream(logUtfFileName, true));
                this.setLoggingPrintWriter(outStream);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(
                        "FileNotFoundException signaled while creating output stream for  " + logUtfFileName);
            }
        }

        log("Checking environment variable JAVA_HOME ");
        if (System.getenv("JAVA_HOME") == null) {
            throw new RuntimeException("JAVA_HOME must be configured in order to run this program");
        }

        log("Execution of tool UTF STARTED");
        log("user.home=" + homeDirName);
        // todo: change jar name
        final String evosuiteJarFilename = String.join(File.separator, homeDirName, "lib", "evosuite-1.1.0.jar");

        File utfJarFile = new File(evosuiteJarFilename);
        if (!utfJarFile.exists()) {
            throw new RuntimeException("File evosuite.jar was not found at folder "
                    + String.join(File.separator, homeDirName, "evosuite", "dist"));
        }

        final String junitOutputDirName = String.join(File.separator, homeDirName, "temp", "testcases");
        File junitOutputDir = new File(junitOutputDirName);
        if (!junitOutputDir.exists()) {
            log("Creating directory " + junitOutputDir);
            junitOutputDir.mkdirs();
        }

        final String junitPackageName;
        if (!cName.contains(".")) {
            junitPackageName = null;
        } else {
            final int lastIndexOfDot = cName.lastIndexOf(".");
            junitPackageName = cName.substring(0, lastIndexOfDot);
        }

        final String classPath = createClassPath(binFile, classPathList, utfJarFile);
        StringBuffer cmdLine = new StringBuffer();

        String javaCommand = buildJavaCommand();
        cmdLine.append(String.format("%s -jar %s -class %s -projectCP %s -Dsearch_budget=%s -Dtest_dir=%s ", javaCommand, evosuiteJarFilename, cName, classPath, Math.round(timeBudget * 0.5), junitOutputDirName));

        final String regressionTestFileName;
        regressionTestFileName = String.join(File.separator, junitOutputDirName, "RegressionTest.java");

        String cmdToExecute = cmdLine.toString();

        File homeDir = new File(homeDirName);
        int retVal = launch(homeDir, cmdToExecute);
        log("UTF execution finished with exit code " + retVal);
        this.setLatestRandoopRetVal(retVal);

        File regressionTestFile = new File(regressionTestFileName);
        if (regressionTestFile.exists()) {
            log("Deleting regression test suite file " + regressionTestFileName);
            regressionTestFile.delete();
        } else {
            log("Regression test suite file " + regressionTestFileName
                    + " could not be deleted since it does not exist");
        }

        log("Execution of tool UTF FINISHED");
    }

    private void setLatestRandoopRetVal(int retVal) {
        this.latestRandoopRetVal = Integer.valueOf(retVal);
    }

    private void cleanLatestRandoopRetVal() {
        this.latestRandoopRetVal = null;
    }

    private String buildJavaCommand() {
        String cmd = String.join(File.separator, System.getenv("JAVA_HOME"), "bin", "java");
        return cmd;
    }

    private static String createClassPath(File subjectBinFile, List<File> subjectClassPathList, File randoopJarFile) {
        StringBuffer buff = new StringBuffer();
        buff.append(subjectBinFile.getAbsolutePath());
        for (File classPathFile : subjectClassPathList) {
            buff.append(File.pathSeparator);
            buff.append(classPathFile.getAbsolutePath());
        }
        buff.append(File.pathSeparator);
        buff.append(randoopJarFile.getAbsolutePath());
        return buff.toString();
    }

    public void setLoggingPrintWriter(PrintStream w) {
        this.logOut = w;
    }

    private void log(String msg) {
        if (logOut != null) {
            logOut.println(msg);
        }
    }

    private int launch(File baseDir, String cmdString) {
        DefaultExecutor executor = new DefaultExecutor();
        PumpStreamHandler streamHandler;
        streamHandler = new PumpStreamHandler(logOut, logOut, null);
        executor.setStreamHandler(streamHandler);
        if (baseDir != null) {
            executor.setWorkingDirectory(baseDir);
        }

        int exitValue;
        try {
            log("Spawning new process of command " + cmdString);
            exitValue = executor.execute(CommandLine.parse(cmdString));
            log("Execution of subprocess finished with ret_code " + exitValue);
            return exitValue;
        } catch (IOException e) {
            log("An IOException occurred during the execution of UTF");
            return -1;
        }
    }

    private Integer latestRandoopRetVal = null;

    public Integer getLatestRandoopRetVale() {
        return latestRandoopRetVal;
    }

}
