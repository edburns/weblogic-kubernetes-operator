// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.util.exception.CopyNotSupportedException;
import oracle.weblogic.kubernetes.actions.impl.Exec;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.impl.Service.getServiceNodePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyDefaultTokenExists;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copy;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFolder;
import static oracle.weblogic.kubernetes.utils.FileUtils.createZipFile;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// Test to create model in image domain and verify the domain started successfully
// @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to validate on-prem to k8s use case")
@IntegrationTest
public class ItLiftAndShiftFromOnPremDomain {
  private static String opNamespace = null;
  private static String traefikNamespace = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static ConditionFactory withQuickRetryPolicy = null;
  private static final String LIFT_AND_SHIFT_WORK_DIR = WORK_DIR + "/liftandshiftworkdir";
  private static final String ON_PREM_DOMAIN = "onpremdomain";
  private static final String DISCOVER_DOMAIN_OUTPUT_DIR = "wkomodelfilesdir";
  private static final String DOMAIN_TEMP_DIR = LIFT_AND_SHIFT_WORK_DIR + "/" + ON_PREM_DOMAIN;
  private static final String DOMAIN_SRC_DIR = RESOURCE_DIR + "/" + ON_PREM_DOMAIN;
  private static final String WKO_IMAGE_FILES = LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR;
  private static final String WKO_IMAGE_NAME = "onprem_to_wko_image";
  private static final String BUILD_SCRIPT = "discover_domain.sh";
  private static final Path BUILD_SCRIPT_SOURCE_PATH = Paths.get(RESOURCE_DIR, "bash-scripts", BUILD_SCRIPT);
  private static final String domainUid = "onprem-domain";
  private static int managedServerPort = 8001;
  private static String imageName = null;
  private static LoggingFacade logger = null;
  private Path zipFile;

  private static HelmParams traefikHelmParams = null;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(6, MINUTES).await();

