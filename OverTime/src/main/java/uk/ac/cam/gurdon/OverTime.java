package uk.ac.cam.gurdon;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.Overlay;
import ij.gui.TextRoi;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.HyperStackConverter;

@Plugin(type = Command.class, menuPath = "Image>Hyperstacks>OverTime")
public class OverTime implements Command, ActionListener, ChangeListener, ItemListener {

	//constants
	private static final String[] UNIT = {"days", "hours", "minutes", "seconds", "milliseconds"};
	private static final String[] UNITABBREV = {"d", "h", "min", "s", "ms"};
	private static final String[] UNITREGEX = {"[Dd]ay.*?", "[Hh]o?u?r.*?", "[Mm]in.*?", "[Ss]ec.*?", "[Mm](illi)?s(econd)?s?"};
	private static final double[] SECPERUNIT =  {86400, 3600, 60, 1, 0.001};
	private static final int DAY = 0, HOUR = 1, MIN = 2, SEC = 3, MILLI = 4;
	private static final String[] FONT = {"Sans-Serif", "Serif", "Monospaced"};
	private static final String[] FORMAT = {"Short", "Abbreviated", "Long"};
	private static final int SHORT_FORMAT = 0, ABBREV_FORMAT = 1, LONG_FORMAT = 2;
	private static final String[] LOCATION = {"Top-left", "Top", "Top-right", "Right", "Bottom-right", "Bottom", "Bottom-left", "Left"};
	private static final int LOC_TL = 0, LOC_T = 1, LOC_TR = 2, LOC_R = 3, LOC_BR = 4, LOC_B = 5, LOC_BL = 6, LOC_L = 7;
	
	//params
	private String fontFamily;
	private int pt;
	private boolean bold;
	private boolean italic;
	private boolean aa;
	private int format;
	private int loc;
	private boolean[] use;
	
	//gui
	private JFrame gui;
	private JButton addButton, removeButton, rgbButton;
	private JComboBox<String> fontCombo, formatCombo, locationCombo;
	private JSpinner sizeSpinner;
	private ColourPanel colourChooser;
	private JCheckBox[] unitTick;
	private JCheckBox aaTick, boldTick, italicTick;
	
	
	public OverTime(){
		loadPrefs();
	}
	
	private JPanel makePanel(Object... things){
		JPanel pan = new JPanel();
		pan.setLayout(new FlowLayout(FlowLayout.CENTER,2,2));
		for(Object obj : things){
			if(obj instanceof JComponent){
				pan.add((JComponent)obj);
			}
			else if(obj instanceof String){
				pan.add(new JLabel((String)obj));
			}
			else if(obj instanceof Integer){
				pan.add(Box.createHorizontalStrut((int) obj));
			}
			else if(obj instanceof Color){
				pan.setBackground((Color) obj);
			}
			else if(obj instanceof JComponent[]){
				for(JComponent sub:(JComponent[])obj) pan.add(sub);
			}
		}
		return pan;
	}
	
