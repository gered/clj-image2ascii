package clj_image2ascii.java;

import clojure.lang.IFn;
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
	public static void read(ImageInputStream stream, IFn fn) throws IOException {
		ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
		reader.setInput(stream, false);

		// will hold a copy of the last frame's "full" image which we can use to restore from a "restoreToPrevious"
		// disposal method if found in a subsequent frame. this will constantly be changed as we read through the
		// gif's frames and come across non-restoreToPrevious disposal method frames.
		BufferedImage lastFullFrame = null;

		// will hold the size of the "canvas" which we will be drawing each frame into to generate complete
		// BufferedImage instances for each frames ImageFrame instance. this is the full width/height of the entire
		// animated gif
		int width = -1;
		int height = -1;

		// get the logical screen dimensions (canvas dimensions) and set the ImageFrame width/height's using it if it
		// is present at all. if not present, we will ignore for now
		// NOTE: it's very important that we prefer to use this section's width/height rather then
		//       the first frame's dimensions. it is not guaranteed that the first frame will be
		//       sized to the full image size (commonly it will be, but not always)
		IIOMetadata metadata = reader.getStreamMetadata();
		if (metadata != null) {
			IIOMetadataNode globalRoot = (IIOMetadataNode)metadata.getAsTree(metadata.getNativeMetadataFormatName());

			NodeList globalScreenDescriptor = globalRoot.getElementsByTagName("LogicalScreenDescriptor");

			if (globalScreenDescriptor != null && globalScreenDescriptor.getLength() > 0) {
				IIOMetadataNode screenDescriptor = (IIOMetadataNode)globalScreenDescriptor.item(0);

				if (screenDescriptor != null) {
					width = Integer.parseInt(screenDescriptor.getAttribute("logicalScreenWidth"));
					height = Integer.parseInt(screenDescriptor.getAttribute("logicalScreenHeight"));
				}
			}
		}

		// canvas image. this is going to be our "scratch space" which we will draw each frame into to generate a full
		// BufferedImage object for. this is necessary because some types of animation will specify some frames as
		// smaller images which need to be rendered at certain positions on top of the previous frame, so having a
		// canvas to draw on makes generating the full image for each frame much simpler
		BufferedImage canvas = null;
		Graphics2D canvasGraphics = null;

		for (int frameIndex = 0; ; frameIndex++) {
			BufferedImage image;
			try {
				image = reader.read(frameIndex);
			} catch (IndexOutOfBoundsException io) {
				// no more frames
				break;
			}

			// if there was no logical screen descriptor, then will not have gotten any image dimensions by the
			// time we're here reading the first animation frame, so we can just use the first frame's dimensions,
			// after which we won't care about these anymore.
			if (width == -1 || height == -1) {
				width = image.getWidth();
				height = image.getHeight();
			}

			if (canvas == null) {
				// initialize our canvas image. we do this here because the only place we are 100% guaranteed to
				// have the full animated gifs dimensions at is after we have read the first frame of animation,
				// because the LogicalScreenDescriptor might not have had it, forcing us to wait until this point.
				canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				canvasGraphics = canvas.createGraphics();
				canvasGraphics.setBackground(new Color(0, 0, 0, 0));
			}

			// get this frame's GraphicControlExtension which has various properties we need about this frame
			IIOMetadata frameMetadata = reader.getImageMetadata(frameIndex);
			IIOMetadataNode root = (IIOMetadataNode)frameMetadata.getAsTree(frameMetadata.getNativeMetadataFormatName());
			IIOMetadataNode gce = (IIOMetadataNode)root.getElementsByTagName("GraphicControlExtension").item(0);

			// delay specified as 1/100 of a second (*10 to get in milliseconds). we handle the special zero case
			// after the loop is done, for now treat them as any other frame
			int delay = Integer.valueOf(gce.getAttribute("delayTime"));

			String disposal = gce.getAttribute("disposalMethod");

			// get the offset to render this frame's BufferedImage at on the canvas (this frame's image might be a
			// smaller image that is to overlap with the previous frame)
			int x = 0;
			int y = 0;
			NodeList children = root.getChildNodes();
			for (int nodeIndex = 0; nodeIndex < children.getLength(); nodeIndex++) {
				Node nodeItem = children.item(nodeIndex);
				if (nodeItem.getNodeName().equals("ImageDescriptor")) {
					NamedNodeMap map = nodeItem.getAttributes();
					x = Integer.valueOf(map.getNamedItem("imageLeftPosition").getNodeValue());
					y = Integer.valueOf(map.getNamedItem("imageTopPosition").getNodeValue());
				}
			}

			// draw this frame into our canvas
			canvasGraphics.drawImage(image, x, y, null);

			// invoke the passed Clojure function given passing the delay and the current canvas image which will
			// contain a copy of this frame's "full" image. skip over this for zero-delay frames, which are just
			// intermediate frames to "prep" the canvas for subsequent frames (i guess as a way to clear/fill the
			// background for a bunch of upcoming frames which don't fill the entire canvas? anyway, we don't need them
			// anymore as they aren't meant to be displayed).
			// More info: http://www.imagemagick.org/Usage/anim_basics/#zero
			if (delay > 0)
				fn.invoke(canvas, delay);

			// handle certain disposal methods
			if (disposal.equals("restoreToPrevious")) {
				// "When the current image is finished, return the canvas to what it looked like before the image was
				//  overlaid. If the previous frame image also used a ['restoreToPrevious'] disposal method, then the
				//  result will be that same as what it was before that frame.. etc.. etc.. etc..."
				// -- http://www.imagemagick.org/Usage/anim_basics/#dispose

				// reset the canvas
				canvas = new BufferedImage(lastFullFrame.getColorModel(), lastFullFrame.copyData(null), lastFullFrame.isAlphaPremultiplied(), null);
				canvasGraphics = canvas.createGraphics();
				canvasGraphics.setBackground(new Color(0, 0, 0, 0));

			} else if (disposal.equals("restoreToBackgroundColor")) {
				// "When the time delay is finished for a particular frame, the area that was overlaid by that frame
				//  is cleared. Not the whole canvas, just the area that was overlaid. Once that is done then the
				//  resulting canvas is what is passed to the next frame of the animation, to be overlaid by that
				//  frames image."
				// -- http://www.imagemagick.org/Usage/anim_basics/#dispose

				// clears the region of the canvas that was overlapped by this frame's image, such that the canvas is
				// ready for the next frame
				canvasGraphics.clearRect(x, y, image.getWidth(), image.getHeight());
			}

			// keep a copy of the current canvas image if this frame can be used to recover from a "restoreToPrevious"
			// disposal method in a future frame
			if (!disposal.equals("restoreToPrevious") || lastFullFrame == null)
				lastFullFrame = new BufferedImage(canvas.getColorModel(), canvas.copyData(null), canvas.isAlphaPremultiplied(), null);;
		}

		reader.dispose();
	}
}
