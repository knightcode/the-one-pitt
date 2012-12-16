/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package gui.playfield;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/**
 * Reference scale bar graphic. This is the small reference scale
 * on the upper left corner of the playfield.
 */
public class ScaleReferenceGraphic extends PlayFieldGraphic {
	/** length of the reference bar (map meters) */
	private final int LENGTH = 100; 
	/** x position of the left end of the bar (pixels) */
	private final int X_POS = 20;
	/** y position of the left end of the bar (pixels) */
	private final int Y_POS = 20;
	/** height of the bar (pixels) */
	private final int SIZE = 8; 
	/** size of the font */
	private final int FONT_SIZE = 10;
	/** color of the bar */
	private final Color REF_COLOR = Color.BLACK;
	
	@Override
	public void draw(Graphics2D g2) {
		int endX = X_POS + scale(LENGTH);
		int h = SIZE/2;
		
		g2.setFont(new Font(null, Font.PLAIN, FONT_SIZE));
		
		g2.setColor(REF_COLOR);
		g2.drawLine(X_POS, Y_POS-h, X_POS, Y_POS+h); // left end
		g2.drawLine(X_POS, Y_POS, endX, Y_POS); // horizontal line
		g2.drawLine(endX, Y_POS-h, endX, Y_POS+h);
		
		g2.drawString(LENGTH+"m", X_POS + scale(LENGTH)/2 - 12, Y_POS - 1);
	}

}
