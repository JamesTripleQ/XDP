package neon.xdp.data.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.impl.campaign.procgen.DefenderDataOverride;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import neon.xdp.data.plugins.XDPModPlugin;
import neon.xdp.data.scripts.campaign.ids.XDP_IDs;
import neon.xdp.data.scripts.world.vigil.XDP_HoldoutDemandNegator;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicCampaign;

import java.awt.*;

import static com.fs.starfarer.api.impl.campaign.ids.Commodities.ALPHA_CORE;

public class XDP_gate implements SectorGeneratorPlugin {
	public static String NOT_RANDOM_MISSION_TARGET = "$not_random_mission_target";

	@Override
	public void generate(SectorAPI sector) {
		Vector2f location  = new Vector2f(-41517, -22061);

		StarSystemAPI system = Global.getSector().createStarSystem("Vigil");
		system.setName("Vigil");
		system.getLocation().set(location);
		system.initNonStarCenter();
		system.generateAnchorIfNeeded();
	    system.addTag(Tags.THEME_DERELICT);
		system.addTag(Tags.THEME_HIDDEN);
		system.addTag(Tags.THEME_UNSAFE);
		system.addTag(Tags.THEME_DERELICT_PROBES);

		system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");

		//In the Abyss


		HyperspaceTerrainPlugin hyperTerrain = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
		NebulaEditor editor = new NebulaEditor(hyperTerrain);
		editor.clearArc(system.getLocation().x, system.getLocation().y, 0, 200, 0, 360f);

		//Vigil Star
		PlanetAPI gate = system.initStar("xdp_gate", // unique id for this star
				"star_red_supergiant", // id in planets.json
				1500f, 		  // radius (in pixels at default zoom)
				700, // corona
				10f, // solar wind burn level
				0.7f, // flare probability
				3.0f); // CR loss multiplier, good values are in the range of 1-5

		system.setLightColor(new Color(255, 80, 0));
		system.getLocation().set(location);
		//Maddie Fractal World

		system.addAsteroidBelt(gate, 300, 9000, 1000, 160, 220); // Ring system located between inner and outer planets
		system.addRingBand(gate, "misc", "rings_asteroids0", 256f, 1, Color.white, 256f, 8900, 200f, null, null);
		system.addRingBand(gate, "misc", "rings_dust0", 256f, 1, Color.white, 256f, 9000, 200f, null, null);

        PlanetAPI fueldepot = system.addPlanet("xdp_fueldepot", gate, "DESIGNATION FFL-217-PS-RN", "gas_giant", 50, 300, 6000, 400);
		fueldepot.setCustomDescriptionId("xdp_fueldepot");
		fueldepot.getMarket().addCondition(Conditions.HIGH_GRAVITY);
		fueldepot.getMarket().addCondition(Conditions.VOLATILES_PLENTIFUL);
		fueldepot.getMarket().addCondition(Conditions.DENSE_ATMOSPHERE);
		fueldepot.getMarket().addCondition(Conditions.EXTREME_WEATHER);
		fueldepot.getMarket().getMemoryWithoutUpdate().set(NOT_RANDOM_MISSION_TARGET, true);
		fueldepot.getMemoryWithoutUpdate().set("$xdp_fueldepottag", true);

		SectorEntityToken field = system.addTerrain(Terrain.MAGNETIC_FIELD,
				new MagneticFieldTerrainPlugin.MagneticFieldParams(150f, // terrain effect band width
						320, // terrain effect middle radius
						fueldepot, // entity that it's around
						309f, // visual band start
						710f, // visual band end
						new Color(130, 60, 150, 130), // base color
						4f, // probability to spawn aurora sequence, checked once/day when no aurora in progress
						new Color(130, 60, 150, 130),
						new Color(150, 30, 120, 150),
						new Color(10, 190, 200, 190),
						new Color(100, 200, 150, 240),
						new Color(100, 200, 130, 255),
						new Color(75, 90, 160, 255),
						new Color(100, 200, 200, 240)));
		field.setCircularOrbit(fueldepot, 0, 0, 75);
//		Misc.setDefenderOverride(
//				fueldepot,//Code name foir the entity
//				new DefenderDataOverride("derelict", //code name for the faction doing the defender
//						1f, //Probibility there will be defenders
//						150, // Minimum fleet points for the defenders
//						200 // Maxmimum fleet points for the defenders
//				));


		PlanetAPI manufacturingcenter2 = system.addPlanet("xdp_manufacturingcenter2", fueldepot, "DESIGNATION MNG-5541-PS-RN", "barren_castiron", 50, 60, 720, 90);
		manufacturingcenter2.setCustomDescriptionId("xdp_manufacturingcenter2");
		manufacturingcenter2.getMarket().addCondition(Conditions.NO_ATMOSPHERE);
		manufacturingcenter2.getMarket().addCondition(Conditions.COLD);
		manufacturingcenter2.getMarket().addCondition(Conditions.DARK);
		manufacturingcenter2.getMarket().addCondition(Conditions.RUINS_SCATTERED);
		manufacturingcenter2.getMarket().addCondition(Conditions.RARE_ORE_SPARSE);
		manufacturingcenter2.getMarket().addCondition(Conditions.ORE_MODERATE);
		manufacturingcenter2.getMarket().getMemoryWithoutUpdate().set(NOT_RANDOM_MISSION_TARGET, true);
		manufacturingcenter2.getMemoryWithoutUpdate().set("$xd_manufacturingcenter2tag", true);
		manufacturingcenter2.getMarket().setFactionId("derelict");
		SectorEntityToken field2 = system.addTerrain(Terrain.MAGNETIC_FIELD,
				new MagneticFieldTerrainPlugin.MagneticFieldParams(150f, // terrain effect band width
						320, // terrain effect middle radius
						manufacturingcenter2, // entity that it's around
						61f, // visual band start
						200f, // visual band end
						new Color(130, 60, 150, 130), // base color
						2f, // probability to spawn aurora sequence, checked once/day when no aurora in progress
						new Color(130, 60, 150, 130),
						new Color(150, 30, 120, 150),
						new Color(10, 190, 200, 190),
						new Color(100, 200, 150, 240),
						new Color(100, 200, 130, 255),
						new Color(75, 90, 160, 255),
						new Color(100, 200, 200, 240)));
		field2.setCircularOrbit(fueldepot, 0, 0, 75);
//		Misc.setDefenderOverride(
//				manufacturingcenter2,//Code name foir the entity
//				new DefenderDataOverride("derelict", //code name for the faction doing the defender
//						1f, //Probibility there will be defenders
//						190, // Minimum fleet points for the defenders
//						300 // Maxmimum fleet points for the defenders
//				));


		SectorEntityToken anchor = system.getHyperspaceAnchor();
		CustomCampaignEntityAPI beacon = Global.getSector().getHyperspace().addCustomEntity("warning_beacon_xdp_", "Warning Beacon", "warning_beacon", Factions.DERELICT);
		beacon.setCircularOrbitPointingDown(anchor, 100, 300, 65f);
		Color glowColor = new Color(250,55,0,255);
		Color pingColor = new Color(250,55,0,255);
		Misc.setWarningBeaconColors(beacon, glowColor, pingColor);
		beacon.getMemoryWithoutUpdate().set("$xdp_beacontag", true);
		beacon.setCustomDescriptionId("xdp_warningbeacon");
		beacon.getMemoryWithoutUpdate().set("$xdp_warningbeacontag", true);
		Misc.setDefenderOverride(
				beacon,//Code name foir the entity
				new DefenderDataOverride("derelict", //code name for the faction doing the defender
						0.5f, //Probibility there will be defenders
						40, // Minimum fleet points for the defenders
						60 // Maxmimum fleet points for the defenders
				));

		//SuperDerelictSeededFleetManager(
		//		SectorEntityToken manufacturingcenter, // Where the thing is spawning from
		//float thresholdLY, //How far away your fleet is from this thing before it starts spawning things (to save on CPU)
		//int minFleets, //(The minimum number o ffleets to spawn)
		//int maxFleets 10,//(The maximum number o ffleets to spawn)
		//float respawnDelay, //(How long it takes for a fleet to spawn in days up to max fleets)
		//int minPts, //(How small the fleet can be)
		//int maxPts) //(How large the fleet can be)

		//Or, written in this form:
		//system.addScript(new SuperDerelictSeededFleetManager(entity, thresholdLY, minFleets, maxFleets, respawnDelay, minPts, maxPts));
		//For example:



		SectorEntityToken VigilRelay = system.addCustomEntity(null, "Comm Relay", "comm_relay", "derelict");
		VigilRelay.setCircularOrbit(gate, 190, 7000, 280);

		SectorEntityToken VigilBuoy = system.addCustomEntity(null, "Nav Buoy", "nav_buoy", "derelict"); // Makeshift nav buoy at L5 of Orguk
		VigilBuoy.setCircularOrbit(gate, 310, 7000, 280);

		PlanetAPI manufacturingcenter = system.addPlanet("xdp_manufacturingcenter", gate, "DESIGNATION MNG-5540-PS-RN", "barren_castiron", 50, 80, 3800, 90);
		manufacturingcenter.setCustomDescriptionId("xdp_manufacturingcenter");
		manufacturingcenter.getMarket().addCondition(Conditions.NO_ATMOSPHERE);
		manufacturingcenter.getMarket().addCondition(Conditions.LOW_GRAVITY);
		manufacturingcenter.getMarket().addCondition(Conditions.COLD);
		manufacturingcenter.getMarket().addCondition(Conditions.RUINS_WIDESPREAD);
		manufacturingcenter.getMarket().addCondition(Conditions.RARE_ORE_RICH);
		manufacturingcenter.getMarket().addCondition(Conditions.ORE_RICH);
		manufacturingcenter.getMarket().getMemoryWithoutUpdate().set(NOT_RANDOM_MISSION_TARGET, true);
		manufacturingcenter.getMemoryWithoutUpdate().set("$xdp_manufacturingcentertag", true);
//		Misc.setDefenderOverride(
//				manufacturingcenter,//Code name foir the entity
//				new DefenderDataOverride("derelict", //code name for the faction doing the defender
//						1f, //Probibility there will be defenders
//						150, // Minimum fleet points for the defenders
//						300 // Maxmimum fleet points for the defenders
//				));


		PlanetAPI habplanet = system.addPlanet("xdp_habplanet", gate, "DESIGNATION HBT-1030789-PS-RN", "terran-eccentric", 50, 75, 5900, 90);
		habplanet.setCustomDescriptionId("xdp_habplanet");
		habplanet.getMarket().addCondition(Conditions.HABITABLE);
		habplanet.getMarket().addCondition(Conditions.RUINS_VAST);
		habplanet.getMarket().addCondition(Conditions.FARMLAND_ADEQUATE);
		habplanet.getMarket().addCondition(Conditions.ORE_SPARSE);
		habplanet.getMarket().addCondition(Conditions.MILD_CLIMATE);
		habplanet.getMarket().getMemoryWithoutUpdate().set(NOT_RANDOM_MISSION_TARGET, true);
		habplanet.getMemoryWithoutUpdate().set("$xdp_habplanettag", true);
//		Misc.setDefenderOverride(
//				habplanet,//Code name foir the entity
//				new DefenderDataOverride("derelict", //code name for the faction doing the defender
//						1f, //Probibility there will be defenders
//						150, // Minimum fleet points for the defenders
//						300 // Maxmimum fleet points for the defenders
//				));


		PlanetAPI fringeworld = system.addPlanet("xdp_fringeworld", gate, "DESIGNATION HBT-1030799-PS-RN-FL", "cryovolcanic", 50, 50, 12000, 450);
		fringeworld.setCustomDescriptionId("xdp_fringeworld");
		fringeworld.getMarket().addCondition(Conditions.EXTREME_WEATHER);
		fringeworld.getMarket().addCondition(Conditions.VERY_COLD);
		fringeworld.getMarket().addCondition(Conditions.RUINS_SCATTERED);
        fringeworld.getMarket().addCondition(Conditions.TECTONIC_ACTIVITY);
        fringeworld.getMarket().addCondition(Conditions.FARMLAND_ADEQUATE);
        fringeworld.getMarket().addCondition(Conditions.ORE_SPARSE);
        fringeworld.getMarket().getMemoryWithoutUpdate().set(NOT_RANDOM_MISSION_TARGET, true);
        fringeworld.getMemoryWithoutUpdate().set("$xdp_fringeworldtag", true);
//		Misc.setDefenderOverride(
//				fringeworld,//Code name foir the entity
//				new DefenderDataOverride("derelict", //code name for the faction doing the defender
//						1f, //Probibility there will be defenders
//						150, // Minimum fleet points for the defenders
//						300 // Maxmimum fleet points for the defenders
//				));


		SectorEntityToken deadgate = system.addCustomEntity(
				"xdp_deadgate",
				"Inactive Gate",
				"inactive_gate",
				"derelict");
		deadgate.setCircularOrbitPointingDown(gate, 270, 4900, 175);
		deadgate.setCustomDescriptionId("xdp_deadgate");
		deadgate.setInteractionImage("illustrations", "dead_gate");
		deadgate.getMemoryWithoutUpdate().set("$xdp_deadgatetag", true);


		SectorEntityToken miningstation = system.addCustomEntity(
				"xdp_miningstation",
				"Explorarium Mining Station",
				"station_mining_remnant",
				"derelict");
		miningstation.setCircularOrbitPointingDown(manufacturingcenter, 270, 200, 175);
		miningstation.setCustomDescriptionId("xdp_miningstation");
		miningstation.setInteractionImage("illustrations", "abandoned_station3");
		miningstation.addTag("xdp_miningstationtag");
		miningstation.getMemoryWithoutUpdate().set("$xdp_miningstationtag", true);
		Misc.setDefenderOverride(
				miningstation,//Code name foir the entity
				new DefenderDataOverride("derelict", //code name for the faction doing the defender
						1f, //Probibility there will be defenders
						150, // Minimum fleet points for the defenders
						300 // Maxmimum fleet points for the defenders
				));

		SectorEntityToken surveyship = system.addCustomEntity(
				"xdp_surveyship",
				"Explorarium Survey Ship",
				"derelict_survey_ship",
				"derelict");
		surveyship.setCircularOrbitPointingDown(habplanet, 270, 225, 175);
		surveyship.setCustomDescriptionId("xdp_miningstation");
		surveyship.addTag("xdp_surveyshiptag");
		surveyship.getMemoryWithoutUpdate().set("$xdp_surveyshiptag", true);
		Misc.setDefenderOverride(
				surveyship,//Code name foir the entity
				new DefenderDataOverride("derelict", //code name for the faction doing the defender
						1f, //Probibility there will be defenders
						150, // Minimum fleet points for the defenders
						300 // Maxmimum fleet points for the defenders
				));

		//Jump points
		JumpPointAPI jumpPointAlpha = Global.getFactory().createJumpPoint("vigil_jump_point_alpha", "Vigil, Inner Jump-Point");
		OrbitAPI orbitVigil = Global.getFactory().createCircularOrbit(gate, 4000, 4300, 300);
		jumpPointAlpha.setOrbit(orbitVigil);
		jumpPointAlpha.setStandardWormholeToHyperspaceVisual();
		system.addEntity(jumpPointAlpha);

		JumpPointAPI jumpPointBeta = Global.getFactory().createJumpPoint("vigil_jump_point_beta", "Vigil, Jump-Point");
		OrbitAPI orbitVigil2 = Global.getFactory().createCircularOrbit(gate, 4000, 12000, 300);
		jumpPointBeta.setOrbit(orbitVigil2);
		jumpPointBeta.setStandardWormholeToHyperspaceVisual();
		system.addEntity(jumpPointBeta);
		system.autogenerateHyperspaceJumpPoints(true, true);

		//
		// BEGIN FORGESHIP
		//

		SectorEntityToken station = system.addCustomEntity("xdp_holdout", "Active Derelict Mothership", "xdp_mothership", Factions.DERELICT);
		station.setCircularOrbitPointingDown(system.getEntityById("xdp_fueldepot"), 45, 650, 50);
		station.setSensorProfile(1f);
		station.setDiscoverable(true);
		station.getDetectedRangeMod().modifyFlat("gen", 5000f);
//		Misc.setDefenderOverride(
//				station,//Code name foir the entity
//				new DefenderDataOverride("derelict", //code name for the faction doing the defender
//						1f, //Probibility there will be defenders
//						240, // Minimum fleet points for the defenders
//						300 // Maxmimum fleet points for the defenders
//				));

		MarketAPI market = Global.getFactory().createMarket("xdp_holdout_market", station.getName(), 3);
		market.setFactionId(Factions.DERELICT);
		market.setHidden(true);

		market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
		market.setPrimaryEntity(station);

		market.addIndustry(XDP_IDs.CENTRAL_PROCESSING);
		market.addIndustry(Industries.SPACEPORT);
		market.addIndustry(Industries.WAYSTATION);
		market.addIndustry(Industries.HEAVYINDUSTRY);
		market.addIndustry(Industries.HEAVYBATTERIES);

		market.addCondition(XDP_IDs.DEFENSIVE_DRONESYSTEM);

		market.addSubmarket(XDP_IDs.FORGESHIP_MARKET);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		market.addSubmarket(Submarkets.LOCAL_RESOURCES);

		market.getMemoryWithoutUpdate().set("$noBar", true);

		// forgeship submarket has no tariff tho
		market.getTariff().modifyFlat("default_tariff", market.getFaction().getTariffFraction());

		station.setMarket(market);

		market.setEconGroup(market.getFactionId());
		market.getMemoryWithoutUpdate().set(DecivTracker.NO_DECIV_KEY, true);
		market.addTag(Tags.MARKET_NO_OFFICER_SPAWN); // otherwise we get "human" Dustkeeper officers/admins

		market.reapplyIndustries();

		Global.getSector().getEconomy().addMarket(market, false);

		market.removeSubmarket(Submarkets.GENERIC_MILITARY);
		StoragePlugin storage = (StoragePlugin)market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
		if (storage != null) {
			storage.setPlayerPaidToUnlock(true);
		}

		CommDirectoryAPI directory = market.getCommDirectory();
		//directory.addPerson(XDP_People.getPerson(XDP_People.INVICTA));

		//XDP_People.getPerson(XDP_People.INVICTA).setMarket(market);

		// put in Cerulean as the admin
		market.setAdmin(XDP_People.getPerson(ALPHA_CORE));

		// need a script to automatically fulfil Holdout's demand (akin to pirate bases' "Brought in by raiders")
		Global.getSector().getEconomy().addUpdateListener(new XDP_HoldoutDemandNegator(market));
		// script to spawn fleets from Holdout
		system.addScript(new XDP_HoldoutDemandNegator(market));
		system.addScript(new HoldoutDefenseSpawner(market));
		Misc.makeStoryCritical(market, "xdp_forgeship_protected");

		//
		// END FORGESHIP
		//


	}

