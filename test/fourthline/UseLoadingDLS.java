/*
 * Copyright (C) 2015 たんらる
 */

package fourthline;

import java.io.File;
import java.io.IOException;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.sampled.LineUnavailableException;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import fourthline.mabiicco.midi.InstClass;
import fourthline.mabiicco.midi.InstType;
import fourthline.mabiicco.midi.MabiDLS;

abstract public class UseLoadingDLS extends FileSelect {
	@BeforeClass
	public static void initializeDefaultDLS() {
		InstClass.debug = true;
		try {
			MabiDLS midi = MabiDLS.getInstance();
			if (midi.getAvailableInstByInstType(InstType.MAIN_INST_LIST).length == 0) {
				midi.initializeMIDI();
				midi.loadingDLSFile(new File(MabiDLS.DEFALUT_DLS_PATH));
			}
		} catch (IOException | MidiUnavailableException | InvalidMidiDataException | LineUnavailableException e) {
			throw new AssertionError();
		}
	}

	@AfterClass
	public static void cleanupDLS() {
		InstClass.debug = false;
	}
}
