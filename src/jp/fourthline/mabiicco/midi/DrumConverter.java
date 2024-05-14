/*
 * Copyright (C) 2024 たんらる
 */

package jp.fourthline.mabiicco.midi;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import jp.fourthline.mabiicco.AppResource;
import jp.fourthline.mabiicco.MabiIccoProperties;
import jp.fourthline.mabiicco.ui.IMMLManager;
import jp.fourthline.mabiicco.ui.UIUtils;
import jp.fourthline.mabiicco.ui.editor.MMLScoreUndoEdit;
import jp.fourthline.mmlTools.MMLTrack;
import jp.fourthline.mmlTools.core.ResourceLoader;
import jp.fourthline.mmlTools.parser.MMLEventParser;

/**
 * ドラム変換 (General MIDI -> Mabi)
 */
public final class DrumConverter {
	private final Map<KeyMap, KeyMap> defaultMap = new HashMap<>();
	private final Map<KeyMap, KeyMap> drumMap = new HashMap<>();   // mid -> mabi
	private final Map<Integer, KeyMap> mabiMap = new TreeMap<>();  // note -> mabi
	private final Map<Integer, KeyMap> midMap = new TreeMap<>();   // note -> mid

	private static final String X_NOTE = "O3D";
	private static final String X_NAME = "Snare ghost";

	private static class KeyMap implements Comparable<KeyMap> {
		private final int key;
		private final String keyName;
		private final Optional<String> name;

		private KeyMap(String key, String name, InstClass inst) {
			this.keyName = key;
			if (key.startsWith("O")) {
				this.key = MMLEventParser.firstNoteNumber(key);
			} else {
				this.key= Integer.parseInt(key);
			}

			if ((inst == null) || (inst.isValid(this.key))) {
				this.name = Optional.of(name);
			} else {
				this.name = Optional.empty();
			}
		}

		public String getName() {
			if (name.isPresent()) {
				return name.get();
			}
			return "-";
		}
		@Override
		public int hashCode() {
			return key;
		}

		@Override
		public int compareTo(KeyMap o) {
			return key - o.key;
		}

		@Override
		public String toString() {
			var str = keyName;
			if (name.isPresent()) {
				str += ": " + name.get();
			}
			return str;
		}
	}

	private static DrumConverter instance = null;
	public static DrumConverter getInstance() {
		if (instance == null) {
			instance = new DrumConverter();
		}
		return instance;
	}

	private final InstClass inst = MabiDLS.getInstance().getAvailableInstByInstType(List.of(InstType.DRUMS))[0];

	private DrumConverter() {
		ResourceBundle instPatch = ResourceBundle.getBundle("midDrum_mabiDrum", new ResourceLoader());
		instPatch.keySet().stream().sorted().forEach(key -> {
			String lineStr = instPatch.getString(key);
			String s[] = lineStr.split("\t");
			String mabiDrumNote = "O" + s[2] + s[3].replace("b", "-").replace("#", "+");
			var midEntry = new KeyMap(key, s[0], null);
			var mabiEntry = new KeyMap(mabiDrumNote, s[4], inst);
			if (mabiMap.containsKey(mabiEntry.key)) {
				mabiEntry = mabiMap.get(mabiEntry.key);
			} else {
				mabiMap.put(mabiEntry.key, mabiEntry);
			}
			if (!midMap.containsKey(midEntry.key)) {
				drumMap.put(midEntry, mabiEntry);
				midMap.put(midEntry.key, midEntry);
			}
		});

		var n = new KeyMap(X_NOTE, X_NAME, null);
		if (!mabiMap.containsKey(n.key)) {
			mabiMap.put(n.key, n);
		}
		drumMap.put(null, n);
		defaultMap.putAll(drumMap);

		// カスタム設定のロード.
		loadData();

		// 不足しているMabiMapをここで作ってしまえばよいのでは.
		var key = List.of("C", "C+", "D", "D+", "E", "F", "F+", "G", "G+", "A", "A+", "B");
		for (int i = 2; i < 7; i++) {
			for (var t : key) {
				var m = new KeyMap("O"+i+t, "<UNKNOWN>", inst);
				if (!mabiMap.containsKey(m.key)) {
					if (m.name.isPresent()) {
						// 音データがあるけど名前がわからない.
						throw new IllegalStateException("unknown: " + key);
					}
					mabiMap.put(m.key, m);
				}
			}
		}
	}

