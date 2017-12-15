package org.jenkinsci.plugins.gitserver;

import hudson.Extension;
import hudson.security.csrf.CrumbExclusion;

import javax.servlet.FilterChain;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Vector;

/**
 * CSRF exclusion for git-upload-pack.
 * 
 * <p>
 * We do some basic checks to significantly limit the scope of exclusion, but
 * because of the dynamic nature of the URL structure, this doesn't guarantee
 * that we have no leak.
 *
 * So to further protect Jenkins, we pass through a fake {@link HttpServletRequest}
 * that masks the values of the submission.
 * 
 * <p>
 * If the fake request is routed to {@link HttpGitRepository}, which is
 * the only legitimate destination of the request, we'll unwrap this fake request
 * and pass the real request to JGit.
 *
 * <p>
 * In this way, even if an attacker manages to route the request to elsewhere in Jenkins,
 * that request will not be interpreted as a POST request.
 * 
 * @author Kohsuke Kawaguchi
 */
@Extension
public class CSRFExclusionImpl extends CrumbExclusion {

    public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!"application/x-git-receive-pack-request".equals(request.getHeader("Content-Type")))
            return false;

//        String path = request.getPathInfo();
//        if(!path.contains("/repo.git/") || !path.endsWith("/git-receive-pack"))
//            return false;

        HttpServletRequestWrapper w = new HttpServletRequestWrapper(request) {
            @Override
            public String getQueryString() {
                return "bogus";
            }

            @Override
            public String getParameter(String name) {
                return "bogus";
            }

            @Override
            public Map getParameterMap() {
                return Collections.emptyMap();
            }

            @Override
            public Enumeration getParameterNames() {
                return new Vector().elements();
            }

            @Override
            public String[] getParameterValues(String name) {
                return new String[]{"bogus"};
            }

            @Override
            public String getMethod() {
                return "BOGUS";
            }

            @Override
            public ServletInputStream getInputStream() throws IOException {
                return new ServletInputStream() {
                    @Override
                    public int read() throws IOException {
                        return -1;
                    }

                    @Override
                    public boolean isFinished() {
                        return false;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener readListener) {
                        // Do nothing?
                    }
                };
            }
        };
        w.setAttribute(ORIGINAL_REQUEST,request);
        
        chain.doFilter(w,response);
        return true;
    }

    static final String ORIGINAL_REQUEST = CSRFExclusionImpl.class.getName()+".originalRequest";

    public static HttpServletRequest unwrapRequest(HttpServletRequest req) {
        return (HttpServletRequest) req.getAttribute(CSRFExclusionImpl.ORIGINAL_REQUEST);
    }
}
