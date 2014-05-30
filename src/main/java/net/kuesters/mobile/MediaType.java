package net.kuesters.mobile;

import org.apache.commons.lang.StringUtils;

/**
 * A small set of MIME types.
 * 
 * @author <a href="http://www.kuesters.net">Jens K&uuml;sters</a>
 */
public enum MediaType {
	TEXT_HTML("text", "html"), TEXT_CSS("text", "css"),
	APPLICATION_XHTML_XML("application", "xhtml+xml"),
	APPLICATION_JAVASCRIPT("application", "javascript"), APPLICATION_X_JAVASCRIPT("application", "x-javascript"),
	IMAGE_GIF("image", "gif"), IMAGE_JPEG("image", "jpeg"), IMAGE_PNG("image", "png"),
	UNKNOWN("", "");

	private String type;
	private String subtype;

	MediaType(String type, String subtype) {
		this.type = type;
		this.subtype = subtype;
	}

	public static MediaType parseMediaType(String mediaType) {
		String type = StringUtils.substringBefore(mediaType, "/");
		String subType = StringUtils.substringAfter(mediaType, "/");

		for (MediaType value : values()) {
			if (value.type.equalsIgnoreCase(type) && value.subtype.equalsIgnoreCase(subType)) {
				return value;
			}
		}

		return UNKNOWN;
	}

}
