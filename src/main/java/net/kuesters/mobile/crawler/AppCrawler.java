package net.kuesters.mobile.crawler;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.kuesters.mobile.MobileApp;
import net.kuesters.mobile.crawler.AppCrawlerUtil.AssetType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


/**
 * The crawler that collects all the files needed for a {@link MobileApp} from an external source.
 * 
 * @author <a href="http://www.kuesters.net">Jens K&uuml;sters</a>
 */
public class AppCrawler {

	/** The logger. */
	private static final Log LOG = LogFactory.getLog(AppCrawler.class);

	/** The app that is crawled. */
	private MobileApp app;

	/** The crawled URLs of the latest crawl. */
	private List<String> crawledURLs;

	/** The ZIP output stream. */
	private ZipOutputStream zipOutputStream;

	/** The list of errors that happened during the latest crawl. */
	private List<String> errors;

	/** True if <code>/icon.png</code> was found. */
	private boolean hasIcon;

	/** True if <code>/splash.png</code> was found. */
	private boolean hasSplashScreen;

	/**
	 * Instantiates a new app crawler.
	 * 
	 * @param app
	 *            the app
	 */
	public AppCrawler(MobileApp app) {
		this.app = app;
		this.crawledURLs = new ArrayList<String>();
		this.errors = new ArrayList<String>();
	}

	/**
	 * Gets the mobile app.
	 * 
	 * @return the mobile app
	 */
	public MobileApp getApp() {
		return app;
	}

	/**
	 * Gets the crawled URLs.
	 * 
	 * @return the crawled URLs
	 */
	public List<String> getCrawledURLs() {
		return crawledURLs;
	}

	/**
	 * Checks if a URL is crawled.
	 * 
	 * @param url
	 *            the URL
	 * @return true, if the URL is crawled
	 */
	public boolean isCrawled(String url) {
		return getCrawledURLs().contains(url);
	}

	/**
	 * Gets a list of errors that occurred during the last crawl.
	 * 
	 * @return the errors
	 */
	public List<String> getErrors() {
		return errors;
	}

	/**
	 * Crawl an app starting with it's {@link MobileApp#getStartUrl()}.
	 * <p>
	 * After the crawl possible errors can be obtained via {@link #getErrors()}.
	 * </p>
	 * 
	 * @return the resulting ZIP file as byte array output stream
	 */
	public ByteArrayOutputStream crawl() {
		LOG.info("Start crawling app " + app.getName());

		String startUrl = app.getStartUrl();

		getCrawledURLs().clear();
		// avoid crawling a phonegap.js file because it will be added dynamically by PhoneGap Build
		getCrawledURLs().add(app.getStartUrl() + "/phonegap.js");
		getErrors().clear();
		hasIcon = false;
		hasSplashScreen = false;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			zipOutputStream = new ZipOutputStream(baos);
			zipOutputStream.setComment("Content for " + app.getName());

			crawl(startUrl, "/index.html");
			crawlDefaultIcon();
			crawlDefaultSplashScreen();
			addConfig();
			baos.close();
			zipOutputStream.close();
		} catch (Exception e) {
			LOG.warn("Couldn't crawl " + startUrl, e);
		}

		LOG.info("Finished crawling app " + app.getName() + ". Found " + getCrawledURLs().size() + " file/s. " + getErrors().size() + " error/s occured.");

