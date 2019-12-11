Git Server Plugin
=================

This plugin wraps the server-side functionality of JGit so that other plugins can easily expose
Git repositories from Jenkins via its SSH transport and HTTP in a collaborative fashion.
This plugin is a library plugin, in the sense that it doesn't have any user visible feature on its own.

## Developer Guide

See [Git userContent plugin](https://plugins.jenkins.io/git-userContent) as a nice and short example of how to take advantages of this feature.

In terms of code, there are two classes that plugins like `git-userContent-plugin` should be interested in.

One is
[`HttpGitRepository`](https://github.com/jenkinsci/git-server-plugin/blob/master/src/main/java/org/jenkinsci/plugins/gitserver/HttpGitRepository.java),
which represents Git repository access via HTTP.
Typically you have some directory inside `$JENKINS_HOME` that houses the repository, then you subtype `GitHttpRepository` and override abstract methods to fill in the missing details.
[`FileBackedHttpGitRepository`](https://github.com/jenkinsci/git-server-plugin/blob/master/src/main/java/org/jenkinsci/plugins/gitserver/FileBackedHttpGitRepository.java)
is a convenient default implementation that simplifies this further.
[GitUserContentRepository](https://github.com/jenkinsci/git-userContent-plugin/blob/master/src/main/java/org/jenkinsci/plugins/gitUserContent/GitUserContentRepository.java)
in `git-userContent-plugin` is an example of using this class. This use
also implements
[RootAction](https://wiki.jenkins-ci.org/display/JENKINS/Action+and+its+family+of+subtypes#Actionanditsfamilyofsubtypes-RootAction)
to bind this repository at `http://server/jenkins/userContent.git`, and
This combination is fairly common.

The other class of interest is [`RepositoryResolver`](https://github.com/jenkinsci/git-server-plugin/blob/master/src/main/java/org/jenkinsci/plugins/gitserver/RepositoryResolver.java).
Git server plugin adds necessary [Jenkins SSH](https://wiki.jenkins-ci.org/display/JENKINS/Jenkins+SSH) CLI hook for exposing Git repositories over SSH.
The only missing link here is that when the client runs `git clone ssh://server/foo/bar/zot.git`,
we need to figure out what repositories on the server corresponds to `/foo/bar/zot.git`, and that's what the `RepositoryResolver` extension point does.
[The sample implementation](https://github.com/jenkinsci/git-userContent-plugin/blob/master/src/main/java/org/jenkinsci/plugins/gitUserContent/GitUserContentRepositorySSHAccess.java) in `git-userContent-plugin` will be hopefully self-explanatory.
In this case, `GitUserContentRepository` is a singleton (because it's `RootAction`), so we inject that and basically just delegate the calls to it.

## Reporting issues

Use [Jenkins Bugtracker](https://issues.jenkins-ci.org/) to report issues or feature requests
(project = `JENKINS`, component = `git-server-plugin`).
