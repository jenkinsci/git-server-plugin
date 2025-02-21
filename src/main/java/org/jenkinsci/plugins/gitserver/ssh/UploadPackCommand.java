package org.jenkinsci.plugins.gitserver.ssh;

import hudson.AbortException;
import org.eclipse.jgit.transport.UploadPack;
import org.jenkinsci.main.modules.sshd.SshCommandFactory.CommandLine;
import org.jenkinsci.plugins.gitserver.RepositoryResolver;

/**
 * Implements "git-upload-pack" in Jenkins SSH that lets clients
 * download commits from us.
 *
 * @author Kohsuke Kawaguchi
 */
public class UploadPackCommand extends AbstractGitCommand {
    public UploadPackCommand(CommandLine cmdLine) {
        super(cmdLine);
    }

    @Override
    protected int doRun() throws Exception {
        for (RepositoryResolver rr : RepositoryResolver.all()) {
            UploadPack up = rr.createUploadPack(repoName);
            if (up != null) {
                up.upload(getInputStream(), getOutputStream(), getErrorStream());
                return 0;
            }
        }

        throw new AbortException("No such repository exists:" + repoName);
    }
}
