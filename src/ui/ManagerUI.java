/*
 * ManagerUI.java
 *
 * Copyright (C) 2008 AppleGrew
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 */
package ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.lang.String;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JViewport;

import util.MiscFunctions;

import module.Module;
import module.moduleWindow;
import module.ModuleUI;
import framework.Clock;
import framework.Manager;
import framework.Wire;
import framework.Port;
import fio.Loader;

import java.awt.event.MouseEvent;
import java.awt.Color;
import java.awt.geom.Point2D;

/**
 * It handles the rendering of client space. It is also responsible for handling the events generated by client space.
 * 
 * @author Nirupam
 * @author Rohit
 * 
 */
public class ManagerUI extends JPanel implements Runnable {
    private static final long   serialVersionUID	 = 6987896984766987839L;
    private static final int    FPS		      = 80;
    private static final int    NO_DELAYS_PER_YIELD      = 6;

    private volatile boolean    renderAllways	    = false;
    private volatile Mode       mode		     = Mode.EDIT_MODE;
    private ArrayList<Module>   modules		  = null;
    private ArrayList<Wire>     wires		    = null;
    private ArrayList<ModuleUI> modulesUI		= null;
    private ArrayList<WireUI>   wiresUI		  = null;
    private ArrayList<HandleUI> handlesUI		= null;
    private ArrayList<Class>    loadedModules	    = null;
    private ArrayList<String>   loadedModuleNames	= null;
    private boolean	     noOneMoving	      = true;
    private HandleUI	    draggingHandle	   = null;
    private String	      name;
    private volatile boolean    terminateManagerUI;
    private String	      selectionName	    = null;
    private Image	       simBuffer;
    // private Loader loader;
    private Manager	     manager;
    private Thread	      t;
    private Rectangle	   boundingbox	      = null;		// Stores the reactangle within which all ComponentUI objects lie. Used for
                                                                // calculating min. size of
    // client area.
    final int		   WireUI2WireSteppingRatio = 3;		    // Used to make data flow animation smoother without increasing
                                                                                // latency of wires.
    JLabel		      timeLabel		= null;
    public Clock		clock		    = null;

    /**
         * This also creates a new instance of Manager from package framework.
         * 
         */
    public ManagerUI(String name, JLabel timeLabel) {
	terminateManagerUI = false;
	this.name = name;
	this.timeLabel = timeLabel;
	manager = new Manager(name);
	clock = manager.clock;
	this.addMouseMotionListener(new ManagerUIMouseMotionListener());
	this.addMouseListener(new ManagerUIMouseListener());
	modules = new ArrayList<Module>();
	wires = new ArrayList<Wire>();
	modulesUI = new ArrayList<ModuleUI>();
	wiresUI = new ArrayList<WireUI>();
	handlesUI = new ArrayList<HandleUI>();
	loadedModules = new ArrayList<Class>();
	loadedModuleNames = new ArrayList<String>();
	// loader=new Loader();
	renderAllways = false;
	boundingbox = null;
	this.setBackground(Color.white);
    }

    /**
         * Signals ManagerUI to terminate its thread.
         * 
         */
    public void terminatethread() {
	terminateManagerUI = true;
    }

    /**
         * Signals ManagerUI to start/restart its thread. Calling startthread() will automatically set renderAllways to true.
         */
    public void startthread() {
	renderAllways = true;
	if (t != null) {
	    terminateManagerUI = true;
	    try {
		t.join();// Wait for the thread to end.
	    } catch (InterruptedException e) {
		System.out.println("Failed to join to ManagerUI Thread before starting another one.");
		e.printStackTrace();
	    }
	}
	terminateManagerUI = false;
	t = new Thread(this, name);
	t.start();
    }

    /**
         * Enabling renderAllways will make ManagerUI continuously display frames at simulation FPS, irrespective of any modes. This is required if
         * there are animated gifs in the client space, otherwise, turn it off this is a huge processing hog. Calling startthread() will automatically
         * set renderAllways to true.
         * 
         * @param b
         */
    public void setRenderAllways(boolean b) {
	if (b) {
	    if (renderAllways)
		return;
	    startthread();// It will automatically set renderAllways to true and terminate current thread if running and restart it.
	} else {
	    if (!renderAllways)
		return;
	    renderAllways = false;
	    if (t != null) {
		terminateManagerUI = true;
		try {
		    t.join();// Wait for the thread to end.
		} catch (InterruptedException e) {
		    System.out.println("Failed to join to ManagerUI Thread.");
		    e.printStackTrace();
		}
		terminateManagerUI = false;
	    }
	}
    }

