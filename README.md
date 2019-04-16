# Pipeline Git Status Wrapper Plugin
[![Build Status](https://ci.jenkins.io/job/Plugins/job/pipeline-gitstatuswrapper-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/pipeline-gitstatuswrapper-plugin/job/master/)

This plugin lets you wrap a block of commands with `gitStatusWrapper`, which handles updating GitHub statuses automatically!

✅ gitStatusWrapper makes your life easier! ✅
```
stage('EZPZ Updates') {
    steps {
        gitStatusWrapper(credentialsId: 'github-token', gitHubContext: 'Status', description: 'Validating') {
            sh "./do_stuff.sh"
        }
    }
}
```

💀🚫 Stop calling githubNotify 3+ times 🚫💀
```
stage('Annoying status updates') {
    script {
      try {
        githubNotify(credentialsId: 'github-token', context: 'Status', description: 'Validating', status: 'PENDING')
        sh "./do_stuff.sh"
        githubNotify(credentialsId: 'github-token', context: 'Status', description: 'Validating', status: 'SUCCESS')
      }
      catch (Exception e) {
        githubNotify(credentialsId: 'github-token', gitHubContext: 'Status', description: 'Validating', status: 'FAILURE')
      }
    }
}
```


The available parameters are:

| Parameter       | Description  |
| -------------   |:-------------|
| _credentialsId_ | The id of the github's credentials to use, must be of type UsernameAndPassword and contain the password or a personal access token. |
| _description_   | A description that will appear at the notification |
| _gitHubContext_ | The status context. GitHub uses the context to differentiate statuses |
| _sha_           | The sha that identifies the commit to set the status on |
| _repo_          | The repo that owns the commit we want to set the status on |
| _account_       | The account that owns the repository |
| _gitApiUrl_     | GitHub Enterprise instance API URL |
| _targetUrl_     | The targetUrl for the notification|


# Inferring parameter values and defaults

## Inferring
Instead of specify all your parameters, this step will try to infer some of them if they
are not provided. The parameters that can be inferred are:

| Parameter       |
| -------------   |
| _credentialsId_ |
| _sha_           |
| _repo_          |
| _account_       |

*Note that infer will only work if you have Git Build Data. If you find problems when inferring, specify the
required data explicitly. (You can access this data on your Jenkinsfile by using the appropriate env variables)*

## Defaults
The plugin will default some parameters as a convenience. The following are defaults:

| Parameter       | Default |
| -------------   |:--------|
| _gitApiUrl_     | `https://api.github.com` |
| _gitHubContext_ | "gitStatusWrapper" |
| _targetUrl_     | The jenkins project build URL |
| _description_   | "" |

# Examples

## Explicit settings
Jenkinsfile:
```
pipeline {
    agent any
    stages {
        stage('Run tests') {
            steps {
                gitStatusWrapper(credentialsId: 'github-token', description: 'Running Tests', gitHubContext: 'jenkins/appTests', 
                sha: 'ffg2ab', repo: 'my-repo', account: 'myaccount', gitApiUrl: 'https://[github.mycorp.com]/api/', 
                targetUrl: 'https://[myTestingSite].me') {
                    sh "./get_test_data.sh"
                    sh "./run_my_tests.sh"
                }
            }
        }
    }
}
```
Output:
```
...
[Pipeline] gitStatusWrapper
[GitStatusWrapper] - Setting PENDING status for jenkins/appTests on commit ffg2ab130985981ac0735f9789e19750f7200bd6
[Pipeline] {
[Pipeline] sh
+ ./get_test_data.sh
success
[Pipeline] sh
+ ./run_my_tests.sh
ok
ok
ok
[Pipeline] }
[GitStatusWrapper] - Setting SUCCESS status for jenkins/appTests on commit ffg2ab130985981ac0735f9789e19750f7200bd6
...
```


## Inferring properties and default values

Pipeline job with `Jenkinsfile from SCM`
```
pipeline {
    agent any
    stages {
        stage('Run tests') {
            steps {
                gitStatusWrapper(credentialsId: 'github-token') {
                    sh "./get_test_data.sh"
                    sh "./run_my_tests.sh"
                }
            }
        }
    }
}
```
Output:
```
...
[Pipeline] gitStatusWrapper
[GitStatusWrapper] - Setting PENDING status for gitStatusWrapper on commit ffg2ab130985981ac0735f9789e19750f7200bd6
[Pipeline] {
[Pipeline] sh
+ ./get_test_data.sh
success
[Pipeline] sh
+ ./run_my_tests.sh
ok
ok
ok
[Pipeline] }
[GitStatusWrapper] - Setting SUCCESS status for gitStatusWrapper on commit ffg2ab130985981ac0735f9789e19750f7200bd6
...
```

## Example of failure
Pipeline job with `Jenkinsfile from SCM`
```
pipeline {
    agent any
    stages {
        stage('Run tests') {
            steps {
                gitStatusWrapper(credentialsId: 'github-token') {
                    sh "./get_test_data.sh"
                    sh "./run_my_tests.sh"
                }
            }
        }
    }
}
```
Output:
```
...
[Pipeline] gitStatusWrapper
[GitStatusWrapper] - Setting PENDING status for gitStatusWrapper on commit ffg2ab130985981ac0735f9789e19750f7200bd6
[Pipeline] {
[Pipeline] sh
+ ./get_test_data.sh
success
[Pipeline] sh
+ ./run_my_tests.sh
ok
ok
! failure com.project.TestCalcs.java !
[Pipeline] }
[GitStatusWrapper] - Setting Failure status for gitStatusWrapper on commit ffg2ab130985981ac0735f9789e19750f7200bd6
...
```

# No pipeline? No Problem
This plugin also includes a builder plugin, so you can wrap your freestyle projects with the same goodness as the pipeline version.

<img src="src/main/webapp/img/builder_example.png"/>

## Users

Use the plugin? Let us know to get your logo here!

<div>
    <p>---------Created and Used by---------</p>
    <a href="http://opensource.intuit.com/"><img width="225" height="110" src="src/main/webapp/img/Intuit_user.png"/></a>
    <p>---------------------------------------</p>
</div>
