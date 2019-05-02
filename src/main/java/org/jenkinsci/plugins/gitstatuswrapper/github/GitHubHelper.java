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
package org.jenkinsci.plugins.gitstatuswrapper.github;

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.git.util.BuildData;
import hudson.util.FormValidation;
import java.io.IOException;
import java.net.Proxy;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import org.jenkinsci.plugins.github.util.BuildDataHelper;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.github_branch_source.PullRequestSCMRevision;
import org.jenkinsci.plugins.gitstatuswrapper.Messages;
import org.jenkinsci.plugins.gitstatuswrapper.credentials.CredentialsHelper;
import org.jenkinsci.plugins.gitstatuswrapper.jenkins.JenkinsHelpers;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

public class GitHubHelper {

  public static final String DEFAULT_GITHUB_API_URL = "https://api.github.com";

  public static final String CREDENTIALS_ID_NOT_EXISTS = Messages
      .GitHubHelper_CREDENTIALS_ID_NOT_EXISTS();
  public static final String NULL_CREDENTIALS_ID = Messages.GitHubHelper_NULL_CREDENTIALS_ID();
  public static final String CREDENTIALS_LOGIN_INVALID = Messages
      .GitHubHelper_CREDENTIALS_LOGIN_INVALID();
  public static final String INVALID_REPO = Messages.GitHubHelper_INVALID_REPO();
  public static final String INVALID_COMMIT = Messages.GitHubHelper_INVALID_COMMIT();

  public static final String UNABLE_TO_INFER_DATA = Messages.GitHubHelper_UNABLE_TO_INFER_DATA();
  public static final String UNABLE_TO_INFER_COMMIT = Messages
      .GitHubHelper_UNABLE_TO_INFER_COMMIT();
  public static final String UNABLE_TO_INFER_CREDENTIALS_ID = Messages
      .GitHubHelper_UNABLE_TO_INFER_CREDENTIALS_ID();

  public static final String GIT_SCM_COMMIT_ENV_NAME = "GIT_COMMIT";

  public static GitHub getGitHubIfValid(String credentialsId, @Nonnull String gitApiUrl,
      Proxy proxy, Item context) throws IOException {
    if (credentialsId == null || credentialsId.isEmpty()) {
      throw new IllegalArgumentException(NULL_CREDENTIALS_ID);
    }
    UsernamePasswordCredentials credentials = CredentialsHelper
        .getCredentials(UsernamePasswordCredentials.class, credentialsId, context);
    if (credentials == null) {
      throw new IllegalArgumentException(CREDENTIALS_ID_NOT_EXISTS);
    }
    GitHubBuilder githubBuilder = new GitHubBuilder();

    githubBuilder
        .withOAuthToken(credentials.getPassword().getPlainText(), credentials.getUsername());

    githubBuilder = githubBuilder.withProxy(proxy);
    githubBuilder = githubBuilder.withEndpoint(gitApiUrl);

    GitHub github = githubBuilder.build();

    if (github.isCredentialValid()) {
      return github;
    } else {
      throw new IllegalArgumentException(CREDENTIALS_LOGIN_INVALID);
    }
  }

  public static GHRepository getRepoIfValid(String credentialsId, String gitApiUrl, Proxy proxy,
      String account, String repo, Item context) throws IOException {
    GitHub github = getGitHubIfValid(credentialsId, gitApiUrl, proxy, context);
    GHRepository repository = github.getUser(account).getRepository(repo);
    if (repository == null) {
      throw new IllegalArgumentException(INVALID_REPO);
    }
    return repository;
  }

  public static GHCommit getCommitIfValid(String credentialsId, String gitApiUrl, Proxy proxy,
      String account, String repo, String sha, Item context) throws IOException {
    GHRepository repository = getRepoIfValid(credentialsId, gitApiUrl, proxy, account, repo,
        context);
    GHCommit commit = repository.getCommit(sha);
    if (commit == null) {
      throw new IllegalArgumentException(INVALID_COMMIT);
    }
    return commit;
  }

  public static String inferBuildRepo(Run<?, ?> run) throws IOException {
    return getRemoteData(run, 4).replace(".git", "");
  }

  public static String inferBuildAccount(Run<?, ?> run) throws IOException {
    return getRemoteData(run, 3);
  }

  private static String getRemoteData(Run<?, ?> run, Integer index) throws IOException {
    BuildData data = run.getAction(BuildData.class);
    if (data != null && data.getRemoteUrls() != null && !data.getRemoteUrls().isEmpty()) {
      String remoteUrl = data.remoteUrls.iterator().next();
      return remoteUrl.split("\\/")[index];
    }

    throw new IOException("Unable to infer git repo from build data");
  }


  public static String inferBuildCommitSHA1(Run<?, ?> run) throws IOException {
    SCMRevisionAction action = run.getAction(SCMRevisionAction.class);
    if (action != null) {
      SCMRevision revision = action.getRevision();
      if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl) {
        return ((AbstractGitSCMSource.SCMRevisionImpl) revision).getHash();
      } else if (revision instanceof PullRequestSCMRevision) {
        return ((PullRequestSCMRevision) revision).getPullHash();
      } else {
        throw new IllegalArgumentException(UNABLE_TO_INFER_COMMIT);
      }
    } else {
      try {
        return BuildDataHelper.getCommitSHA1(run).name();
      } catch (IOException e) {
        throw new IllegalArgumentException(UNABLE_TO_INFER_COMMIT, e);
      }
    }
  }

  public static String inferBuildCredentialsId(Run<?, ?> run) {
    try {
      String credentialsID = getSource(run).getCredentialsId();
      if (credentialsID != null) {
        return credentialsID;
      } else {
        throw new IllegalArgumentException(UNABLE_TO_INFER_CREDENTIALS_ID);
      }
    } catch (IllegalArgumentException e) {
      //TODO: Find a way to get the credentials used by CpsScmFlowDefinition
      throw e;
    }
  }

  private static GitHubSCMSource getSource(Run<?, ?> run) {
    ItemGroup parent = run.getParent().getParent();
    if (parent instanceof SCMSourceOwner) {
      SCMSourceOwner owner = (SCMSourceOwner) parent;
      for (SCMSource source : owner.getSCMSources()) {
        if (source instanceof GitHubSCMSource) {
          return ((GitHubSCMSource) source);
        }
      }
      throw new IllegalArgumentException(UNABLE_TO_INFER_DATA);
    } else {
      throw new IllegalArgumentException(UNABLE_TO_INFER_DATA);
    }
  }

  public static FormValidation testApiConnection(final String credentialsId, final String gitApiUrl,
      Item context) {
    Jenkins.getInstance().checkPermission(Job.CONFIGURE);
    try {
      GitHubHelper.getGitHubIfValid(credentialsId, gitApiUrl, JenkinsHelpers.getProxy(gitApiUrl),
          context);
      return FormValidation.ok("Success");
    } catch (Exception e) {
      return FormValidation.error(e.getMessage());
    }
  }
}
