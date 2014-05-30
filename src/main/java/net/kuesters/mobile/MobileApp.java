package net.kuesters.mobile;

public class MobileApp {

	private String name;
	private String startUrl;
	private String packageName;
	private String appVersion;

	public MobileApp(String name, String startUrl, String packageName,
			String appVersion, String description) {
		this.name = name;
		this.startUrl = startUrl;
		this.packageName = packageName;
		this.appVersion = appVersion;
		this.description = description;
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

}
