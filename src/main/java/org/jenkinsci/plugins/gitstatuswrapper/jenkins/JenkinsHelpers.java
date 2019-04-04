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
package org.jenkinsci.plugins.gitstatuswrapper.jenkins;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.model.Item;
import hudson.model.Job;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import java.util.List;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import java.net.Proxy;

public class JenkinsHelpers {

  /**
   * Uses proxy if configured on pluginManager/advanced page
   *
   * @param host GitHub's hostname to build proxy to
   * @return proxy to use it in connector. Should not be null as it can lead to unexpected behaviour
   */
  @Nonnull
  public static Proxy getProxy(String host) {
    Jenkins jenkins = Jenkins.getActiveInstance();

    if (host == null || host.isEmpty()) {
      host = "https://api.github.com";
    }

    if (jenkins.proxy == null) {
      return Proxy.NO_PROXY;
    } else {
      return jenkins.proxy.createProxy(host);
    }
  }

  public static ListBoxModel fillCredentialsIdItems(Item project){
    Jenkins.get().checkPermission(Job.CONFIGURE);
    AbstractIdCredentialsListBoxModel result = new StandardListBoxModel();
    List<UsernamePasswordCredentials> credentialsList = CredentialsProvider
        .lookupCredentials(UsernamePasswordCredentials.class, project, ACL.SYSTEM);
    for (UsernamePasswordCredentials credential : credentialsList) {
      result = result.with((IdCredentials) credential);
    }
    return result;
  }


}
