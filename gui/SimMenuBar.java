/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package gui;

import gui.playfield.PlayField;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import core.Settings;
import core.SettingsError;

/**
 * Menu bar of the simulator GUI
 *
 */
public class SimMenuBar extends JMenuBar implements ActionListener {
	/** title of the about window */
	public static final String ABOUT_TITLE = "about ONE";
	/** GPLv3 license text for about window */
	public static final String ABOUT_TEXT = "Copyright (C) 2007 TKK/Netlab\n\n"+
	"This program is free software: you can redistribute it and/or modify\n"+
    "it under the terms of the GNU General Public License as published by\n"+
    "the Free Software Foundation, either version 3 of the License, or\n"+
    "(at your option) any later version.\n\n"+
    "This program is distributed in the hope that it will be useful,\n"+
    "but WITHOUT ANY WARRANTY; without even the implied warranty of\n"+
    "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the\n"+
    "GNU General Public License for more details.\n\n" +
    "You should have received a copy of the GNU General Public License\n"+
    "along with this program.  If not, see <http://www.gnu.org/licenses/>.\n\n"+
    "Map data copyright: Maanmittauslaitos, 2007";
	
	private JCheckBoxMenuItem enableBgImage;
	private JCheckBoxMenuItem enableNodeName;
	private JCheckBoxMenuItem enableNodeCoverage;
	private JCheckBoxMenuItem enableNodeConnections;
	private JCheckBoxMenuItem enableMapGraphic;
	private JCheckBoxMenuItem autoClearOverlay;
	private JMenuItem clearOverlay;
	private JMenuItem about;
	private PlayField field;
	
	/** The namespace where underlay image -related settings are found */
	private static final String UNDERLAY_NS = "GUI.UnderlayImage";
	
	public SimMenuBar(PlayField field) {
		this.field = field;
		init();
	}

	private void init() {
		JMenu pfMenu = new JMenu("Playfield graphics");
		JMenu help = new JMenu("Help");
		Settings settings = new Settings(UNDERLAY_NS);
		if (settings.contains("fileName")) {
			// create underlay image menu item only if filename is specified 
			enableBgImage = createCheckItem(pfMenu,"Show underlay image",false);
		}
		enableNodeName = createCheckItem(pfMenu, "Show node name string",true);
		enableNodeCoverage = createCheckItem(pfMenu, 
				"Show node radio coverage", true);
		enableNodeConnections = createCheckItem(pfMenu,
				"Show node's connections", true);
		enableMapGraphic = createCheckItem(pfMenu,"Show map graphic",true);
		autoClearOverlay = createCheckItem(pfMenu, "Autoclear overlay",true);
		clearOverlay = createMenuItem(pfMenu,"Clear overlays now");
		about = createMenuItem(help,"about");
		this.add(pfMenu);
		this.add(Box.createHorizontalGlue());
		this.add(help);
	}
	
	private JMenuItem createMenuItem(Container c, String txt) {
		JMenuItem i = new JMenuItem(txt);
		i.addActionListener(this);
		c.add(i);
		return i;
	}
	
	private JCheckBoxMenuItem createCheckItem(Container c,String txt, 
			boolean selected) {
		JCheckBoxMenuItem i = new JCheckBoxMenuItem(txt);
		i.setSelected(selected);
		i.addActionListener(this);
		c.add(i);
		return i;
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source == enableBgImage) {
			toggleUnderlayImage();
		}
		else if (source == this.enableNodeName) {
			gui.playfield.NodeGraphic.setDrawNodeName(
					enableNodeName.isSelected());
		}
		else if (source == this.enableNodeCoverage) {
			gui.playfield.NodeGraphic.setDrawCoverage(
					enableNodeCoverage.isSelected());
		}
		else if (source == this.enableNodeConnections) {
			gui.playfield.NodeGraphic.setDrawConnections(
					enableNodeConnections.isSelected());
		}
		else if (source == this.enableMapGraphic) {
			field.setShowMapGraphic(enableMapGraphic.isSelected());
		}
		else if (source == this.autoClearOverlay) {
			field.setAutoClearOverlay(autoClearOverlay.isSelected());
		}
		else if (source == this.clearOverlay) {
			field.clearOverlays();
		}
		else if (source == this.about) {
			JOptionPane.showMessageDialog(this, ABOUT_TEXT, ABOUT_TITLE, 
					JOptionPane.INFORMATION_MESSAGE);
		}
		
	}
	
	/**
	 * Toggles the showing of underlay image. Image is read from the file only
	 * when it is enabled to save some memory.
	 */
	private void toggleUnderlayImage() {
		if (enableBgImage.isSelected()) {
			String imgFile = null;
			int[] offsets;
			double scale, rotate;
			BufferedImage image;
			try {
				Settings settings = new Settings(UNDERLAY_NS);
				imgFile = settings.getSetting("fileName");
				offsets = settings.getCsvInts("offset", 2);
				scale = settings.getDouble("scale");
				rotate = settings.getDouble("rotate");
	            image = ImageIO.read(new File(imgFile));
	        } catch (IOException ex) {
	        	warn("Couldn't set underlay image " + imgFile + ". " + 
	        			ex.getMessage());
	        	enableBgImage.setSelected(false);
	        	return;
	        }
	        catch (SettingsError er) {
	        	warn("Problem with the underlay image settings: " + 
	        			er.getMessage());
	        	return;
	        }
			field.setUnderlayImage(image, offsets[0], offsets[1],
					scale, rotate);
		}
		else {
			// disable the image
			field.setUnderlayImage(null, 0, 0, 0, 0);
		}
	}
	
	private void warn(String txt) {
		JOptionPane.showMessageDialog(null, txt, "warning", 
				JOptionPane.WARNING_MESSAGE);
	}
	
}
