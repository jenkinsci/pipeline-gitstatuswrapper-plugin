/*
MIT License

Copyright (c) 2019 Zachary Sherwin

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package org.jenkinsci.plugins.gitstatuswrapper.pipeline;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import jenkins.YesNoMaybe;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.jenkinsci.plugins.gitstatuswrapper.Messages;
import org.jenkinsci.plugins.gitstatuswrapper.github.GitHubHelper;
import org.jenkinsci.plugins.gitstatuswrapper.jenkins.JenkinsHelpers;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;


public final class GitStatusWrapperStep extends Step {

  private EnvVars env;

  /**
   * A string label to differentiate the send status from the status of other systems.
   */
  private String gitHubContext = "";
  /**
   * The optional GitHub enterprise instance api url endpoint.
   *
   * Used when you are using your own GitHub enterprise instance instead of the default GitHub SaaS
   * (http://github.com)
   */
  private String gitApiUrl = "";
  /**
   * The id of the jenkins stored credentials to use to connect to GitHub, must identify a
   * UsernamePassword credential
   */
  private String credentialsId = "";
  /**
   * The GitHub's account that owns the repo to notify
   */
  private String account = "";
  /**
   * The repository that owns the commit to notify
   */
  private String repo = "";
  /**
   * The commit to notify unique sha1, used as commit identifier
   */
  private String sha = "";
  /**
   * A short description of the status to send
   */
  private String description = "";
  /**
   * The target URL to associate with the sendstatus.
   *
   * This URL will be linked from the GitHub UI to allow users to easily see the 'source' of the
   * Status.
   */
  private String targetUrl = "";

  /**
   * Optional variables to set the status description to upon Success
   *
   * This variable can also be a regex pattern if the string is wrapped in '/', ex: /(.*)/
   *
   * Defaults to description if empty
   */
  private String successDescription = "";
  /**
   * Optional variables to set the status description to upon Failure
   *
   * This variable can also be a regex pattern if the string is wrapped in '/', ex: /(.*)/
   *
   * Defaults to description if empty
   */
  private String failureDescription = "";


  public String getGitHubContext() {
    if (StringUtils.isEmpty(gitHubContext)) {
      this.gitHubContext = "gitStatusWrapper";
    }
    return gitHubContext;
  }

  @DataBoundSetter
  public void setGitHubContext(String gitHubContext) {
    this.gitHubContext = gitHubContext;
  }

  public String getGitApiUrl() {
    if (StringUtils.isEmpty(gitApiUrl)) {
      this.gitApiUrl = GitHubHelper.DEFAULT_GITHUB_API_URL;
    }
    return gitApiUrl;
  }

  @DataBoundSetter
  public void setGitApiUrl(String gitApiUrl) {
    this.gitApiUrl = gitApiUrl;
  }

  public String getCredentialsId() {
    return credentialsId;
  }

  @DataBoundSetter
  public void setCredentialsId(String credentialsId) {
    this.credentialsId = credentialsId;
  }

  public String getRepo() {
    return repo;
  }

  @DataBoundSetter
  public void setRepo(String repo) {
    this.repo = repo;
  }

  public String getSha() {
    return sha;
  }

  @DataBoundSetter
  public void setSha(String sha) {
    this.sha = sha;
  }

  public String getDescription() {
    return description;
  }

  @DataBoundSetter
  public void setDescription(String description) {
    this.description = description;
  }

  public String getTargetUrl() {
    return targetUrl;
  }

  @DataBoundSetter
  public void setTargetUrl(String targetUrl) {
    this.targetUrl = targetUrl;
  }

  public String getAccount() {
    return this.account;
  }

  @DataBoundSetter
  public void setAccount(String account) {
    this.account = account;
  }

  public String getSuccessDescription() {
    return StringUtils.isEmpty(successDescription) ? this.getDescription() : successDescription;
  }

  @DataBoundSetter
  public void setSuccessDescription(String successDescription) {
    this.successDescription = successDescription;
  }

  public String getFailureDescription() {
    return StringUtils.isEmpty(failureDescription) ? this.getDescription() : failureDescription;
  }

  @DataBoundSetter
  public void setFailureDescription(String failureDescription) {
    this.failureDescription = failureDescription;
  }

  @DataBoundConstructor
  public GitStatusWrapperStep() {
  }

  @Override
  public StepExecution start(StepContext context) throws Exception {
    env = context.get(EnvVars.class);
    return new ExecutionImpl(context, this);
  }

  @Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
  public static class DescriptorImpl extends StepDescriptor {

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return Collections.singleton(TaskListener.class);
    }

    @Override
    public String getFunctionName() {
      return Messages.GitStatusWrapper_FUNCTION_NAME();
    }

    @Override
    public String getDisplayName() {
      return Messages.GitStatusWrapper_DISPLAY_NAME();
    }

    @Override
    public boolean takesImplicitBlockArgument() {
      return true;
    }

    public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project) {
      return JenkinsHelpers.fillCredentialsIdItems(project);
    }

    @POST
    public FormValidation doTestConnection(
        @QueryParameter("credentialsId") final String credentialsId,
        @QueryParameter("gitApiUrl") String gitApiUrl, @AncestorInPath Item context) {
      return GitHubHelper.testApiConnection(credentialsId, gitApiUrl, context);
    }
  }

  public static final class ExecutionImpl extends StepExecution {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(ExecutionImpl.class.getName());
    public static final String UNABLE_TO_INFER_COMMIT = Messages
        .GitHubHelper_UNABLE_TO_INFER_COMMIT();

    private final transient GitStatusWrapperStep step;
    private transient BodyExecution body;
    private transient Run run;

    public transient GHRepository repository;
    public transient GHCommit commit;

    public transient TaskListener listener;
    public transient FilePath workspace;

    protected ExecutionImpl(@Nonnull StepContext context, GitStatusWrapperStep step) {
      super(context);
      this.step = step;

    }

    @Override
    public boolean start() throws Exception {
      run = getContext().get(Run.class);
      listener = getContext().get(TaskListener.class);
      workspace = getContext().get(FilePath.class);

      this.step.setSha(this.getSha());
      this.step.setRepo(this.getRepo());
      this.step.setCredentialsId(this.getCredentialsId());
      this.step.setAccount(this.getAccount());
      this.step.setTargetUrl(this.getTargetUrl());

      this.repository = GitHubHelper
          .getRepoIfValid(this.step.getCredentialsId(), this.step.getGitApiUrl(),
              JenkinsHelpers.getProxy(step.getGitApiUrl()), this.step.getAccount(),
              this.step.getRepo(),
              run.getParent());

      this.commit = repository.getCommit(this.step.getSha());

      this.setStatus(GHCommitState.PENDING);

      EnvVars envOverride = new EnvVars();
      EnvironmentExpander envEx = EnvironmentExpander
          .merge(getContext().get(EnvironmentExpander.class),
              new ExpanderImpl(envOverride));
      body = getContext().newBodyInvoker().withContext(envEx).withCallback(new Callback(this))
          .start();

      return false;
    }

    public void setStatus(GHCommitState state)
        throws IOException {
      listener.getLogger().println(
          String.format(Messages.GitStatusWrapper_PRIMARY_LOG_TEMPLATE(),
              state.toString(),
              this.step.getGitHubContext(), commit.getSHA1())
      );
      String description = getDescriptionForState(state);

      this.repository.createCommitStatus(commit.getSHA1(),
          state, this.step.getTargetUrl(), description,
          this.step.getGitHubContext());
    }

    /***
     * get the description for the git status
     * resolve regex if it was set
     *
     * @param state Pending/Success/Failure
     * @return
     * @throws IOException
     */
    private String getDescriptionForState(final GHCommitState state)
        throws IOException {
      if (state == GHCommitState.PENDING) {
        return this.step.getDescription();
      }

      String description = state == GHCommitState.SUCCESS ? this.step.getSuccessDescription()
          : this.step.getFailureDescription();

      String result = description;
      if (description.startsWith("/") && description.endsWith("/")) {
        //Regex pattern found, resolve
        String descRegex = description.substring(1, description.length() - 1);
        final String buildLog = JenkinsHelpers.getBuildLogOutput(run);
        Matcher matcher = Pattern.compile(descRegex, Pattern.MULTILINE).matcher(buildLog);
        if (matcher.find()) {
          result = matcher.group(1);
        } else {
          listener.getLogger().println(
              String.format(Messages.GitStatusWrapper_FAIL_TO_MATCH_REGEX(), descRegex)
          );
        }
      }

      return result;
    }


    public String getCredentialsId() {
      if (StringUtils.isEmpty(step.getCredentialsId())) {
        return GitHubHelper.inferBuildCredentialsId(run);
      } else {
        return step.getCredentialsId();
      }
    }

    public String getRepo() throws IOException {
      if (StringUtils.isEmpty(step.getRepo())) {
        return GitHubHelper.inferBuildRepo(run);
      } else {
        return step.getRepo();
      }
    }

    public String getSha() {
      if (StringUtils.isEmpty(step.getSha())) {
        try {
          return GitHubHelper.inferBuildCommitSHA1(run);
        } catch (Exception e) {
          if (!StringUtils.isEmpty(step.env.get(GitHubHelper.GIT_SCM_COMMIT_ENV_NAME))) {
            return step.env.get(GitHubHelper.GIT_SCM_COMMIT_ENV_NAME);
          } else {
            throw new IllegalArgumentException(UNABLE_TO_INFER_COMMIT);
          }
        }
      } else {
        return step.getSha();
      }
    }

    public String getTargetUrl() {
      if (StringUtils.isEmpty(step.getTargetUrl())) {
        return DisplayURLProvider.get().getRunURL(run);
      } else {
        return step.getTargetUrl();
      }
    }

    public String getAccount() throws IOException {
      if (StringUtils.isEmpty(step.getAccount())) {
        return GitHubHelper.inferBuildAccount(run);
      } else {
        return step.getAccount();
      }
    }

    /**
     * Callback to cleanup tmp script after finishing the job
     */
    private static class Callback extends BodyExecutionCallback {

      ExecutionImpl execution;

      public Callback(ExecutionImpl execution) {
        this.execution = execution;
      }

      @Override
      public final void onSuccess(StepContext context, Object result) {
        try {
          execution.setStatus(GHCommitState.SUCCESS);
        } catch (Exception x) {
          context.onFailure(x);
          return;
        }
        context.onSuccess(result);
      }

      @Override
      public void onFailure(StepContext context, Throwable t) {
        try {
          execution.setStatus(GHCommitState.FAILURE);
        } catch (Exception x) {
          t.addSuppressed(x);
        }
        context.onFailure(t);
      }

      private static final long serialVersionUID = 1L;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
      if (body != null) {
        body.cancel(cause);
      }
    }

    /**
     * Takes care of overriding the environment with our defined overrides
     */
    private static final class ExpanderImpl extends EnvironmentExpander {

      private static final long serialVersionUID = 1;
      private final Map<String, String> overrides;

      private ExpanderImpl(EnvVars overrides) {
        LOGGER.log(Level.FINE, "Overrides: " + overrides.toString());
        this.overrides = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
          this.overrides.put(entry.getKey(), entry.getValue());
        }
      }

      @Override
      public void expand(EnvVars env) throws IOException, InterruptedException {
        env.overrideAll(overrides);
      }
    }
  }

}