	/**
	 * DrumConvertの対象となるドラムタイムの楽器であるかどうかを判定する.
	 * @param track
	 * @return
	 */
	public static boolean isDrumTrack(MMLTrack track) {
		if (track == null) {
			return false;
		}
		var type = MabiDLS.getInstance().getInstByProgram(track.getProgram()).getType();
		return (type == InstType.DRUMS);
	}

	public void midDrum2MabiDrum(IMMLManager mmlManager) {
		var track = mmlManager.getActiveTrack();
		if (isDrumTrack(track)) {
			for (var eventList : track.getMMLEventList()) {
				for (var noteEvent : eventList.getMMLNoteEventList()) {
					var key = midMap.get(noteEvent.getNote());
					var item = drumMap.get(key);
					noteEvent.setNote(item.key);
				}
			}
			mmlManager.updateActivePart(true);
		}
	}

	private void saveData() {
		var p = new Properties();
		defaultMap.forEach((mid, mabi) -> {
			var v = drumMap.get(mid);
			if (!mabi.equals(v)) {
				int key = Integer.MIN_VALUE;
				if (mid != null) {
					key = mid.key;
				}
				p.setProperty(Integer.toString(key), Integer.toString(v.key));
			}
		});
		ByteArrayOutputStream bstream = new ByteArrayOutputStream();
		try {
			p.store(bstream, "");
		} catch (IOException e) {
			e.printStackTrace();
		}

		MabiIccoProperties.getInstance().drumConvertCustomMap.set(MMLScoreUndoEdit.compress(bstream.toString()));
	}

