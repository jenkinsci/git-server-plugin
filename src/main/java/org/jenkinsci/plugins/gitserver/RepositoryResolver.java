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
 * <h2>Repository Name</h2>
 * <p>
 * Both methods of this interface uses the parameter 'fullRepositoryName'.
 *
 * <p>
 * This parameter represents the repository path name as given by git client.
 * For example, "foo/bar.git" for client running "git push jenkins:foo/bar.git"
 * and "/foo/bar.git" for client running "git push ssh://jenkins/foo/bar.git".
 *
 * <p>
 * To avoid conflicts, plugins are highly encouraged to require a known prefix.
 * For example, if you are implementing acme-plugin, you should only recognize
 * "acme/foo.git" or "acme/foo/bar.git" but not "foo.git"
 *
 * <p>
 * Similarly, because of the difference in the way the leading '/' appears based on the protocol,
 * most implementations should support both "/acme/foo.git" and "acme/foo.git".
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
     *      See class javadoc for details.
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
     *      See class javadoc for details.
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
