package net.kuesters.mobile.crawler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.kuesters.mobile.MediaType;
import net.kuesters.mobile.MobileApp;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * A class with utilities for the {@link AppCrawler}.
 * 
 * @author <a href="http://www.kuesters.net">Jens K&uuml;sters</a>
 */
public class AppCrawlerUtil {

	/** The Constant TYPE_MAPPINGS. */
	private static final Map<MediaType, AssetType> TYPE_MAPPINGS;

	static {
		TYPE_MAPPINGS = new HashMap<MediaType, AssetType>();

		TYPE_MAPPINGS.put(MediaType.TEXT_HTML, AssetType.HTML);
		TYPE_MAPPINGS.put(MediaType.APPLICATION_XHTML_XML, AssetType.HTML);
		TYPE_MAPPINGS.put(MediaType.IMAGE_GIF, AssetType.IMAGE);
		TYPE_MAPPINGS.put(MediaType.IMAGE_JPEG, AssetType.IMAGE);
		TYPE_MAPPINGS.put(MediaType.IMAGE_PNG, AssetType.IMAGE);
		TYPE_MAPPINGS.put(MediaType.TEXT_CSS, AssetType.STYLESHEET);
		TYPE_MAPPINGS.put(MediaType.APPLICATION_JAVASCRIPT, AssetType.SCRIPT);
		TYPE_MAPPINGS.put(MediaType.APPLICATION_X_JAVASCRIPT, AssetType.SCRIPT);
	}

	/**
	 * The enumeration of asset types.
	 */
	public enum AssetType {
		/** The HTML type. */
		HTML,
		/** The stylesheet type. */
		STYLESHEET,
		/** The script type. */
		SCRIPT,
		/** The image type. */
		IMAGE,
		/** The other type. */
		OTHER
	}

	/**
	 * Gets the asset type.
	 * 
	 * @param mediaType
	 *            the media type
	 * @return the asset type
	 */
	public static AssetType getAssetType(MediaType mediaType) {
		return TYPE_MAPPINGS.containsKey(mediaType) ? TYPE_MAPPINGS
				.get(mediaType) : AssetType.OTHER;
	}

	/**
	 * Gets the asset type.
	 * 
	 * @param mediaType
	 *            the media type
	 * @return the asset type
	 */
	public static AssetType getAssetType(String mediaType) {
		return StringUtils.isNotBlank(mediaType) ? getAssetType(MediaType
				.parseMediaType(mediaType)) : AssetType.OTHER;
	}

	/**
	 * Gets the config.xml document.
	 * 
	 * @param app
	 *            the app
	 * @param additionalTags
	 *            the additional tags
	 * @param hasIcon
	 *            true to add an <code>icon</code> tag
	 * @param hasSplashScreen
	 *            true to add a <code>gap:splash</code> tag
	 * @return the XML document as an input stream
	 * @throws ParserConfigurationException
	 *             the parser configuration exception
	 * @throws TransformerException
	 *             the transformer exception
	 * @throws IOException
	 * @throws SAXException
	 */
	public static InputStream getConfigXMLDocument(MobileApp app,
			String additionalTags, boolean hasIcon, boolean hasSplashScreen)
			throws ParserConfigurationException, TransformerException,
			SAXException, IOException {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory
				.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

		Document doc = docBuilder.newDocument();

		Element rootElement = doc.createElement("widget");
		rootElement.setAttribute("xmlns", "http://www.w3.org/ns/widgets");
		rootElement.setAttribute("xmlns:gap", "http://phonegap.com/ns/1.0");
		rootElement.setAttribute("id", app.getPackageName());
		rootElement.setAttribute("version", app.getAppVersion());
		doc.appendChild(rootElement);

		Element nameElement = doc.createElement("name");
		rootElement.appendChild(nameElement);
		nameElement.appendChild(doc.createTextNode(app.getName()));

		Element descriptionElement = doc.createElement("description");
		rootElement.appendChild(descriptionElement);
		descriptionElement
				.appendChild(doc.createTextNode(app.getDescription()));

		if (hasIcon) {
			Element iconElement = doc.createElement("icon");
			iconElement.setAttribute("src", "icon.png");
			rootElement.appendChild(iconElement);
		}

		if (hasSplashScreen) {
			Element splashElement = doc.createElement("gap:splash");
			splashElement.setAttribute("src", "splash.png");
			rootElement.appendChild(splashElement);
		}

		if (StringUtils.isNotBlank(additionalTags)) {
			NodeList fragmentNodes = docBuilder
					.parse(IOUtils.toInputStream("<more>" + additionalTags
							+ "</more>")).getFirstChild().getChildNodes();
			for (int i = 0; i < fragmentNodes.getLength(); i++) {
				Node fragmentNode = fragmentNodes.item(i);
				rootElement.appendChild(doc.importNode(fragmentNode, true));
			}
		}

		TransformerFactory transformerFactory = TransformerFactory
				.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		StreamResult result = new StreamResult(outputStream);

		transformer.setOutputProperty(OutputKeys.METHOD, "xml");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(
				"{http://xml.apache.org/xslt}indent-amount", "5");
		transformer.transform(source, result);

		return new ByteArrayInputStream(outputStream.toByteArray());
	}

	private AppCrawlerUtil() {
	}
}
