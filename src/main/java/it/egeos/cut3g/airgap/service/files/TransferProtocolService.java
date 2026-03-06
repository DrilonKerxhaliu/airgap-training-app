package it.egeos.cut3g.airgap.service.files;


import it.egeos.cut3g.airgap.persistence.entity.PackageEntity;
import it.egeos.cut3g.airgap.service.downstream.DownstreamConfigLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;

@Service
public class TransferProtocolService {

    @Autowired
    private FileTransferService fileTransfer;
    @Autowired
    private FtpTransferService ftpTransfer;
    @Autowired
    private DownstreamConfigLoader configLoader;

    public void transfer(PackageEntity pkg) throws Exception {

        String destination = configLoader.getDestination();

        URI uri = URI.create(destination);

        if ("file".equalsIgnoreCase(uri.getScheme())) {

            fileTransfer.transfer(pkg, uri);

        } else if ("ftp".equalsIgnoreCase(uri.getScheme())) {

            ftpTransfer.transfer(pkg, uri);

        } else {

            throw new IllegalArgumentException("Unsupported protocol");
        }
    }
}
