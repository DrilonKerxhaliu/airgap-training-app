package it.egeos.cut3g.airgap.service.manifest;

import java.util.List;

public class PackageManifest {
    private String packageName;
    private String md5DataTar;
    private List<ManifestFileItem> files;

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getMd5DataTar() { return md5DataTar; }
    public void setMd5DataTar(String md5DataTar) { this.md5DataTar = md5DataTar; }

    public List<ManifestFileItem> getFiles() { return files; }
    public void setFiles(List<ManifestFileItem> files) { this.files = files; }
}
