package uk.ac.cam.gurdon;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.border.MatteBorder;


public class ColourPanel extends JPanel implements MouseListener{
	private static final long serialVersionUID = 5556785200493709559L;
	
	private OverTime parent;
	private Color sel;
	private ColourTile[] tiles;
	
	
	private class ColourTile extends JPanel{
		private static final long serialVersionUID = -984484355409585963L;
		private final Dimension DIM = new Dimension(20,20);
		
		public Color colour;
		private MatteBorder selBorder, offBorder;
		
		public ColourTile(Color col){
			this.colour = col;
			setBackground(colour);
			this.selBorder = BorderFactory.createMatteBorder(2,2,2,2,new Color(255-col.getRed(),255-col.getGreen(),255-col.getBlue()));
			setBorder(offBorder);
		}
		
		public void setSelected(boolean isit){
			if(isit){
				setBorder(selBorder);
			}
			else{
				setBorder(null);
			}
		}
		
		public Dimension getPreferredSize(){
			return DIM;
		}
		
	}
	
	public ColourPanel(OverTime parent, int cols, int rows){
		this.parent = parent;
		int n = cols*rows;
		Color[] COLOURS = new Color[n];
		for(int g=0;g<cols;g++){
			float v = g/(float)(cols-1);
			COLOURS[g] = Color.getHSBColor(0f, 0f, 1f-v);	//grayscale on first row
		}
		for(int i=cols;i<n;i++){
			COLOURS[i] = Color.getHSBColor((float) (i-cols) / (float) (n-cols+1), 1f, 1f);	//all other colours
		}
		
		setLayout( new GridLayout(rows, cols, 2, 2) );
		tiles = new ColourTile[n];
		for(int i=0;i<n;i++){
			tiles[i] = new ColourTile(COLOURS[i]);
			tiles[i].addMouseListener(this);
			add(tiles[i]);
		}
		setSelection(tiles[0]);
	}

	public Color getColour(){
		return sel;
	}
	
	private void setSelection(ColourTile tile){
		for(ColourTile ct : tiles){
			ct.setSelected(false);
		}
		tile.setSelected(true);
		sel = tile.colour;
	}
	
	@Override
	public void mouseClicked(MouseEvent me) {
		ColourTile tile = (ColourTile)me.getSource();
		setSelection(tile);
		parent.labelTime(false);
	}

	@Override
	public void mouseEntered(MouseEvent me) {}

	@Override
	public void mouseExited(MouseEvent me) {}

	@Override
	public void mousePressed(MouseEvent me) {}

	@Override
	public void mouseReleased(MouseEvent me) {}
	
}
