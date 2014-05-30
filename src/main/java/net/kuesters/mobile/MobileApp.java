package net.kuesters.mobile;

public class MobileApp {

	private String name;
	private String startUrl;
	private String packageName;
	private String appVersion;
	private long phoneGapBuildId;
	private byte[] contentsZip;
	private String contentsZipFileName;

	public MobileApp(String name, String startUrl, String packageName,
			String appVersion) {
		this.name = name;
		this.startUrl = startUrl;
		this.packageName = packageName;
		this.appVersion = appVersion;
	}

	private String description;

	public String getName() {
		return name;
	}

	public String getStartUrl() {
		return startUrl;
	}

	public String getPackageName() {
		return packageName;
	}

	public String getAppVersion() {
		return appVersion;
	}

	public String getDescription() {
		return description;
	}

	public long getPhoneGapBuildId() {
		return phoneGapBuildId;
	}

	public byte[] getContentsZip() {
		return contentsZip;
	}

	public String getContentsZipFileName() {
		return contentsZipFileName;
	}

	public void setPhoneGapBuildId(long phoneGapBuildId) {
		this.phoneGapBuildId = phoneGapBuildId;
	}

	public void setContentsZip(byte[] contentsZip) {
		this.contentsZip = contentsZip;
	}

	public void setContentsZipFileName(String contentsZipFileName) {
		this.contentsZipFileName = contentsZipFileName;
	}

	public void setDescription(String description) {
		this.description = description;
	}

}
