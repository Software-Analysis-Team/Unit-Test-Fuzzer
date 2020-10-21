/**
  * Copyright (c) 2017 Universitat Politècnica de València (UPV)

  * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

  * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

  * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

  * 3. Neither the name of the UPV nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

  * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
  **/
package sbst.runtool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;

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

		this.cleanLatestEvosuiteRetVal();

		final String homeDirName = "."; // current folder
		new File(homeDirName + "/temp").mkdir();

		final String tempDirName = String.join(File.separator, homeDirName, "temp");
		File tempDir = new File(tempDirName);
		if (!tempDir.exists()) {
			throw new RuntimeException("Expected Temporary folder " + tempDirName + " was not found");
		}

		if (logOut == null) {
			final String logRandoopFileName = String.join(File.separator, tempDirName, "log_evosuite.txt");
			PrintStream outStream;
			try {
				outStream = new PrintStream(new FileOutputStream(logRandoopFileName, true));
				this.setLoggingPrintWriter(outStream);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(
						"FileNotFoundException signaled while creating output stream for  " + logRandoopFileName);
			}
		}

		log("Checking environment variable JAVA_HOME ");
		if (System.getenv("JAVA_HOME") == null) {
			throw new RuntimeException("JAVA_HOME must be configured in order to run this program");
		}

		log("Execution of tool Evosuite STARTED");
		log("user.home=" + homeDirName);
		final String evosuiteJarFilename = String.join(File.separator, homeDirName, "lib", "evosuite-1.0.6.jar");

		File evosuiteJarFile = new File(evosuiteJarFilename);
		if (!evosuiteJarFile.exists()) {
			throw new RuntimeException("File evosuite-1.0.6.jar was not found at folder");
		}

		final String junitOutputDirName = String.join(File.separator, homeDirName, "temp", "testcases");
		File junitOutputDir = new File(junitOutputDirName);
		if (!junitOutputDir.exists()) {
			log("Creating directory " + junitOutputDir);
			junitOutputDir.mkdirs();
		}

		final String testClass = cName;
		final long timeLimit = timeBudget;

		final String junitPackageName;
		if (!testClass.contains(".")) {
			junitPackageName = null;
		} else {
			final int lastIndexOfDot = testClass.lastIndexOf(".");
			junitPackageName = testClass.substring(0, lastIndexOfDot);
		}

		final String classPath = createClassPath(binFile, classPathList, evosuiteJarFile);
		StringBuffer cmdLine = new StringBuffer();

		String javaCommand = buildJavaCommand();
		cmdLine.append(String.format("%s -Xmx12g -jar ./lib/evosuite-1.0.6.jar ", javaCommand));
		cmdLine.append(String.format("-class %s ", testClass));
		cmdLine.append(String.format("-projectCP %s ", classPath));
		cmdLine.append("-Duse_separate_classloader=false ");
		cmdLine.append("-Dminimize=false ");
		cmdLine.append("-Dcoverage=false ");
		cmdLine.append("-Dassertion_strategy=all ");
		cmdLine.append(String.format("-Dsearch_budget=%s ", timeLimit / 2));
		cmdLine.append("-Dstopping_condition=MaxTime ");
		cmdLine.append(String.format("-DOUTPUT_DIR==%s ", junitOutputDirName));
		cmdLine.append(String.format("-Dtest_dir=%s ", junitOutputDirName));

//		final String regressionTestFileName;
//		if (junitPackageName != null) {
//			cmdLine.append(String.format("--junit-package-name=%s ", junitPackageName));
//
//			regressionTestFileName = String.join(File.separator, junitOutputDirName,
//					junitPackageName.replace(".", File.separator), "RegressionTest.java");
//		} else {
//			regressionTestFileName = String.join(File.separator, junitOutputDirName, "RegressionTest.java");
//
//		}
//
//		cmdLine.append("--clear=10000 ");
//		cmdLine.append("--string-maxlen=5000 ");
//		cmdLine.append("--forbid-null=false ");
//		cmdLine.append("--null-ratio=0.1 ");
//		cmdLine.append("--no-error-revealing-tests=true ");
//		cmdLine.append("--omitmethods=random ");
//		cmdLine.append("--silently-ignore-bad-class-names=true ");
//		cmdLine.append("--testsperfile=100 ");
//		cmdLine.append("--ignore-flaky-tests=true ");

		String cmdToExecute = cmdLine.toString();

		File homeDir = new File(homeDirName);
		int retVal = launch(homeDir, cmdToExecute);
		log("Evosuite execution finished with exit code " + retVal);
		this.setLatestEvosuiteRetVal(retVal);

//		File regressionTestFile = new File(regressionTestFileName);
//		if (regressionTestFile.exists()) {
//			log("Deleting regression test suite file " + regressionTestFileName);
//			regressionTestFile.delete();
//		} else {
//			log("Regression test suite file " + regressionTestFileName
//					+ " could not be deleted since it does not exist");
//		}

		log("Execution of tool Evosuite FINISHED");
	}

	private void setLatestEvosuiteRetVal(int retVal) {
		this.latestEvosuiteRetVal = retVal;
	}

	private void cleanLatestEvosuiteRetVal() {
		this.latestEvosuiteRetVal = null;
	}

	private String buildJavaCommand() {
		String cmd = String.join(File.separator, System.getenv("JAVA_HOME"), "bin", "java");
		return cmd;
	}

	private static String createClassPath(File subjectBinFile, List<File> subjectClassPathList, File jarFile) {
		StringBuffer buff = new StringBuffer();
		buff.append(subjectBinFile.getAbsolutePath());
		for (File classPathFile : subjectClassPathList) {
			buff.append(File.pathSeparator);
			buff.append(classPathFile.getAbsolutePath());
		}
		buff.append(File.pathSeparator);
		buff.append(jarFile.getAbsolutePath());
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
			log("An IOException occurred during the execution of Evosuite");
			return -1;
		}
	}

	private Integer latestEvosuiteRetVal = null;

	public Integer getLatestEvosuiteRetVal() {
		return latestEvosuiteRetVal;
	}

}
