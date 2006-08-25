/* $Id$ */
/***************************************************************************
 *                      (C) Copyright 2003 - Marauroa                      *
 ***************************************************************************
 ***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 ***************************************************************************/
package games.stendhal.server.entity.portal;

import games.stendhal.common.Direction;
import games.stendhal.server.entity.RPEntity;
import games.stendhal.server.events.TurnListener;
import games.stendhal.server.events.TurnNotifier;

import java.awt.geom.Rectangle2D;

import marauroa.common.game.AttributeNotFoundException;
import marauroa.common.game.RPClass;

/**
 * A door is a special kind of portal which requires a key to pass it.
 * If the player carries the key with him, he can use the door just
 * like a normal portal; it will automatically open and close.
 * 
 * Note that you can link a door with a portal; that way, people only
 * require the key when walking in one direction and can walk in the
 * other direction without any key.
 */
public class Door extends Portal implements TurnListener {
	
	/** Whether or not the door is currently open */
	private boolean open;
    
	/**
	 * How many turns it takes until door automatically closes itself
	 * after somebody walked through it.
	 */
	private static final int TURNS_TO_STAY_OPEN = 9; /* 3 seconds */
    
	/**
	 * The turn at which this door should close the next time. 
	 */
	private int turnToClose = 0;

	public static void generateRPClass() {
		RPClass door = new RPClass("door");
		door.isA("entity");
		door.add("class", RPClass.STRING);
		door.add("locked", RPClass.STRING, RPClass.PRIVATE);
		door.add("open", RPClass.FLAG);
	}

	/**
	 * Creates a new door.
	 * @param key The item that must be carried to pass through this door 
	 * @param clazz The class. Responsible for how this door looks like.
	 * @param dir The direction in which one has to walk in order to pass
	 *            through this door
	 * @throws AttributeNotFoundException
	 */
	public Door(String key, String clazz, Direction dir)
			throws AttributeNotFoundException {
		super();
		put("type", "door");
		put("class", clazz);
		put("locked", key);

		setDirection(dir);

		open = false;
	}

	@Override
	public void getArea(Rectangle2D rect, double x, double y) {
		rect.setRect(x, y, 1, 1);
	}

	@Override
	public void update() {
		super.update();
		open = has("open");
	}

	/**
	 * Opens the door. 
	 */
	public void open() {
		this.open = true;
		
		TurnNotifier turnNotifier = TurnNotifier.get();
		// remember the turn number, in case someone else uses the door
		// while it is still open; in this time, the door should stay
		// open longer.
		this.turnToClose = turnNotifier.getNumberOfNextTurn() + TURNS_TO_STAY_OPEN;
        turnNotifier.notifyAtTurn(turnToClose, this, null);
        
		put("open", "");
	}

	/**
	 * Closes the door. 
	 */
	public void close() {
		this.open = false;
		remove("open");
	}

	public boolean isOpen() {
		return open;
	}

	@Override
	public void onUsed(RPEntity user) {
		if (has("locked") && user.isEquipped(get("locked"))) {
			// open it, even it is already open to reset turnToClose
			open();
			notifyWorldAboutChanges();
		} else {
			if (isOpen()) {
				close();
				notifyWorldAboutChanges();
			}
		}
		if (isOpen()) {
			super.onUsed(user);
		}
	}

	@Override
	public void onUsedBackwards(RPEntity user) {
		open();
		notifyWorldAboutChanges();
	}

	@Override
	public String describe() {
		String text = "You see a door.";
		if (hasDescription()) {
			text = getDescription();
		}
		text += " It is " + (isOpen() ? "open." : "closed.");
		return (text);
	}

	public void onTurnReached(int currentTurn, String message) {
		// if two players use this turn, we will be called twice.
		// Ignore the first call.
		if (currentTurn == turnToClose) {
			close();
			notifyWorldAboutChanges();
		}
	}

}
