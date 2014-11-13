package hu.qgears.opengl.commons;

/**
 * Supported types of mipmaps
 * @author rizsi
 *
 */
public enum EMipMapType {
	/**
	 * No mipmap
	 */
	none,
	/**
	 * standard mipmap generated by the hardware.
	 * Implementation requires GL3.0!
	 */
	standard,
}
