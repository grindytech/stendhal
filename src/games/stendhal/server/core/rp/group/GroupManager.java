/* $Id$ */
/***************************************************************************
 *                   (C) Copyright 2003-2010 - Stendhal                    *
 ***************************************************************************
 ***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 ***************************************************************************/
package games.stendhal.server.core.rp.group;

import games.stendhal.server.core.events.TurnListener;
import games.stendhal.server.core.events.TurnNotifier;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * manages player groups
 *
 * @author hendrik
 */
public class GroupManager implements TurnListener {

	private Set<Group> groups = new HashSet<Group>();

	/**
	 * creates a new GroupManager.
	 */
	public GroupManager() {
		TurnNotifier.get().notifyInSeconds(60, this);
	}

	/**
	 * returns the group the specified player is a member of.
	 *
	 * @param playerName name of player
	 * @return Group or <code>null</code> if the player is not a member of any group
	 */
	public Group getGroup(String playerName) {
		for (Group group : groups) {
			if (group.hasMember(playerName)) {
				return group;
			}
		}
		return null;
	}


	/**
	 * Creates a new group
	 *
	 * @param playerName name of player
	 * @return the new group or <code>null</code> if the group could not be created
	 */
	public Group createGroup(String playerName) {
		if (getGroup(playerName) != null) {
			return null;
		}

		Group group = new Group();
		group.addMember(playerName);
		groups.add(group);
		return group;
	}


	/**
	 * destroys a group
	 *
	 * @param playerName name of player
	 * @return true, if the group was destroyed; false otherwise
	 */
	public boolean destroyGroup(String playerName) {
		Group group = getGroup(playerName);
		if (group == null) {
			return false;
		}
		group.destory();
		groups.remove(group);
		return true;
	}


	/**
	 * handles timeouts and removes empty groups
	 */
	public void clean() {
		Iterator<Group> itr = groups.iterator();
		while (itr.hasNext()) {
			Group group = itr.next();
			group.clean();
			if (group.isEmpty()) {
				itr.remove();
			}
		}
	}

	/**
	 * cleans up 
	 */
	public void onTurnReached(int currentTurn) {
		clean();
		TurnNotifier.get().notifyInSeconds(60, this);
	}
}
