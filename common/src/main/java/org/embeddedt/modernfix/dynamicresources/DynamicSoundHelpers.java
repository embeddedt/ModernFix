package org.embeddedt.modernfix.dynamicresources;

public class DynamicSoundHelpers {
	/**
	 * The duration until a sound is eligible for eviction if unused.
	 */
	public static final int MAX_SOUND_LIFETIME_SECS = 120;
	
	/**
	 * The max amount of sounds loaded
	 * This was chosen because Minecraft Java can play up to 255 sounds, with a bit of leeway for extra sounds being loaded in.
	 */
	public static final int MAX_SOUND_COUNT = 300;
}
