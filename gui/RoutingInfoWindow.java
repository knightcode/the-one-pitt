/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package gui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;

import routing.RoutingInfo;
import core.DTNHost;
import core.SimClock;

/**
 * A window for displaying routing information
 */
public class RoutingInfoWindow extends JFrame implements ActionListener {
	private DTNHost host;
	private JButton refreshButton;
	private JScrollPane treePane;
	
	public RoutingInfoWindow(DTNHost host) {
		this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		this.host = host;
		Container cp = this.getContentPane();
		this.setLayout(new BorderLayout());
		
		this.treePane = new JScrollPane();
		updateTree();
		
		cp.add(treePane, BorderLayout.NORTH);
		
		this.refreshButton = new JButton("refresh");
		this.refreshButton.addActionListener(this);
		cp.add(refreshButton, BorderLayout.SOUTH);
		
		this.pack();		
		this.setVisible(true);
	}
	
	private void updateTree() {
		JTree tree;
		super.setTitle("Routing Info of " + host + " at " + SimClock.getTime());
		RoutingInfo ri = host.getRoutingInfo();
		DefaultMutableTreeNode top = new DefaultMutableTreeNode(ri);
		addChildren(top, ri);
		
		tree = new JTree(top);
		for (int i=0; i < tree.getRowCount(); i++) {
			tree.expandRow(i); // expand all rows
		}
		
		this.treePane.setViewportView(tree);
		this.treePane.revalidate();
	}
	
	
	
	private void addChildren(DefaultMutableTreeNode node, RoutingInfo info) {
		for (RoutingInfo ri : info.getMoreInfo()) {
			DefaultMutableTreeNode child = new DefaultMutableTreeNode(ri);
			node.add(child);
			// recursively add children of this info
			addChildren(child, ri);
		}
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == this.refreshButton) {
			updateTree();
		}
		
	}
	
}
