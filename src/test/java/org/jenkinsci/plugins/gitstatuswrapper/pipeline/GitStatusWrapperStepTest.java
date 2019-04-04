package org.jenkinsci.plugins.gitstatuswrapper.pipeline;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.model.Result;
import java.net.Proxy;
import org.jenkinsci.plugins.gitstatuswrapper.github.GitHubHelper;
import org.jenkinsci.plugins.gitstatuswrapper.DummyCredentials;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({GitStatusWrapperStep.class, GitHubHelper.class})
@PowerMockIgnore ({"javax.crypto.*"})
public class GitStatusWrapperStepTest {
  public static final String SUCCESSFUL_LOG_MSG = "Successful Log!";

  public static final String SUCCESS_JENKINS_PAYLOAD =
      "node { gitStatusWrapper( account: 'myAccount', gitHubContext: 'status/context', " +
          "credentialsId: 'dummy', description: 'OK', " +
          "repo: 'myRepo', sha: '439ac0b0c4870bf5936e84940d73128db905e93d', " +
          "targetUrl: 'http://www.someTarget.com') { echo '"+ SUCCESSFUL_LOG_MSG + "' }}";

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
          "targetUrl: 'http://www.someTarget.com') { sh 'exit 1' }}";

  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  @Test
  public void build() throws Exception {
    successfulPluginSetup(SUCCESS_JENKINS_PAYLOAD);
  }

  @Test
  public void buildWithEnterprise() throws Exception {
    successfulPluginSetup(SUCCESS_JENKINS_ENTERPRISE_PAYLOAD);
  }

  @Test
  public void buildWithFailingBlockMustFail() throws Exception {
    StatusWrapperTestObj statusWrapperTestObj = setupStableEnvMockObj(FAIL_JENKINS_PAYLOAD_BAD_BLOCK);

    jenkins.assertBuildStatus(Result.FAILURE, jenkins.waitForCompletion(statusWrapperTestObj.getRun()));
    Mockito.verify(statusWrapperTestObj.getRepo(), Mockito.times(1)).createCommitStatus(anyString(), Mockito.eq(GHCommitState.PENDING), anyString(), anyString(), anyString());
    Mockito.verify(statusWrapperTestObj.getRepo(), Mockito.times(1)).createCommitStatus(anyString(), Mockito.eq(GHCommitState.FAILURE), anyString(), anyString(), anyString());
    jenkins.assertLogContains("exit 1", statusWrapperTestObj.getRun());
  }


  private StatusWrapperTestObj successfulPluginSetup(String jobDefinition) throws Exception {
    StatusWrapperTestObj statusWrapperTestObj = setupStableEnvMockObj(jobDefinition);

    jenkins.assertBuildStatus(Result.SUCCESS, jenkins.waitForCompletion(statusWrapperTestObj.getRun()));
    Mockito.verify(statusWrapperTestObj.getRepo(), Mockito.times(1)).createCommitStatus(anyString(), Mockito.eq(GHCommitState.PENDING), anyString(), anyString(), anyString());
    Mockito.verify(statusWrapperTestObj.getRepo(), Mockito.times(1)).createCommitStatus(anyString(), Mockito.eq(GHCommitState.SUCCESS), anyString(), anyString(), anyString());
    jenkins.assertLogContains(SUCCESSFUL_LOG_MSG, statusWrapperTestObj.getRun());
    return statusWrapperTestObj;
  }

  private StatusWrapperTestObj setupStableEnvMockObj(String jobDefinition) throws Exception{
    StatusWrapperTestObj statusWrapperTestObj = new StatusWrapperTestObj().invoke();
    GitHubBuilder ghb = statusWrapperTestObj.getGhb();
    GitHub gh = statusWrapperTestObj.getGh();
    GHUser user = statusWrapperTestObj.getUser();
    GHCommit commit = statusWrapperTestObj.getCommit();
    GHRepository repo = statusWrapperTestObj.getRepo();

    PowerMockito.when((repo.getCommit(anyString()))).thenReturn(commit);
    PowerMockito.when((repo.createCommitStatus(anyString(), any(GHCommitState.class), anyString(), anyString(), anyString()))).thenReturn(null);

    PowerMockito.when(user.getRepository(anyString())).thenReturn(repo);

    PowerMockito.when(gh.isCredentialValid()).thenReturn(true);
    PowerMockito.when(gh.getUser(anyString())).thenReturn(user);

    PowerMockito.when(ghb.withProxy(Matchers.<Proxy>anyObject())).thenReturn(ghb);
    PowerMockito.when(ghb.withOAuthToken(anyString(), anyString())).thenReturn(ghb);
    PowerMockito.when(ghb.withEndpoint(anyString())).thenReturn(ghb);
    PowerMockito.when(ghb.build()).thenReturn(gh);

    PowerMockito.whenNew(GitHubBuilder.class).withNoArguments().thenReturn(ghb);

    Credentials dummy = new DummyCredentials(CredentialsScope.GLOBAL, "user", "psw");
    SystemCredentialsProvider.getInstance().getCredentials().add(dummy);

    WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");
    p.setDefinition(new CpsFlowDefinition(
        jobDefinition, true));

    WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
    statusWrapperTestObj.setRun(b1);
    return statusWrapperTestObj;
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
