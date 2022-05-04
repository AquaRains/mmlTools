/*
 * Copyright (C) 2015 たんらる
 */

package jp.fourthline.mabiicco.midi;

public interface IPlayNote {
	public void playNote(int note, int velocity);
	public void playNote(int note[], int velocity);
	public void offNote();
}
