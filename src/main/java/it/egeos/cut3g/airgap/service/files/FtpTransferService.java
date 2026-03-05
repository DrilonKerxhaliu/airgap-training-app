package it.egeos.cut3g.airgap.service.files;


import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.net.URI;

@Service
public class FtpTransferService {

    public void transfer(PackageEntity pkg, URI uri) throws Exception {

        FTPClient ftp = new FTPClient();

        try {
            ftp.connect(uri.getHost());

            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                ftp.disconnect();
                throw new Exception("FTP connection refused for package " + pkg.getPackageName());
            }

            // ftp login with username password if provided in URI

            try (FileInputStream fis = new FileInputStream(pkg.getPackagePath())) {
                boolean success = ftp.storeFile(uri.getPath() + "/" + pkg.getPackageName(), fis);

                if (!success) {
                    throw new Exception("FTP storeFile failed for package " + pkg.getPackageName()
                            + " - Reply: " + ftp.getReplyString());
                }
            }

            ftp.logout();
        } catch (Exception ex) {
            throw new Exception("FTP transfer failed for package " + pkg.getPackageName() + ": " + ex.getMessage(), ex);
        } finally {
            if (ftp.isConnected()) {
                ftp.disconnect();
            }
        }
    }
}