    // create a reusable quick retry policy
    withQuickRetryPolicy = with().pollDelay(1, SECONDS)
        .and().with().pollInterval(2, SECONDS)
        .atMost(15, SECONDS).await();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique Voyager namespace
    logger.info("Get a unique namespace for Voyager");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    traefikNamespace = namespaces.get(1);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domainNamespace = namespaces.get(2);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // install and verify Traefik
    logger.info("Installing Traefik controller using helm");
    traefikHelmParams = installAndVerifyTraefik(traefikNamespace, 0, 0);

  }

  // Create a MiiDomain from an on prem domain.
  // This test first uses WDT DiscoverDomain tool on an on-prem domain. This tool when used with
  // target option wko, will create the necessary wdt model file, properties file, an archive file
  // and a domain yaml file. The test then use the resulting model file to create an MiiDomain
  @Test
  @DisplayName("Create model in image domain and verify external admin services")
  public void testCreateMiiDomainWithClusterFromOnPremDomain() {
    // admin/managed server name here should match with model yaml in MII_BASIC_WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 5;

    assertDoesNotThrow(() -> {
      Files.createDirectories(Paths.get(LIFT_AND_SHIFT_WORK_DIR));
    });

    // Copy the on-prem domain files to a temporary directory
    try {
      copyFolder(DOMAIN_SRC_DIR, DOMAIN_TEMP_DIR);
    } catch (IOException  ioex) {
      logger.info("Exception while copying domain files from " + DOMAIN_SRC_DIR + " to " + DOMAIN_TEMP_DIR, ioex);
    }

    // We need to build the app so that we can pass it into the weblogic pod along with the config files,
    // so that wdt discoverDomain could be run.
    List<String> appDirList = Collections.singletonList("onprem-app");

    logger.info("Build the application archive using what is in {0}", appDirList);
    assertTrue(
        buildAppArchive(
            defaultAppParams()
                .srcDirList(appDirList)
                .appName("opdemo")),
        String.format("Failed to create application archive for %s",
            "opdemo"));

    //copy file from stage dir to where the config files are
    try {
      copy(Paths.get(ARCHIVE_DIR, "/wlsdeploy/applications/opdemo.ear"), Paths.get(DOMAIN_TEMP_DIR, "/opdemo.ear"));
    } catch (IOException ioex) {
      logger.info("Copy of the application to the domain directory failed");
    }

    Path tempDomainDir = Paths.get(DOMAIN_TEMP_DIR);
    zipFile = Paths.get(createZipFile(tempDomainDir));
    logger.info("zipfile is in {0}", zipFile.toString());

    // Call WDT DiscoverDomain tool with wko target to get the required file to create a
    // Mii domain image. Since WDT requires weblogic installation, we start a pod and run
    // wdt discoverDomain tool in the pod
    V1Pod webLogicPod = setupWebLogicPod(domainNamespace);

    // copy the onprem domain zip file to /u01 location inside pod
    try {
      Kubernetes.copyFileToPod(domainNamespace, webLogicPod.getMetadata().getName(),
          null, zipFile, Paths.get("/u01/", zipFile.getFileName().toString()));
    } catch (ApiException | IOException ioex) {
      logger.info("Exception while copying file " + zipFile + " to pod", ioex);
    }

    //copy the build script discover_domain.sh to /u01 location inside pod
    try {
      Kubernetes.copyFileToPod(domainNamespace, webLogicPod.getMetadata().getName(),
          null, BUILD_SCRIPT_SOURCE_PATH, Paths.get("/u01", BUILD_SCRIPT));
    } catch (ApiException | IOException  ioex) {
      logger.info("Exception while copying file " + zipFile + " to pod", ioex);
    }
    logger.info("kubectl copied " + BUILD_SCRIPT + " into the pod");

    // Check that all the required files have been copied into the pod
    try {
      ExecResult ex = Exec.exec(webLogicPod, null, false, "/bin/ls", "-ls", "/u01");
      if (ex.stdout() != null) {
        logger.info("Exec stdout {0}", ex.stdout());
      }
      if (ex.stderr() != null) {
        logger.info("Exec stderr {0}", ex.stderr());
      }
    } catch (ApiException | IOException | InterruptedException ioex) {
      logger.info("Exception while listing the files in /u01", ioex);
    }

    // Run the discover_domain.sh script in the pod
    try {
      ExecResult exec = Exec.exec(webLogicPod, null, false, "/bin/sh", "/u01/" + BUILD_SCRIPT);
      if (exec.stdout() != null) {
        logger.info("Exec stdout {0}", exec.stdout());
      }
      if (exec.stderr() != null) {
        logger.info("Exec stderr {0}", exec.stderr());
      }

      // WDT discoverDomain tool creates a model file, a variable file, domain.yaml and script that creates the secrets.
      // Copy the directory that contains the files to workdir
      Kubernetes.copyDirectoryFromPod(webLogicPod,
          Paths.get("/u01", DISCOVER_DOMAIN_OUTPUT_DIR).toString(), Paths.get(LIFT_AND_SHIFT_WORK_DIR));
    } catch (ApiException | IOException | InterruptedException | CopyNotSupportedException ioex) {
      logger.info("Exception while copying file "
          + Paths.get("/u01", DISCOVER_DOMAIN_OUTPUT_DIR) + " from pod", ioex);
    }

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    //create a MII image
    imageName = createImageAndVerify(WKO_IMAGE_NAME, Collections.singletonList(WKO_IMAGE_FILES + "/onpremdomain.yaml"),
        Collections.singletonList(DOMAIN_TEMP_DIR + "/opdemo.ear"),
        Collections.singletonList(WKO_IMAGE_FILES + "/onpremdomain.properties"),
        WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, "WLS", true,
        "onpremdomain", false);

    // docker login and push image to docker registry if necessary
    logger.info("Push the image {0} to Docker repo", WKO_IMAGE_NAME);
    dockerLoginAndPushImageToRegistry(WKO_IMAGE_NAME);

    // Namespace and password needs to be updated in Create_k8s_secrets.sh
    updateCreateSecretsFile();

    // Namespace, domainHome, domainHomeSourceType, imageName and modelHome needs to be updated in model.yaml
    updateDomainYamlFile();

    // run create_k8s_secrets.sh that discoverDomain created to create necessary secrets
    CommandParams params = new CommandParams().defaults();
    params.command("sh "
        + Paths.get(LIFT_AND_SHIFT_WORK_DIR, "/u01/", DISCOVER_DOMAIN_OUTPUT_DIR, "/create_k8s_secrets.sh").toString());

    logger.info("Run create_k8s_secrets.sh to create secrets");
    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create secrets");

    logger.info("Run kubectl to create the domain");
    params = new CommandParams().defaults();
    params.command("kubectl apply -f "
        + Paths.get(LIFT_AND_SHIFT_WORK_DIR, "/u01/", DISCOVER_DOMAIN_OUTPUT_DIR + "/model.yaml").toString());

    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create domain custom resource");

    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace));

    // verify the admin server service and pod is created
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify managed server services created and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // create ingress rules with path routing for Traefik
    createTraefikIngressRoutingRules(domainNamespace);

    int traefikNodePort = getServiceNodePort(traefikNamespace, traefikHelmParams.getReleaseName(), "web");
    assertTrue(traefikNodePort != -1,
        "Could not get the default external service node port");
    logger.info("Found the Traefik service nodePort {0}", traefikNodePort);
    logger.info("The K8S_NODEPORT_HOST is {0}", K8S_NODEPORT_HOST);

    String curlString = String.format("curl -v --show-error --noproxy '*' "
            + "http://%s:%s/opdemo/?dsName=testDatasource",
        K8S_NODEPORT_HOST, traefikNodePort);

    ExecResult execResult = null;
    logger.info("curl command {0}", curlString);

    try {
      Thread.sleep(6000);
    } catch (Exception ex) {
      ex.printStackTrace();
    }

    execResult = assertDoesNotThrow(
        () -> exec(curlString, true));

    if (execResult.exitValue() == 0) {
      logger.info("\n HTTP response is \n " + execResult.stdout());
      logger.info("curl command returned {0}", execResult.toString());
      assertTrue(execResult.stdout().contains("WebLogic on prem to wko App"),
          "Not able to access the application");
    } else {
      fail("HTTP request to the app failed" + execResult.stderr());
    }
  }

  private static V1Pod setupWebLogicPod(String namespace) {
    final LoggingFacade logger = getLogger();
    ConditionFactory withStandardRetryPolicy = with().pollDelay(10, SECONDS)
        .and().with().pollInterval(2, SECONDS)
        .atMost(3, MINUTES).await();
    verifyDefaultTokenExists();

    //want to create a pod with a unique name
    //If a test calls buildApplication for 2 (or more) different applications
    //to be built, since the pod will have been created for the first application already,
    //this method will fail when called the second time around. Hence the need for a
    //unique name for the pod.
    String uniqueName = Namespace.uniqueName();
    final String podName = "weblogic-build-pod-" + uniqueName;

    // create a V1Container with specific scripts and properties for creating domain
    V1Container container = new V1Container()
        //.addCommandItem("/bin/sh")
        .addEnvItem(new V1EnvVar()
            .name("WDT_VERSION")
            .value(WDT_VERSION))
        .addEnvItem(new V1EnvVar()
            .name("DOMAIN_SRC")
            .value("onpremdomain"))
        .addEnvItem(new V1EnvVar()
            .name("DISCOVER_DOMAIN_OUTPUT_DIR")
            .value(DISCOVER_DOMAIN_OUTPUT_DIR))
        .addEnvItem(new V1EnvVar()
            .name("APP")
            .value(System.getenv("opdemo")))
        .addEnvItem(new V1EnvVar()
            .name("http_proxy")
            .value(System.getenv("http_proxy")))
        .addEnvItem(new V1EnvVar()
            .name("https_proxy")
            .value(System.getenv("http_proxy")));

    V1Pod podBody = new V1Pod()
        .spec(new V1PodSpec()
            .containers(Arrays.asList(container
                .name("weblogic-container")
                .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
                .imagePullPolicy("IfNotPresent")
                .addCommandItem("sleep")
                .addArgsItem("600")))
            .imagePullSecrets(Arrays.asList(new V1LocalObjectReference()
                .name(BASE_IMAGES_REPO_SECRET))))
        .metadata(new V1ObjectMeta().name(podName))
        .apiVersion("v1")
        .kind("Pod");
    V1Pod wlsPod = assertDoesNotThrow(() -> Kubernetes.createPod(namespace, podBody));

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for {0} to be ready in namespace {1}, "
                    + "(elapsed time {2} , remaining time {3}",
                podName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(podReady(podName, null, namespace));

    return wlsPod;
  }

  private static void updateCreateSecretsFile() {
    try {
      replaceStringInFile(LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR + "/create_k8s_secrets.sh",
          "NAMESPACE=onprem-domain", "NAMESPACE=" + domainNamespace);
      replaceStringInFile(LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR + "/create_k8s_secrets.sh",
          "weblogic-credentials <user> <password>", "weblogic-credentials " + ADMIN_USERNAME_DEFAULT
              + " " + ADMIN_PASSWORD_DEFAULT);
      replaceStringInFile(LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR + "/create_k8s_secrets.sh",
          "scott <password>", "scott tiger");
      replaceStringInFile(LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR + "/create_k8s_secrets.sh",
          "runtime-encryption-secret <password>", "runtime-encryption-secret welcome1");
    } catch (IOException ioex) {
      logger.info("Exception while replacing user password in the script file");
    }
  }

  private static void updateDomainYamlFile() {
    try {
      replaceStringInFile(LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR + "/model.yaml",
          "namespace: onprem-domain", "namespace: " + domainNamespace);
      replaceStringInFile(LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR + "/model.yaml",
          "domainHome: \\{\\{\\{domainHome\\}\\}\\}", "domainHome: /u01/domains/" + domainUid);
      replaceStringInFile(LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR + "/model.yaml",
          "domainHomeSourceType: \\{\\{\\{domainHomeSourceType\\}\\}\\}", "domainHomeSourceType: FromModel");
      replaceStringInFile(LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR + "/model.yaml",
          "image: \\{\\{\\{imageName\\}\\}\\}", "image: " + imageName);
      replaceStringInFile(LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR + "/model.yaml",
          "name: ocir", "name: ocir-secret");
      replaceStringInFile(LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR + "/model.yaml",
          "modelHome: \\{\\{\\{modelHome\\}\\}\\}", "modelHome: /u01/wdt/models");
    } catch (IOException ioex) {
      logger.info("Exception while replacing user password in the script file");
    }
  }

  private static void createTraefikIngressRoutingRules(String domainNamespace) {
    logger.info("Creating ingress rules for domain traffic routing");
    Path srcFile = Paths.get(RESOURCE_DIR, "traefik/traefik-ingress-rules-onprem.yaml");
    Path dstFile = Paths.get(RESULTS_ROOT, "traefik/traefik-ingress-rules-onprem.yaml");
    assertDoesNotThrow(() -> {
      Files.deleteIfExists(dstFile);
      Files.createDirectories(dstFile.getParent());
      Files.write(dstFile, Files.readString(srcFile).replaceAll("@NS@", domainNamespace)
          .replaceAll("@domainuid@", domainUid)
          .getBytes(StandardCharsets.UTF_8));
    });
    String command = "kubectl create -f " + dstFile;
    logger.info("Running {0}", command);
    ExecResult result;
    try {
      result = ExecCommand.exec(command, true);
      String response = result.stdout().trim();
      logger.info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
          result.exitValue(), response, result.stderr());
      assertEquals(0, result.exitValue(), "Command didn't succeed");
    } catch (IOException | InterruptedException ex) {
      logger.severe(ex.getMessage());
    }
  }

}

