package org.jenkinsci.plugins.gitserver;

import hudson.FilePath;
import hudson.remoting.Pipe;
import hudson.remoting.VirtualChannel;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.BasePackFetchConnection;
import org.eclipse.jgit.transport.BasePackPushConnection;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.PackTransport;
import org.eclipse.jgit.transport.PushConnection;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UploadPack;

/**
 * {@link Transport} implementation across pipes.
 *
 * @author Kohsuke Kawaguchi
 */
public class ChannelTransport extends Transport implements PackTransport {
    private final FilePath remoteRepository;

    public static Transport open(Repository local, FilePath remoteRepository)
            throws NotSupportedException, URISyntaxException, TransportException {
        if (remoteRepository.isRemote()) return new ChannelTransport(local, remoteRepository);
        else return Transport.open(local, remoteRepository.getRemote());
    }

    public ChannelTransport(Repository local, FilePath remoteRepository) throws URISyntaxException {
        super(local, new URIish("channel:" + remoteRepository.getRemote()));
        this.remoteRepository = remoteRepository;
    }

    @Override
    public FetchConnection openFetch() throws NotSupportedException, TransportException {
        final Pipe l2r = Pipe.createLocalToRemote();
        final Pipe r2l = Pipe.createRemoteToLocal();

        try {
            remoteRepository.actAsync(new GitFetchTask(l2r, r2l));
        } catch (IOException e) {
            throw new TransportException("Failed to open a fetch connection", e);
        } catch (InterruptedException e) {
            throw new TransportException("Failed to open a fetch connection", e);
        }

        return new BasePackFetchConnection(this) {
            {
                init(new BufferedInputStream(r2l.getIn()), new BufferedOutputStream(l2r.getOut()));
                readAdvertisedRefs();
            }
        };
    }

    @Override
    public PushConnection openPush() throws NotSupportedException, TransportException {
        final Pipe l2r = Pipe.createLocalToRemote();
        final Pipe r2l = Pipe.createRemoteToLocal();

        try {
            remoteRepository.actAsync(new GitPushTask(l2r, r2l));
        } catch (IOException e) {
            throw new TransportException("Failed to open a fetch connection", e);
        } catch (InterruptedException e) {
            throw new TransportException("Failed to open a fetch connection", e);
        }

        return new BasePackPushConnection(this) {
            {
                init(new BufferedInputStream(r2l.getIn()), new BufferedOutputStream(l2r.getOut()));
                readAdvertisedRefs();
            }
        };
    }

    @Override
    public void close() {
        // no-op
    }

    private static class GitFetchTask extends MasterToSlaveFileCallable<Void> {
        private final Pipe l2r;
        private final Pipe r2l;

        public GitFetchTask(Pipe l2r, Pipe r2l) {
            this.l2r = l2r;
            this.r2l = r2l;
        }

        public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            Repository repo = new FileRepositoryBuilder().setWorkTree(f).build();
            try {
                final UploadPack rp = new UploadPack(repo);
                rp.upload(new BufferedInputStream(l2r.getIn()), new BufferedOutputStream(r2l.getOut()), null);
                return null;
            } finally {
                IOUtils.closeQuietly(l2r.getIn());
                IOUtils.closeQuietly(r2l.getOut());
                repo.close();
            }
        }
    }

    private static class GitPushTask extends MasterToSlaveFileCallable<Void> {
        private final Pipe l2r;
        private final Pipe r2l;

        public GitPushTask(Pipe l2r, Pipe r2l) {
            this.l2r = l2r;
            this.r2l = r2l;
        }

        public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            Repository repo = new FileRepositoryBuilder().setWorkTree(f).build();
            try {
                final ReceivePack rp = new ReceivePack(repo);
                rp.receive(new BufferedInputStream(l2r.getIn()), new BufferedOutputStream(r2l.getOut()), null);
                return null;
            } finally {
                IOUtils.closeQuietly(l2r.getIn());
                IOUtils.closeQuietly(r2l.getOut());
                repo.close();
            }
        }
    }
}
