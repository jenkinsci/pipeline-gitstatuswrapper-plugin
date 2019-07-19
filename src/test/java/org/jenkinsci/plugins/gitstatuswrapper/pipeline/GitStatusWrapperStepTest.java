package org.jenkinsci.plugins.gitstatuswrapper.pipeline;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.model.Result;
import org.eclipse.jgit.annotations.NonNull;
import org.jenkinsci.plugins.gitstatuswrapper.DummyCredentials;
import org.jenkinsci.plugins.gitstatuswrapper.github.GitHubHelper;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.kohsuke.github.*;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.net.Proxy;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;

@RunWith(PowerMockRunner.class)
@PrepareForTest({GitStatusWrapperStep.class, GitHubHelper.class})
@PowerMockIgnore ({"javax.crypto.*"})
public class GitStatusWrapperStepTest {
  public static final String SUCCESSFUL_LOG_MSG = "Successful Log!";

  public static final String SUCCESS_JENKINS_PAYLOAD =
          "node { gitStatusWrapper( account: 'myAccount', gitHubContext: 'status/context', " +
                  "credentialsId: 'dummy', description: 'OK', " +
                  "repo: 'myRepo', sha: '439ac0b0c4870bf5936e84940d73128db905e93d', " +
                  "targetUrl: 'http://www.someTarget.com') " +
                  "{ echo '"+ SUCCESSFUL_LOG_MSG + "' }}";

  public static final String SUCCESS_JENKINS_ENTERPRISE_PAYLOAD =
      "node { gitStatusWrapper( account: 'myAccount', gitHubContext: 'status/context', " +
          "credentialsId: 'dummy', description: 'OK', " +
          "repo: 'myRepo', sha: '439ac0b0c4870bf5936e84940d73128db905e93d', " +
          "targetUrl: 'http://www.someTarget.com', gitApiUrl: 'https://api.example.com') " +
          "{ echo '"+ SUCCESSFUL_LOG_MSG + "' }}";

  public static final String FAIL_JENKINS_PAYLOAD_BAD_BLOCK =
      "node { gitStatusWrapper( account: 'myAccount', gitHubContext: 'status/context', " +
          "credentialsId: 'dummy', description: 'OK', " +
          "repo: 'myRepo', sha: '439ac0b0c4870bf5936e84940d73128db905e93d', " +
          "targetUrl: 'http://www.someTarget.com') { error 'exit 1' }}";

  public static final String SUCCESS_JENKINS_RESTART_PAYLOAD =
          "node { gitStatusWrapper( account: 'myAccount', gitHubContext: 'status/context', " +
                  "credentialsId: 'dummy', description: 'OK', " +
                  "repo: 'myRepo', sha: '439ac0b0c4870bf5936e84940d73128db905e93d', " +
                  "targetUrl: 'http://www.someTarget.com') " +
                  "{ semaphore 'wait-inside'\n };\n" +
                  "echo '" + SUCCESSFUL_LOG_MSG + "';" +
                  "}";

  @Rule
  public RestartableJenkinsRule jenkins = new RestartableJenkinsRule();

  @Test
  public void build() throws Exception {
    jenkins.then((JenkinsRule j) -> {
      successfulPluginSetup(j, SUCCESS_JENKINS_PAYLOAD);
    });
  }

  @Test
  public void buildWithEnterprise() throws Exception {
    jenkins.then((JenkinsRule j) -> {
      successfulPluginSetup(j, SUCCESS_JENKINS_ENTERPRISE_PAYLOAD);
    });
  }

  @Test
  public void buildWithRestart() throws Exception {
    jenkins.then((JenkinsRule j) -> {
      StatusWrapperTestObj statusWrapperTestObj = setupStableEnvMockObj(j, SUCCESS_JENKINS_RESTART_PAYLOAD);
      SemaphoreStep.waitForStart("wait-inside/1", statusWrapperTestObj.getRun());
    });
    jenkins.then((JenkinsRule j) -> {
      addCredentials();

      WorkflowJob p = j.jenkins.getItemByFullName("p", WorkflowJob.class);
      WorkflowRun b = p.getBuildByNumber(1);
      SemaphoreStep.success("wait-inside/1", null);
      j.waitForMessage(SUCCESSFUL_LOG_MSG, b);
    });
  }

