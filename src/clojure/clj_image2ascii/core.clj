(ns clj-image2ascii.core
  (:import (javax.imageio ImageIO)
           (javax.imageio.stream ImageInputStream)
           (java.awt RenderingHints Graphics2D)
           (java.awt.image BufferedImage)
           (java.net URL)
           (java.io File InputStream)
           (clj_image2ascii.java ImageToAscii AnimatedGif ImageFrame)))

(defn get-image-by-url
  "returns a BufferedImage loaded from the URL specified, or null if an error occurs"
  (^BufferedImage [^URL url]
   (try
     (ImageIO/read url)
     (catch Exception ex))))

(defn get-image-by-file
  "returns a BufferedImage loaded from the file specified, or null if an error occurs"
  (^BufferedImage [^File file]
   (try
     (ImageIO/read file)
     (catch Exception ex))))

(defn get-image-stream-by-url
  (^ImageInputStream [^URL url]
   (try
     (let [^InputStream stream (.openStream url)]
       (ImageIO/createImageInputStream stream))
     (catch Exception ex))))

(defn get-image-stream-by-file
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