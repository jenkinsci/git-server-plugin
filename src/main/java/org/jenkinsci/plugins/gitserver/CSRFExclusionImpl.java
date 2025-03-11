package org.jenkinsci.plugins.gitserver;

import hudson.Extension;
import hudson.security.csrf.CrumbExclusion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

/**
 * CSRF exclusion for git-upload-pack.
 *
 * <p>
 * We do some basic checks to significantly limit the scope of exclusion, but
 * because of the dynamic nature of the URL structure, this doesn't guarantee
 * that we have no leak.
 *
 * <p>
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
    private static final String BOGUS = "bogus";

    @Override
    public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!"application/x-git-receive-pack-request".equals(request.getHeader("Content-Type"))) return false;

        HttpServletRequestWrapper w = new HttpServletRequestWrapper(request) {
            @Override
            public String getQueryString() {
                return BOGUS;
            }

            @Override
            public String getParameter(String name) {
                return BOGUS;
            }

            @Override
            public Map<String, String[]> getParameterMap() {
                return Collections.emptyMap();
            }

            @Override
            public Enumeration<String> getParameterNames() {
                return Collections.emptyEnumeration();
            }

            @Override
            public String[] getParameterValues(String name) {
                return new String[] {BOGUS};
            }

            @Override
            public String getMethod() {
                return BOGUS.toUpperCase(Locale.ROOT);
            }

            @Override
            public ServletInputStream getInputStream() {
                return new ServletInputStream() {
                    @Override
                    public boolean isFinished() {
                        return false;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public int read() {
                        return -1;
                    }

                    @Override
                    public void setReadListener(ReadListener readListener) {
                        // Do nothing.
                    }
                };
            }
        };
        w.setAttribute(ORIGINAL_REQUEST, request);

        chain.doFilter(w, response);
        return true;
    }

    static final String ORIGINAL_REQUEST = CSRFExclusionImpl.class.getName() + ".originalRequest";

    public static HttpServletRequest unwrapRequest(HttpServletRequest req) {
        return (HttpServletRequest) req.getAttribute(CSRFExclusionImpl.ORIGINAL_REQUEST);
    }
}