	private void loadData() {
		String str = MabiIccoProperties.getInstance().drumConvertCustomMap.get();
		if (str.length() == 0) return;
		String data = MMLScoreUndoEdit.decompress(str);
		if (data == null) return;
		ByteArrayInputStream istream = new ByteArrayInputStream(data.getBytes());
		var p = new Properties();
		try {
			p.load(istream);
			p.forEach((a, b) -> {
				if (a instanceof String mid) {
					if (b instanceof String mabi) {
						int midKey = Integer.parseInt(mid);
						int mabiKey = Integer.parseInt(mabi);
						var m1 = midKey == Integer.MIN_VALUE ? null : midMap.get(midKey);
						var m2 = mabiMap.get(mabiKey);
						drumMap.put(m1, m2);
					}
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static class Editor extends JPanel {
		private static final long serialVersionUID = -7937826516875634485L;
		private final JComboBox<KeyMap> combo = new JComboBox<>();
		private final JLabel midLabel = new JLabel();
		private final JLabel mabiLabel = new JLabel();

		private String mabiName(KeyMap mid, KeyMap mabi) {
			boolean isDefault = c.defaultMap.get(mid).key == mabi.key;
			String str = mabi.getName();
			if (!isDefault) {
				str += "  (*)";
			}
			return str;
		}

		private final DrumConverter c = DrumConverter.getInstance();
		private final JTable table;
		private final Vector<Vector<Object>> list = new Vector<>();

		private KeyMap getKeyMap(int row) {
			var item = list.get(row);
			var key = item.get(0);
			KeyMap mid = null;
			if (!"-".equals(key)) {
				mid = c.midMap.get(key);
			}
			return mid;
		}

		private Editor() {
			super(new BorderLayout());

			c.midMap.forEach((key, value) -> {
				var mid = value;
				var mabi = c.drumMap.get(value);
				list.add(new Vector<>(List.of(mid.key, mid.getName(), mabi.keyName, mabiName(mid, mabi))));
			});
			var noMapItem = c.drumMap.get(null);
			list.add(new Vector<>(List.of("-", "-", noMapItem.key, mabiName(null, noMapItem))));

			table = UIUtils.createTable(list, new Vector<>(List.of("GM key", "GM name", "Mabi key", "Mabi name")));
			table.getColumnModel().getColumn(0).setMinWidth(100);
			table.getColumnModel().getColumn(0).setMaxWidth(100);
			table.getColumnModel().getColumn(2).setMinWidth(100);
			table.getColumnModel().getColumn(2).setMaxWidth(100);
			table.setRowSelectionAllowed(true);
			table.getSelectionModel().addListSelectionListener(t -> updateCombo());

			// combo
			combo.setEnabled(false);
			JPanel comboPanel = UIUtils.createTitledPanel("edit map", new BorderLayout());
			JPanel comboButtonPanel = new JPanel();
			JButton c1 = new JButton("Default");
			c1.addActionListener(t -> setDefaultMap());
			JButton c2 = new JButton("Apply");
			c2.addActionListener(t -> changeMap());
			comboButtonPanel.add(c1);
			comboButtonPanel.add(c2);
			JPanel cp1 = new JPanel(new BorderLayout());
			cp1.add(new JLabel("MIDI: "), BorderLayout.WEST);
			cp1.add(midLabel, BorderLayout.CENTER);
			cp1.add(new JButton("S1"), BorderLayout.EAST);
			JPanel cp2 = new JPanel(new BorderLayout());
			cp2.add(new JLabel("現在: "), BorderLayout.WEST);
			cp2.add(mabiLabel, BorderLayout.CENTER);
			cp2.add(new JButton("S2"), BorderLayout.EAST);
			JPanel cp3 = new JPanel(new BorderLayout());
			cp3.add(new JLabel("変更: "), BorderLayout.WEST);
			cp3.add(combo, BorderLayout.CENTER);
			var b3 = new JButton("S3");
			b3.addActionListener(t -> {
				if (combo.getSelectedItem() instanceof KeyMap mabi) {
					playNote(MabiDLS.DLS_BANK, c.inst.getProgram(), mabi.key);
				}
			});
			cp3.add(b3, BorderLayout.EAST);
			JPanel cp4 = new JPanel(new BorderLayout());
			cp4.add(cp1, BorderLayout.NORTH);
			cp4.add(cp2, BorderLayout.CENTER);
			cp4.add(cp3, BorderLayout.SOUTH);

			comboPanel.add(comboButtonPanel, BorderLayout.SOUTH);
			comboPanel.add(cp4, BorderLayout.CENTER);

			// layout
			JScrollPane scrollPane = new JScrollPane(table);
			scrollPane.setPreferredSize(new Dimension(600, 400));

			JLabel gmDesc = new JLabel(" * GM: General MIDI");
			JPanel panel = new JPanel(new BorderLayout());
			panel.add(scrollPane, BorderLayout.CENTER);
			panel.add(gmDesc, BorderLayout.SOUTH);

			add(panel, BorderLayout.CENTER);
			add(comboPanel, BorderLayout.SOUTH);
		}

		private void playNote(int bank, int program, int note) {
			System.out.println(" > " + bank + " " + program + " " + note);
			MabiDLS.getInstance().playCustom(note, 100, bank, program);
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {}
			MabiDLS.getInstance().playCustom(-1, 0, bank, program);
		}

		private void updateCombo() {
			int row = table.getSelectedRow();
			var mid = getKeyMap(row);
			var mabi = c.drumMap.get(mid);
			midLabel.setText(mid == null ? "-" : mid.toString());
			mabiLabel.setText(mabi.toString());
			combo.removeAllItems();
			c.mabiMap.entrySet().forEach(m -> combo.addItem(m.getValue()));
			combo.setSelectedItem(c.drumMap.get(mid));
			combo.setEnabled(true);
		}

		private void setKeyMap(int row, KeyMap mid, KeyMap mabi) {
			c.drumMap.put(mid, mabi);
			table.getModel().setValueAt(mabi.keyName, row, 2);
			table.getModel().setValueAt(mabiName(mid, mabi), row, 3);
		}

		private void setDefaultMap() {
			int row = table.getSelectedRow();
			var mid = getKeyMap(row);
			var mabi = c.defaultMap.get(mid);
			setKeyMap(row, mid, mabi);
			c.saveData();
		}

		private void changeMap() {
			if (combo.getSelectedItem() instanceof KeyMap mabi) {
				int row = table.getSelectedRow();
				var mid = getKeyMap(row);
				setKeyMap(row, mid, mabi);
				c.saveData();
			}
		}
	}

	public static void showConvertMap(Frame parentFrame) {
		var contents = new Editor();
		String title = AppResource.appText("menu.drum_converting_map");
		JOptionPane.showMessageDialog(parentFrame, contents, title, JOptionPane.PLAIN_MESSAGE);
	}
}