    public boolean getRenderAllways() {
	return renderAllways;
    }

    /**
         * Updates value of boundingbox. Call this whenever any ComponentUI objects move or are added or removed.
         * 
         */
    private synchronized void updateBoundingBox() {
	int x = 0, y = 0;
	for (int i = 0; i < modulesUI.size(); i++) {
	    ModuleUI m = modulesUI.get(i);
	    if (x <= m.getCoord().x)
		x = m.getCoord().x;
	    if (y <= m.getCoord().y)
		y = m.getCoord().y;
	}
	for (int i = 0; i < handlesUI.size(); i++) {
	    HandleUI m = handlesUI.get(i);
	    if (x <= m.handle.getCoord().x)
		x = (int) m.handle.getCoord().x;
	    if (y <= m.handle.getCoord().y)
		y = (int) m.handle.getCoord().y;
	}
	x += 60;
	y += 60;
	if (boundingbox == null)
	    boundingbox = new Rectangle(x, y);
	else {
	    boundingbox.width = x;
	    boundingbox.height = y;
	}
    }

    public void ResizeToSmallestSize() {
	int x = 0, y = 0;
	if (boundingbox != null) {
	    x = boundingbox.width;
	    y = boundingbox.height;
	}
	JViewport v = (JViewport) this.getParent();
	if (x < v.getSize().width)
	    x = v.getSize().width;
	if (y < v.getSize().height)
	    y = v.getSize().height;
	Resize(x, y);
    }

    /**
         * Used by WireUI.
         * 
         * @param mod
         * @return
         */
    ModuleUI getModuleUI(Module mod) {
	return modulesUI.get(modules.lastIndexOf(mod));
    }

    /**
         * Reset's state of Manager in ManagerUI.
         * 
         */
    public void reset() {
	manager.endSimulation();
	manager.initSimulation();
    }

    public Mode getMode() {
	return mode;
    }

    public void changeMode(Mode newMode) {
	if (this.mode != newMode) {

	    switch (newMode) {
	    case WIRE_CREATION_MODE:
		this.mode = newMode;
		break;
	    // Checks and conditions can
	    case SIMULATION_MODE: // be added here
		if (mode == Mode.EDIT_MODE)
		    startSimulation();
		if (mode == Mode.PAUSED_MODE)
		    resumeSimulation();
		this.mode = newMode;
		break;

	    case EDIT_MODE:
		if (mode == Mode.SIMULATION_MODE || mode == Mode.PAUSED_MODE)
		    stopSimulation();
		this.mode = newMode;
		break;

	    case PAUSED_MODE:
		if (this.mode == Mode.SIMULATION_MODE) {
		    this.mode = newMode;
		    pauseSimulation();
		}
		break;
	    }
	    for (int i = 0; i < modulesUI.size(); i++) {
		moduleWindow mw = modulesUI.get(i).getModWin();
		if (mw != null)
		    mw.updatePropertyPageState(mode);
	    }
	    System.out.println(mode + ":" + name + " "); // remove later

	}
    }

    public void addNewComponent(Module m) {
	manager.addModule(m);
    }

    public ManagerUI getReference() {
	return this;
    }

    private synchronized void addHandle(Handle handle, ComponentUI owner, WireUI AssociatedWireUI) {
	HandleUI h = new HandleUI(handle, owner);
	h.wireUIOwner = AssociatedWireUI;
	handlesUI.add(h);
    }

    private void addHandle(Handle handle, ComponentUI owner) {
	handlesUI.add(new HandleUI(handle, owner));
    }

    public void Resize(int width, int height) {
	setPreferredSize(new Dimension(width, height));
	revalidate();
	if (simBuffer != null) {
	    simBuffer.flush();
	    simBuffer = createImage(width, height);
	    if (simBuffer == null) {
		System.out.println("simBuffer is null");
		return;
	    }
	    if (!renderAllways)
		renderToBuf();
	}
    }

    /**
         * Removes all handles that are child of 'owner'.
         * 
         * @param owner
         */
    private synchronized void removeHandles(ComponentUI owner) {
	for (int i = 0; i < handlesUI.size(); i++)
	    while (i < handlesUI.size() && handlesUI.get(i).owner == owner)
		handlesUI.remove(i);

    }

    private synchronized void rePositionHandles(ComponentUI componentui, Point2D.Double loc) {
	if (componentui != null) {
	    for (int i = 0; i < handlesUI.size(); i++) {
		if (handlesUI.get(i).owner == componentui)
		    handlesUI.get(i).handle.setCoord(loc);
	    }
	}
    }

