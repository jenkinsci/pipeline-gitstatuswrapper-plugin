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
package org.jenkinsci.plugins.gitstatuswrapper.builder;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.VariableResolver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.jenkinsci.plugins.gitstatuswrapper.Messages;
import org.jenkinsci.plugins.gitstatuswrapper.github.GitHubHelper;
import org.jenkinsci.plugins.gitstatuswrapper.jenkins.JenkinsHelpers;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

public class GitStatusWrapperBuilder extends Builder {

  private AbstractBuild<?, ?> build;
  private BuildListener listener;
  private GitStatusWrapperBuilder statusWrapperData;

  public List<BuildStep> getBuildSteps() {
    if (buildSteps == null) {
      return new ArrayList<>();
    }

    return buildSteps;
  }

  private List<BuildStep> buildSteps;

  public String getGitHubContext() {
    return gitHubContext;
  }

  @DataBoundSetter
  public void setGitHubContext(String gitHubContext) {
    this.gitHubContext = gitHubContext;
  }

  public String getAccount() {
    return account;
  }

  @DataBoundSetter
  public void setAccount(String account) {
    this.account = account;
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

  public String getScript() {
    return script;
  }

  public void setScript(String script) {
    this.script = script;
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

  @DataBoundSetter
  public void setGitApiUrl(String gitApiUrl) {
    this.gitApiUrl = gitApiUrl;
  }

  @DataBoundSetter
  public void setCredentialsId(String credentialsId) {
    this.credentialsId = Util.fixEmpty(credentialsId);
  }

  public String getGitApiUrl() {
    return this.gitApiUrl;
  }

  public String getCredentialsId() {
    return this.credentialsId;
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

  /**
   * A string label to differentiate the send status from the status of other systems.
   */
  private String gitHubContext = "";
  /**
   * The GitHub's account that owns the repo to notify
   */
  private String account;
  /**
   * The repository that owns the commit to notify
   */
  private String repo;
  /**
   * The commit to notify unique sha1, used as commit identifier
   */
  private String sha;
  /**
   * Script to run for this status check
   */
  private String script;
  /**
   * A short description of the status to send
   */
  private String description;
  /**
   * The target URL to associate with the sendstatus.
   *
   * This URL will be linked from the GitHub UI to allow users to easily see the 'source' of the
   * Status.
   */
  private String targetUrl = "";
  /**
   * The optional GitHub enterprise instance api url endpoint.
   *
   * Used when you are using your own GitHub enterprise instance instead of the default GitHub SaaS
   * (http://github.com)
   */
  private String gitApiUrl;
  /**
   * The id of the jenkins stored credentials to use to connect to GitHub, must identify a
   * UsernamePassword credential
   */
  private String credentialsId;
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

  @DataBoundConstructor
  public GitStatusWrapperBuilder(List<BuildStep> buildSteps) {
    if (buildSteps != null) {
      this.buildSteps = buildSteps;
    }
  }

  private String resolveEnvOrDefault(String toResolve, String defaultString, EnvVars env,
      VariableResolver<String> vr) {
    String resolved = defaultString;

    if (!StringUtils.isEmpty(toResolve)) {
      resolved = Util.replaceMacro(toResolve, vr);
      resolved = env.expand(resolved);
    }

    return resolved;
  }

  @Override
  public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
      throws InterruptedException, IOException {
    this.build = build;
    this.listener = listener;

    VariableResolver<String> vr = build.getBuildVariableResolver();
    EnvVars env = build.getEnvironment(listener);

    statusWrapperData = new GitStatusWrapperBuilder(this.buildSteps);

    statusWrapperData.setGitHubContext(
        resolveEnvOrDefault(this.gitHubContext, Messages.GitStatusWrapper_FUNCTION_NAME(), env,
            vr));
    statusWrapperData.setAccount(resolveEnvOrDefault(this.account, "", env, vr));
    if (statusWrapperData.getAccount().isEmpty()) {
      statusWrapperData.setAccount(GitHubHelper.inferBuildAccount(build));
    }

    statusWrapperData.setRepo(resolveEnvOrDefault(this.repo, "", env, vr));
    if (statusWrapperData.getRepo().isEmpty()) {
      statusWrapperData.setRepo(GitHubHelper.inferBuildRepo(build));
    }

    statusWrapperData.setSha(resolveEnvOrDefault(this.sha, "", env, vr));
    if (statusWrapperData.getSha().isEmpty()) {
      statusWrapperData.setSha(GitHubHelper.inferBuildCommitSHA1(build));
    }

    statusWrapperData.setCredentialsId(resolveEnvOrDefault(this.credentialsId, "", env, vr));
    if (statusWrapperData.getCredentialsId().isEmpty()) {
      statusWrapperData.setCredentialsId(GitHubHelper.inferBuildCredentialsId(build));
    }

    statusWrapperData.setDescription(resolveEnvOrDefault(this.description, "", env, vr));
    statusWrapperData.setTargetUrl(
        resolveEnvOrDefault(this.targetUrl, DisplayURLProvider.get().getRunURL(build), env, vr));
    statusWrapperData.setGitApiUrl(
        resolveEnvOrDefault(this.gitApiUrl, GitHubHelper.DEFAULT_GITHUB_API_URL, env, vr));
    statusWrapperData.setSuccessDescription(
        resolveEnvOrDefault(this.successDescription, this.description, env, vr));
    statusWrapperData.setFailureDescription(
        resolveEnvOrDefault(this.failureDescription, this.description, env, vr));

    GHRepository repository = GitHubHelper
        .getRepoIfValid(statusWrapperData.credentialsId, statusWrapperData.gitApiUrl,
            JenkinsHelpers.getProxy(statusWrapperData.gitApiUrl), statusWrapperData.account,
            statusWrapperData.repo, build.getParent());

    GHCommit commit = repository.getCommit(statusWrapperData.sha);

    setStatus(listener, repository, commit, GHCommitState.PENDING);

    boolean everyStepSuccessful = true;

    try {
      for (BuildStep buildStep : buildSteps) {
        if (!buildStep.perform(build, launcher, listener)) {
          everyStepSuccessful = false;
          break;
        }
      }
    } catch (IOException | InterruptedException ioe) {
      setStatus(listener, repository, commit, GHCommitState.FAILURE);
      throw ioe;
    }

    if (everyStepSuccessful) {
      setStatus(listener, repository, commit, GHCommitState.SUCCESS);
    } else {
      setStatus(listener, repository, commit, GHCommitState.FAILURE);
    }
    return everyStepSuccessful;
  }

  private void setStatus(BuildListener listener, GHRepository repository, GHCommit commit,
      GHCommitState state)
      throws IOException {
    listener.getLogger().println(
        String.format(Messages.GitStatusWrapper_PRIMARY_LOG_TEMPLATE(),
            state,
            statusWrapperData.getGitHubContext(), commit.getSHA1())
    );

    String description = getDescriptionForState(state);

    repository.createCommitStatus(commit.getSHA1(),
        state, statusWrapperData.getTargetUrl(), description,
        statusWrapperData.getGitHubContext());
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
      return this.getDescription();
    }

    String description = state == GHCommitState.SUCCESS ? statusWrapperData.getSuccessDescription()
        : statusWrapperData.getFailureDescription();

    String result = description;
    if (description.startsWith("/") && description.endsWith("/")) {
      //Regex pattern found, resolve
      String descRegex = description.substring(1, description.length() - 1);
      final String buildLog = JenkinsHelpers.getBuildLogOutput(build);
      Matcher matcher = Pattern.compile(descRegex, Pattern.MULTILINE).matcher(buildLog);
      if (matcher.find()) {
        result = matcher.group(1);
      } else {
        listener.getLogger().println(
            String.format(Messages.GitStatusWrapper_FAIL_TO_MATCH_REGEX(), descRegex));
      }
    }

    return result;
  }

  /**
   * Jenkins defines a method {@link Builder#getDescriptor()}, which returns the corresponding
   * {@link hudson.model.Descriptor} object.
   *
   * Since we know that it's actually {@link DescriptorImpl}, override the method and give a better
   * return type, so that we can access {@link DescriptorImpl} methods more easily.
   *
   * This is not necessary, but just a coding style preference.
   *
   * @return descriptor for this builder
   */
  @Override
  public final DescriptorImpl getDescriptor() {
    // see Descriptor javadoc for more about what a descriptor is.
    return (DescriptorImpl) super.getDescriptor();
  }

  /**
   * Descriptor for {@link GitStatusWrapperBuilder}. The class is marked as public so that it can be
   * accessed from views.
   */
  @Extension
  public static final class DescriptorImpl
      extends BuildStepDescriptor<Builder> {

    public DescriptorImpl() {
      super(GitStatusWrapperBuilder.class);
      load();
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      return true;
    }

    @Override
    public String getDisplayName() {
      return Messages.GitStatusWrapper_DISPLAY_NAME();
    }

    public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project) {
      return JenkinsHelpers.fillCredentialsIdItems(project);
    }

    @POST
    public FormValidation doTestConnection(
        @QueryParameter("credentialsId") final String credentialsId,
        @QueryParameter("gitApiUrl") final String gitApiUrl, @AncestorInPath Item context) {
      return GitHubHelper.testApiConnection(credentialsId, gitApiUrl, context);
    }
  }
}
