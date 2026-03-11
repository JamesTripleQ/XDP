package neon.xdp.data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.FactionCommissionIntel;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import neon.xdp.data.scripts.xdp_MotherShipLocationIntel;
import neon.xdp.data.scripts.xdp_NexusRestocker;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * Utility class for initializing Paradeigma skill content
 * No longer a custom start class
 */
public class xdp_ToBeMined {

    private static final Logger log = Logger.getLogger(xdp_ToBeMined.class);

    /**
     * Initialize all Paradeigma-related content when player acquires the skill
     * This replaces the old custom start functionality
     */
    public static void initializeParadeigmaContent() {
        log.info("Initializing Paradeigma content for player");

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;

        // Set memory flags
        Global.getSector().getMemoryWithoutUpdate().set("$xdp_MothershipStart", true);

        // Add location intel if not already present
        if (!Global.getSector().getIntelManager().hasIntelOfClass(xdp_MotherShipLocationIntel.class)) {
            Global.getSector().getIntelManager().addIntel(new xdp_MotherShipLocationIntel(), false);
        }

        // Add listener for nexus restocking
        if (!Global.getSector().getListenerManager().hasListenerOfClass(xdp_NexusRestocker.class)) {
            Global.getSector().getListenerManager().addListener(new xdp_NexusRestocker());
        }

        // Give commission with Derelict faction
        FactionCommissionIntel commission = new FactionCommissionIntel(Global.getSector().getFaction(Factions.DERELICT));
        commission.missionAccepted();

        // Make Derelict faction visible in intel tab
        FactionAPI derelict = Global.getSector().getFaction(Factions.DERELICT);
        derelict.setShowInIntelTab(true);

        // Initialize all nexuses with cargo
        initializeNexusCargo();

        // Set up mothership storage if needed
        setupMothershipStorage();

        log.info("Paradeigma content initialization complete");
    }

    private static void initializeNexusCargo() {
        // Get all custom entities of type DERELICT_MOTHERSHIP
        List<SectorEntityToken> nexii = new ArrayList<>();
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (SectorEntityToken entity : system.getCustomEntities()) {
                if (Entities.DERELICT_MOTHERSHIP.equals(entity.getCustomEntityType())) {
                    nexii.add(entity);
                }
            }
        }

        // Filter out "derelict_mothership"
        List<SectorEntityToken> filteredNexii = new ArrayList<>();
        for (SectorEntityToken entity : nexii) {
            if (!"derelict_mothership".equals(entity.getId())) {
                filteredNexii.add(entity);
            }
        }

        // Add cargo to each nexus
        for (SectorEntityToken entity : filteredNexii) {
            if (entity.getCargo() != null) {
                CargoAPI cargo = addNexusCargo(entity);
                if (cargo != null) {
                    entity.getCargo().addAll(cargo);
                }
            }
        }
    }

    private static CargoAPI addNexusCargo(SectorEntityToken nexus) {
        float mult = 1f;

        CargoAPI cargo = Global.getFactory().createCargo(false);
        cargo.addCommodity(Commodities.GAMMA_CORE,
                Math.round(8 + Math.random() * 7) * (int)mult);
        cargo.addCommodity(Commodities.BETA_CORE,
                Math.round(5 + Math.random() * 4) * (int)mult);
        cargo.addCommodity(Commodities.ALPHA_CORE,
                Math.round(4 + Math.random() * 3) * (int)mult);
        cargo.addCommodity(Commodities.FUEL,
                Math.round(9000 + Math.random() * 6000) * (int)mult);
        cargo.addCommodity(Commodities.SUPPLIES,
                Math.round(3000 + Math.random() * 2000) * (int)mult);

        Random random = new Random();

        if (random.nextFloat() > 0.97f) {
            cargo.addCommodity(Commodities.OMEGA_CORE, 1f);
        }

        return cargo;
    }

    private static void setupMothershipStorage() {
        // Find a system with derelict mothership theme
        List<StarSystemAPI> systemsWithTag = new ArrayList<>();
        for (StarSystemAPI starSystem : Global.getSector().getStarSystems()) {
            if (starSystem.hasTag(Tags.THEME_DERELICT_MOTHERSHIP)) {
                systemsWithTag.add(starSystem);
            }
        }

        if (systemsWithTag.isEmpty()) return;

        StarSystemAPI stationsystem = systemsWithTag.get(new Random().nextInt(systemsWithTag.size()));

        // Check if storage entity already exists
        boolean storageExists = false;
        for (SectorEntityToken entity : stationsystem.getCustomEntities()) {
            if ("xdp_MothershipStorage".equals(entity.getId())) {
                storageExists = true;
                break;
            }
        }

        if (!storageExists) {
            SectorEntityToken station = stationsystem.addCustomEntity(
                    "xdp_MothershipStorage",
                    "Mothership Global Storage",
                    "station_side05",
                    Factions.NEUTRAL
            );
            Misc.setAbandonedStationMarket("xdp_MotherShipStorage", station);
            station.setSensorProfile(0f);
            station.setInteractionImage("illustrations", "city_from_above");
            if (station.getMarket() != null) {
                station.getMarket().addIndustry(Industries.SPACEPORT);
            }
            station.setCircularOrbitPointingDown(stationsystem.getCenter(), 0f, 10000f, 9999f);
        }

        // Create derelict station if AoTD mod is present
        if (Global.getSettings().getModManager().isModEnabled("aotd_qol")) {
            boolean stationExists = false;
            for (SectorEntityToken entity : stationsystem.getCustomEntities()) {
                if ("xdp_MotherShipStation".equals(entity.getId())) {
                    stationExists = true;
                    break;
                }
            }

            if (!stationExists) {
                SectorEntityToken station1 = stationsystem.addCustomEntity(
                        "xdp_MotherShipStation",
                        "Derelict Station",
                        "station_side05",
                        Factions.DERELICT
                );
                MarketAPI market = Global.getFactory().createMarket("xdp_MotherShipMarket", "Derelict Market", 1);
                market.setFactionId(Factions.DERELICT);
                market.setPrimaryEntity(station1);
                station1.setMarket(market);
                market.addCondition(Conditions.POPULATION_1);
                market.addSubmarket(Submarkets.SUBMARKET_OPEN);
                market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
                market.addIndustry(Industries.SPACEPORT);
                market.addIndustry(Industries.POPULATION);
                market.addTag(Tags.STATION);
                market.addTag(Tags.MARKET_NO_OFFICER_SPAWN);
                market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
                Global.getSector().getEconomy().addMarket(market, false);

                market.getCommodityData(Commodities.SUPPLIES).addTradeMod(Factions.DERELICT, 1500f, 30f);
                market.getCommodityData(Commodities.FOOD).addTradeMod(Factions.DERELICT, 3000f, 30f);

                station1.setSensorProfile(0f);
                station1.setInteractionImage("illustrations", "city_from_above");
                station1.setCircularOrbitPointingDown(stationsystem.getCenter(), 0f, 10000f, 9999f);
            }
        }

        // Set start location memory
        Global.getSector().getMemoryWithoutUpdate().set("$nex_startLocation", stationsystem.getCenter().getId());
    }
}