    private synchronized void mergeHandle(HandleUI h) {
	if (h == null || !h.is_naked())
	    return;
	for (int i = 0; i < handlesUI.size(); i++) {
	    HandleUI hi = handlesUI.get(i);
	    if (hi.isClicked(h.handle.getCoord())) {// Don't get misguided by isClciked name. It could had been named isPointLiesWithin.
		if (h.wireUIOwner == hi.wireUIOwner) {
		    if (h.wireUIOwner.areAdjacent(h.handle, hi.handle)) {
			h.wireUIOwner.removeNakedHandle(h.handle);
			handlesUI.remove(h);
			break;
		    }
		}
	    }
	}
    }

    // ************INNER CLASS***FOR HANDELING MOUSE EVENTS******************
    class ManagerUIMouseListener extends java.awt.event.MouseAdapter {
	int  dClkRes       = 300; // double-click speed in ms
	long timeMouseDown = 0;  // last mouse down time
	int  lastX	 = 0, lastY = 0; // last x and y

	private void getNewModToBuffer() {
	    if (selectionName != null && buffer.EditMode.module == null || buffer.EditMode.moduleUI == null) {
		Class newClass;
		if (loadedModuleNames.contains(selectionName)) {
		    newClass = loadedModules.get(loadedModuleNames.indexOf(selectionName));
		} else {
		    try {
			// newClass=loader.load(selectionName);
			// Loader (our custom classloader in Loader.java) has been phased out, since this is simpler and
			// that was making JVM throw "LinkageError [classname] violates loader constraints"
			// exception when anyone tried to access member functions or variables from objects
			// of classes loaded by Loader classloader.
			
			//String className = selectionName.replaceAll("/", ".");// Converting Pathname to classname
			//className = className.substring(0, className.lastIndexOf('.'));
			String className = MiscFunctions.convertPath2className(selectionName);
			System.out.println("Loaded: " + className);
			newClass = Class.forName(className);
			loadedModules.add(newClass);
			loadedModuleNames.add(selectionName);
		    } catch (Exception Le) {
			if (selectionName != null) {
			    System.out.println("Error while loading module: " + selectionName);
			    Le.printStackTrace();
			}
			return;
		    }
		}
		try {
		    newClass.newInstance();
		} catch (Exception Ie) {
		    System.out.println("Error while instantiating module: " + selectionName);
		    Ie.printStackTrace();
		}
		if (buffer.EditMode.module.getModuleUI() == null)
		    buffer.EditMode.module.setModuleUI(new ModuleUI(buffer.EditMode.module));
		buffer.EditMode.moduleUI = buffer.EditMode.module.getModuleUI();
	    }
	}

	private boolean isDoubleClick(java.awt.event.MouseEvent event) {
	    /*
                 * * check for double click
                 */
	    long diff = event.getWhen() - timeMouseDown;
	    if ((lastX == event.getX()) && (lastY == event.getY()) && (diff < dClkRes)) {
		// System.out.println("double click " + diff);
		return true;
	    } else {
		// single click action could be added here
		// System.out.println("simple click " + diff);
		timeMouseDown = event.getWhen();
		lastX = event.getX();
		lastY = event.getY();
	    }
	    return false;
	}

	public void mousePressed(java.awt.event.MouseEvent e) {
	    if (e.getButton() == MouseEvent.BUTTON1) {
		switch (mode) {
		case EDIT_MODE:
		    // This has been done so that the components donot get dragged when dragging has started from some
		    // place other than the intended component. This prevents unintended dragging, when the user by mistake
		    // drags out the mouse.
		    for (int i = 0; i < handlesUI.size(); i++) {
			if (handlesUI.get(i).isClicked(e.getPoint())) {
			    handlesUI.get(i).setStatus(HandleUI.SELECTED);
			    break;
			}
		    }
		    for (int i = 0; i < modulesUI.size(); i++)
			if (modulesUI.get(i).isClicked(e.getPoint())) {
			    modulesUI.get(i).setStatus(ModuleUI.SELECTED);
			    break;
			}
		    if (!renderAllways)
			render();
		    break;
		}
	    }
	}

	public void mouseReleased(java.awt.event.MouseEvent e) {
	    noOneMoving = true;
	    for (int i = 0; i < modulesUI.size(); i++)
		modulesUI.get(i).setStatus(ModuleUI.NONE);
	    for (int i = 0; i < handlesUI.size(); i++)
		handlesUI.get(i).setStatus(HandleUI.NONE);
	    mergeHandle(draggingHandle);
	    draggingHandle = null;
	    if (!renderAllways)
		render();
	}

