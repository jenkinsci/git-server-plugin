package org.jenkinsci.plugins.gitserver.ssh;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.User;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.List;
import jenkins.model.Jenkins;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.jenkinsci.main.modules.cli.auth.ssh.PublicKeySignatureWriter;
import org.jenkinsci.main.modules.cli.auth.ssh.UserPropertyImpl;
import org.jenkinsci.main.modules.sshd.SSHD;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security3342Test {
    private static final String USER = "tester";

    @Test
    @Issue("SECURITY-3342")
    void openRepositoryPermissionCheckTest(@TempDir Path tmp, JenkinsRule j) throws Exception {

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(
                new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().to(USER));
        User tester = User.getOrCreateByIdOrFullName(USER);
        KeyPair keyPair = generateKeys(tester);
        j.jenkins.save();

        // Fixed ssh port for Jenkins ssh server
        SSHD server = SSHD.get();
        server.setPort(0);

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
                        return List.of();
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
        clone.setURI("ssh://" + USER + "@localhost:" + server.getActualPort() + "/userContent.git");
        Path dir1 = Files.createTempDirectory(tmp, null);
        clone.setDirectory(dir1.toFile());

        // Do the git clone for a user with Jenkins.READ permission
        assertDoesNotThrow(clone::call).close();
        Path gitDir1 = dir1.resolve(".git");
        assertTrue(Files.isDirectory(gitDir1), ".git directory exist, clone operation succeed");

        // Do the git clone for a user without Jenkins.READ permission
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());
        j.jenkins.save();

        Path dir2 = Files.createTempDirectory(tmp, null);
        clone.setDirectory(dir2.toFile());

        // Verify that the expected exception was caught with the correct message
        InvalidRemoteException e = assertThrows(InvalidRemoteException.class, clone::call);
        assertInstanceOf(NoRemoteRepositoryException.class, e.getCause());
        NoRemoteRepositoryException e2 = (NoRemoteRepositoryException) e.getCause();
        assertTrue(e2.getMessage()
                .contains("hudson.security.AccessDeniedException3: tester is missing the Overall/Read permission"));

        // Verify that the .git directory is not created
        Path gitDir2 = dir2.resolve(".git");
        assertFalse(Files.isDirectory(gitDir2), ".git directory exist, clone operation succeed");
    }

    private static KeyPair generateKeys(User user) throws NoSuchAlgorithmException, IOException {
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
