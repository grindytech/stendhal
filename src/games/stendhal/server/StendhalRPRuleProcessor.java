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
package games.stendhal.server;

import games.stendhal.common.Pair;
import games.stendhal.server.actions.*;
import games.stendhal.server.entity.PlantGrower;
import games.stendhal.server.entity.SheepFood;
import games.stendhal.server.entity.Entity;
import games.stendhal.server.entity.Player;
import games.stendhal.server.entity.RPEntity;
import games.stendhal.server.entity.Blood;
import games.stendhal.server.entity.item.Corpse;
import games.stendhal.server.entity.npc.Behaviours;
import games.stendhal.server.entity.npc.NPC;
import games.stendhal.server.pathfinder.Path;
// import org.python.util.PythonInterpreter;
// import org.python.core.*;
// import games.stendhal.server.scripting.StendhalPythonConfig;
import java.util.*;
import marauroa.common.Log4J;
import marauroa.common.game.*;
import marauroa.server.game.*;
import org.apache.log4j.Logger;

public class StendhalRPRuleProcessor implements IRPRuleProcessor {
	/** the logger instance. */
	private static final Logger logger = Log4J
			.getLogger(StendhalRPRuleProcessor.class);

	private JDBCPlayerDatabase database;

	private static Map<String, ActionListener> actionsMap;
	static {
		actionsMap = new HashMap<String, ActionListener>();
	}

	private RPServerManager rpman;

	private StendhalRPWorld world;

	private List<Player> playersObject;

	private List<Player> playersObjectRmText;

	private List<NPC> npcs;

	private List<NPC> npcsToAdd;

	private List<NPC> npcsToRemove;

	private List<Pair<RPEntity, RPEntity>> entityToKill;

	private List<RespawnPoint> respawnPoints;

	private List<SheepFood> sheepFoodItems;

	private List<PlantGrower> plantGrowers;

	private List<Corpse> corpses;

	private List<Corpse> corpsesToRemove;

	private List<Blood> bloods;

	private List<Blood> bloodsToRemove;

	private StendhalScriptSystem scripts;

	public static void register(String action, ActionListener actionClass) {
		if (actionsMap.get(action) != null) {
			logger.error("Registering twice the same action handler: "
						+ action);
		}
		actionsMap.put(action, actionClass);
	}

	private void registerActions() {
		Administration.register();
		Attack.register();
		Buddy.register();
		Chat.register();
		Displace.register();
		Equipment.register();
		Face.register();
		Look.register();
		Move.register();
		Outfit.register();
		Own.register();
		PlayersQuery.register();
		Stop.register();
		Use.register();
	}

	public StendhalRPRuleProcessor() {
		database = (JDBCPlayerDatabase) JDBCPlayerDatabase.getDatabase();
		playersObject = new LinkedList<Player>();
		playersObjectRmText = new LinkedList<Player>();
		npcs = new LinkedList<NPC>();
		respawnPoints = new LinkedList<RespawnPoint>();
		sheepFoodItems = new LinkedList<SheepFood>();
		plantGrowers = new LinkedList<PlantGrower>();
		npcsToAdd = new LinkedList<NPC>();
		npcsToRemove = new LinkedList<NPC>();
		entityToKill = new LinkedList<Pair<RPEntity, RPEntity>>();
		corpses = new LinkedList<Corpse>();
		corpsesToRemove = new LinkedList<Corpse>();
		bloods = new LinkedList<Blood>();
		bloodsToRemove = new LinkedList<Blood>();
		scripts = StendhalScriptSystem.get();
		registerActions();
	}

	public void addGameEvent(String source, String event, String... params) {
		try {
			Transaction transaction = database.getTransaction();
			database.addGameEvent(transaction, source, event, params);
			transaction.commit();
		} catch (Exception e) {
			logger.warn("Can't store game event", e);
		}
	}

