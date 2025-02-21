package org.jenkinsci.plugins.gitserver.ssh;

import hudson.AbortException;
import java.io.IOException;
import org.eclipse.jgit.transport.ReceivePack;
import org.jenkinsci.main.modules.sshd.SshCommandFactory.CommandLine;
import org.jenkinsci.plugins.gitserver.RepositoryResolver;

/**
 * Implements "git-receive-pack" in Jenkins SSH that receives uploaded commits from clients.
 *
 * @author Kohsuke Kawaguchi
 */
public class ReceivePackCommand extends AbstractGitCommand {
    public ReceivePackCommand(CommandLine cmdLine) {
        super(cmdLine);
    }

    @Override
    protected int doRun() throws IOException, InterruptedException {
        for (RepositoryResolver rr : RepositoryResolver.all()) {
            ReceivePack rp = rr.createReceivePack(repoName);
            if (rp != null) {
                rp.receive(getInputStream(), getOutputStream(), getErrorStream());
                return 0;
            }
        }

        throw new AbortException("No such repository exists:" + repoName);
    }
}
