(ns clj-image2ascii.core
  (:import (javax.imageio ImageIO)
           (javax.imageio.stream ImageInputStream)
           (java.awt RenderingHints Graphics2D)
           (java.awt.image BufferedImage)
           (java.net URL)
           (java.io File InputStream)
           (clj_image2ascii.java ImageToAscii AnimatedGif ImageFrame)))

(defn get-image-by-url
  "returns a BufferedImage loaded from a URL pointing to an image, or null if an
   exception occurs"
  (^BufferedImage [^URL url]
   (try
     (ImageIO/read url)
     (catch Exception ex))))

(defn get-image-by-file
  "returns a BufferedImage loaded from an image file, or null if an exception occurs"
  (^BufferedImage [^File file]
   (try
     (ImageIO/read file)
     (catch Exception ex))))

(defn get-image-stream-by-url
  "returns an ImageInputStream loaded from a URL pointing to an image, or null if an
   exception occurs"
  (^ImageInputStream [^URL url]
   (try
     (let [^InputStream stream (.openStream url)]
       (ImageIO/createImageInputStream stream))
     (catch Exception ex))))

(defn get-image-stream-by-file
  "returns an ImageInputStream loaded from an image file, or null if an exception
   occurs"
  (^ImageInputStream [^File file]
   (try
     (ImageIO/createImageInputStream file)
     (catch Exception ex))))

(defn scale-image
  "takes a source BufferedImage and scales it proportionally using the new width,
   returning the scaled image as a new BufferedImage"
  (^BufferedImage [^BufferedImage image new-width]
   (let [new-height   (* (/ new-width (.getWidth image))
                         (.getHeight image))
         scaled-image (BufferedImage. new-width new-height BufferedImage/TYPE_INT_RGB)
         gfx2d        (doto (.createGraphics scaled-image)
                        (.setRenderingHint RenderingHints/KEY_INTERPOLATION
                                           RenderingHints/VALUE_INTERPOLATION_BILINEAR)
                        (.drawImage image 0 0 new-width new-height nil)
                        (.dispose))]
     scaled-image)))

(defn convert-image
  "converts a BufferedImage to an ASCII representation. scale-to-width is a new width
   in pixels to scale the image to, if specified. the scaling will be done such that
   the entire image is scaled proportionally and is done before the ASCII conversion
   is performed. the return value is a map containing:

   :width  - the width in pixels of the image that was converted
   :height - the height in pixels of the image that was converted
   :color? - indicates if the ASCII includes color information or not
   :image  - a string containing the image's ASCII representation. if color? is true,
             then this string will include HTML <span> tags wrapping each individual
             ASCII character with <br> tags between each line of 'pixels'. if false,
             then only the raw characters are included, with newline characters
             between each line of 'pixels'."
  ([^BufferedImage image color?]
   (convert-image image nil color?))
  ([^BufferedImage image scale-to-width color?]
   (let [current-width (.getWidth image)
         new-width     (or scale-to-width current-width)
         final-image   (if-not (= new-width current-width)
                         (scale-image image new-width)
                         image)]
     {:width  (.getWidth final-image)
      :height (.getHeight final-image)
      :color? (if color? true false)   ; forcing an explicit true/false because i am nitpicky like that
      :image  (ImageToAscii/convert final-image color?)})))

(defn- fix-gif-frame-delay [delay]
  ; based on the findings here: http://nullsleep.tumblr.com/post/16524517190/animated-gif-minimum-frame-delay-browser-compatibility
  ; basically, we should not allow any delay less then 0.02s (20ms) because none of the major browsers support it
  ; and this library is primarily intended for web use, where we want to mimic GIF-like playback (even though we
  ; won't be held by the same limitations since we will be using javascript for our animation)
  (let [ms (* delay 10)]
    (if (< ms 20)
      20
      ms)))

(defn- get-ascii-gif-frames [^ImageInputStream image-stream scale-to-width color?]
  (->> (AnimatedGif/read image-stream)
       (mapv
         (fn [^ImageFrame frame]
           (-> (.image frame)
               (convert-image scale-to-width color?)
               (assoc :delay (fix-gif-frame-delay (.delay frame))))))))

(defn convert-animated-gif-frames
  "converts an ImageInputStream created from an animated GIF to a series of ASCII
   frames representing each frame of animation in the source GIF. scale-to-width is
   a new width in pixels to scale the image to, if specified. the scaling will be
   done such that the entire image is scaled proportionally and is done before the
   ASCII conversion is performed. the return value is a map containing:

   :width  - the width in pixels of the image (and all frames) that was converted
   :height - the height in pixels of the image (and all frames) that was converted
   :color? - indicates if the ASCII includes color information or not
   :frames - a vector of maps, one per frame of animation in the order they should
             be played for proper animation

   each frame is a map containing:

   :delay  - the time in milliseconds that this frame should be displayed before
             the next frame is to be displayed
   :image  - a string containing the frame's ASCII representation. if color? is true,
             then this string will include HTML <span> tags wrapping each individual
             ASCII character with <br> tags between each line of 'pixels'. if false,
             then only the raw characters are included, with newline characters
             between each line of 'pixels'."
  ([^ImageInputStream image-stream color?]
   (convert-animated-gif-frames image-stream nil color?))
  ([^ImageInputStream image-stream scale-to-width color?]
   (let [frames (get-ascii-gif-frames image-stream scale-to-width color?)
         width  (-> frames first :width)
         height (-> frames first :height)]
     {:width  width
      :height height
      :color? (if color? true false)   ; forcing an explicit true/false because i am nitpicky like that
      :frames (mapv #(select-keys % [:image :delay]) frames)})))