	public void mouseClicked(java.awt.event.MouseEvent e) {
	    noOneMoving = true;
	    if (e.getButton() == MouseEvent.BUTTON3) {

		switch (mode) {
		case WIRE_CREATION_MODE:
		    for (int i = 0; i < modulesUI.size(); i++)
			modulesUI.get(i).setStatus(ModuleUI.NONE);
		    removeHandles(buffer.WireCreationMode.wireUI);
		    buffer.WireCreationMode.port = null;
		    buffer.WireCreationMode.wire = null;
		    buffer.WireCreationMode.wireUI = null;
		    mode = Mode.EDIT_MODE;
		    if (!renderAllways)
			render();
		    break;
		case SIMULATION_MODE:
		    break;
		case EDIT_MODE:
		    selectionName = null;
		    if (buffer.EditMode.module != null || buffer.EditMode.moduleUI != null) { // CANCEL SELECTION
			buffer.EditMode.module = null;
			buffer.EditMode.moduleUI = null;
			System.out.println("cancelled");
		    }
		    if (!renderAllways)
			render();
		    break;
		}

	    } else if (e.getButton() == MouseEvent.BUTTON1) {

		if (isDoubleClick(e)) {// ----HANDLE DOUBLE CLICK
		    boolean flag = false;

		    switch (mode) {

		    case WIRE_CREATION_MODE:
			removeHandles(buffer.WireCreationMode.wireUI);
			buffer.WireCreationMode.port = null;
			buffer.WireCreationMode.wire = null;
			buffer.WireCreationMode.wireUI = null;
			mode = Mode.EDIT_MODE;

			for (int i = 0; i < modulesUI.size(); i++)
			    modulesUI.get(i).setStatus(ModuleUI.NONE);
			for (int i = 0; i < modulesUI.size(); i++) {
			    if (modulesUI.get(i).isClicked(e.getPoint())) {
				modulesUI.get(i).getModWin().makeWindowVisible();
			    }
			}
			break;
		    case EDIT_MODE:
			for (int i = 0; i < modulesUI.size(); i++)
			    modulesUI.get(i).setStatus(ModuleUI.NONE);
			flag = false;
			for (int i = 0; i < modulesUI.size(); i++) {
			    if (modulesUI.get(i).isClicked(e.getPoint())) {
				flag = true;
				modulesUI.get(i).getModWin().makeWindowVisible();
			    }
			}
			if (!flag) {
			    for (int i = 0; i < wiresUI.size(); i++) {
				if (wiresUI.get(i).isClicked(e.getPoint())) {
				    wiresUI.get(i).getModWin().makeWindowVisible();
				}
			    }
			}
			break;
		    case PAUSED_MODE:
		    case SIMULATION_MODE:
			for (int i = 0; i < modulesUI.size(); i++)
			    modulesUI.get(i).setStatus(ModuleUI.NONE);
			flag = false;
			for (int i = 0; i < modulesUI.size(); i++) {
			    if (modulesUI.get(i).isClicked(e.getPoint())) {
				flag = true;
				modulesUI.get(i).getModWin().makeWindowVisible();
			    }
			}
			if (!flag) {
			    for (int i = 0; i < wiresUI.size(); i++) {
				if (wiresUI.get(i).isClicked(e.getPoint())) {
				    wiresUI.get(i).getModWin().makeWindowVisible();
				}
			    }
			}
			break;

		    }
		} else {// Single click
		    switch (mode) {
		    case WIRE_CREATION_MODE:
			boolean flag = false;
			for (int i = 0; i < modulesUI.size(); i++) {
			    if (modulesUI.get(i).isClicked(e.getPoint())) {
				modulesUI.get(i).setStatus(ModuleUI.NONE);
				if (buffer.WireCreationMode.port.getOwner() != modules.get(i)) {
				    buffer.WireCreationMode.port.getOwner().addPort(buffer.WireCreationMode.port, 0);
				    Port p = new Port("Ethernet", modules.get(i));

				    modules.get(i).addPort(p, 0);
				    buffer.WireCreationMode.wire.addPort(p);
				    Handle[] h = buffer.WireCreationMode.wireUI.commit(p);
				    // modulesUI.get(i).addHandle(h[0]);
				    if (h[0].isPort())
					addHandle(h[0], getModuleUI(buffer.WireCreationMode.port.getOwner()), buffer.WireCreationMode.wireUI);
				    /*
                                         * else addHandle(h[0],buffer.WireCreationMode.wireUI);
                                         */
				    addHandle(h[1], modulesUI.get(i), buffer.WireCreationMode.wireUI);
				    wires.add(buffer.WireCreationMode.wire);
				    wiresUI.add(buffer.WireCreationMode.wireUI);
				    manager.addWire(buffer.WireCreationMode.wire);
				    buffer.WireCreationMode.wireUI.createWireModWin();
				    buffer.WireCreationMode.wireUI.setModWin(new moduleWindow(buffer.WireCreationMode.wireUI.getWireModWin()));
				    mode = Mode.EDIT_MODE;
				    buffer.WireCreationMode.port = null;
				    buffer.WireCreationMode.wire = null;
				    buffer.WireCreationMode.wireUI = null;

				} else {

				    removeHandles(buffer.WireCreationMode.wireUI);
				    buffer.WireCreationMode.port = null;
				    buffer.WireCreationMode.wire = null;
				    buffer.WireCreationMode.wireUI = null;
				    mode = Mode.EDIT_MODE;

				}
				flag = true;
				break;
			    }
			}
			if (!flag) {
			    // Handle h= buffer.WireCreationMode.wireUI.commit(new Point2D.Double(e.getX(),e.getY()));
			    Handle h[] = buffer.WireCreationMode.wireUI.commit(new Point2D.Double(e.getX(), e.getY()));
			    // if(h!=null) getModuleUI(buffer.WireCreationMode.wire.getPort(0).getOwner()).addHandle(h);
			    if (h[0].isPort())
				addHandle(h[0], getModuleUI(buffer.WireCreationMode.wire.getPort(0).getOwner()), buffer.WireCreationMode.wireUI);
			    /*
                                 * else addHandle(h[0],buffer.WireCreationMode.wireUI);
                                 */
			    addHandle(h[1], buffer.WireCreationMode.wireUI);
			}
			if (!renderAllways)
			    render();
			break;
		    case SIMULATION_MODE:
			break;

		    case EDIT_MODE:
			if (e.isShiftDown()) {
			    if (selectionName != null && buffer.EditMode.module == null && buffer.EditMode.moduleUI == null) {
				getNewModToBuffer();
				buffer.EditMode.moduleUI.setCoord(e.getPoint());
			    }
			} else
			    selectionName = null;
			if (buffer.EditMode.module != null && buffer.EditMode.moduleUI != null) {
			    buffer.EditMode.module.setSno(modules.size() + 1);
			    modules.add(buffer.EditMode.module);
			    manager.addModule(buffer.EditMode.module);
			    System.out.println(buffer.EditMode.module.getName());
			    buffer.EditMode.module = null;
			    if (buffer.EditMode.moduleUI.getModWin() == null) {
				buffer.EditMode.moduleUI.setModWin(new moduleWindow(buffer.EditMode.moduleUI));
			    }
			    modulesUI.add(buffer.EditMode.moduleUI);
			    buffer.EditMode.moduleUI = null;
			    for (int i = 0; i < modulesUI.size(); i++) {
				modulesUI.get(i).setStatus(ModuleUI.NONE);
			    }

			} else {
			    for (int i = 0; i < modulesUI.size(); i++) {
				if (modulesUI.get(i).isClicked(e.getPoint())) {
				    modulesUI.get(i).setStatus(ModuleUI.SELECTED);
				    mode = Mode.WIRE_CREATION_MODE;
				    buffer.WireCreationMode.port = new Port("Eternet", modules.get(i));
				    buffer.WireCreationMode.wire = new Wire(buffer.WireCreationMode.port);
				    buffer.WireCreationMode.wireUI = new WireUI(buffer.WireCreationMode.wire, getReference());
				    Point2D.Double p0 = new Point2D.Double(e.getX(), e.getY());
				    buffer.WireCreationMode.mousePointerLocation = p0;

				} else {
				    modulesUI.get(i).setStatus(ModuleUI.NONE);
				}

			    }
			}
			if (!renderAllways)
			    render();
			break;
		    }
		}

	    } else if (e.getButton() == MouseEvent.BUTTON2) {

		switch (mode) {
		case WIRE_CREATION_MODE:
		    for (int i = 0; i < modulesUI.size(); i++) {
			if (modulesUI.get(i).isClicked(e.getPoint())) {
			    modulesUI.get(i).setStatus(ModuleUI.MOVING);
			    mode = Mode.EDIT_MODE;
			    buffer.WireCreationMode.port = null;
			    buffer.WireCreationMode.wire = null;
			    buffer.WireCreationMode.wireUI = null;
			}
		    }
		    if (!renderAllways)
			render();
		    break;
		case SIMULATION_MODE:
		    break;
		case EDIT_MODE:
		    for (int i = 0; i < modulesUI.size(); i++) {
			if (modulesUI.get(i).isClicked(e.getPoint())) {
			    if (modulesUI.get(i).getStatus() == ModuleUI.MOVING)
				modulesUI.get(i).setStatus(ModuleUI.NONE);
			    else
				modulesUI.get(i).setStatus(ModuleUI.MOVING);

			} else
			    modulesUI.get(i).setStatus(ModuleUI.NONE);
		    }
		    if (!renderAllways)
			render();
		    break;
		}
	    }
	}

