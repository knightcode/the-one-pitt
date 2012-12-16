/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import core.DTNHost;

/**
 * Node chooser panel
 *
 */
public class NodeChooser extends JPanel implements ActionListener {
	private DTNSimGUI gui;
	/** the maximum number of nodes to show in the list per page */
	public static final int MAX_NODE_COUNT = 500;
	private static final String HOST_KEY = "host";
	private List<DTNHost> nodes;
	private JComboBox groupChooser;
	private JPanel nodesPanel;
	private JPanel chooserPanel;

	public NodeChooser(List<DTNHost> nodes,	DTNSimGUI gui) {
		// create a replicate to not interfere with original's ordering
		this.nodes = new ArrayList<DTNHost>(nodes);
		this.gui = gui;
		Collections.sort(this.nodes);
		
		init();
	}
	
	/**
	 * Initializes the node chooser panels
	 */
	private void init() {
		nodesPanel = new JPanel();
		chooserPanel = new JPanel();
		
		this.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.FIRST_LINE_START;
		
		nodesPanel.setLayout(new BoxLayout(nodesPanel,BoxLayout.Y_AXIS));
		nodesPanel.setBorder(BorderFactory.createTitledBorder(getBorder(),
				"Nodes"));
		
		if (nodes.size() > MAX_NODE_COUNT) {
			String[] groupNames = new String[(nodes.size()-1)/MAX_NODE_COUNT+1];
			int last = 0;
			for (int i=0, n=nodes.size(); i <= (n-1) / MAX_NODE_COUNT; i++) {
				int next = MAX_NODE_COUNT * (i+1) - 1;
				if (next > n) {
					next = n-1;
				}
				groupNames[i] = (last + "..." + next);
				last = next + 1;
			}
			groupChooser = new JComboBox(groupNames);
			groupChooser.addActionListener(this);
			chooserPanel.add(groupChooser);
		}
		
		setNodes(0);
		c.gridy = 0;
		this.add(chooserPanel, c);
		c.gridy = 1;
		this.add(nodesPanel, c);
	}

	/**
	 * Sets the right set of nodes to display
	 * @param offset Index of the first node to show
	 */
	private void setNodes(int offset) {
		nodesPanel.removeAll();

		for (int i=offset; i<nodes.size() && i < offset + MAX_NODE_COUNT; i++) {
			DTNHost h = nodes.get(i);
			JButton jb = new JButton(h.toString());
			jb.putClientProperty(HOST_KEY, h);
			jb.addActionListener(this);
			nodesPanel.add(jb);
		}
		
		revalidate();
		repaint();
	}
	
	/**
	 * Action listener method for buttons and node set chooser
	 */
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() instanceof JButton) {
			JButton source = (JButton)e.getSource();
			DTNHost host = (DTNHost)source.getClientProperty(HOST_KEY);
			gui.setFocus(host);
		}
		else if (e.getSource() == groupChooser) {
			setNodes(groupChooser.getSelectedIndex() * MAX_NODE_COUNT);
		}
	}
	
}