	/**
	 * 
	 * Set the context where the actions are executed.
	 * 
	 * 
	 * 
	 * @param rpman
	 * 
	 * @param world
	 * 
	 */
	public void setContext(RPServerManager rpman, RPWorld world) {
		try {
			this.rpman = rpman;
			this.world = (StendhalRPWorld) world;
			StendhalRPAction.initialize(rpman, this, world);
			Behaviours.initialize(rpman, this, world);
			Path.initialize(world);
			Entity.setRPContext(this, this.world);
			/* Initialize quests */
			new StendhalQuestSystem(this.world,	this);
			for (IRPZone zone : world) {
				StendhalRPZone szone = (StendhalRPZone) zone;
				npcs.addAll(szone.getNPCList());
				respawnPoints.addAll(szone.getRespawnPointList());
				sheepFoodItems.addAll(szone.getSheepFoodItemList());
				plantGrowers.addAll(szone.getPlantGrowers());
			}
			// /* Run python script */
			// PythonInterpreter interpreter=new PythonInterpreter();
			// interpreter.execfile("data/script/stendhal.py");
			//      
			// PyInstance
			// object=(PyInstance)interpreter.eval("Configuration()");
			// StendhalPythonConfig
			// config=(StendhalPythonConfig)object.__tojava__(StendhalPythonConfig.class);
			//      
			// config.setContext(this,this.world);
			// config.init();
			/*
			 * Run optional StendhalServerExtension(s) TODO: Read the extensions
			 * to load from configuration file
			 */
			/* Run groovy script extension */
			StendhalServerExtension.getInstance(
					"games.stendhal.server.scripting.StendhalGroovyRunner",
					this, this.world).init();
			/* Run http server */
			// StendhalServerExtension.getInstance("games.stendhal.server.StendhalHttpServer",this,
			// this.world).init();
		} catch (Exception e) {
			logger.fatal("cannot set Context. exiting", e);
			System.exit(-1);
		}
	}

	public boolean checkGameVersion(String game, String version) {
		if (game.equals("stendhal")) {
			return true;
		} else {
			return false;
		}
	}

	private boolean isValidUsername(String username) {
		/** TODO: Complete this. Should read the list from XML file */
		if (username.indexOf(' ') != -1)
			return false;
		if (username.toLowerCase().contains("admin"))
			return false;
		return true;
	}

	public boolean createAccount(String username, String password, String email) {
		if (!isValidUsername(username)) {
			return false;
		}
		stendhalcreateaccount account = new stendhalcreateaccount();
		return account.execute(username, password, email);
	}

	public void addNPC(NPC npc) {
		npcsToAdd.add(npc);
	}

	public void killRPEntity(RPEntity entity, RPEntity who) {
		entityToKill.add(new Pair<RPEntity, RPEntity>(entity, who));
	}

	public void removePlayerText(Player player) {
		playersObjectRmText.add(player);
	}

	public void addCorpse(Corpse corpse) {
		corpses.add(corpse);
	}

	public void removeCorpse(Corpse corpse) {
		for (RPSlot slot : corpse.slots()) {
			for (RPObject object : slot) {
				if (object instanceof Corpse) {
					removeCorpse((Corpse) object);
				}
			}
		}
		corpsesToRemove.add(corpse);
	}

	public void addBlood(Blood blood) {
		bloods.add(blood);
	}

	public boolean bloodAt(int x, int y) {
		for (Blood blood : bloods) {
			if (blood.getx() == x && blood.gety() == y) {
				return true;
			}
		}
		return false;
	}

	public void removeBlood(Blood blood) {
		bloodsToRemove.add(blood);
	}

	public List<Player> getPlayers() {
		return playersObject;
	}

	public List<SheepFood> getSheepFoodItems() {
		return sheepFoodItems;
	}

	public List<PlantGrower> getPlantGrowers() {
		return plantGrowers;
	}

	public List<NPC> getNPCs() {
		return npcs;
	}

	public boolean removeNPC(NPC npc) {
		return npcsToRemove.add(npc);
	}

	public boolean onActionAdd(RPAction action, List<RPAction> actionList) {
		return true;
	}

	public boolean onIncompleteActionAdd(RPAction action,
			List<RPAction> actionList) {
		return true;
	}

	public RPAction.Status execute(RPObject.ID id, RPAction action) {
		Log4J.startMethod(logger, "execute");
		RPAction.Status status = RPAction.Status.SUCCESS;
		try {
			Player player = (Player) world.get(id);
			String type = action.get("type");
			ActionListener actionListener = actionsMap.get(type);
			actionListener.onAction(world, this, player, action);
		} catch (Exception e) {
			logger.error("cannot execute action " + action, e);
		} finally {
			Log4J.finishMethod(logger, "execute");
		}
		return status;
	}

	public int getTurn() {
		return rpman.getTurn();
	}