		return baos;
	}

	/**
	 * Crawl a given URL.
	 * 
	 * @param urlString
	 *            the URL as string
	 * @param contentType
	 *            the content type
	 * @return the path leading to this file in the resulting ZIP file
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 * @throws MalformedURLException
	 *             the malformed URL exception
	 */
	private String crawl(String urlString, String absoluteReferrerPath) throws IOException, MalformedURLException, FileNotFoundException {
		// we don't want to handle mailto: links
		if (urlString.contains("mailto:"))
			return urlString;

		urlString = getNormalizedURL(urlString);
		String resultingPath = getAbsoluteResultingPath(urlString);

		if (!isCrawled(urlString)) {
			LOG.info("Start crawling " + urlString);

			getCrawledURLs().add(urlString);
			InputStream inputStream = null;

			URLConnection connection = getConnection(urlString);

			// Workaround for web fonts because of wrong mime type delivered in the response header
			boolean isWebfont = StringUtils.endsWithAny(connection.getURL().getPath(), new String[] { "eot", "otf", "ttf", "woff" });

			AssetType assetType = isWebfont ? AssetType.OTHER : AppCrawlerUtil.getAssetType(connection.getContentType());

			LOG.info(assetType);

			if (assetType == AssetType.HTML) {
				// get the HTML document to parse it and extract more links to crawl
				Document doc = Jsoup.parse(connection.getInputStream(), connection.getContentEncoding(), urlString);

				Elements links = doc.select("a[href]");
				Elements media = doc.select("[src]");
				Elements imports = doc.select("link[href]");

				for (Element link : links) {
					String absoluteHref = link.attr("abs:href");

					if (StringUtils.isNotBlank(absoluteHref) && StringUtils.startsWithIgnoreCase(absoluteHref, app.getStartUrl())) {
						try {
							link.attr("href", crawl(absoluteHref, resultingPath));
						} catch (Exception e) {
							LOG.warn("Couldn't crawl " + absoluteHref, e);
							getErrors().add(e.getLocalizedMessage());
						}
					}
				}

				for (Element link : imports) {
					String absoluteHref = link.attr("abs:href");
					if (StringUtils.isNotBlank(absoluteHref)) {
						try {
							if ("stylesheet".equals(link.attr("rel")))
								link.attr("href", crawl(absoluteHref, resultingPath));
						} catch (Exception e) {
							LOG.warn("Couldn't crawl " + absoluteHref, e);
							getErrors().add(e.getLocalizedMessage());
						}
					}
				}

				for (Element src : media) {
					String absoluteSrc = src.attr("abs:src");
					if (StringUtils.isNotBlank(absoluteSrc)) {
						try {
							src.attr("src", crawl(absoluteSrc, resultingPath));
						} catch (Exception e) {
							LOG.warn("Couldn't crawl " + absoluteSrc, e);
							getErrors().add(e.getLocalizedMessage());
						}
					}
				}

				inputStream = IOUtils.toInputStream(doc.html());
			} else if (assetType == AssetType.STYLESHEET) {
				/*
				 * Get the CSS content and parse all @import and url(...) declarations to crawl their links. Regular expressions found at
				 * https://forums.oracle.com/forums/thread.jspa?threadID=2042775
				 */
				String css = IOUtils.toString(connection.getInputStream());

				String stringLiteralRegex = "(?:\"(?:\\.|[^\\\"])*\"|'(?:\\.|[^\\'])*')";
				String urlRegex = String.format("(?:url\\(\\s*(?:%s|[^)]*)\\s*\\))", stringLiteralRegex);
				String importRegex = String.format("(?:@import\\s+(?:%s|%s))", urlRegex, stringLiteralRegex);

				String regex = String.format("/\\*[\\s\\S]*?\\*/|(%s)|(%s)|%s", importRegex, urlRegex, stringLiteralRegex);

				Pattern p = Pattern.compile(regex);
				Matcher m = p.matcher(css);

				while (m.find()) {
					if (m.group(1) != null || m.group(2) != null) {
						String matched = m.group();
						try {
							String strippedURL = matched.replaceAll("^.*?[\\(\"']\\s*[\"']?|[\"')\\s]*$", "");
							URL baseURL = new URL(urlString);
							strippedURL = new URL(baseURL, strippedURL).toString();

							String relativePath = crawl(strippedURL, resultingPath);

							if (StringUtils.isNotBlank(relativePath))
								css.replace(strippedURL, relativePath);
						} catch (Exception e) {
							LOG.warn("Couldn't crawl " + matched, e);
							getErrors().add(e.getLocalizedMessage());
						}
					}
				}

				inputStream = IOUtils.toInputStream(css);
			} else {
				// everything else will only be copied but not parsed
				inputStream = connection.getInputStream();
			}

			if (inputStream != null) {
				LOG.info("Save to " + resultingPath);
				addZipEntry(inputStream, resultingPath);
				inputStream.close();
			} else {
				throw new FileNotFoundException(urlString + "?");
			}
			LOG.info("Finished crawling " + urlString);
		}

		return getRelativeResultingPath(resultingPath, absoluteReferrerPath);
	}

	/**
	 * Normalizes URLs to avoid duplicates.
	 * 
	 * @param urlString
	 *            the source's URL
	 * @return the normalized URL as a string
	 * @throws MalformedURLException
	 *             the malformed URL exception
	 */
	private String getNormalizedURL(String urlString) throws MalformedURLException {
		URL url = new URL(urlString);
		return url.getProtocol() + "://" + url.getHost() + (url.getPort() > 0 && url.getPort() != url.getDefaultPort() ? ":" + url.getPort() : "") + url.getFile();
	}

	/**
	 * Builds the absolute path to the crawled file as it will be saved locally for the mobile app.
	 * 
	 * @param urlString
	 *            the source's URL
	 * @return the absolute local path to be used for the crawled file
	 * @throws MalformedURLException
	 *             the malformed url exception
	 */
	private String getAbsoluteResultingPath(String urlString) throws MalformedURLException {
		URL url = new URL(urlString);
		URL appUrl = new URL(app.getStartUrl());

		StringBuffer resultingPath = new StringBuffer("");
		resultingPath.append(!StringUtils.startsWithIgnoreCase(urlString, app.getStartUrl()) ? url.getHost().replace(".", "-") + "-" + url.getPort() : "");
		resultingPath.append(url.getPath().replaceFirst(Pattern.quote(appUrl.getPath()), "").replace(":", "/").replace(",", "/"));
		// convert parameters to directories
		resultingPath.append((StringUtils.isNotBlank(url.getQuery()) ? url.getQuery().replace("=", "/").replace("&", "/").replace(":", "/") : ""));
		// if the path doesn't contain a dot we consider it to be a directory and append a slash
		resultingPath.append(!url.getPath().contains(".") && !url.getPath().endsWith("/") ? "/" : "");
		// in case of a trailing slash we add an index.html for the directory's index file
		resultingPath.append(resultingPath.toString().endsWith("/") ? "index.html" : "");

		String resultingPathString = resultingPath.toString();

		return (!resultingPathString.startsWith("/") ? "/" : "") + resultingPathString;
	}

	/**
	 * Builds the relative path to the crawled file compared to a referring file.
	 * <p>
	 * This was inspired by C# code from <a href= "http://mrpmorris.blogspot.de/2007/05/convert-absolute-path-to-relative-path.html"
	 * >http://mrpmorris.blogspot.de/2007/05/convert-absolute-path-to-relative-path.html</a>
	 * </p>
	 * 
	 * @param absoluteResultingPath
	 *            the absolute path that will be made relative
	 * @param absoluteReferrerPath
	 *            the absolute path of the referrer as the base of the relative path
	 * @return the relative resulting path
	 */
	private String getRelativeResultingPath(String absoluteResultingPath, String absoluteReferrerPath) {
		if (absoluteResultingPath.equals(absoluteReferrerPath))
			return "";

		String[] absoluteDirectories = absoluteResultingPath.split("/");
		String[] referrerDirectories = absoluteReferrerPath.split("/");

		// Get the shortest of the two paths
		int length = referrerDirectories.length < absoluteDirectories.length ? referrerDirectories.length : absoluteDirectories.length;

		// Use to determine where in the loop we exited
		int lastCommonRoot = -1;

		// Find common root
		for (int i = 0; i < length; i++)
			if (referrerDirectories[i].equals(absoluteDirectories[i]))
				lastCommonRoot = i;
			else
				break;

		// If we didn't find a common prefix then throw
		if (lastCommonRoot == -1) {
			LOG.warn("Paths do not have a common base: " + absoluteResultingPath + " -> " + absoluteReferrerPath);
			throw new IllegalArgumentException("Paths do not have a common base");
		}
		// Build up the relative path
		StringBuilder relativePath = new StringBuilder();

		// Add on the ..
		for (int i = lastCommonRoot + 2; i < referrerDirectories.length; i++)
			if (referrerDirectories[i].length() > 0)
				relativePath.append("../");

		// Add on the folders
		for (int i = lastCommonRoot + 1; i < absoluteDirectories.length - 1; i++)
			relativePath.append(absoluteDirectories[i] + "/");
		relativePath.append(absoluteDirectories[absoluteDirectories.length - 1]);

		String result = relativePath.toString();
		if (result.startsWith("/"))
			result = result.substring(1);

		LOG.info(absoluteReferrerPath + " -> " + absoluteResultingPath + ": " + result);
		return result;
	}

	/**
	 * Adds an input stream as a new entry to the crawler's.
	 * 
	 * @param inputStream
	 *            the input stream
	 * @param path
	 *            the path
	 * @throws IOException
	 *             Signals that an I/O exception has occurred. {@link #zipOutputStream}.
	 */
	private void addZipEntry(InputStream inputStream, String path) throws IOException {
		zipOutputStream.putNextEntry(new ZipEntry(path));
		IOUtils.copy(inputStream, zipOutputStream);
		zipOutputStream.closeEntry();
	}

	/**
	 * Crawls the default icon at <code>/icon.png</code>.
	 */
	private void crawlDefaultIcon() {
		try {
			crawl(app.getStartUrl() + "/icon.png", "/index.html");
			hasIcon = true;
		} catch (Exception e) {
			LOG.warn("Could not retrieve default app icon.");
		}
	}

	/**
	 * Crawls the default splash screen at <code>/splash.png</code>.
	 */
	private void crawlDefaultSplashScreen() {
		try {
			crawl(app.getStartUrl() + "/splash.png", "/index.html");
			hasSplashScreen = true;
		} catch (Exception e) {
			LOG.warn("Could not retrieve default splash screen.");
		}
	}

	/**
	 * Adds the <a href="https://build.phonegap.com/docs/config-xml">PhoneGap Build XML configuration</a> at <code>/config.xml</code>.
	 * <p>
	 * If the {@link #app} has a <code>/config.xml</code> that can be crawled, it's contents will be integrated and all referred resources will be crawled, too.
	 * </p>
	 */
	private void addConfig() {
		String additionalTags = null;
		try {
			String urlString = app.getStartUrl() + "/config.xml";
			URLConnection connection = getConnection(urlString);
			Document doc = Jsoup.parse(connection.getInputStream(), connection.getContentEncoding(), urlString);
			additionalTags = StringUtils.trimToNull(doc.body().html());
		} catch (Exception e) {
			LOG.warn("Could retrieve additional config.xml tags from app.", e);
		}
		try {
			addZipEntry(AppCrawlerUtil.getConfigXMLDocument(app, additionalTags, hasIcon, hasSplashScreen), "/config.xml");
		} catch (Exception e) {
			LOG.warn("Could add config.xml.", e);
		}
	}

	/**
	 * Gets the connection to the given URL.
	 * 
	 * @param urlString
	 *            the URL as string
	 * @return the connection
	 * @throws MalformedURLException
	 *             the malformed URL exception
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	private URLConnection getConnection(String urlString) throws MalformedURLException, IOException {
		URLConnection connection = new URL(urlString).openConnection();
		connection.setRequestProperty("User-Agent", "AppCrawler Service");
		connection.setConnectTimeout(3000);
		connection.setReadTimeout(10000);
		return connection;
	}
}
