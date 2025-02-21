package org.jenkinsci.plugins.gitserver.ssh;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import hudson.Functions;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.jenkinsci.main.modules.cli.auth.ssh.PublicKeySignatureWriter;
import org.jenkinsci.main.modules.cli.auth.ssh.UserPropertyImpl;
import org.jenkinsci.main.modules.sshd.SSHD;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class Security3342Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Before
    public void setUp() {
        assumeFalse(Functions.isWindows());
    }

    @Test
    @Issue("SECURITY-3342")
    public void openRepositoryPermissionCheckTest() throws Exception {

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(
                new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().to("tester"));
        hudson.model.User tester = hudson.model.User.getOrCreateByIdOrFullName("tester");
        KeyPair keyPair = generateKeys(tester);
        j.jenkins.save();

        // Fixed ssh port for Jenkins ssh server
        SSHD server = SSHD.get();
        server.setPort(2222);

        final SshdSessionFactory instance = new SshdSessionFactory() {
            @Override
            protected Iterable<KeyPair> getDefaultKeys(File sshDir) {
                return List.of(keyPair);
            }

            @Override
            protected ServerKeyDatabase getServerKeyDatabase(File homeDir, File sshDir) {
                return new ServerKeyDatabase() {
                    @Override
                    public List<PublicKey> lookup(
                            String connectAddress, InetSocketAddress remoteAddress, Configuration config) {
                        return Collections.emptyList();
                    }

                    @Override
                    public boolean accept(
                            String connectAddress,
                            InetSocketAddress remoteAddress,
                            PublicKey serverKey,
                            Configuration config,
                            CredentialsProvider provider) {
                        return true;
                    }
                };
            }
        };
        SshSessionFactory.setInstance(instance);

        CloneCommand clone = Git.cloneRepository();
        clone.setURI("ssh://tester@localhost:2222/userContent.git");
        File dir1 = tmp.newFolder();
        clone.setDirectory(dir1);

        // Do the git clone for a user with Jenkins.READ permission
        Git gitClone1 = clone.call();
        File gitDir1 = new File(dir1, ".git");
        assertTrue(".git directory exist, clone operation succeed", gitDir1.exists());

        // Do the git clone for a user without Jenkins.READ permission
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());
        j.jenkins.save();

        File dir2 = tmp.newFolder();
        clone.setDirectory(dir2);

        try {
            Git gitClone2 = clone.call();
        } catch (Exception e) {
            // Verify that the expected exception was caught with the correct message
            assertTrue(
                    e.getCause() != null
                            && e.getCause()
                                    .getMessage()
                                    .contains(
                                            "hudson.security.AccessDeniedException3: tester is missing the Overall/Read permission"));
        }
        // Verify that the .git directory is not created
        File gitDir2 = new File(dir2, ".git");
        assertFalse(".git directory exist, clone operation succeed", gitDir2.exists());
    }

    private static KeyPair generateKeys(hudson.model.User user) throws NoSuchAlgorithmException, IOException {
        // I'd prefer to generate Ed25519 keys here, but the API is too awkward currently
        // ECDSA keys would be even more awkward as we'd need a copy of the curve parameters
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String encodedPublicKey = "ssh-rsa " + new PublicKeySignatureWriter().asString(keyPair.getPublic());
        user.addProperty(new UserPropertyImpl(encodedPublicKey));
        return keyPair;
    }
}
