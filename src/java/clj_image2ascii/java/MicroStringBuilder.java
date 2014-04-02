package clj_image2ascii.java;

// I wanted a StringBuilder that had an append() method which allowed appending unsigned byte values
// as a 2-char hex representation (e.g. 255 => "FF") in a way that did not allocate any extra memory
// and could be done "in-place" with the StringBuilder's internal character array. This kind of evolved
// into this class being added since I was only using 2 append() methods from java.lang.StringBuilder
// anyway. Except for the appendAsHex(), this class does not perform noticeably faster then
// java.lang.StringBuilder does.

public class MicroStringBuilder {
	// copied from java.lang.Integer.digits (which is private so we can't just reference it, boourns)
	static final char[] digits = {
		'0' , '1' , '2' , '3' , '4' , '5' ,
		'6' , '7' , '8' , '9' , 'a' , 'b' ,
		'c' , 'd' , 'e' , 'f' , 'g' , 'h' ,
		'i' , 'j' , 'k' , 'l' , 'm' , 'n' ,
		'o' , 'p' , 'q' , 'r' , 's' , 't' ,
		'u' , 'v' , 'w' , 'x' , 'y' , 'z'
	};

	final char[] chars;
	int length;

	public MicroStringBuilder(int capacity) {
		chars = new char[capacity];
		length = 0;
	}

	public void append(final String s) {
		final int srcLength = s.length();
		s.getChars(0, srcLength, chars, length);
		length += srcLength;
	}

	public void append(final char c) {
		chars[length] = c;
		++length;
	}

	// modification of java.lang.Integer.toUnsignedString -- no garbage generated, but limited to max value
	// of 255 ...hence the 'unsigned byte' thing :)
	public void appendAsHex(int unsignedByte) {
		for (int i = 0; i < 2; ++i) {
			final int index = length + 1 - i;
			if (unsignedByte != 0) {
				chars[index] = digits[unsignedByte & 15];
				unsignedByte >>>= 4;
			} else
				chars[index] = '0';
		}
		length += 2;
	}

	@Override
	public String toString() {
		return new String(chars, 0, length);
	}
}
