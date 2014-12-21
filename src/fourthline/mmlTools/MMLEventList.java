/*
 * Copyright (C) 2013-2014 たんらる
 */

package fourthline.mmlTools;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import fourthline.mmlTools.core.MMLTicks;
import fourthline.mmlTools.parser.MMLEventParser;


/**
 * 1行のMMLデータを扱います.
 */
public final class MMLEventList implements Serializable, Cloneable {
	private static final long serialVersionUID = -1430758411579285535L;

	private List<MMLNoteEvent>   noteList   = new ArrayList<>();
	private List<MMLTempoEvent>  tempoList;

	/**
	 * 
	 * @param mml
	 */
	public MMLEventList(String mml) {
		this(mml, null);
	}

	public MMLEventList(String mml, List<MMLTempoEvent> globalTempoList) {
		if (globalTempoList != null) {
			tempoList = globalTempoList;
		} else {
			tempoList = new ArrayList<MMLTempoEvent>();
		}

		parseMML(mml);
	}

	private void parseMML(String mml) {
		MMLEventParser parser = new MMLEventParser(mml);

		while (parser.hasNext()) {
			MMLEvent event = parser.next();

			if (event instanceof MMLTempoEvent) {
				((MMLTempoEvent) event).appendToListElement(tempoList);
			} else if (event instanceof MMLNoteEvent) {
				if (((MMLNoteEvent) event).getNote() >= 0) {
					noteList.add((MMLNoteEvent) event);
				}
			}
		}
	}

	public void setGlobalTempoList(List<MMLTempoEvent> globalTempoList) {
		tempoList = globalTempoList;
	}

	public List<MMLTempoEvent> getGlobalTempoList() {
		return tempoList;
	}

	public long getTickLength() {
		if (noteList.size() > 0) {
			int lastIndex = noteList.size() - 1;
			MMLNoteEvent lastNote = noteList.get( lastIndex );

			return lastNote.getEndTick();
		} else {
			return 0;
		}
	}

	public List<MMLNoteEvent> getMMLNoteEventList() {
		return noteList;
	}

	private Object nextEvent(Iterator<? extends MMLEvent> iterator) {
		if (iterator.hasNext()) {
			return iterator.next();
		} else {
			return null;
		}
	}

	/**
	 * 指定したtickOffset位置にあるNoteEventを検索します.
	 * @param tickOffset
	 * @return 見つからなかった場合は、nullを返します.
	 */
	public MMLNoteEvent searchOnTickOffset(long tickOffset) {
		for (MMLNoteEvent noteEvent : noteList) {
			if (noteEvent.getTickOffset() <= tickOffset) {
				if (tickOffset < noteEvent.getEndTick()) {
					return noteEvent;
				}
			} else {
				break;
			}
		}

		return null;
	}

	/**
	 * 指定したtickOffset位置の手前のNoteを検索します.
	 * @param tickOffset
	 * @return
	 */
	public MMLNoteEvent searchPrevNoteOnTickOffset(long tickOffset) {
		MMLNoteEvent prevNote = new MMLNoteEvent(-1, 0, 0);
		for (MMLNoteEvent noteEvent : noteList) {
			if (noteEvent.getTickOffset() >= tickOffset) {
				break;
			}
			prevNote = noteEvent;
		}
		return prevNote;
	}

	/**
	 * 指定したtickOffset位置のparsed-MML文字列に対するIndexを取得します.
	 * @param tickOffset
	 * @return
	 */
	public int[] indexOfMMLString(long tickOffset) {
		int start = 0;
		for (MMLNoteEvent noteEvent : noteList) {
			int index[] = noteEvent.getIndexOfMMLString();
			if (noteEvent.getTickOffset() <= tickOffset) {
				if (tickOffset < noteEvent.getEndTick()) {
					return index;
				}
			} else {
				return new int[] { start, index[0] };
			}
			start = index[1];
		}
		return new int[] { start, start };
	}

	/**
	 * ノートイベントを追加します.
	 * TODO: MMLNoteEvent のメソッドのほうがいいかな？Listを引数として渡す.
	 * @param addNoteEvent
	 */
	public void addMMLNoteEvent(MMLNoteEvent addNoteEvent) {
		int i;
		if ((addNoteEvent.getNote() < 0) || (addNoteEvent.getTick() <= 0) || (addNoteEvent.getEndTick() <= 0)) {
			return;
		}
		int offset = addNoteEvent.getTickOffset();
		if (offset < 0) {
			addNoteEvent.setTick( (addNoteEvent.getTick() + offset) );
			addNoteEvent.setTickOffset(0);
		}

		// 追加したノートイベントに重なる前のノートを調節します.
		for (i = 0; i < noteList.size(); i++) {
			MMLNoteEvent noteEvent = noteList.get(i);
			int tickOverlap = noteEvent.getEndTick() - addNoteEvent.getTickOffset();
			if (addNoteEvent.getTickOffset() < noteEvent.getTickOffset()) {
				break;
			}
			if (tickOverlap >= 0) {
				// 追加するノートに音が重なっている.
				int tick = noteEvent.getTick() - tickOverlap;
				if (tick == 0) {
					noteList.remove(i);
					break;
				} else {
					noteEvent.setTick(tick);
					i++;
					break;
				}
			}
		}

		// ノートイベントを追加します.
		noteList.add(i++, addNoteEvent);

		// 追加したノートイベントに重なっている後続のノートを削除します.
		for ( ; i < noteList.size(); ) {
			MMLNoteEvent noteEvent = noteList.get(i);
			int tickOverlap = addNoteEvent.getEndTick() - noteEvent.getTickOffset();

			if (tickOverlap > 0) {
				noteList.remove(i);
			} else {
				break;
			}
		}
	}

