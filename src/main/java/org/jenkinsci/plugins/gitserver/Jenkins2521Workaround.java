package org.jenkinsci.plugins.gitserver;

import org.kohsuke.stapler.compression.CompressionFilter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * Work around JENKINS-25212 until we can depend on core that has Stapler 1.234.
 *
 * By hiding "Accept-Encoding" request header, jgit will not attempt to do its own compression,
 * which triggers setHeader("Content-Encoding","gzip") that triggers {@link CompressionFilter}
 * incorrectly.
 *
 * @author Kohsuke Kawaguchi
 */
class Jenkins2521Workaround extends HttpServletRequestWrapper {
    Jenkins2521Workaround(HttpServletRequest req) {
        super(req);
    }

    @Override
    public String getHeader(String name) {
        if (name.equals("Accept-Encoding"))
            return null;
        return super.getHeader(name);
    }
}
