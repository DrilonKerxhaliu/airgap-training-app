package it.egeos.cut3g.airgap.service.files;


import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.net.URI;

@Service
public class FtpTransferService {

    @Value("${airgap.ftp.port:21}")
    private int defaultPort;

    @Value("${airgap.ftp.passive-mode:true}")
    private boolean passiveMode;

    public void transfer(PackageEntity pkg, URI uri) throws Exception {
        FTPClient ftp = new FTPClient();

        try {
            int port = uri.getPort() == -1 ? defaultPort : uri.getPort();
            ftp.connect(uri.getHost(), port);

            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                throw new Exception("FTP not reachable: " + uri);
            }
            String userInfo = uri.getUserInfo();

            if (userInfo == null || !userInfo.contains(":")) {
                throw new IllegalArgumentException(
                        "FTP URI must contain username:password (ftp://user:pass@host/...)"
                );
            }

            String[] parts = userInfo.split(":", 2);
            String username = parts[0];
            String password = parts[1];

            boolean login = ftp.login(username, password);

            if (!login) {
                throw new Exception("FTP login failed for user: " + username);
            }

            if (passiveMode) {
                ftp.enterLocalPassiveMode();
            }

            ftp.setFileType(FTPClient.BINARY_FILE_TYPE);

            String remotePath = uri.getPath();
            if (remotePath == null || remotePath.isBlank()) {
                remotePath = "/";
            }

            String targetFile = remotePath + "/" + pkg.getPackageName();

            try (FileInputStream fis = new FileInputStream(pkg.getPackagePath())) {

                boolean success = ftp.storeFile(targetFile, fis);

                if (!success) {
                    throw new Exception("FTP upload failed: " + ftp.getReplyString());
                }
            }

            ftp.logout();

        } catch (Exception ex) {
            throw new Exception("FTP transfer failed: " + ex.getMessage(), ex);
        } finally {
            if (ftp.isConnected()) {
                ftp.disconnect();
            }
        }
    }
}