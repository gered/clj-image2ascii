package clj_image2ascii.java;

import java.awt.image.BufferedImage;

// This method of converting an image to ascii representation is based on the method used in
// Claskii (https://github.com/LauJensen/Claskii). Some improvements have been made, such as
// a better way of calculating pixel brightness and little tweaks to how the right ASCII
// character is selected, as well as obviously a conversion to Java purely for performance.

public class ImageToAscii {
	static final char[] asciiChars = {'#', 'A', '@', '%', '$', '+', '=', '*', ':', ',', '.', ' '};
	static final int numAsciiChars = asciiChars.length - 1;
	static final int spanLength = "<span style=\"color:#112233;\">X</span>".length();
	static final int lineTerminatorLength = "<br>".length();

	public static String convert(BufferedImage image, boolean useColor) {
		final int width = image.getWidth();
		final int height = image.getHeight();

		final int maxLength = (useColor ?
		                       (width * height * spanLength) + (height * lineTerminatorLength) :
		                       (width * height) + height);

		final MicroStringBuilder sb = new MicroStringBuilder(maxLength);

		final int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

		for (int y = 0; y < height; ++y) {
			for (int x = 0; x < width; ++x) {
				final int argb = pixels[(y * width) + x];
				final int r = (0x00ff0000 & argb) >> 16;
				final int g = (0x0000ff00 & argb) >> 8;
				final int b = (0x000000ff & argb);
				final double brightness = Math.sqrt((r * r * 0.241f) +
				                                    (g * g * 0.691f) +
				                                    (b * b * 0.068f));
				int charIndex;
				if (brightness == 0.0f)
					charIndex = numAsciiChars;
				else
					charIndex = (int)((brightness / 255.0f) * numAsciiChars);

				final char pixelChar = asciiChars[charIndex > 0 ? charIndex : 0];

				if (useColor) {
					sb.append("<span style=\"color:#");
					sb.appendAsHex(r);
					sb.appendAsHex(g);
					sb.appendAsHex(b);
					sb.append(";\">");
					sb.append(pixelChar);
					sb.append("</span>");
				} else
					sb.append(pixelChar);
			}
			if (useColor)
				sb.append("<br>");
			else
				sb.append('\n');
		}

		return sb.toString();
	}
}