	/**
	 * 指定のMMLeventを削除する.
	 * 最後尾はtrim.
	 * @param deleteItem
	 */
	public void deleteMMLEvent(MMLEvent deleteItem) {
		noteList.remove(deleteItem);
	}

	/**
	 * 指定されたノートに音量コマンドを設定する.
	 * 後続の同音量のノートも更新する.
	 */
	public void setVelocityCommand(MMLNoteEvent targetNote, int velocity) {
		setUnsetVelocityCommand(targetNote, velocity, true);
	}

	/**
	 * 指定されたノートに音量コマンドを解除する.
	 * 後続の同音量のノートも更新する.
	 */
	public void unsetVelocityCommand(MMLNoteEvent targetNote) {
		setUnsetVelocityCommand(targetNote, 0, false);
	}

	private void setUnsetVelocityCommand(MMLNoteEvent targetNote, int velocity, boolean isON) {
		int beforeVelocity = targetNote.getVelocity();
		int prevVelocity = MMLNoteEvent.INIT_VOL;
		for (MMLNoteEvent note : noteList) {
			if (note.getTickOffset() >= targetNote.getTickOffset()) {
				if (beforeVelocity == note.getVelocity()) {
					note.setVelocity(isON ? velocity : prevVelocity);
				} else {
					break;
				}
			} else {
				prevVelocity = note.getVelocity();
			}
		}
	}

	public String toMMLString() throws UndefinedTickException {
		return toMMLString(false, 0, true);
	}

	public String toMMLString(boolean withTempo, boolean mabiTempo) throws UndefinedTickException {
		return toMMLString(withTempo, 0, mabiTempo);
	}

	private MMLNoteEvent insertTempoMML(StringBuilder sb, MMLNoteEvent prevNoteEvent, MMLTempoEvent tempoEvent, boolean mabiTempo)
			throws UndefinedTickException {
		if (prevNoteEvent.getEndTick() != tempoEvent.getTickOffset()) {
			int tickLength = tempoEvent.getTickOffset() - prevNoteEvent.getEndTick();
			int tickOffset = prevNoteEvent.getEndTick();
			int note = prevNoteEvent.getNote();
			// FIXME: 最後の1つのrだけに細工すればよいね.
			if (mabiTempo) {
				// マビノギ補正（rrrT***N の処理
				MMLTicks ticks = new MMLTicks("c", tickLength, false);
				if (prevNoteEvent.getVelocity() != 0) {
					sb.append("v0");
				}
				sb.append(ticks.toMMLText());
				prevNoteEvent = new MMLNoteEvent(note, tickLength, tickOffset, 0);
			} else {
				MMLTicks ticks = new MMLTicks("r", tickLength, false);
				prevNoteEvent = new MMLNoteEvent(prevNoteEvent.getNote(), tickLength, tickOffset, prevNoteEvent.getVelocity());
				sb.append(ticks.toMMLText());
			}
		}
		sb.append(tempoEvent.toMMLString());

		return prevNoteEvent;
	}

