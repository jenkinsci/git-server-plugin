package org.jenkinsci.plugins.gitserver.ssh;

import hudson.Extension;
import org.apache.sshd.server.command.Command;
import org.jenkinsci.main.modules.sshd.SshCommandFactory;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SshCommandFactoryImpl extends SshCommandFactory {
    @Override
    public Command create(CommandLine cmds) {
        if (cmds.size() < 1) return null;

        if (cmds.get(0).equals("git-receive-pack")) return new ReceivePackCommand(cmds);
        if (cmds.get(0).equals("git-upload-pack")) return new UploadPackCommand(cmds);

        return null;
    }
}