	@Override
	public void run() {

		gui = new JFrame("OverTime");
		gui.setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("logo_icon.gif")));
		gui.setLayout(new BoxLayout(gui.getContentPane(), BoxLayout.Y_AXIS));
		
		locationCombo = new JComboBox<String>(LOCATION);
		locationCombo.setSelectedIndex(loc);
		gui.add(makePanel("Location:", locationCombo));
		
		
		colourChooser = new ColourPanel(this, 10, 6);
		gui.add(makePanel(colourChooser));
		
		
		gui.add(Box.createVerticalStrut(20));
		
		fontCombo = new JComboBox<String>(FONT);
		sizeSpinner = new JSpinner(new SpinnerNumberModel(pt,1,128,1));
		gui.add(makePanel("Font:", fontCombo, sizeSpinner));
		
		aaTick = new JCheckBox("Anti-alias", aa);
		boldTick = new JCheckBox("Bold", bold);
		italicTick = new JCheckBox("Italic", italic);
		gui.add(makePanel(aaTick,boldTick,italicTick));
		
		gui.add(Box.createVerticalStrut(20));
		
		formatCombo = new JComboBox<String>(FORMAT);
		formatCombo.setSelectedIndex(format);
		gui.add(makePanel("Format:",formatCombo));
		
		unitTick = new JCheckBox[UNIT.length];
		for(int u=0;u<unitTick.length;u++){
			unitTick[u] = new JCheckBox(UNITABBREV[u], use[u]);
		}
		gui.add(makePanel("Show:",unitTick));
	
		gui.add(Box.createVerticalStrut(20));
		
		addButton = new JButton("Add");
		removeButton = new JButton("Remove");
		rgbButton = new JButton("Create RGB");
		
		gui.add(makePanel(addButton, removeButton, rgbButton ));
		
		
		addButton.addActionListener(this);
		removeButton.addActionListener(this);
		rgbButton.addActionListener(this);
		
		fontCombo.addActionListener(this);
		sizeSpinner.addChangeListener(this);
		aaTick.addItemListener(this);
		boldTick.addItemListener(this);
		italicTick.addItemListener(this);
		formatCombo.addActionListener(this);
		locationCombo.addActionListener(this);
		for(int u=0;u<unitTick.length;u++){
			unitTick[u].addItemListener(this);
		}
		
		gui.pack();
		gui.setLocationRelativeTo(null);
		gui.setVisible(true);
		
		labelTime(false);
	}

	void labelTime(boolean makeRGB){
		if(gui==null||!gui.isVisible()) return;
		
		Color col = colourChooser.getColour();
		fontFamily = (String) fontCombo.getSelectedItem();
		pt = (int) sizeSpinner.getValue();
		bold = boldTick.isSelected();
		italic = italicTick.isSelected();
		aa = aaTick.isSelected();
		format = formatCombo.getSelectedIndex();
		loc = locationCombo.getSelectedIndex();
		
		int fontStyle = (bold?Font.BOLD:Font.PLAIN)+(italic?Font.ITALIC:Font.PLAIN);
		Font font = new Font(fontFamily, fontStyle, pt);
		Canvas canvas = new Canvas();
		FontMetrics fm = canvas.getFontMetrics(font);
		
		for(int u=0;u<use.length;u++){
			use[u] = unitTick[u].isSelected();
		}
		
		ImagePlus imp = WindowManager.getCurrentImage();
		if(imp==null){
			return;
		}

		Overlay ol = new Overlay();
		int W = imp.getWidth();
		int H = imp.getHeight();
		int C = imp.getNChannels();
		int Z = imp.getNSlices();
		int T = imp.getNFrames();
		Calibration cal = imp.getCalibration();
		double interval = cal.frameInterval;
		String unitT = cal.getTimeUnit();
		for(int t=0;t<T;t++){
			double nsec = -1;
			boolean gotCal = false;
			for(int u=0;u<UNITREGEX.length;u++){
				if(unitT.matches(UNITREGEX[u])){
					nsec = t*interval*SECPERUNIT[u];
					gotCal = true;
					break;
				}
			}
			if(!gotCal||nsec<0){
				JOptionPane.showMessageDialog(gui, "Unknown time calibration: "+interval+" "+unitT, "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}
			String label = formatSeconds(nsec, format);
			
			int strW = fm.stringWidth(label);
			int strH = fm.getHeight();
			
			Rectangle rect = null;
			if(imp.getRoi()!=null){
				rect = imp.getRoi().getBounds();
			}
			
			int x, y;
			if(rect!=null){		//use Roi if there was one, otherwise use selected location
				x = rect.x;
				y = rect.y;
			}
			else{
				switch(loc){
					case LOC_TL:
						x = (int) 0;
						y = (int) 0;
						break;
					case LOC_T:
						x = (int) ((0.5 * W) - (0.5 * strW));
						y = (int) 0;
						break;
					case LOC_TR:
						x = (int) (W - (strW));
						y = (int) 0;
						break;
					case LOC_R:
						x = (int) (W - (strW));
						y = (int) ((0.5 * H) - (0.5 * strH));
						break;
					case LOC_BR:
						x = (int) (W - (strW));
						y = (int) (H - strH);
						break;
					case LOC_B:
						x = (int) ((0.5 * W) - (0.5 * strW));
						y = (int) (H - strH);
						break;
					case LOC_BL:
						x = (int) 0;
						y = (int) (H - strH);
						break;
					case LOC_L:
						x = (int) 0;
						y = (int) ((0.5 * H) - (0.5 * strH));
						break;
					default:
						throw new IllegalArgumentException("Unknown location: "+loc);
				}
			}
			
			TextRoi text = new TextRoi(x, y, label);
			text.setCurrentFont(font);
			text.setStrokeColor(col);
			text.setAntialiased(aa);
			if(C>1||Z>1){
				text.setPosition(-1,-1,t+1);
			}
			else{
				text.setPosition(t+1);
			}
			ol.add(text);
		}
		imp.setOverlay(ol);
		if(makeRGB){
			ImagePlus flat = new Duplicator().run(imp);
			flat.setTitle(imp.getTitle()+"_RGB");
			flat.flattenStack();
			flat.show();
		}
		
		savePrefs();
		
	}
	

	
	private String formatSeconds(double nsec, int format){
		StringBuilder sb = new StringBuilder();
		for(int s=0;s<SECPERUNIT.length;s++){
			if(use[s]){
				int val = (int) (nsec/SECPERUNIT[s]);
				if(s==MILLI){	//milliseconds, last unit
					val = (int) Math.round(nsec/SECPERUNIT[s]);
				}
				if(sb.length()>0){
					if(format==SHORT_FORMAT) sb.append( (s==MILLI)?".":":" );
					else sb.append(" ");
				}
				if(s==MILLI&&val<100&&val>0)	sb.append("0");
				if(val<10)	sb.append("0"); 
				sb.append(val);
				if(format==ABBREV_FORMAT) sb.append(" "+UNITABBREV[s]);
				else if(format==LONG_FORMAT) sb.append(" "+UNIT[s]);
				nsec = nsec%SECPERUNIT[s];
			}
		}
		return sb.toString();
	}
	
	private void loadPrefs(){
		fontFamily = Prefs.get("OverTime.fontFamily", "SansSerif");
		pt = (int) Prefs.get("OverTime.pt", 18);
		bold = Prefs.get("OverTime.bold", false);
		italic = Prefs.get("OverTime.italic", false);
		aa = Prefs.get("OverTime.aa", true);
		format = (int) Prefs.get("OverTime.format", SHORT_FORMAT);
		loc = (int) Prefs.get("OverTime.loc", LOC_TL);
		use = new boolean[5];
		for(int i=0;i<5;i++){
			use[i] = Prefs.get("OverTime.use"+i, true);
		}
	}
	
	private void savePrefs(){
		Prefs.set("OverTime.fontFamily", fontFamily);
		Prefs.set("OverTime.pt", pt);
		Prefs.set("OverTime.bold", bold);
		Prefs.set("OverTime.italic", italic);
		Prefs.set("OverTime.aa", aa);
		Prefs.set("OverTime.format", format);
		Prefs.set("OverTime.loc", loc);
		for(int i=0;i<5;i++){
			Prefs.set("OverTime.use"+i, use[i]);
		}
	}
	
	@Override
	public void actionPerformed(ActionEvent ae) {
		if(ae.getSource() instanceof JButton){
			JButton src = ((JButton)ae.getSource());
			if(src==addButton){
				labelTime(false);
			}
			if(src==removeButton){
				ImagePlus imp = WindowManager.getCurrentImage();
				if(imp==null||imp.getOverlay()==null){
					return;
				}
				imp.setOverlay(null);
			}
			else if(src==rgbButton){
				labelTime(true);
			}
		}
		else{
			labelTime(false);
		}
	}
	
	@Override
	public void stateChanged(ChangeEvent ce) {
		labelTime(false);
	}
	
	@Override
	public void itemStateChanged(ItemEvent ie) {
		labelTime(false);
	}
	
	
	public static void main(String[] arg){

		ImageJ.main(arg);
		
		ImagePlus img = new ImagePlus("E:\\test data\\2d tracktest.tif");
		final ImagePlus image = HyperStackConverter.toHyperStack(img, img.getNChannels(), img.getNSlices(), img.getNFrames());
		image.setDisplayMode(IJ.GRAYSCALE);
		image.setPosition(1, 1, 3);
		image.show();
		
		new OverTime().run();
		
	}


	
}