	/**
	 * テンポ出力を行うかどうかを指定してMML文字列を作成する.
	 * TODO: 長いなぁ。
	 * @param withTempo trueを指定すると、tempo指定を含むMMLを返します.
	 * @param totalTick 最大tick長. これに満たない場合は、末尾を休符分で埋めます.
	 * @return
	 */
	public String toMMLString(boolean withTempo, int totalTick, boolean mabiTempo)
			throws UndefinedTickException {
		//　テンポ
		Iterator<MMLTempoEvent> tempoIterator = null;
		MMLTempoEvent tempoEvent = null;
		tempoIterator = tempoList.iterator();
		tempoEvent = (MMLTempoEvent) nextEvent(tempoIterator);

		StringBuilder sb = new StringBuilder();
		int noteCount = noteList.size();

		// initial note: octave 4, tick 0, offset 0, velocity 8
		MMLNoteEvent noteEvent = new MMLNoteEvent(12*4, 0, 0, MMLNoteEvent.INITIAL_VOLUMN);
		MMLNoteEvent prevNoteEvent = noteEvent;
		for (int i = 0; i < noteCount; i++) {
			noteEvent = noteList.get(i);

			// テンポのMML挿入判定
			while ( (tempoEvent != null) && (tempoEvent.getTickOffset() <= noteEvent.getTickOffset()) ) {
				if (withTempo) {
					// tempo挿入 (rrrT***N の処理)
					prevNoteEvent = insertTempoMML(sb, prevNoteEvent, tempoEvent, mabiTempo);
				}
				tempoEvent = (MMLTempoEvent) nextEvent(tempoIterator);
			}

			// endTickOffsetがTempoを跨いでいたら、'&'でつなげる.
			if ( (tempoEvent != null) && (noteEvent.getTickOffset() < tempoEvent.getTickOffset()) && (tempoEvent.getTickOffset() < noteEvent.getEndTick()) ) {
				int tick = tempoEvent.getTickOffset() - noteEvent.getTickOffset();
				int tick2 = noteEvent.getTick() - tick;

				MMLNoteEvent divNoteEvent = new MMLNoteEvent(noteEvent.getNote(), tick, noteEvent.getTickOffset(), noteEvent.getVelocity());
				sb.append( divNoteEvent.toMMLString(prevNoteEvent) );

				if (withTempo) {
					sb.append( tempoEvent.toMMLString() );
				}
				tempoEvent = (MMLTempoEvent) nextEvent(tempoIterator);

				divNoteEvent.setTick(tick2);
				sb.append('&').append( divNoteEvent.toMMLString() );
			} else {
				sb.append( noteEvent.toMMLString(prevNoteEvent) );
			}
			prevNoteEvent = noteEvent;
		}

		// テンポがまだ残っていれば、その分をつなげる.
		while ( (tempoEvent != null) ) {
			if (withTempo) {
				// tempo挿入 (rrrT***N の処理)
				prevNoteEvent = insertTempoMML(sb, prevNoteEvent, tempoEvent, mabiTempo);
			}
			tempoEvent = (MMLTempoEvent) nextEvent(tempoIterator);
		}

		return sb.toString();
	}

	@Override
	public String toString() {
		return tempoList.toString() + noteList.toString();
	}

	@Override
	public MMLEventList clone() {
		try {
			MMLEventList obj = (MMLEventList) super.clone();
			obj.noteList = new ArrayList<>();
			obj.tempoList = new ArrayList<>(tempoList);
			for (MMLNoteEvent note : noteList) {
				obj.noteList.add(note.clone());
			}
			return obj;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	public int getAlignmentStartTick(MMLEventList list2, int tickOffset) {
		MMLNoteEvent target = list2.searchOnTickOffset(tickOffset);
		if ( (target == null) || (target.getTickOffset() == tickOffset) ) {
			return tickOffset;
		} else {
			return list2.getAlignmentStartTick(this, target.getTickOffset());
		}
	}

	public int getAlignmentEndTick(MMLEventList list2, int endTick) {
		MMLNoteEvent target = list2.searchOnTickOffset(endTick-1);
		if ( (target == null) || (target.getEndTick() == endTick) ) {
			return endTick;
		} else {
			return list2.getAlignmentEndTick(this, target.getEndTick());
		}
	}

	public void swap(MMLEventList list2, int startTick, int endTick) {
		List<MMLNoteEvent> tmp1 = new ArrayList<>();
		List<MMLNoteEvent> tmp2 = new ArrayList<>();
		for (MMLNoteEvent noteEvent : noteList) {
			if ( (noteEvent.getTickOffset() >= startTick) && (noteEvent.getEndTick() <= endTick) ) {
				tmp1.add(noteEvent);
			}
		}
		for (MMLNoteEvent noteEvent : tmp1) {
			noteList.remove(noteEvent);
		}

		for (MMLNoteEvent noteEvent : list2.getMMLNoteEventList()) {
			if ( (noteEvent.getTickOffset() >= startTick) && (noteEvent.getEndTick() <= endTick) ) {
				tmp2.add(noteEvent);
			}
		}
		for (MMLNoteEvent noteEvent : tmp2) {
			list2.getMMLNoteEventList().remove(noteEvent);
			addMMLNoteEvent(noteEvent);
		}
		for (MMLNoteEvent noteEvent : tmp1) {
			list2.addMMLNoteEvent(noteEvent);
		}
	}

	public void move(MMLEventList list2, int startTick, int endTick) {
		List<MMLNoteEvent> tmp1 = new ArrayList<>();
		for (MMLNoteEvent noteEvent : noteList) {
			if ( (noteEvent.getTickOffset() >= startTick) && (noteEvent.getEndTick() <= endTick) ) {
				tmp1.add(noteEvent);
			}
		}
		for (MMLNoteEvent noteEvent : tmp1) {
			deleteMMLEvent(noteEvent);
			list2.addMMLNoteEvent(noteEvent);
		}
	}

	public void copy(MMLEventList list2, int startTick, int endTick) {
		for (MMLNoteEvent noteEvent : noteList) {
			if ( (noteEvent.getTickOffset() >= startTick) && (noteEvent.getEndTick() <= endTick) ) {
				list2.addMMLNoteEvent( noteEvent.clone() );
			}
		}
	}
}
