/***********************************************************************************
 * Copyright (c) 2018 /// Project SWG /// www.projectswg.com                       *
 *                                                                                 *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on          *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies. *
 * Our goal is to create an emulator which will provide a server for players to    *
 * continue playing a game similar to the one they used to play. We are basing     *
 * it on the final publish of the game prior to end-game events.                   *
 *                                                                                 *
 * This file is part of Holocore.                                                  *
 *                                                                                 *
 * --------------------------------------------------------------------------------*
 *                                                                                 *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 *                                                                                 *
 * Holocore is distributed in the hope that it will be useful,                     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU Affero General Public License for more details.                             *
 *                                                                                 *
 * You should have received a copy of the GNU Affero General Public License        *
 * along with Holocore.  If not, see <http://www.gnu.org/licenses/>.               *
 ***********************************************************************************/
package com.projectswg.holocore.services.support.npc.spawn;

import com.projectswg.common.data.encodables.tangible.PvpFaction;
import com.projectswg.common.data.location.Location;
import com.projectswg.common.data.swgfile.ClientFactory;
import com.projectswg.holocore.intents.support.objects.swg.DestroyObjectIntent;
import com.projectswg.holocore.intents.support.objects.swg.ObjectCreatedIntent;
import com.projectswg.holocore.intents.support.data.config.ConfigChangedIntent;
import com.projectswg.holocore.resources.support.data.config.ConfigFile;
import com.projectswg.holocore.resources.support.objects.permissions.ContainerPermissionsType;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.objects.swg.building.BuildingObject;
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureDifficulty;
import com.projectswg.holocore.resources.support.objects.swg.custom.AIBehavior;
import com.projectswg.holocore.resources.support.objects.swg.custom.AIObject;
import com.projectswg.holocore.resources.support.data.server_info.DataManager;
import com.projectswg.holocore.resources.support.data.server_info.StandardLog;
import com.projectswg.holocore.resources.support.data.server_info.loader.BuildingLoader;
import com.projectswg.holocore.resources.support.data.server_info.loader.BuildingLoader.BuildingLoaderInfo;
import com.projectswg.holocore.resources.support.data.server_info.loader.npc.NpcLoader;
import com.projectswg.holocore.resources.support.data.server_info.loader.npc.NpcLoader.NpcInfo;
import com.projectswg.holocore.resources.support.data.server_info.loader.npc.NpcPatrolRouteLoader;
import com.projectswg.holocore.resources.support.data.server_info.loader.npc.NpcPatrolRouteLoader.PatrolRouteWaypoint;
import com.projectswg.holocore.resources.support.data.server_info.loader.npc.NpcStatLoader;
import com.projectswg.holocore.resources.support.data.server_info.loader.npc.NpcStatLoader.DetailNpcStatInfo;
import com.projectswg.holocore.resources.support.data.server_info.loader.npc.NpcStatLoader.NpcStatInfo;
import com.projectswg.holocore.resources.support.data.server_info.loader.spawn.StaticSpawnLoader;
import com.projectswg.holocore.resources.support.data.server_info.loader.spawn.StaticSpawnLoader.StaticSpawnInfo;
import com.projectswg.holocore.resources.support.npc.spawn.Spawner;
import com.projectswg.holocore.resources.support.npc.spawn.Spawner.ResolvedPatrolWaypoint;
import com.projectswg.holocore.resources.support.npc.spawn.Spawner.SpawnerFlag;
import com.projectswg.holocore.resources.support.npc.spawn.SpawnerType;
import com.projectswg.holocore.resources.support.objects.ObjectCreator;
import com.projectswg.holocore.resources.support.npc.spawn.NPCCreator;
import com.projectswg.holocore.services.support.objects.ObjectStorageService.ObjectLookup;
import me.joshlarson.jlcommon.concurrency.ScheduledThreadPool;
import me.joshlarson.jlcommon.control.IntentHandler;
import me.joshlarson.jlcommon.control.Service;
import me.joshlarson.jlcommon.log.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class SpawnerService extends Service {
	
	private static final String IDLE_MOOD = "idle";
	
	private final Map<Long, Spawner> spawnerMap;
	private final ScheduledThreadPool executor;
	
	public SpawnerService() {
		this.spawnerMap = new HashMap<>();
		this.executor = new ScheduledThreadPool(1, "spawner-service");
	}
	
	@Override
	public boolean initialize() {
		executor.start();
		if (DataManager.getConfig(ConfigFile.FEATURES).getBoolean("SPAWN-EGGS-ENABLED", true))
			loadSpawners();
		
		return true;
	}
	
	@Override
	public boolean terminate() {
		executor.stop();
		executor.awaitTermination(1000);
		return true;
	}
	
	@IntentHandler
	private void handleConfigChangedIntent(ConfigChangedIntent cci) {
		String newValue, oldValue;
		
		if (cci.getChangedConfig().equals(ConfigFile.FEATURES) && cci.getKey().equals("SPAWN-EGGS-ENABLED")) {
			newValue = cci.getNewValue();
			oldValue = cci.getOldValue();

			if (!newValue.equals(oldValue)) {
				if (Boolean.valueOf(newValue) && spawnerMap.isEmpty()) { // If nothing's been spawned, create it.
					loadSpawners();
				} else { // If anything's been spawned, delete it.
					removeSpawners();
				}
			}
		}
	}
	
	@IntentHandler
	private void handleDestroyObjectIntent(DestroyObjectIntent doi) {
		SWGObject destroyedObject = doi.getObject();
		if (!(destroyedObject instanceof AIObject))
			return;
		
		Spawner spawner = spawnerMap.remove(destroyedObject.getObjectId());
		
		if (spawner == null) {
			Log.e("Killed AI object %s has no linked Spawner - it cannot respawn!", destroyedObject);
			return;
		}
		
		executor.execute(spawner.getRespawnDelay() * 1000, () -> spawnNPC(spawner));
	}
	
	private void removeSpawners() {
		spawnerMap.values().forEach(spawner -> new DestroyObjectIntent(spawner.getSpawnerObject()).broadcast());
		spawnerMap.clear();
	}
	
	private void spawnNPC(Spawner spawner) {
		spawnerMap.put(NPCCreator.createNPC(spawner), spawner);
	}
	
	private void loadSpawners() {
		long startTime = StandardLog.onStartLoad("spawners");
		
		SpawnLoader loader = new SpawnLoader(this::spawnNPC);
		loader.load();
		createPatrolRouteWaypoints();
		
		StandardLog.onEndLoad(spawnerMap.size(), "spawners", startTime);
	}
	
	private static void createPatrolRouteWaypoints() {
		NpcPatrolRouteLoader npcPatrolRouteLoader = NpcPatrolRouteLoader.load();
		npcPatrolRouteLoader.forEach(route -> {
			for (PatrolRouteWaypoint waypoint : route) {
				SWGObject obj = ObjectCreator.createObjectFromTemplate("object/tangible/ground_spawning/patrol_waypoint.iff");
				obj.setContainerPermissions(ContainerPermissionsType.ADMIN);
				obj.setLocation(getPatrolWaypointLocation(waypoint));
				obj.moveToContainer(getPatrolWaypointParent(waypoint));
				ObjectCreatedIntent.broadcast(obj);
			}
		});
	}
	
	private static Location getPatrolWaypointLocation(PatrolRouteWaypoint waypoint) {
		return Location.builder()
				.setTerrain(waypoint.getTerrain())
				.setX(waypoint.getX())
				.setY(waypoint.getY())
				.setZ(waypoint.getZ()).build();
	}
	
	private static SWGObject getPatrolWaypointParent(PatrolRouteWaypoint waypoint) {
		if (waypoint.getBuildingId().isEmpty()) {
			Log.w("PatrolRouteWaypoint: Undefined building id for patrol id: %d and group id: %d", waypoint.getPatrolId(), waypoint.getGroupId());
			return null;
		}
		
		BuildingLoaderInfo buildingInfo = BuildingLoader.load().getBuilding(waypoint.getBuildingId());
		if (buildingInfo == null) {
			Log.w("PatrolRouteWaypoint: Invalid building id for patrol id: %d and group id: %d", waypoint.getPatrolId(), waypoint.getGroupId());
			return null;
		}
		
		if (buildingInfo.getId() == 0)
			return null;
		
		SWGObject building = ObjectLookup.getObjectById(buildingInfo.getId());
		if (!(building instanceof BuildingObject)) {
			Log.w("PatrolRouteWaypoint: Invalid building [%d] for patrol id: %d and group id: %d", buildingInfo.getId(), waypoint.getPatrolId(), waypoint.getGroupId());
			return null;
		}
		
		SWGObject cell = ((BuildingObject) building).getCellByNumber(waypoint.getCellId());
		if (cell == null) {
			Log.w("PatrolRouteWaypoint: Invalid cell [%d] for building: %d, patrol id: %d and group id: %d", waypoint.getCellId(), buildingInfo.getId(), waypoint.getPatrolId(), waypoint.getGroupId());
			return null;
		}
		
		return cell;
	}
	
	private static class SpawnLoader {
		
		private final Consumer<Spawner> npcSpawner;
		
		// Loaders
		private BuildingLoader buildingLoader;
		private NpcStatLoader npcStatLoader;
		private NpcLoader npcLoader;
		private StaticSpawnLoader staticSpawnLoader;
		private NpcPatrolRouteLoader npcPatrolRouteLoader;
		
		// Spawn info
		private BuildingLoaderInfo building;
		private NpcInfo npc;
		private NpcStatInfo npcStat;
		private List<ResolvedPatrolWaypoint> waypoints;
		
		public SpawnLoader(Consumer<Spawner> npcSpawner) {
			this.npcSpawner = npcSpawner;
			this.buildingLoader = null;
			this.npcStatLoader = null;
			this.npcLoader = null;
			this.staticSpawnLoader = null;
			this.npcPatrolRouteLoader = null;
			
			this.building = null;
			this.npc = null;
			this.npcStat = null;
			this.waypoints = null;
		}
		
		public void load() {
			buildingLoader = BuildingLoader.load();
			npcStatLoader = NpcStatLoader.load();
			npcLoader = NpcLoader.load();
			staticSpawnLoader = StaticSpawnLoader.load();
			npcPatrolRouteLoader = NpcPatrolRouteLoader.load();
			
			staticSpawnLoader.iterate(this::loadStaticSpawn);
		}
		
		private void loadStaticSpawn(StaticSpawnInfo spawn) {
			building = buildingLoader.getBuilding(spawn.getBuildingId());
			if (building == null) {
				Log.e("Invalid entry for spawn id [%d] - unknown building: '%s'", spawn.getId(), spawn.getBuildingId());
				return;
			}
			
			npc = npcLoader.getNpc(spawn.getNpcId());
			if (npc == null) {
				Log.w("Invalid entry for spawn id [%d] - unknown NPC: '%s'", spawn.getId(), spawn.getNpcId());
				return;
			}
			
			npcStat = npcStatLoader.getNpcStats(npc.getCombatLevel());
			if (npcStat == null) {
				Log.w("Invalid entry for spawn id [%d], NPC id [%d] - unknown NPC stat for level: %d", spawn.getId(), npc.getId(), npc.getCombatLevel());
				return;
			}
			
			if (spawn.getPatrolId() < 1000) {
				waypoints = null;
			} else {
				waypoints = npcPatrolRouteLoader.getPatrolRoute(spawn.getPatrolId())
						.stream()
						.map(route -> new ResolvedPatrolWaypoint(getPatrolWaypointParent(route), getPatrolWaypointLocation(route), route.getDelay(), route.getPatrolType()))
						.collect(Collectors.toList());
			}
			
			loadSpawner(spawn);
		}
		
		private void loadSpawner(StaticSpawnInfo spawn) {
			Spawner spawner = new Spawner(spawn.getId());
			spawner.setCreatureId(npc.getId());
			spawner.setIffTemplates(createTemplateList(npc.getIff()));
			spawner.setCreatureName(npc.getName());
			spawner.setCombatLevel((short) npc.getCombatLevel());
			spawner.setSpawnerFlag(SpawnerFlag.valueOf(npc.getAttackable()));
			spawner.setPatrolRoute(waypoints);
			spawner.setFormation(spawn.getPatrolFormation());
			spawner.setAttackSpeed(npc.getAttackSpeed());
			spawner.setMovementSpeed(npc.getMovementSpeed());
			setRespawnDelay(spawner, spawn);
			setDifficulty(spawner);
			setMoodAnimation(spawner, spawn);
			setAiBehavior(spawner, spawn);
			setLocation(spawner, spawn);
			setFaction(spawner);
			createEgg(spawner, spawn);
			
			for (int i = 0; i < spawn.getAmount(); i++) {
				npcSpawner.accept(spawner);
			}
		}
		
		private void createEgg(Spawner spawner, StaticSpawnInfo spawn) {
			SpawnerType spawnerType = SpawnerType.valueOf(spawn.getSpawnerType());
			SWGObject egg = ObjectCreator.createObjectFromTemplate(spawnerType.getObjectTemplate());
			egg.setContainerPermissions(ContainerPermissionsType.ADMIN);
			egg.setLocation(spawner.getLocation());
			egg.moveToContainer(getCell(spawner.getSpawnerId(), spawn.getCellId(), building));
			spawner.setSpawnerObject(egg);
			ObjectCreatedIntent.broadcast(egg);
		}
		
		private void setRespawnDelay(Spawner spawner, StaticSpawnInfo spawn) {
			int minRespawnDelay = spawn.getMinSpawnTime();
			int maxRespawnDelay = spawn.getMaxSpawnTime();
			
			if (minRespawnDelay > maxRespawnDelay) {
				Log.e("Spawner with ID %d has a minimum respawn time larger than the maximum respawn time", spawner.getSpawnerId());
				maxRespawnDelay = minRespawnDelay;
			}
			spawner.setMinRespawnDelay(minRespawnDelay);
			spawner.setMaxRespawnDelay(maxRespawnDelay);
		}
		
		private void setDifficulty(Spawner spawner) {
			char difficultyChar = npc.getDifficulty().charAt(0);
			
			CreatureDifficulty difficulty;
			DetailNpcStatInfo detailInfo; // undefined to trigger warnings if not defined below
			switch (difficultyChar) {
				default:
					Log.w("Unknown creature difficulty: %s", difficultyChar);
				case 'N':
					difficulty = CreatureDifficulty.NORMAL;
					detailInfo = npcStat.getNormalDetailStat();
					break;
				case 'E':
					difficulty = CreatureDifficulty.ELITE;
					detailInfo = npcStat.getEliteDetailStat();
					break;
				case 'B':
					difficulty = CreatureDifficulty.BOSS;
					detailInfo = npcStat.getBossDetailStat();
					break;
			}
			spawner.setMaxHealth(detailInfo.getHealth());
			spawner.setMaxAction(detailInfo.getAction());
			spawner.setCreatureDifficulty(difficulty);
		}
		
		private void setLocation(Spawner spawner, StaticSpawnInfo spawn) {
			Location loc = Location.builder()
					.setTerrain(building.getTerrain())
					.setPosition(spawn.getX(), spawn.getY(), spawn.getZ())
					.setHeading(spawn.getHeading())
					.build();
			spawner.setLocation(loc);
		}
		
		private void setFaction(Spawner spawner) {
			PvpFaction faction;
			
			switch (npc.getFaction()) {
				case "rebel":		faction = PvpFaction.REBEL; break;
				case "imperial":	faction = PvpFaction.IMPERIAL; break;
				default: return;
			}
			
			spawner.setFaction(faction, npc.isSpecForce());
		}
		
		private void setMoodAnimation(Spawner spawner, StaticSpawnInfo spawn) {
			String moodAnimation = spawn.getMood();
			
			if (moodAnimation.equals(IDLE_MOOD))
				moodAnimation = "neutral";
			
			spawner.setMoodAnimation(moodAnimation);
		}
		
		private void setAiBehavior(Spawner spawner, StaticSpawnInfo spawn) {
			AIBehavior aiBehavior = AIBehavior.valueOf(spawn.getBehavior());
			spawner.setAIBehavior(aiBehavior);
			if (aiBehavior == AIBehavior.LOITER) {
				spawner.setFloatRadius(spawn.getLoiterRadius());
			}
		}
		
		private SWGObject getCell(int spawnId, int cellId, BuildingLoaderInfo buildingInfo) {
			if (buildingInfo.getId() != 0 && cellId == 0) {
				Log.e("No cell ID specified for spawner with ID %d", spawnId);
				return null;
			} else if (buildingInfo.getId() == 0) {
				if (cellId != 0)
					Log.w("Unnecessary cell ID specified for spawner with ID %d", spawnId);
				return null; // No cell to find
			}
			
			SWGObject building = ObjectLookup.getObjectById(buildingInfo.getId());
			if (!(building instanceof BuildingObject)) {
				Log.w("Skipping spawner with ID %d - building_id %d didn't reference a BuildingObject!", spawnId, buildingInfo.getId());
				return null;
			}
			
			SWGObject cellObject = ((BuildingObject) building).getCellByNumber(cellId);
			if (cellObject == null) {
				Log.e("Spawner with ID %d - building %d didn't have cell ID %d!", spawnId, buildingInfo.getId(), cellId);
			}
			return cellObject;
		}
		
		private String [] createTemplateList(String templates) {
			int count = 0;
			int ind = -1;
			while (ind < templates.length()) {
				ind = templates.indexOf(';', ind + 1);
				count++;
				if (ind == -1)
					break;
			}
			
			ind = 0;
			String[] templateList = new String[count];
			for (int i = 0; i < count; i++) {
				int next = templates.indexOf(';', ind);
				if (next == -1)
					next = templates.length();
				templateList[i] = ClientFactory.formatToSharedFile("object/mobile/" + templates.substring(ind, next));
				ind = next + 1;
			}
			return templateList;
		}
		
	}
}