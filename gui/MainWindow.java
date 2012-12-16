/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package gui;

import gui.playfield.PlayField;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import core.World;

/**
 * Main window for the program. Takes care of layouting the main components
 * in the window.
 */
public class MainWindow extends JFrame {
	private static final String WINDOW_TITLE = "ONE";
	private static final int WIN_XSIZE = 900;
	private static final int WIN_YSIZE = 700;
	// log panel's initial weight in the split panel 
	private static final double SPLIT_PANE_LOG_WEIGHT = 0.2;
	
	private JScrollPane playFieldScroll;
	
    public MainWindow(String scenName, World world, PlayField field,
    		GUIControls guiControls, InfoPanel infoPanel,
    		EventLogPanel elp, DTNSimGUI gui) {
    	super(WINDOW_TITLE + " - " + scenName);
    	JFrame.setDefaultLookAndFeelDecorated(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        JPanel leftPane = new JPanel();
        leftPane.setLayout(new BoxLayout(leftPane,BoxLayout.Y_AXIS));
    	JScrollPane hostListScroll;
        JSplitPane fieldLogSplit;
        JSplitPane logControlSplit;
        JSplitPane mainSplit;
        
    	setLayout(new BorderLayout());
        setJMenuBar(new SimMenuBar(field));
        
        playFieldScroll = new JScrollPane(field);
        playFieldScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 
        		Integer.MAX_VALUE));
        
        hostListScroll = new JScrollPane(new NodeChooser(world.getHosts(),gui));
        hostListScroll.setHorizontalScrollBarPolicy(
        		JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        logControlSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
        		new JScrollPane(elp.getControls()),new JScrollPane(elp));
        logControlSplit.setResizeWeight(0.1);
        logControlSplit.setOneTouchExpandable(true);
        
        fieldLogSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
        		leftPane, logControlSplit);
        fieldLogSplit.setResizeWeight(1-SPLIT_PANE_LOG_WEIGHT);
        fieldLogSplit.setOneTouchExpandable(true);
        
        setPreferredSize(new Dimension(WIN_XSIZE, WIN_YSIZE));

        leftPane.add(guiControls);
        leftPane.add(playFieldScroll);
        leftPane.add(infoPanel);
        
        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, 
        		fieldLogSplit, hostListScroll);
        mainSplit.setOneTouchExpandable(true);
        mainSplit.setResizeWeight(0.60);        
        this.getContentPane().add(mainSplit);
        
        pack();
    }

    /**
     * Returns a reference of the play field scroll panel
     * @return a reference of the play field scroll panel
     */
    public JScrollPane getPlayFieldScroll() {
    	return this.playFieldScroll;
    }
    
}
