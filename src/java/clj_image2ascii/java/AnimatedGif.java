package clj_image2ascii.java;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Helper for extracting each frame of animation from a GIF as a separate BufferedImage.
 * This handles a bunch of edge cases when dealing with different kinds of animated GIFs
 * such as, getting the proper width/height, handling transparency properly, taking
 * disposal methods into account, and properly grabbing each frame's delay time before
 * the next frame should be displayed.
 * @author SteveH (http://stackoverflow.com/a/18425922)
 * @author gered (_extremely_ minor tweaks)
 */
public class AnimatedGif {
	public static ImageFrame[] read(ImageInputStream stream) throws IOException {
		ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
		reader.setInput(stream, false);

		ArrayList<ImageFrame> frames = new ArrayList<ImageFrame>(2);

		int width = -1;
		int height = -1;

		IIOMetadata metadata = reader.getStreamMetadata();
		if (metadata != null) {
			IIOMetadataNode globalRoot = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());

			NodeList globalScreenDescriptor = globalRoot.getElementsByTagName("LogicalScreenDescriptor");

			if (globalScreenDescriptor != null && globalScreenDescriptor.getLength() > 0) {
				IIOMetadataNode screenDescriptor = (IIOMetadataNode) globalScreenDescriptor.item(0);

				if (screenDescriptor != null) {
					width = Integer.parseInt(screenDescriptor.getAttribute("logicalScreenWidth"));
					height = Integer.parseInt(screenDescriptor.getAttribute("logicalScreenHeight"));
				}
			}
		}

		BufferedImage master = null;
		Graphics2D masterGraphics = null;

		for (int frameIndex = 0;; frameIndex++) {
			BufferedImage image;
			try {
				image = reader.read(frameIndex);
			} catch (IndexOutOfBoundsException io) {
				break;
			}

			if (width == -1 || height == -1) {
				width = image.getWidth();
				height = image.getHeight();
			}

			IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(frameIndex).getAsTree("javax_imageio_gif_image_1.0");
			IIOMetadataNode gce = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
			int delay = Integer.valueOf(gce.getAttribute("delayTime"));
			String disposal = gce.getAttribute("disposalMethod");

			int x = 0;
			int y = 0;

			if (master == null) {
				master = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				masterGraphics = master.createGraphics();
				masterGraphics.setBackground(new Color(0, 0, 0, 0));
			} else {
				NodeList children = root.getChildNodes();
				for (int nodeIndex = 0; nodeIndex < children.getLength(); nodeIndex++) {
					Node nodeItem = children.item(nodeIndex);
					if (nodeItem.getNodeName().equals("ImageDescriptor")) {
						NamedNodeMap map = nodeItem.getAttributes();
						x = Integer.valueOf(map.getNamedItem("imageLeftPosition").getNodeValue());
						y = Integer.valueOf(map.getNamedItem("imageTopPosition").getNodeValue());
					}
				}
			}
			masterGraphics.drawImage(image, x, y, null);

			BufferedImage copy = new BufferedImage(master.getColorModel(), master.copyData(null), master.isAlphaPremultiplied(), null);
			frames.add(new ImageFrame(copy, delay, disposal));

			if (disposal.equals("restoreToPrevious")) {
				BufferedImage from = null;
				for (int i = frameIndex - 1; i >= 0; i--) {
					if (!frames.get(i).disposal.equals("restoreToPrevious") || frameIndex == 0) {
						from = frames.get(i).image;
						break;
					}
				}

				master = new BufferedImage(from.getColorModel(), from.copyData(null), from.isAlphaPremultiplied(), null);
				masterGraphics = master.createGraphics();
				masterGraphics.setBackground(new Color(0, 0, 0, 0));
			} else if (disposal.equals("restoreToBackgroundColor")) {
				masterGraphics.clearRect(x, y, image.getWidth(), image.getHeight());
			}
		}
		reader.dispose();

		return frames.toArray(new ImageFrame[frames.size()]);
	}
}