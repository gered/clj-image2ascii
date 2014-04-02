package clj_image2ascii.java;

import java.awt.image.BufferedImage;

/**
 * Holds information about a single image frame from an animated GIF.
 * @author SteveH (http://stackoverflow.com/a/18425922)
 * @author gered (_extremely_ minor tweaks)
 */
public class ImageFrame {
	public final int delay;
	public final BufferedImage image;
	public final String disposal;

	public ImageFrame(BufferedImage image, int delay, String disposal) {
		this.image = image;
		this.delay = delay;
		this.disposal = disposal;
	}
}
