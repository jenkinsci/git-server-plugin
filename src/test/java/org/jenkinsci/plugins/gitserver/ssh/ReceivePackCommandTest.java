package org.jenkinsci.plugins.gitserver.ssh;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import hudson.Functions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.jenkinsci.main.modules.cli.auth.ssh.PublicKeySignatureWriter;
import org.jenkinsci.main.modules.cli.auth.ssh.UserPropertyImpl;
import org.jenkinsci.main.modules.sshd.SSHD;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * This class contains code borrowed and adapted from the Jenkins SSHD plugin.
 * Original source: org.jenkinsci.main.modules.sshd.SSHDTest.java
 */
public class ReceivePackCommandTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        assumeFalse(Functions.isWindows());
    }

    @Test
    @Issue("SECURITY-3319")
    public void shouldNotParseAtChar() throws Exception {
        hudson.model.User enabled = hudson.model.User.getOrCreateByIdOrFullName("enabled");
        KeyPair keyPair = generateKeys(enabled);
        SSHD server = SSHD.get();
        server.setPort(0);
        server.start();

        Path tempPath = Files.createTempFile("tempFile", ".txt");
        tempPath.toFile().deleteOnExit();
        String content = "AtGotParsed";
        Files.write(tempPath, content.getBytes());

        try (SshClient client = SshClient.setUpDefaultClient()) {
            client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
            client.start();
            ConnectFuture future = client.connect("enabled", new InetSocketAddress(server.getActualPort()));
            try (ClientSession session = future.verify(10, TimeUnit.SECONDS).getSession()) {
                session.addPublicKeyIdentity(keyPair);
                assertTrue(session.auth().await(10, TimeUnit.SECONDS));

                String command = "git-receive-pack @" + tempPath;
                try (ClientChannel channel = session.createExecChannel(command)) {
                    ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
                    channel.setErr(errorStream);
                    channel.open().verify(5, TimeUnit.SECONDS);
                    channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), TimeUnit.SECONDS.toMillis(5));

                    String errorMessage = errorStream.toString();
                    assertThat(errorMessage, containsString("@" + tempPath));
                    assertThat(errorMessage, not(containsString(content)));
                }
            }
        }
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