	public void mouseEntered(java.awt.event.MouseEvent e) {
	    switch (mode) {
	    case WIRE_CREATION_MODE:
		break;
	    case SIMULATION_MODE:
		break;

	    case EDIT_MODE:
		getNewModToBuffer();
		break;
	    }
	}

	public void mouseExited(java.awt.event.MouseEvent e) {
	    selectionName = null;
	}
    }

    class ManagerUIMouseMotionListener extends java.awt.event.MouseMotionAdapter {
	public void mouseMoved(java.awt.event.MouseEvent e) {
	    switch (mode) {
	    case WIRE_CREATION_MODE:
		for (int i = 0; i < modulesUI.size(); i++) {
		    if (modulesUI.get(i).isClicked(e.getPoint()))
			modulesUI.get(i).setStatus(ModuleUI.SELECTED);
		    else
			modulesUI.get(i).setStatus(ModuleUI.NONE);
		}
		buffer.WireCreationMode.mousePointerLocation = new Point2D.Double(e.getX(), e.getY());
		if (!renderAllways)
		    render();
		break;
	    case SIMULATION_MODE:
		break;
	    case EDIT_MODE:
		if (buffer.EditMode.moduleUI != null) {
		    buffer.EditMode.moduleUI.setCoord(e.getPoint());
		    if (!renderAllways)
			render();
		}
		break;
	    }

	}

