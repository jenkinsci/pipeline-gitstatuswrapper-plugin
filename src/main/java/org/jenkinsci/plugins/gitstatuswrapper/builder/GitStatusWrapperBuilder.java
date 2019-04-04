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

  @DataBoundConstructor
  public GitStatusWrapperBuilder(List<BuildStep> buildSteps) {
    if (buildSteps != null) {
      this.buildSteps = buildSteps;
    }
  }

  @Override
  public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
      throws InterruptedException, IOException {

    VariableResolver<String> vr = build.getBuildVariableResolver();
    EnvVars env = build.getEnvironment(listener);
    String ghContext, ghAccount, ghRepo, ghSHA, ghDescription, ghTargetURL, ghApiURL, ghCrentialsId;

    if (!StringUtils.isEmpty(this.gitHubContext)) {
      ghContext = Util.replaceMacro(this.gitHubContext, vr);
      ghContext = env.expand(ghContext);
    } else {
      ghContext = Messages.GitStatusWrapper_FUNCTION_NAME();
    }

    if (!StringUtils.isEmpty(this.account)) {
      ghAccount = Util.replaceMacro(this.account, vr);
      ghAccount = env.expand(ghAccount);
    } else {
      ghAccount = GitHubHelper.inferBuildAccount(build);
    }

    if (!StringUtils.isEmpty(this.repo)) {
      ghRepo = Util.replaceMacro(this.repo, vr);
      ghRepo = env.expand(ghRepo);
    } else {
      ghRepo = GitHubHelper.inferBuildRepo(build);
    }

    if (!StringUtils.isEmpty(this.sha)) {
      ghSHA = Util.replaceMacro(this.sha, vr);
      ghSHA = env.expand(ghSHA);
    } else {
      ghSHA = GitHubHelper.inferBuildCommitSHA1(build);
    }

    if (!StringUtils.isEmpty(this.description)) {
      ghDescription = Util.replaceMacro(this.description, vr);
      ghDescription = env.expand(ghDescription);
    } else {
      ghDescription = "";
    }

    if (!StringUtils.isEmpty(this.targetUrl)) {
      ghTargetURL = Util.replaceMacro(this.targetUrl, vr);
      ghTargetURL = env.expand(ghTargetURL);
    } else {
      ghTargetURL = DisplayURLProvider.get().getRunURL(build);
    }

    if (!StringUtils.isEmpty(this.gitApiUrl)) {
      ghApiURL = Util.replaceMacro(this.gitApiUrl, vr);
      ghApiURL = env.expand(ghApiURL);
    } else {
      ghApiURL = GitHubHelper.DEFAULT_GITHUB_API_URL;
    }

    if (!StringUtils.isEmpty(this.credentialsId)) {
      ghCrentialsId = Util.replaceMacro(this.credentialsId, vr);
      ghCrentialsId = env.expand(ghCrentialsId);
    } else {
      ghCrentialsId = GitHubHelper.inferBuildCredentialsId(build);
    }

    GHRepository repository = GitHubHelper
        .getRepoIfValid(ghCrentialsId, ghApiURL, JenkinsHelpers.getProxy(ghApiURL), ghAccount,
            ghRepo, build.getParent());

    GHCommit commit = GitHubHelper
        .getCommitIfValid(ghCrentialsId, ghApiURL, JenkinsHelpers.getProxy(ghApiURL), ghAccount,
            ghRepo, ghSHA, build.getParent());

    setStatus(listener, ghContext, ghDescription, ghTargetURL, repository, commit,
        GHCommitState.PENDING);

    boolean everyStepSuccessful = true;

    try {
      for (BuildStep buildStep : buildSteps) {
        if (!buildStep.perform(build, launcher, listener)) {
          everyStepSuccessful = false;
          break;
        }
      }
    } catch (IOException | InterruptedException ioe) {
      setStatus(listener, ghContext, ghDescription, ghTargetURL, repository, commit,
          GHCommitState.FAILURE);
      throw ioe;
    }

    if (everyStepSuccessful) {
      setStatus(listener, ghContext, ghDescription, ghTargetURL, repository, commit,
          GHCommitState.SUCCESS);
    } else {
      setStatus(listener, ghContext, ghDescription, ghTargetURL, repository, commit,
          GHCommitState.FAILURE);
    }
    return everyStepSuccessful;
  }

  private void setStatus(BuildListener listener, String ghContext, String ghDescription,
      String ghTargetURL, GHRepository repository, GHCommit commit, GHCommitState failure)
      throws IOException {
    listener.getLogger().println(
        String.format(Messages.GitStatusWrapper_PRIMARY_LOG_TEMPLATE(),
            failure,
            ghContext, commit.getSHA1())
    );
    repository.createCommitStatus(commit.getSHA1(),
        failure, ghTargetURL, ghDescription,
        ghContext);
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