  @Test
  public void buildWithFailingBlockMustFail() throws Exception {
    jenkins.then((JenkinsRule j) -> {
      StatusWrapperTestObj statusWrapperTestObj = setupStableEnvMockObj(j, FAIL_JENKINS_PAYLOAD_BAD_BLOCK);
      j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(statusWrapperTestObj.getRun()));
      Mockito.verify(statusWrapperTestObj.getRepo(), Mockito.times(1)).createCommitStatus(anyString(), Mockito.eq(GHCommitState.PENDING), anyString(), anyString(), anyString());
      Mockito.verify(statusWrapperTestObj.getRepo(), Mockito.times(1)).createCommitStatus(anyString(), Mockito.eq(GHCommitState.FAILURE), anyString(), anyString(), anyString());
      j.assertLogContains("exit 1", statusWrapperTestObj.getRun());
    });
  }

  private StatusWrapperTestObj successfulPluginSetup(@NonNull JenkinsRule j, String jobDefinition) throws Exception {
    StatusWrapperTestObj statusWrapperTestObj = setupStableEnvMockObj(j, jobDefinition);

    j.assertBuildStatus(Result.SUCCESS, j.waitForCompletion(statusWrapperTestObj.getRun()));
    Mockito.verify(statusWrapperTestObj.getRepo(), Mockito.times(1)).createCommitStatus(anyString(), Mockito.eq(GHCommitState.PENDING), anyString(), anyString(), anyString());
    Mockito.verify(statusWrapperTestObj.getRepo(), Mockito.times(1)).createCommitStatus(anyString(), Mockito.eq(GHCommitState.SUCCESS), anyString(), anyString(), anyString());
    j.assertLogContains(SUCCESSFUL_LOG_MSG, statusWrapperTestObj.getRun());
    return statusWrapperTestObj;
  }

  private StatusWrapperTestObj setupStableEnvMockObj(@NonNull JenkinsRule j, String jobDefinition) throws Exception {
    StatusWrapperTestObj statusWrapperTestObj = new StatusWrapperTestObj().invoke();
    GitHubBuilder ghb = statusWrapperTestObj.getGhb();
    GitHub gh = statusWrapperTestObj.getGh();
    GHUser user = statusWrapperTestObj.getUser();
    GHCommit commit = statusWrapperTestObj.getCommit();
    GHRepository repo = statusWrapperTestObj.getRepo();

    PowerMockito.when(commit.getSHA1()).thenReturn("abc123");
    PowerMockito.when(repo.getCommit(anyString())).thenReturn(commit);
    PowerMockito.when((repo.createCommitStatus(anyString(), any(GHCommitState.class), anyString(), anyString(), anyString()))).thenReturn(null);

    PowerMockito.when(user.getRepository(anyString())).thenReturn(repo);

    PowerMockito.when(gh.isCredentialValid()).thenReturn(true);
    PowerMockito.when(gh.getUser(anyString())).thenReturn(user);

    PowerMockito.when(ghb.withProxy(Matchers.<Proxy>anyObject())).thenReturn(ghb);
    PowerMockito.when(ghb.withOAuthToken(anyString(), anyString())).thenReturn(ghb);
    PowerMockito.when(ghb.withEndpoint(anyString())).thenReturn(ghb);
    PowerMockito.when(ghb.build()).thenReturn(gh);

    PowerMockito.whenNew(GitHubBuilder.class).withNoArguments().thenReturn(ghb);

    addCredentials();

    WorkflowJob p = j.createProject(WorkflowJob.class, "p");
    p.setDefinition(new CpsFlowDefinition(
        jobDefinition, true));

    WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
    statusWrapperTestObj.setRun(b1);
    return statusWrapperTestObj;
  }

  private void addCredentials() {
    Credentials dummy = new DummyCredentials(CredentialsScope.GLOBAL, "user", "psw");
    SystemCredentialsProvider.getInstance().getCredentials().add(dummy);
  }

  private class StatusWrapperTestObj {

    private GitHubBuilder ghb;
    private GitHub gh;
    private GHUser user;
    private GHCommit commit;
    private GHRepository repo;
    private WorkflowRun run;

    public WorkflowRun getRun() {
      return run;
    }
    public void setRun(WorkflowRun run){
      this.run = run;
    }

    public GitHubBuilder getGhb() {
      return ghb;
    }

    public GitHub getGh() {
      return gh;
    }

    public GHUser getUser() {
      return user;
    }

    public GHCommit getCommit() {
      return commit;
    }

    public GHRepository getRepo() {
      return repo;
    }

    public StatusWrapperTestObj invoke() {
      ghb = PowerMockito.mock(GitHubBuilder.class);
      gh = PowerMockito.mock(GitHub.class);
      user = PowerMockito.mock(GHUser.class);
      commit = PowerMockito.mock(GHCommit.class);
      repo = PowerMockito.mock(GHRepository.class);
      return this;
    }
  }
}