	public void mouseDragged(java.awt.event.MouseEvent e) {

	    if (!locInBound(e.getPoint())) {
		if (mode == Mode.EDIT_MODE && e.getPoint().x > 0 && e.getPoint().y > 0) {
		    boolean flag = false;
		    for (int i = 0; i < modulesUI.size(); i++)
			if (modulesUI.get(i).isClicked(e.getPoint()) || modulesUI.get(i).getStatus() == ModuleUI.MOVING) {
			    flag = true;
			}
		    if (!flag)
			for (int i = 0; i < handlesUI.size(); i++)
			    if (handlesUI.get(i).isClicked(e.getPoint()) || handlesUI.get(i).getStatus() == HandleUI.MOVING) {
				flag = true;
			    }
		    if (!flag)
			return;
		    adjustSize(e.getPoint());
		} else
		    return;
	    }
	    if (mode == Mode.WIRE_CREATION_MODE || mode == Mode.EDIT_MODE) {
		Rectangle r = new Rectangle(e.getX(), e.getY(), 30, 30);
		((JPanel) e.getSource()).scrollRectToVisible(r);
	    }

	    switch (mode) {
	    case WIRE_CREATION_MODE:
		buffer.WireCreationMode.mousePointerLocation.setLocation(e.getPoint());
		if (!renderAllways)
		    render();
		break;
	    case SIMULATION_MODE:
		break;
	    case EDIT_MODE:
		for (int i = 0; i < handlesUI.size(); i++) {
		    if (handlesUI.get(i).is_naked()) {// Moves only 'naked' handles.
			if (noOneMoving && handlesUI.get(i).isClicked(e.getPoint())) {
			    if ((handlesUI.get(i).getStatus() != HandleUI.MOVING) && handlesUI.get(i).getStatus() == ModuleUI.SELECTED)
				handlesUI.get(i).setStatus(HandleUI.MOVING);
			    draggingHandle = handlesUI.get(i);
			    noOneMoving = false;
			}
			if ((handlesUI.get(i).getStatus() == HandleUI.MOVING)) {
			    Point2D.Double point = new Point2D.Double(e.getPoint().x, e.getPoint().y);
			    handlesUI.get(i).handle.setCoord(point);
			}
		    }
		}

		for (int i = 0; i < modulesUI.size(); i++) {
		    if (noOneMoving && modulesUI.get(i).isClicked(e.getPoint())) {
			if ((modulesUI.get(i).getStatus() != ModuleUI.MOVING && modulesUI.get(i).getStatus() == ModuleUI.SELECTED))
			    modulesUI.get(i).setStatus(ModuleUI.MOVING);
			noOneMoving = false;
		    }
		    if ((modulesUI.get(i).getStatus() == ModuleUI.MOVING)) {
			modulesUI.get(i).setCoord(e.getPoint());
			rePositionHandles(modulesUI.get(i), modulesUI.get(i).getDoubleCoord());
			// modulesUI.get(i).rePositionHandles();
		    }
		}
		updateBoundingBox();
		if (!renderAllways)
		    render();
		break;
	    }

	}
    }

