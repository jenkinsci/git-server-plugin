package org.jenkinsci.plugins.gitserver.ssh;

import hudson.AbortException;
import hudson.model.User;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.sshd.server.Command;
import org.jenkinsci.main.modules.sshd.AsynchronousCommand;
import org.jenkinsci.main.modules.sshd.SshCommandFactory.CommandLine;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

/**
 * Implements the SSH {@link Command} for the server side git command.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class AbstractGitCommand extends AsynchronousCommand {
    @Argument(index=0, metaVar="REPO", required=true, usage="repository name")
    protected String repoName;

    AbstractGitCommand(CommandLine cmdLine) {
        super(cmdLine);
    }

    @Override
    protected final int run() throws Exception {
        // run this command in the context of the authenticated user.
        // this is a work around until we can rely on ssd module 1.3
        Authentication old = SecurityContextHolder.getContext().getAuthentication();
        if (Jenkins.getInstance().isUseSecurity())
            SecurityContextHolder.getContext().setAuthentication(User.get(getSession().getUsername()).impersonate());

        try {
            try {
                new CmdLineParser(this).parseArgument(getCmdLine().subList(1,getCmdLine().size()));
            } catch (CmdLineException e) {
                throw new AbortException(e.getMessage());
            }

            return doRun();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(old);
        }
    }
    
    protected abstract int doRun() throws Exception;
}
