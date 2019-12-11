## Changelog Archive

This page contains changelof for versions 1.7 and older.
See [GitHub releases](https://github.com/jenkinsci/git-server-plugin/releases) for newer versions.

### 1.8 and newer

See [GitHub releases](https://github.com/jenkinsci/git-server-plugin/releases).

### 1.7

Release date: (July 12, 2016)

-   [JENKINS-35510](https://issues.jenkins-ci.org/browse/JENKINS-35510)
    Update to the new Parent POM

### 1.6

Release date: (Dec 18 2014)

-   git clone fails over HTTP, although SSH works
    ([JENKINS-25212](https://issues.jenkins-ci.org/browse/JENKINS-25212))

### 1.5

Release date: (Nov 14 2014)

-   git-push via HTTP will work correctly even when CSRF protection is
    enabled. No more "No valid crumb was included in request" error.

### 1.4

Release date: (Oct 14 2014)

-   De-couple jgit dependencies from [Git Client
    Plugin](https://wiki.jenkins.io/display/JENKINS/Git+Client+Plugin)
    to avoid LinkageError caused by jgit version incompatibility.

### 1.3

Release date: (Feb 10 2014)

-   Another JGit incompatibility with Git Client
    ([JENKINS-21163](https://issues.jenkins-ci.org/browse/JENKINS-21163))

### 1.2

Release date: (Aug 09 2013)

-   JGit incompatibility with new Git Client plugin
    ([JENKINS-18973](https://issues.jenkins-ci.org/browse/JENKINS-18973))