    private boolean locInBound(Point p) {
	int x = p.x, y = p.y;
	if (y >= 0 && y <= (this.getSize().height - 30) && x >= 0 && x <= (this.getSize().width - 30)) {
	    return true;
	} else {
	    return false;
	}
    }

    private void adjustSize(Point p) {
	int x = p.x, y = p.y;
	int X = this.getSize().width, Y = this.getSize().height;
	boolean flag = false;
	if (y > (this.getSize().height - 30)) {
	    Y = y + 30;
	    flag = true;
	}
	if (x > (this.getSize().width - 30)) {
	    X = x + 30;
	    flag = true;
	}
	if (flag)
	    Resize(X, Y);
    }

    // *******************EDIT MODE*********************************
    public void setSelectionName(String selection) {
	if (mode != Mode.PAUSED_MODE && mode != Mode.SIMULATION_MODE) {
	    this.selectionName = selection;
	    changeMode(Mode.EDIT_MODE);
	}
    }

    // ******************SIMULATION MODE***********************
    public void run() {
	long afterTime, beforeTime, timeDiff, sleepTime;
	long overSleepTime = 0L; // Amount of time (in ms) over slept.
	int noDelays = 0; // Counter for no. of sleeps it has skipped.
	long period = (long) (((double) 1000) / FPS); // Amount of time a frame maybe displayed, in ms.

	terminateManagerUI = false;
	beforeTime = System.currentTimeMillis();
	int stepping = WireUI2WireSteppingRatio;
	while (!terminateManagerUI) {
	    boolean ret;
	    ret = stepSimulation(stepping);
	    if (ret)
		stepping--;
	    if (stepping == 0)
		stepping = WireUI2WireSteppingRatio;
	    if (renderAllways)
		ret = true;
	    if (ret)
		render();
	    afterTime = System.currentTimeMillis();
	    timeDiff = afterTime - beforeTime;
	    sleepTime = (period - timeDiff) - overSleepTime; // time left in this loop

	    if (sleepTime > 0) { // some time left in this cycle
		try {
		    Thread.sleep(sleepTime); // in ms
		} catch (InterruptedException ex) {}
		overSleepTime = (System.currentTimeMillis() - afterTime) - sleepTime;
	    } else { // sleepTime <= 0; frame took longer than the period
		overSleepTime = 0L;

		if (++noDelays >= NO_DELAYS_PER_YIELD) {
		    Thread.yield(); // give another thread a chance to run
		    noDelays = 0;
		}
	    }
	    beforeTime = System.currentTimeMillis();
	}
	t = null;
    }

    /**
         * Progresses the simulation one step.
         * 
         */
    public boolean stepSimulation(int stepping) {
	/*
         * if(mode!=Mode.SIMULATION_MODE) return false; if(clock.tick()){ manager.stepSimulation(); //The following objects take the decision of
         * stepping themselves based on mode of ModuleUI. //NOTE: ModuleUI MUST be called before wireUI is called because it creates the dataUI object
         * that is used by wireUI. for(int i=0;i<modulesUI.size();i++) modulesUI.get(i).stepSimulation(this.mode); for(int i=0;i<wiresUI.size();i++)
         * wiresUI.get(i).stepSimulation(this.mode); return true; }else return false;
         */

	boolean clocktick = false;
	if (mode == Mode.SIMULATION_MODE)
	    clocktick = clock.tick(stepping != WireUI2WireSteppingRatio);
	if (clocktick && stepping == WireUI2WireSteppingRatio) {
	    manager.stepSimulation();
	    timeLabel.setText("Time: " + clock.getTime() + " us");
	}
	// The following objects take the decision of stepping themselves based on mode of ModuleUI.
	// NOTE: ModuleUI MUST be called before wireUI is called because it creates the dataUI object that is used by wireUI.
	if (mode != Mode.SIMULATION_MODE || clocktick) {
	    if (stepping == WireUI2WireSteppingRatio || mode != Mode.SIMULATION_MODE) {
		for (int i = 0; i < modulesUI.size(); i++)
		    modulesUI.get(i).stepSimulation(this.mode);
	    }
	    for (int i = 0; i < wiresUI.size(); i++)
		wiresUI.get(i).stepSimulation(this.mode);
	    return true;
	}
	return false;
    }

    /**
         * It calls renderToBuf first to render to buffer then calls activePaint to render it to screen.
         * 
         */
    public void render() {
	renderToBuf();
	// activePaint();
	Rectangle rect = this.getVisibleRect();
	repaint(rect);
    }

