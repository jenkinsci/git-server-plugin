package org.jenkinsci.plugins.gitserver.ssh;

import hudson.AbortException;
import org.apache.sshd.server.command.Command;
import org.jenkinsci.main.modules.sshd.AsynchronousCommand;
import org.jenkinsci.main.modules.sshd.SshCommandFactory.CommandLine;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ParserProperties;

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
    protected final int runCommand() throws Exception {
            try {
                ParserProperties properties = ParserProperties.defaults().withAtSyntax(false);
                new CmdLineParser(this, properties).parseArgument(getCmdLine().subList(1,getCmdLine().size()));
            } catch (CmdLineException e) {
                throw new AbortException(e.getMessage());
            }

            return doRun();
    }
    
    protected abstract int doRun() throws Exception;
}
