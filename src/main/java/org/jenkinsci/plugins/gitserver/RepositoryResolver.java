package org.jenkinsci.plugins.gitserver;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;

import java.io.IOException;

/**
 * Resolves the full name of the repository as given by Git clients to actual {@link Repository}.
 *
 * <p>
 * This extension point allows multiple plugins to expose their Git repositories via SSH/Git protocol.
 *
 * @author Kohsuke Kawaguchi
 * @see HttpGitRepository
 */
public abstract class RepositoryResolver implements ExtensionPoint {
    /**
     * Returns {@link ReceivePack} used to handle "git push" operation from a client.
     *
     * @param fullRepositoryName
     *      The repository path name as given by git client.
     *      For example, "foo/bar.git" for client running "git push jenkins:foo/bat.git".
     *      To avoid conflicts, plugins are highly encouraged to require a known suffix.
     *      For example, if you are implementing acme-plugin, you should only recognize
     *      "acme/foo.git" or "acme/foo/bar.git" but not "foo.git"
     *
     * @return
     *      null if this resolver doesn't recognize the given path name.
     *      This will allow other {@link RepositoryResolver}s to get a shot at the repository.
     */
    public abstract ReceivePack createReceivePack(String fullRepositoryName) throws IOException, InterruptedException;

    /**
     * Returns {@link UploadPack} used to handle "git fetch" operation from a client.
     *
     * @param fullRepositoryName
     *      The repository path name as given by git client.
     *      For example, "foo/bar.git" for client running "git push jenkins:foo/bat.git".
     *      To avoid conflicts, plugins are highly encouraged to require a known suffix.
     *      For example, if you are implementing acme-plugin, you should only recognize
     *      "acme/foo.git" or "acme/foo/bar.git" but not "foo.git"
     *
     * @return
     *      null if this resolver doesn't recognize the given path name.
     *      This will allow other {@link RepositoryResolver}s to get a shot at the repository.
     */
    public abstract UploadPack createUploadPack(String fullRepositoryName) throws IOException, InterruptedException;

    public static ExtensionList<RepositoryResolver> all() {
        return Jenkins.getInstance().getExtensionList(RepositoryResolver.class);
    }
}
