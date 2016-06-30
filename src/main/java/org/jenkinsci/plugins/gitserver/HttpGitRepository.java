package org.jenkinsci.plugins.gitserver;

import hudson.model.Action;
import jenkins.model.Jenkins;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.resolver.DefaultReceivePackFactory;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UI-bound object that exposes a Git repository via HTTP.
 *
 * <p>
 * To expose a Git repository, bind this object to the URL space via stapler,
 * for example by adding a getter to your {@link Action} object.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class HttpGitRepository {
    private GitServlet g;
    private Exception causeOfDeath;

    public HttpGitRepository() {
    }

    /**
     * Opens the repository this UI-bound object holds on to.
     */
    public abstract Repository openRepository() throws IOException;

    /**
     * Returns the {@link ReceivePack} that handles "git push" from client.
     *
     * The most basic implementation is the following, which allows anyone to push to this repository,
     * so normally you want some kind of access check before that. {@link DefaultReceivePackFactory} isn't suitable
     * here because it requires that the user has non-empty name, which isn't necessarily true in Jenkins
     * (for example, when the security is off entirely.)
     *
     * <pre>
     * return new ReceivePack(db);
     * </pre>
     *
     * @see ReceivePackFactory#create(Object, Repository)
     */
    public abstract ReceivePack createReceivePack(HttpServletRequest context, Repository db) throws ServiceNotEnabledException, ServiceNotAuthorizedException;

    /**
     * Returns the {@link UploadPack} that handles "git fetch" from client.
     *
     * The most basic implementation is the following, which exposes this repository to everyone.
     *
     * <pre>
     * return new DefaultUploadPackFactory().create(context,db);
     * </pre>
     *
     * @see UploadPackFactory#create(Object, Repository)
     */
    public abstract UploadPack createUploadPack(HttpServletRequest context, Repository db) throws ServiceNotEnabledException, ServiceNotAuthorizedException;

    protected GitServlet init() {
        GitServlet g = new GitServlet();
        g.setRepositoryResolver(new org.eclipse.jgit.transport.resolver.RepositoryResolver<HttpServletRequest>() {
            public Repository open(HttpServletRequest req, String name) throws RepositoryNotFoundException {
                try {
                    return openRepository();
                } catch (IOException e) {
                    throw new RepositoryNotFoundException(req.getRequestURI(),e);
                }
            }
        });

        // this creates (and thus configures) the receiver program
        g.setReceivePackFactory(new ReceivePackFactory<HttpServletRequest>() {
            public ReceivePack create(HttpServletRequest req, Repository db) throws ServiceNotEnabledException, ServiceNotAuthorizedException {
                return createReceivePack(req,db);
            }
        });

        g.setUploadPackFactory(new UploadPackFactory<HttpServletRequest>() {
            public UploadPack create(HttpServletRequest req, Repository db) throws ServiceNotEnabledException, ServiceNotAuthorizedException {
                return createUploadPack(req,db);
            }
        });

        try {
            g.init(new ServletConfig() {
                public String getServletName() {
                    return "";
                }

                public ServletContext getServletContext() throws IllegalStateException {
                    Jenkins j = Jenkins.getInstance();
                    if (j == null) {
                        throw new IllegalStateException();
                    }
                    return j.servletContext;
                }

                public String getInitParameter(String name) {
                    return null;
                }

                public Enumeration getInitParameterNames() {
                    return new Vector().elements();
                }
            });
        } catch (ServletException e) {
            LOGGER.log(Level.WARNING,"Failed to initialize GitServlet for " + this,e);
            causeOfDeath = e;
        }
        return g;
    }

    /**
     * Handles git smart HTTP protocol.
     */
    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if (g==null)
            g=init();

        if (causeOfDeath!=null)
            throw new ServletException(causeOfDeath);

        // This is one place where we allow POST without CSRF headers
        HttpServletRequest realRequest = CSRFExclusionImpl.unwrapRequest(req);
        if (realRequest==null)
            realRequest = req;

        realRequest = new Jenkins2521Workaround(realRequest);

        /*
            GitServlet uses getPathInfo() to determine the current request, whereas in Stapler's sense it should be using
            getRestOfPath().

            However, this nicely cancels out with the the GitServlet behavior of wanting one token that specifies
            the repository to work with.

            So in this case one bug cancels out another and it works out well.
         */
        g.service(realRequest,rsp);
    }

    private static final Logger LOGGER = Logger.getLogger(HttpGitRepository.class.getName());
}
