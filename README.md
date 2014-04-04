# clj-image2ascii

Functions for turning images into ASCII equivalents. Largely inspired by
[Claskii](https://github.com/LauJensen/Claskii).

The same basic algorithm to convert pixels to ASCII characters has been taken from Claskii, but a bunch of
optimizations have been added and the code in general has been updated (Claskii was last updated ~4 years ago, Clojure
has evolved a bunch since then) and made a bit easier to use for general purposes and access the resulting ASCII
"image" and it's properties. Additionally, support for animated GIFs has been added, where each frame of animation is
extracted as a full image and then converted to ASCII so that rendering is dead-simple -- just flip the images, no need
to worry about animated GIF disposal methods and such.

This library is mostly just a Clojure wrapper around the core algorithms to convert single images to ASCII and the
animated GIF frame extraction code which are both written in Java for performance reasons. After much benchmarking
of a pure Clojure version of the `ImageToAscii.convert()` method and then trying out a Java equivalent out of
curiosity, I did find that the Java version was significantly faster, so I decided to go with it instead.

Also, credit to the Stack Overflow user SteveH for [this helpful post](http://stackoverflow.com/a/18425922) which
contained really great code for extracting animated GIF frames. This code is used, with a few minor tweaks, in
clj-image2ascii.

## Usage

!["clj-image2ascii version"](https://clojars.org/clj-image2ascii/latest-version.svg)

### Static Images

Converting a single static image is straightforward:

```clojure
(use 'clj-image2ascii.core)

(convert-image
  (get-image-by-url (java.net.URL. "http://i.imgur.com/KAzQTvR.png"))
  120
  false)
=> {:width 120, :height 109, :color? false, :image " <big long string of ASCII here> "}
```

`convert-image` takes a `BufferedImage` object. Two helper functions are provided to make it easy `get-image-by-url`
and `get-image-by-file` which take a `java.net.URL` and `java.io.File` object respectively.

The optional second argument allows you to specify a new width in pixels to scale the image to before it is converted
to ASCII. Scaling is done proportionally so the image won't be distorted. If you don't specify a new width, then no
scaling is performed.

The last argument is always a boolean to indicate if you want color information encoded into the resulting ASCII string
or not. Color information is added by wrapping each character in HTML `<span>` tags and `<br>` tags used for newlines.
No other HTML is added. When no color information is to be added, the resulting ASCII string will only contain ASCII
characters and newlines characters.

Note that converting with color will *significantly* increase the size of the returned ASCII string. Care should be
taken when converting large images without scaling but with color. For example, a 300x300 image converted with color
at it's original size will result in a ~3.8MB string.

### Animated GIFs

clj-image2ascii can extract all of the frames out of an animated GIF into separate ASCII "images." The return value
will also include the frame delay timings in milliseconds so you can easily perform your own ASCII animation by just
swapping frames. You do not need to worry about any of the details about how animated GIFs are encoded (disposal
methods, frames that need to be overlaid onto the previous frame, zero-delay frames, etc), the returned ASCII images
will be converted from each frame's complete image.

```clojure
(use 'clj-image2ascii.core)

(convert-animated-gif-frames
  (get-image-stream-by-url (java.net.URL. "http://i.imgur.com/DiGgmpA.gif"))
  120
  false)
=> {:width 120, :height 67, :color? false, :frames [{:delay 50, :image "..."} {:delay 50, :image "..."} ... ]}
```

The arguments to `convert-animated-gif-frames` work in exactly the same way as they do with `convert-image`.

The returned map contains all of the frames in a vector under `:frames` which are in the order they should be displayed
to be animated. Note that each frame contains it's own delay time (in milliseconds). In animated GIFs, each frame can
have a different delay and this information is kept intact during conversion. The `:image` key in each frame map
will contain the converted ASCII "image" string.

With animated GIFs especially you should be careful about converting with color information, as you can end up with
*extremely* big return values if the GIF is large.

## License

Distributed under the the MIT License. See LICENSE for more details.
