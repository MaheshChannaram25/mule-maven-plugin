/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package integrationTests.mojo.environmentSetup;

import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.rules.TemporaryFolder;
import org.mule.tools.client.agent.AgentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.fail;

public class AgentSetup {

  public static final int ATTEMPTS = 30;
  private Logger log;
  private static final long TEN_SECONDS = 10000;
  private static final String MULE_HOME_FOLDER_PREFIX = "/mule-enterprise-standalone-";
  private static String muleVersion;
  private static final String AGENT_JKS_RELATIVE_PATH = "/conf/mule-agent.jks";
  private static final String AGENT_YMS_RELATIVE_PATH = "/conf/mule-agent.yml";
  private static final String EXECUTABLE_FOLDER_RELATIVE_PATH = "/bin/mule";
  private static final String AMC_SETUP_RELATIVE_FOLDER = "/bin/amc_setup";
  private static final String UNENCRYPTED_CONNECTION_OPTION = "-I";
  private static final int NORMAL_TERMINATION = 0;
  private static final String START_AGENT_COMMAND = "start";
  private static final String ANCHOR_FILE_RELATIVE_PATH = "/apps/agent-anchor.txt";
  private static final String STOP_AGENT_COMMAND = "stop";
  private static String muleExecutable;
  private static List<String> commands = new ArrayList<>();
  private static String muleHome;
  private static Runtime runtime;
  private static Process applicationProcess;

  public AgentSetup(String muleVersion) {
    log = LoggerFactory.getLogger(this.getClass());
    this.muleVersion = muleVersion;
  }


  public void start() throws IOException, InterruptedException {
    Path currentRelativePath = Paths.get("");
    String targetFolder = currentRelativePath.toAbsolutePath().toString() + File.separator + "target";

    muleHome = targetFolder + MULE_HOME_FOLDER_PREFIX + muleVersion;

    deleteFile(muleHome + AGENT_JKS_RELATIVE_PATH);
    deleteFile(muleHome + AGENT_YMS_RELATIVE_PATH);

    muleExecutable = muleHome + EXECUTABLE_FOLDER_RELATIVE_PATH;

    runtime = Runtime.getRuntime();

    unpackAgent();
    startMule();
    checkAgentIsAcceptingDeployments();
  }

  public void stop() throws IOException, InterruptedException {
    stopMule();
    killMuleProcesses();
  }

  private void checkAgentIsAcceptingDeployments() throws InterruptedException, IOException {
    int tries = 0;
    boolean acceptingDeployments;
    TemporaryFolder folder = new TemporaryFolder();
    File dummyFile = folder.newFile("dummy.zip");
    AgentClient agentClient = new AgentClient(new SystemStreamLog(), "http://localhost:9999/");
    do {
      try {
        log.info("Checking if agent is accepting deployments...");
        agentClient.deployApplication("dummy", dummyFile);
        acceptingDeployments = true;
      } catch (Exception e) {
        log.info("Agent is not accepting deployments yet. Trying again...");
        log.error("Cause: " + e.getLocalizedMessage());
        Thread.sleep(TEN_SECONDS);
        acceptingDeployments = false;
        tries++;
      }
      if (tries == ATTEMPTS) { // Trying for approximately 30 X 10 s = 300 s = 5 minutes
        fail("Could not have agent accepting deployments");
      }
    } while (!acceptingDeployments);
    log.info("Agent is accepting deployments.");
  }

  private void startMule() throws InterruptedException, IOException {
    int tries = 0;
    do {
      if (tries != 0) {
        log.info("Failed to start mule. Trying to start again...");
        stopMule();
      }
      commands.clear();
      commands.add(muleExecutable);
      commands.add(START_AGENT_COMMAND);
      log.info("Starting mule...");
      applicationProcess = runtime.exec(commands.toArray(new String[0]));
      applicationProcess.waitFor();
      tries++;
      if (tries == ATTEMPTS) {
        fail("Could not have mule running");
      }
    } while (applicationProcess.exitValue() != NORMAL_TERMINATION);
    log.info("Mule successfully started.");
  }

  private void unpackAgent() throws InterruptedException, IOException {
    String amcExecutable = muleHome + AMC_SETUP_RELATIVE_FOLDER;
    int tries = 0;
    do {
      if (tries != 0) {
        log.info("Failed to unpack agent. Trying to unpack again...");
      }
      commands.clear();
      commands.add(amcExecutable);
      commands.add(UNENCRYPTED_CONNECTION_OPTION);
      log.info("Unpacking agent...");
      applicationProcess = runtime.exec(commands.toArray(new String[0]));
      applicationProcess.waitFor();
      tries++;
      if (tries == ATTEMPTS) {
        fail("Could not unpack agent");
      }
    } while (applicationProcess.exitValue() != NORMAL_TERMINATION);
    log.info("Agent successfully unpacked.");
  }

  private void deleteFile(String pathname) {
    File file = new File(pathname);
    if (file.exists()) {
      file.delete();
    }
  }

  private void stopMule() throws InterruptedException, IOException {
    log.info("Stopping mule...");
    int tries = 0;
    do {
      if (tries != 0) {
        log.info("Failed to stop mule. Trying to stop again...");
      }
      commands.clear();
      commands.add(muleExecutable);
      commands.add(STOP_AGENT_COMMAND);
      applicationProcess = runtime.exec(commands.toArray(new String[0]));
      applicationProcess.waitFor();
      tries++;
      if (tries == ATTEMPTS) {
        fail("Could not stop mule");
      }
    } while (applicationProcess.exitValue() != NORMAL_TERMINATION);
    log.info("Mule successfully stopped.");
  }

  public String getAnchorFilePath() {
    return muleHome + ANCHOR_FILE_RELATIVE_PATH;
  }



  private void killMuleProcesses() throws IOException {
    commands.clear();
    commands.add("ps");
    commands.add("-ax");
    commands.add("|");
    commands.add("grep");
    commands.add("mule");
    commands.add("|");
    commands.add("grep");
    commands.add("wrapper");
    commands.add("|");
    commands.add("cut");
    commands.add("-c");
    commands.add("1-5");
    commands.add("|");
    commands.add("xargs");
    commands.add("kill");
    commands.add("-9");
    runtime.exec(commands.toArray(new String[0]));
  }
}