	/** Notify it when a new turn happens */
	synchronized public void beginTurn() {
		Log4J.startMethod(logger, "beginTurn");
		long start = System.nanoTime();
		int creatures = 0;
		for (RespawnPoint point : respawnPoints)
			creatures += point.size();
		int objects = 0;
		for (IRPZone zone : world)
			objects += zone.size();
		logger.debug("lists: CO:" + corpses.size() + ",F:" + sheepFoodItems.size()
				+ ",NPC:" + npcs.size() + ",P:" + playersObject.size() + ",CR:"
				+ creatures + ",OB:" + objects);
		logger.debug("lists: CO:" + corpsesToRemove.size() + ",NPC:"
				+ npcsToAdd.size() + ",NPC:" + npcsToRemove.size() + ",P:"
				+ playersObjectRmText.size() + ",R:" + respawnPoints.size());
		try {
			// We keep the number of players logged.
			Statistics.getStatistics().set("Players logged",
					playersObject.size());
			// In order for the last hit to be visible dead happens at two
			// steps.
			for (Pair<RPEntity, RPEntity> entity : entityToKill) {
				try {
					entity.first().onDead(entity.second());
				} catch (Exception e) {
					logger.fatal("Player has logout before dead", e);
				}
			}
			entityToKill.clear();
			// Done this way because a problem with comodification... :(
			npcs.removeAll(npcsToRemove);
			corpses.removeAll(corpsesToRemove);
			bloods.removeAll(bloodsToRemove);
			npcs.addAll(npcsToAdd);
			npcsToAdd.clear();
			npcsToRemove.clear();
			corpsesToRemove.clear();
			bloodsToRemove.clear();
			for (Player object : playersObject) {
				if (object.has("risk")) {
					object.remove("risk");
					world.modify(object);
				}
				if (object.has("damage")) {
					object.remove("damage");
					world.modify(object);
				}
				if (object.has("dead")) {
					object.remove("dead");
					world.modify(object);
				}
				if (object.has("online")) {
					object.remove("online");
					world.modify(object);
				}
				if (object.has("offline")) {
					object.remove("offline");
					world.modify(object);
				}
				if (object.hasPath()) {
					if (Path.followPath(object, 1)) {
						object.stop();
						object.clearPath();
					}
					world.modify(object);
				}
				if (!object.stopped()) {
					StendhalRPAction.move(object);
				}
				if (getTurn() % 5 == 0 && object.isAttacking()) // 1 round = 5
				// turns
				{
					StendhalRPAction.attack(object, object.getAttackTarget());
				}
				if (getTurn() % 180 == 0) {
					object.setAge(object.getAge() + 1);
					world.modify(object);
				}
				object.consume(getTurn());
			}
			for (NPC npc : npcs)
				npc.logic();
			for (Player object : playersObjectRmText) {
				if (object.has("text")) {
					object.remove("text");
					world.modify(object);
				}
				if (object.has("private_text")) {
					object.remove("private_text");
					world.modify(object);
				}
			}
			playersObjectRmText.clear();
		} catch (Exception e) {
			logger.error("error in beginTurn", e);
		} finally {
			logger.info("Begin turn: " + (System.nanoTime() - start)
					/ 1000000.0);
			Log4J.finishMethod(logger, "beginTurn");
		}
	}

	synchronized public void endTurn() {
		Log4J.startMethod(logger, "endTurn");
		long start = System.nanoTime();
		try {
			for (SheepFood food : sheepFoodItems)
				food.regrow();
			for (PlantGrower plantGrower : plantGrowers)
				plantGrower.regrow();
			for (RespawnPoint point : respawnPoints)
				point.nextTurn();
			for (Corpse corpse : corpses)
				corpse.logic();
			for (Blood blood : bloods)
				blood.logic();
			scripts.logic();
		} catch (Exception e) {
			logger.error("error in endTurn", e);
		} finally {
			logger.info("End turn: " + (System.nanoTime() - start) / 1000000.0);
			Log4J.finishMethod(logger, "endTurn");
		}
	}

	synchronized public boolean onInit(RPObject object)
			throws RPObjectInvalidException {
		Log4J.startMethod(logger, "onInit");
		try {
			Player player = Player.create(object);
			playersObjectRmText.add(player);
			playersObject.add(player);
			// Notify other players about this event
			for (Player p : getPlayers()) {
				p.notifyOnline(player.getName());
			}
			addGameEvent(player.getName(), "login");
			return true;
		} catch (Exception e) {
			logger.error("There has been a severe problem loading player "
					+ object.get("#db_id"), e);
			return false;
		} finally {
			Log4J.finishMethod(logger, "onInit");
		}
	}

	synchronized public boolean onExit(RPObject.ID id) {
		Log4J.startMethod(logger, "onExit");
		try {
			for (Player object : playersObject) {
				if (object.getID().equals(id)) {
					if (entityToKill.contains(object)) {
						logger.info("Logout before dead");
						return false;
					}
					// Notify other players about this event
					for (Player p : getPlayers()) {
						p.notifyOffline(object.getName());
					}
					Player.destroy(object);
					playersObject.remove(object);
					addGameEvent(object.getName(), "logout");
					logger.debug("removed player " + object);
					break;
				}
			}
			return true;
		} catch (Exception e) {
			logger.error("error in onExit", e);
			return true;
		} finally {
			Log4J.finishMethod(logger, "onExit");
		}
	}

	synchronized public boolean onTimeout(RPObject.ID id) {
		Log4J.startMethod(logger, "onTimeout");
		try {
			return onExit(id);
		} finally {
			Log4J.finishMethod(logger, "onTimeout");
		}
	}
}