	class HoldoutDefenseSpawner implements EveryFrameScript, FleetEventListener {
		private final IntervalUtil spawnTimer = new IntervalUtil(5f, 5f); // Days till it respawns
		private boolean fleetIsAlive = false;
		private boolean instantSpawn;

		private final MarketAPI market;

		public HoldoutDefenseSpawner(MarketAPI market) {
			this.market = market;
			instantSpawn = true;
		}

		@Override
		public boolean isDone() {
			return false;
		}

		@Override
		public boolean runWhilePaused() {
			return false;
		}

		@Override
		public void advance(float amount) {
			if (market == null) return;
			if (!market.getFactionId().equals(Factions.DERELICT)) return;

			if (instantSpawn) {
				spawnFleet();
				instantSpawn = false;
			}

			if (fleetIsAlive) return;

			spawnTimer.advance(Global.getSector().getClock().convertToDays(amount));

			if (spawnTimer.intervalElapsed()) {
				spawnFleet();
			}
		}

		private void spawnFleet() {
            // Customize the fleet here, you don't have to use magic campaign
			 CampaignFleetAPI fleet = MagicCampaign.createFleetBuilder()
					.setFleetName("Holdout Defense Fleet")
					.setFleetFaction(Factions.DERELICT)
					.setFleetType(FleetTypes.PATROL_LARGE)
					.setMinFP(400)
					.setSpawnLocation(market.getPrimaryEntity())
					.setAssignment(FleetAssignment.DEFEND_LOCATION)
					.setAssignmentTarget(market.getPrimaryEntity())
					.create();


             // Do not touch these
             fleet.setNoAutoDespawn(true);
             fleet.addEventListener(this);

			fleetIsAlive = true;
		}

		@Override
		public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
            fleetIsAlive = false;
		}

		@Override
		public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {}
	}
}













