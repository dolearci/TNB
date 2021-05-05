package org.jboss.fuse.tnb.ftp.resource.local;

import org.jboss.fuse.tnb.common.deployment.Deployable;
import org.jboss.fuse.tnb.ftp.service.CustomFtpClient;
import org.jboss.fuse.tnb.ftp.service.Ftp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.images.builder.Transferable;

import com.google.auto.service.AutoService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@AutoService(Ftp.class)
public class LocalFtp extends Ftp implements Deployable {
    private static final Logger LOG = LoggerFactory.getLogger(LocalFtp.class);
    private FtpContainer container;
    private final CustomFtpClient client = new TestContainerFtpOutOfBandClient();

    @Override
    public void deploy() {
        LOG.info("Starting Ftp container");
        container = new FtpContainer(ftpImage(), port(), containerEnvironment());
        container.start();
        LOG.info("Ftp container started");
    }

    @Override
    public void undeploy() {
        if (container != null) {
            LOG.info("Stopping Ftp container");
            container.stop();
        }
    }

    @Override
    public void openResources() {
        // noop
    }

    @Override
    public void closeResources() {
        // noop
    }

    @Override
    protected CustomFtpClient client() {
        return client;
    }

    @Override
    public int port() {
        return 2121;
    }

    @Override
    public String host() {
        // container.getIpAddress() always returns "localhost" (see https://github.com/testcontainers/testcontainers-java/issues/452 ),
        // we need the actual container ip address, because ftp requires multiple ports.
        // Also tried exposing all the ports and using localhost, but then the integration would not work for some reason
        //      (probably because the ftp server is listening for passive connections on the ip, not on localhost)
        return container.getContainerInfo().getNetworkSettings().getNetworks().get("bridge").getIpAddress();
    }

    /**
     * Custom client to make transfering files into the server faster and more stable
     */
    public class TestContainerFtpOutOfBandClient implements CustomFtpClient {

        @Override
        public void storeFile(String fileName, InputStream fileContent) throws IOException {
            container.copyFileToContainer(Transferable.of(fileContent.readAllBytes(), 040777),
                basePath() + "/" + fileName);
        }

        @Override
        public void retrieveFile(String fileName, OutputStream local) throws IOException {
            Path tempFile = Files.createTempFile(null, null);
            try {
                container.copyFileFromContainer(basePath() + "/" + fileName, tempFile.toString());
                org.apache.commons.io.IOUtils.copy(Files.newInputStream(tempFile), local);
            } finally {
                tempFile.toFile().delete();
            }
        }

        @Override
        public void makeDirectory(String dirName) throws IOException {
            try {
                container.execInContainer(String.format("mkdir -m a=rwx '%s/%s'", basePath(), dirName));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