    /**
         * It renders without calling renderBuf(). Useful to save time when paintComponent is called.
         * 
         */
    /*
         * public void renderWithoutUpdate(){ if(simBuffer==null) renderToBuf(); activePaint(); }
         */

    /**
         * Renders to buffer. This is thread safe. Done to keep safe simBuffer during multiple calls from ManagerUI thread and AWT event dispacher
         * thread.
         */
    public Image renderToBuf() {
	int PHEIGHT = this.getSize().height;
	int PWIDTH = this.getSize().width;
	if (PHEIGHT <= 0 || PWIDTH <= 0)
	    return null;
	// this.getHeight()>0?this.getHeight():this.getPreferredSize().height;
	// this.getWidth()>0?this.getWidth():this.getPreferredSize().width;
	Graphics g = null;

	// draw the current frame to an image buffer
	if (simBuffer == null) { // create the buffer
	    simBuffer = createImage(PWIDTH, PHEIGHT);
	    if (simBuffer == null) {
		System.out.println("simBuffer is null");
		return null;
	    }
	}

	g = simBuffer.getGraphics();

	// Clear the background
	g.setColor(Color.white);
	g.fillRect(0, 0, PWIDTH, PHEIGHT);

	if (mode == Mode.EDIT_MODE) {
	    if (buffer.EditMode.moduleUI != null)
		buffer.EditMode.moduleUI.render(g, this);
	} else if (mode == Mode.WIRE_CREATION_MODE) {
	    if (buffer.WireCreationMode.wireUI != null) {
		buffer.WireCreationMode.wireUI.render(g, buffer.WireCreationMode.mousePointerLocation);
		try {
		    buffer.WireCreationMode.wireUI.render(g);
		} catch (java.lang.NullPointerException e) {
		    System.out.println("\n Null Pointer at renderToBuf()  in ManagerUI");
		}
	    }
	}

	for (int i = 0; i < wiresUI.size(); i++)
	    wiresUI.get(i).render(g);
	for (int i = 0; i < modulesUI.size(); i++)
	    modulesUI.get(i).render(g, this);
	if (mode == Mode.EDIT_MODE || mode == Mode.WIRE_CREATION_MODE)
	    for (int i = 0; i < handlesUI.size(); i++)
		handlesUI.get(i).render(g);
	return simBuffer;
    }

    /**
         * Active rendering of buffer to screen. This is thread safe. Done to keep safe simBuffer during multiple calls from ManagerUI thread and AWT
         * event dispacher thread.
         */
    /*
         * public void activePaint(){ Graphics g; try { //g = this.getGraphics( ); // get the panel's graphic context if ((g != null) && (simBuffer !=
         * null)) g.drawImage(simBuffer, 0, 0, null); Toolkit.getDefaultToolkit( ).sync( ); // sync the display on some systems if (g != null)
         * g.dispose( ); } catch (Exception e) { System.out.println("Graphics context error: " + e); e.printStackTrace(); } }
         */

    protected void paintComponent(Graphics g) {
	super.paintComponents(g);
	if (simBuffer == null)
	    renderToBuf();
	g.drawImage(simBuffer, 0, 0, null);
    }

    /**
         * Stops the ManagerUI thread.
         * 
         */
    public void stopSimulation() {
	if (!renderAllways)
	    terminateManagerUI = true;
	manager.endSimulation();
	if (!renderAllways) {
	    if (t != null) {
		try {
		    t.join();
		} catch (InterruptedException e) {
		    System.out.println("Err trying to wait for render thread to die.");
		    e.printStackTrace();
		}
	    }
	    render();
	}
    }

    public void pauseSimulation() {
	if (!renderAllways) {
	    terminateManagerUI = true;
	    if (t != null) {
		try {
		    t.join();
		} catch (InterruptedException e) {
		    System.out.println("Err trying to wait for render thread to die.");
		    e.printStackTrace();
		}
	    }
	    render();
	}
    }

    /**
         * Starts the ManagerUI thread.
         * 
         */
    public void startSimulation() {
	for (int i = 0; i < wiresUI.size(); i++)
	    wiresUI.get(i).updatePrivateData();
	manager.initSimulation();
	if (!renderAllways) {
	    t = new Thread(this, name);
	    t.start();
	}
    }

    public void resumeSimulation() {
	for (int i = 0; i < wiresUI.size(); i++)
	    wiresUI.get(i).updatePrivateData();
	if (!renderAllways) {
	    t = new Thread(this, name);
	    t.start();
	}
    }
}