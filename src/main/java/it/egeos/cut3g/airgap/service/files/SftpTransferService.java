package it.egeos.cut3g.airgap.service.files;

import com.jcraft.jsch.*;
import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.net.URI;
import java.util.Properties;

@Service
public class SftpTransferService {

    public void transfer(PackageEntity pkg, URI uri) {

        Session session = null;
        Channel channel = null;
        ChannelSftp sftp = null;

        try {
            String userInfo = uri.getUserInfo();

            if (userInfo == null || !userInfo.contains(":")) {
                throw new IllegalArgumentException(
                        "SFTP URI must contain username:password (sftp://user:pass@host/...)"
                );
            }

            String[] parts = userInfo.split(":", 2);
            String username = parts[0];
            String password = parts[1];

            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 22 : uri.getPort();

            String remotePath = uri.getPath();
            if (remotePath == null || remotePath.isBlank()) {
                remotePath = "/";
            }

            JSch jsch = new JSch();
            session = jsch.getSession(username, host, port);
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.connect(10000);

            channel = session.openChannel("sftp");
            channel.connect();

            sftp = (ChannelSftp) channel;

            createDirectoriesIfNeeded(sftp, remotePath);

            String targetFile = remotePath + "/" + pkg.getPackageName() + ".tar";

            try (FileInputStream fis = new FileInputStream(pkg.getPackagePath())) {

                sftp.put(fis, targetFile);
            }

        } catch (Exception ex) {
            throw new RuntimeException("SFTP transfer failed: " + ex.getMessage(), ex);
        } finally {
            if (sftp != null) sftp.exit();
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    private void createDirectoriesIfNeeded(ChannelSftp sftp, String path) throws Exception {

        String[] folders = path.split("/");
        String current = "";

        for (String folder : folders) {
            if (folder == null || folder.isEmpty()) continue;

            current += "/" + folder;

            try {
                sftp.cd(current);
            } catch (Exception e) {
                sftp.mkdir(current);
            }
        }
    }
}