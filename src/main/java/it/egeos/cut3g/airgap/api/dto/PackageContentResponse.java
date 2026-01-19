package it.egeos.cut3g.airgap.api.dto;

import java.util.List;

public class PackageContentResponse {
    public String packageId;
    public String packageName;
    public List<FileContentDto> files;
}