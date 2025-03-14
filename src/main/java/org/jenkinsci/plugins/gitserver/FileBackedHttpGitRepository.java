package org.jenkinsci.plugins.gitserver;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.springframework.security.core.Authentication;

/**
 * Convenient subtype of {@link HttpGitRepository} where the repository
 * is non-bare, resides in a directory local to the controller, and you maintain
 * the local up-to-date checkout whenever a change is pushed.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class FileBackedHttpGitRepository extends HttpGitRepository {
    /**
     * Directory of the local workspace on the controller.
     * There will be "./.git" that hosts the actual repository.
     */
    public final Path workspace;

    protected FileBackedHttpGitRepository(Path workspace) {
        this.workspace = workspace;
        try {
            Files.createDirectory(workspace);
        } catch (FileAlreadyExistsException ignored) {
            // don't need to worry about this; if it already exists, we don't care!
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e, () -> "Cannot create a workspace in " + workspace);
        }
    }

    protected FileBackedHttpGitRepository(File workspace) {
        this(workspace.toPath());
    }

    @Override
    public Repository openRepository() throws IOException {
        checkPullPermission();
        Repository r =
                new FileRepositoryBuilder().setWorkTree(workspace.toFile()).build();

        // if the repository doesn't exist, create it
        if (!r.getObjectDatabase().exists()) {
            createInitialRepository(r);
        }
        return r;
    }

    /**
     * Called when there's no .git directory to create one.
     *
     * <p>
     * This implementation also imports whatever currently in there into the repository.
     */
    protected void createInitialRepository(Repository r) throws IOException {
        r.create();

        try {
            // import initial content
            Git git = new Git(r);
            AddCommand cmd = git.add();
            cmd.addFilepattern(".");
            cmd.call();

            CommitCommand co = git.commit();
            co.setAuthor("Jenkins", "noreply@jenkins-ci.org");
            co.setMessage("Initial import of the existing contents");
            co.call();
        } catch (GitAPIException e) {
            LOGGER.log(Level.WARNING, e, () -> "Initial import of " + workspace + " into Git repository failed");
        }
    }

    /**
     * This default implementation allows read access to anyone
     * who can access the HTTP URL this repository is bound to.
     *
     * <p>
     * For example, if this object is used as a project action,
     * and the project isn't readable to Alice, then Alice won't be
     * able to pull from this repository (think of a POSIX file system
     * where /foo/bar is rwx------ and /foo/bar/zot is rwxrwxrwx.)
     */
    @Override
    public UploadPack createUploadPack(HttpServletRequest context, Repository db)
            throws ServiceNotEnabledException, ServiceNotAuthorizedException {
        return new UploadPack(db);
    }

    /**
     * Requires the admin access to be able to push
     */
    @Override
    public ReceivePack createReceivePack(HttpServletRequest context, Repository db)
            throws ServiceNotEnabledException, ServiceNotAuthorizedException {
        Authentication a = Jenkins.getAuthentication2();

        ReceivePack rp = createReceivePack(db);

        rp.setRefLogIdent(new PersonIdent(a.getName(), a.getName() + "@" + context.getRemoteAddr()));

        return rp;
    }

    public ReceivePack createReceivePack(Repository db) {
        checkPushPermission();

        ReceivePack rp = new ReceivePack(db);

        // update userContent after the push
        rp.setPostReceiveHook((rp1, commands) -> {
            try {
                updateWorkspace(rp1.getRepository());
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                rp1.sendMessage("Failed to update workspace: " + sw);
            }
        });
        return rp;
    }

    /**
     * Called when new ref is pushed to update the {@linkplain #workspace local workspace}.
     * The default implementation does "git reset --hard main"
     */
    protected void updateWorkspace(Repository repo) throws IOException, GitAPIException {
        ResetCommand cmd = new Git(repo).reset();
        cmd.setMode(ResetType.HARD);
        cmd.setRef("master");
        cmd.call();
    }

    /**
     * Do something like {@code Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER)}
     * to make sure the user has the permission to push.
     */
    protected abstract void checkPushPermission();

    private static final Logger LOGGER = Logger.getLogger(FileBackedHttpGitRepository.class.getName());
}
