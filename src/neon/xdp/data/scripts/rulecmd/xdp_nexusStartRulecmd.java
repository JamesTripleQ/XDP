package neon.xdp.data.scripts.rulecmd;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.BattleAPI.BattleSide;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.EntityLocation;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import neon.xdp.data.scripts.xdp_MotherShipRaidIntel;
import neon.xdp.data.scripts.xdp_NexusCustomProduction;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Keyboard;
import org.magiclib.util.MagicCampaign;

import java.awt.Color;
import java.util.*;

/**
 * Rule command for handling Explorarium Mothership interactions
 */
public class xdp_nexusStartRulecmd extends BaseCommandPlugin {

    public static final float SUPPLIES_PER_NEXUS = 800f;
    public static final float METALS_PER_NEXUS = 1500f;
    public static final float RARE_METALS_PER_NEXUS = 200f;
    public static final float WEAPONS_THRESHOLD = 0.40f;
    public static final float FIGHTERS_THRESHOLD = 0.50f;
    public static final float AICORES_THRESHOLD = 0.75f;

    public static MarketAPI targetmarket = null;
    public static ArrayList<Integer> rewardlist = new ArrayList<>();
    public static PersonAPI coreguy = null;
    public static FleetMemberAPI ship = null;
    public static Float height = null;
    public static Float width = null;
    public static String factionspec = "";

    private static final Color EXPLORARIUM_COLOR = new Color(100, 250, 210, 250);

    // Flag to track if this is the first interaction
    private boolean isFirstInteraction = true;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        // Ensure dialog is valid
        if (dialog == null) return false;

        // Store the dialog for later use
        dialog.setPromptText("Explorarium Mothership Interface");

        // If no params or first interaction, show the main menu
        if (params == null || params.isEmpty() || isFirstInteraction) {
            isFirstInteraction = false;
            initializeDialog(dialog);
            return true;
        }

        int type = params.get(0).getInt(memoryMap);

        switch (type) {
            case 0:
                handleInitialGreeting(dialog);
                break;
            case 1:
                getCargoPicker(dialog);
                break;
            case 2:
                handleProductionPicker(dialog);
                break;
            case 3:
                handleStorageAccess(dialog);
                break;
            case 4:
                handleFleetRepairs(dialog);
                break;
            case 5:
                handleMainMenu(dialog);
                break;
            case 6:
                handleStorageInfo(dialog);
                break;
            case 7:
                handleStorageOptions(dialog);
                break;
            case 8:
                handleDeconstructPicker(dialog);
                break;
            case 9:
                handleDeconstructShip(dialog);
                break;
            case 10:
                handleComms(dialog);
                break;
            case 11:
                handleRedirectPerson(dialog);
                break;
            case 12:
                showRecountInfo(dialog);
                break;
            case 13:
                handleDeconstructMain(dialog);
                break;
            case 14:
                handleConstructMenu(dialog);
                break;
            case 15:
                showNexusBuildPicker(dialog);
                break;
            case 16:
                getShowRaidTarget(dialog, targetmarket, rewardlist);
                break;
            case 17:
                doSetup(dialog);
                break;
            case 18:
                getRaidReward(dialog);
                break;
            default:
                return false;
        }
        return true;
    }

    /**
     * Initialize the dialog for first interaction
     */
    private void initializeDialog(InteractionDialogAPI dialog) {
        if (dialog == null) return;

        try {
            // Store initial dimensions
            height = dialog.getTextHeight();
            width = dialog.getTextWidth();

            // Clear any existing options and text
            dialog.getOptionPanel().clearOptions();
            dialog.getTextPanel().clear();

            // Set up the visual - use showImageVisual with faction crest
            if (dialog.getInteractionTarget() != null) {
                VisualPanelAPI visual = dialog.getVisualPanel();
                if (dialog.getInteractionTarget().getCustomInteractionDialogImageVisual() != null) {
                    visual.showImageVisual(dialog.getInteractionTarget().getCustomInteractionDialogImageVisual());
                } else {
                    // Show faction crest as an image
                    String crest = Global.getSector().getFaction(Factions.DERELICT).getCrest();
                    visual.showImageVisual(new InteractionDialogImageVisual(crest, 400f, 400f));
                }
            }

            // Store the active person
            if (dialog.getInteractionTarget() != null && dialog.getInteractionTarget().getActivePerson() != null) {
                coreguy = dialog.getInteractionTarget().getActivePerson();
            }

            // Show the main menu
            handleMainMenu(dialog);

        } catch (Exception e) {
            // Log any errors during initialization
            Global.getLogger(this.getClass()).warn("Error initializing mothership dialog", e);
        }
    }

    private void handleInitialGreeting(InteractionDialogAPI dialog) {
        if (dialog == null) return;

        dialog.getTextPanel().clear();
        dialog.getTextPanel().addPara("The Explorarium Mothership welcomes you into its graces, allowing you to make use of its services.");
        dialog.getTextPanel().addPara("Open a comm link with the Mothership to begin.");
        dialog.getTextPanel().setFontSmallInsignia();
        dialog.getTextPanel().addPara("You may repair your ships at no cost at any Mothership, and make use of their services to maintain your fleet.");
        dialog.getTextPanel().addPara("Each Mothership has its own cargo for offer, and are prepared to produce Explorarium hulls and weapons instantaneously - provided you have the credits to authorize the production, that is.");
        dialog.getTextPanel().setFontInsignia();

        // Add continue option
        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Continue", "xdp_nexusMainMenu");
    }

    private void handleProductionPicker(InteractionDialogAPI dialog) {
        if (dialog == null) return;
        // This will be handled by xdp_NexusCustomProduction
        dialog.getTextPanel().addPara("Opening production interface...");
        // In a real implementation, you would show the production dialog here
    }

    private void handleStorageAccess(InteractionDialogAPI dialog) {
        if (dialog == null || dialog.getInteractionTarget() == null) return;

        // Ensure storage entity exists
        ensureStorageEntityExists();

        Global.getSector().addTransientScript(
                new NexusStorageScript((SectorEntityToken) dialog.getInteractionTarget(), dialog)
        );
    }

    private void handleFleetRepairs(InteractionDialogAPI dialog) {
        if (dialog == null) return;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) return;

        int repairedCount = 0;
        for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
            if (member.getRepairTracker().getCR() < member.getRepairTracker().getMaxCR() ||
                    member.getStatus().getHullFraction() < 1f) {
                member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
                member.getStatus().setHullFraction(1f);
                repairedCount++;
            }
        }

        dialog.getTextPanel().addPara("Fleet repairs complete. " + repairedCount + " ships restored to full combat readiness.");

        // Return to main menu after repair
        handleMainMenu(dialog);
    }

    private void handleMainMenu(InteractionDialogAPI dialog) {
        if (dialog == null) return;

        dialog.getOptionPanel().clearOptions();
        dialog.setXOffset(0f);

        if (height != null) {
            dialog.setTextHeight(height);
            dialog.setTextWidth(width);
        } else {
            height = dialog.getTextHeight();
            width = dialog.getTextWidth();
        }

        dialog.getTextPanel().updateSize();
        dialog.getTextPanel().clear();
        dialog.getTextPanel().setFontVictor();
        dialog.getTextPanel().addPara("affirm // detected : Explorarium property transponder from [target_fleet] // waiting waiting waiting...");

        if (Global.getSector().getPlayerFleet().getFleetPoints() > 300) {
            dialog.getTextPanel().addPara("receipt of FLEET no. 417 confirmed. \"Welcome back, esteemed user!\"");
        } else {
            dialog.getTextPanel().addPara("receipt of SCOUT no. 417 confirmed. \"Welcome back, esteemed user!\"");
        }

        // Auto-repair
        for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
            member.getStatus().setHullFraction(1f);
        }

        dialog.getTextPanel().addPara("CASE permit [offload implements] to [target_fleet] // verification required");
        dialog.getTextPanel().addPara("CASE produce [replicate] implements // verification required");
        dialog.getTextPanel().addPara("CASE refit [test/analyze/rearm] offered for [target_fleet] //");
        dialog.getTextPanel().addPara("CASE leave [cutCommLink] // \"We wish you a very nice day.\"");
        dialog.getTextPanel().addPara("CASE repair [restore] expeditiously available");
        dialog.getTextPanel().setFontInsignia();

        // Note: The options are handled by rules.csv, not by adding them here
        // The rules.csv file defines the options for xdp_nexusOpen

        // Set up visual panel
        if (dialog.getInteractionTarget() != null) {
            VisualPanelAPI visual = dialog.getVisualPanel();
            if (dialog.getInteractionTarget().getCustomInteractionDialogImageVisual() != null) {
                visual.showImageVisual(dialog.getInteractionTarget().getCustomInteractionDialogImageVisual());
            } else {
                String crest = Global.getSector().getFaction(Factions.DERELICT).getCrest();
                visual.showImageVisual(new InteractionDialogImageVisual(crest, 400f, 400f));
            }
        }
    }

    private void handleComms(InteractionDialogAPI dialog) {
        if (dialog == null) return;
        // Handle comms - empty in original
        dialog.getTextPanel().addPara("Comms link established.");
    }

    private void handleStorageInfo(InteractionDialogAPI dialog) {
        if (dialog == null) return;

        dialog.getTextPanel().clear();
        dialog.getTextPanel().addPara("You're currently accessing the Explorarium Mothership's global data storage network.");
        dialog.getTextPanel().addPara("Any items stored in here will be available to retrieve from any other Explorarium Mothership in the sector.");
        dialog.getTextPanel().addPara("Exit the storage network to return to using the Explorarium Mothership.");

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Continue", "xdp_nexusStorageOptions");
    }

    private void handleStorageOptions(InteractionDialogAPI dialog) {
        if (dialog == null) return;

        // This is handled by rules.csv in xdp_nexusStorage3
        // Just show the storage info
        dialog.getTextPanel().addPara("Accessing storage network...");
    }

    private void handleDeconstructPicker(InteractionDialogAPI dialog) {
        if (dialog == null) return;

        dialog.setXOffset(0f);
        if (height != null) {
            dialog.setTextHeight(height);
            dialog.setTextWidth(width);
        } else {
            height = dialog.getTextHeight();
            width = dialog.getTextWidth();
        }
        dialog.getTextPanel().updateSize();

        showPickerDialog(dialog);
    }

    private void handleDeconstructShip(InteractionDialogAPI dialog) {
        if (dialog == null || ship == null) return;

        boolean hascores = false;
        FactionAPI remmy = Global.getSector().getFaction(Factions.DERELICT);

        dialog.getTextPanel().setFontSmallInsignia();
        dialog.getOptionPanel().clearOptions();
        dialog.getTextPanel().addPara("The " + ship.getShipName() + " has been deconstructed. Moments later, a sonorous chime emits from the Mothership.");

        // Add ship to known ships
        remmy.addKnownShip(ship.getHullSpec().getHullId(), false);
        remmy.getAlwaysKnownShips().add(ship.getHullSpec().getHullId());
        remmy.addUseWhenImportingShip(ship.getHullSpec().getHullId());

        if (!ship.getHullSpec().getBaseHullId().equals("rat_genesis")) {
            List<String> varlist = Global.getSettings().getAllVariantIds();
            List<String> shipvarlist = new ArrayList<>();

            for (String vid : varlist) {
                if (Global.getSettings().getVariant(vid).getHullSpec().getBaseHullId().equals(ship.getHullSpec().getBaseHullId())) {
                    shipvarlist.add(vid);
                }
            }

            String role = "combatSmall";
            switch (ship.getHullSpec().getHullSize()) {
                case CAPITAL_SHIP:
                    role = "combatCapital";
                    break;
                case CRUISER:
                    role = "combatLarge";
                    break;
                case DESTROYER:
                    role = "combatMedium";
                    break;
                default:
                    role = "combatSmall";
                    break;
            }

            for (String vid : shipvarlist) {
                Global.getSettings().addDefaultEntryForRole(role, vid, 0f);
                Global.getSettings().addEntryForRole(Factions.DERELICT, role, vid, 1f);
            }
        }

        Global.getSector().getPlayerFleet().removeFleetMemberWithDestructionFlash(ship);
        Global.getSoundPlayer().playUISound("ui_industry_install_any_item", 1f, 1f);

        List<String> banlist = new ArrayList<>();
        banlist.add("sotf_dustkeepers_burnouts");
        banlist.add("rat_abyssals");
        banlist.add("ai_all");

        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            if (!banlist.contains(faction.getId()) &&
                    faction.knowsShip(ship.getHullSpec().getHullId()) &&
                    !faction.getId().equals(Factions.DERELICT)) {
                factionspec = faction.getId();
                if (factionspec.equals("rat_abyssals_deep") ||
                        factionspec.equals("tahlan_legiodaemons") ||
                        factionspec.equals("vestige")) {
                    hascores = true;
                }
            }
        }

        if (ship.getHullSpec().getBaseHullId().startsWith("istl") ||
                ship.getHullSpec().getBaseHullId().startsWith("bbplus")) {
            factionspec = "blade_breakers";
        }

        dialog.getTextPanel().addPara("Explorarium fleets may now use the " +
                ship.getHullSpec().getHullNameWithDashClass() + " " +
                ship.getHullSpec().getHullSize().name() + ".");

        doFactionCheck(factionspec, hascores, dialog);

        dialog.getTextPanel().setFontInsignia();
        dialog.getOptionPanel().addOption("Continue", "xdp_nexusDeconstructMain");
    }

    private void handleRedirectPerson(InteractionDialogAPI dialog) {
        if (dialog == null || dialog.getInteractionTarget() == null || coreguy == null) return;
        dialog.getInteractionTarget().setActivePerson(coreguy);
    }

    private void handleDeconstructMain(InteractionDialogAPI dialog) {
        if (dialog == null) return;

        dialog.getTextPanel().clear();
        dialog.getTextPanel().addPara("The Explorarium are ever-hungry to expand their knowledge, especially towards those whose hulls resemble their own.");
        dialog.setXOffset(0f);

        if (height != null) {
            dialog.setTextHeight(height);
            dialog.setTextWidth(width);
        } else {
            height = dialog.getTextHeight();
            width = dialog.getTextWidth();
        }
        dialog.getTextPanel().updateSize();

        // Show the ship picker
        handleDeconstructPicker(dialog);
    }

    private void handleConstructMenu(InteractionDialogAPI dialog) {
        if (dialog == null) return;

        dialog.getTextPanel().clear();

        float expire = Global.getSector().getMemoryWithoutUpdate().getExpire("$xdp_nexusBuildTimeout");
        if (expire > 0f) {
            dialog.getTextPanel().addPara("It will take some time to prepare to construct another Mothership.");
            dialog.getTextPanel().addPara("You may construct another in " + Math.round(expire) + " days.");
        }

        dialog.getTextPanel().addCostPanel("Construction Costs",
                Commodities.SUPPLIES, (int) SUPPLIES_PER_NEXUS, true,
                Commodities.METALS, (int) METALS_PER_NEXUS, true,
                Commodities.RARE_METALS, (int) RARE_METALS_PER_NEXUS, true);

        CargoAPI pcargo = Global.getSector().getPlayerFleet().getCargo();
        boolean supplies = pcargo.getCommodityQuantity(Commodities.SUPPLIES) >= SUPPLIES_PER_NEXUS;
        boolean metals = pcargo.getCommodityQuantity(Commodities.METALS) >= METALS_PER_NEXUS;
        boolean raremetals = pcargo.getCommodityQuantity(Commodities.RARE_METALS) >= RARE_METALS_PER_NEXUS;

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Begin construction", "xdp_nexusConstruct");
        dialog.getOptionPanel().addOption("Return to main menu", "xdp_nexusBack");

        if (Global.getSector().getMemoryWithoutUpdate().getBoolean("$xdp_nexusBuildTimeout") ||
                !supplies || !metals || !raremetals) {
            dialog.getOptionPanel().setEnabled("xdp_nexusConstruct", false);
            String reason = "Cannot build: ";
            if (Global.getSector().getMemoryWithoutUpdate().getBoolean("$xdp_nexusBuildTimeout")) {
                reason += "Build on cooldown. ";
            }
            if (!supplies) reason += "Insufficient supplies. ";
            if (!metals) reason += "Insufficient metals. ";
            if (!raremetals) reason += "Insufficient rare metals.";
            dialog.getOptionPanel().setTooltip("xdp_nexusConstruct", reason);
        }
    }

    // Cargo picker method
    private void getCargoPicker(InteractionDialogAPI dialog) {
        if (dialog == null || dialog.getInteractionTarget() == null) return;

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) return;

        CargoAPI playerCargo = playerFleet.getCargo();
        CargoAPI creds = playerCargo; // Credits are part of cargo

        CargoAPI cargo = Global.getFactory().createCargo(false);
        CargoAPI nexuscargo = dialog.getInteractionTarget().getCargo();

        if (nexuscargo != null) {
            cargo.addAll(nexuscargo);
            cargo.sort();
        }

        final CargoAPI finalCreds = creds;
        final CargoAPI finalNexusCargo = nexuscargo;

        dialog.showCargoPickerDialog("Mothership Supply", "Requisition", "Cancel", true, 310f, cargo, new CargoPickerListener() {
            @Override
            public void pickedCargo(CargoAPI pickedCargo) {
                if (pickedCargo == null) return;
                pickedCargo.sort();
                float cost = getCost(pickedCargo);
                if (cost > 0 && cost <= finalCreds.getCredits().get()) {
                    finalCreds.getCredits().subtract(cost);
                    dialog.getTextPanel().setFontSmallInsignia();
                    for (CargoStackAPI stack : pickedCargo.getStacksCopy()) {
                        playerFleet.getCargo().addItems(stack.getType(), stack.getData(), stack.getSize());
                        AddRemoveCommodity.addStackGainText(stack, dialog.getTextPanel(), false);
                        if (finalNexusCargo != null) {
                            finalNexusCargo.removeItems(stack.getType(), stack.getData(), stack.getSize());
                        }
                    }
                    AddRemoveCommodity.addCreditsLossText(Math.round(cost), dialog.getTextPanel());
                    dialog.getTextPanel().setFontInsignia();
                } else if (cost > finalCreds.getCredits().get()) {
                    dialog.getTextPanel().addPara("Insufficient credits for this transaction.");
                }
                // Return to main menu
                handleMainMenu(dialog);
            }

            @Override
            public void cancelledCargoSelection() {
                handleMainMenu(dialog);
            }

            @Override
            public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp,
                                          boolean pickedUpFromSource, CargoAPI combined) {
                float cost = getCost(combined);
                float pad = 3f;
                float bigpad = 10f;
                Color highlight = Misc.getHighlightColor();
                Color highlightspooky = Misc.getNegativeHighlightColor();
                String hegsprite = Global.getSettings().getFactionSpec(Factions.DERELICT).getCrest();

                panel.addImage(hegsprite, 310f, pad);
                LabelAPI para1 = panel.addPara("You currently have " + Math.round(finalCreds.getCredits().get()) + " credits.", bigpad);
                para1.setHighlight(String.valueOf(Math.round(finalCreds.getCredits().get())));
                para1.setHighlightColor(highlight);

                LabelAPI para2 = panel.addPara("The cost of your selection is " + Math.round(cost) + " credits.", pad);
                para2.setHighlight(String.valueOf(Math.round(cost)));
                para2.setHighlightColor(highlightspooky);

                if (cost > finalCreds.getCredits().get()) {
                    LabelAPI para3 = panel.addPara("You don't have enough credits to authorize this transaction.", pad);
                    para3.setColor(highlightspooky);
                }
            }
        });
    }

    private float getCost(CargoAPI cargo) {
        float cost = 0f;
        for (CargoStackAPI item : cargo.getStacksCopy()) {
            if (item.isCommodityStack()) {
                String commodityId = item.getCommodityId();
                if (Commodities.OMEGA_CORE.equals(commodityId) ||
                        Commodities.ALPHA_CORE.equals(commodityId) ||
                        Commodities.BETA_CORE.equals(commodityId) ||
                        Commodities.GAMMA_CORE.equals(commodityId)) {
                    cost += item.getBaseValuePerUnit() * 3f * item.getSize();
                } else if (Commodities.FUEL.equals(commodityId) || Commodities.SUPPLIES.equals(commodityId)) {
                    cost += item.getBaseValuePerUnit() * 0.5f * item.getSize();
                } else {
                    if ("ai_cores".equals(Global.getSettings().getCommoditySpec(commodityId).getDemandClass())) {
                        cost += item.getBaseValuePerUnit() * 3f * item.getSize();
                    }
                }
            } else if (item.isWeaponStack()) {
                WeaponSpecAPI weaponSpec = item.getWeaponSpecIfWeapon();
                if (weaponSpec != null) {
                    WeaponSize size = weaponSpec.getSize();
                    if (size == WeaponSize.LARGE) {
                        cost += item.getBaseValuePerUnit() * 1.5f * item.getSize();
                    } else if (size == WeaponSize.MEDIUM) {
                        cost += item.getBaseValuePerUnit() * 1.3f * item.getSize();
                    } else {
                        cost += item.getBaseValuePerUnit() * 1f * item.getSize();
                    }
                }
            }
        }
        return cost;
    }

    private void showPickerDialog(InteractionDialogAPI dialog) {
        List<FleetMemberAPI> validShips = getValidShips();
        if (validShips.isEmpty()) {
            dialog.getTextPanel().addPara("None of the automated ships in your fleet are unknown to the Explorarium.");
            dialog.getTextPanel().addPara("Consider returning once you have one.");
            dialog.getOptionPanel().clearOptions();
            dialog.getOptionPanel().addOption("Return", "xdp_nexusBack");
            return;
        }

        dialog.showFleetMemberPickerDialog("Re-origination Protocol",
                "Proceed", "Cancel", 4, 4, 160f, true, false, validShips,
                new FleetMemberPickerListener() {
                    @Override
                    public void pickedFleetMembers(List<FleetMemberAPI> members) {
                        if (members == null || members.isEmpty()) return;

                        for (FleetMemberAPI member : members) {
                            dialog.getOptionPanel().clearOptions();
                            ship = member;

                            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                                if (faction.knowsShip(ship.getHullSpec().getBaseHullId())) {
                                    factionspec = faction.getId();
                                }
                            }

                            dialog.getTextPanel().addPara(member.getShipName() + ", a " +
                                            member.getHullSpec().getHullNameWithDashClass() + " is selected.")
                                    .setHighlight(member.getShipName());

                            showFactionInfo(factionspec, dialog);
                            dialog.getTextPanel().addPara("Proceeding will destroy the ship and add it to the Explorarium's known ships.")
                                    .setHighlight("destroy", "add");
                            dialog.getTextPanel().addPara("This action cannot be undone.").setColor(Color.RED);
                            dialog.getTextPanel().addPara("Would you like to proceed?");
                            dialog.getVisualPanel().showFleetMemberInfo(member);
                            dialog.getOptionPanel().addOption("Proceed with deconstruction", "xdp_nexusDeconstructProceed");
                            dialog.getOptionPanel().addOption("Go back", "xdp_nexusDeconstructMain");
                        }
                    }

                    @Override
                    public void cancelledFleetMemberPicking() {
                        handleDeconstructMain(dialog);
                    }
                });
    }

    private List<FleetMemberAPI> getValidShips() {
        FactionAPI remmies = Global.getSector().getFaction(Factions.DERELICT);
        List<FleetMemberAPI> validShips = new ArrayList<>();

        for (FleetMemberAPI playerFM : Global.getSector().getPlayerFleet().getMembersWithFightersCopy()) {
            if (playerFM.getVariant().hasHullMod("automated") &&
                    playerFM.getHullSpec().hasTag(Tags.AUTOMATED_RECOVERABLE) &&
                    !remmies.knowsShip(playerFM.getHullSpec().getBaseHullId()) &&
                    !playerFM.isFighterWing()) {
                validShips.add(playerFM);
            }
        }
        return validShips;
    }

    private void doFactionCheck(String spec, boolean factionHasCores, InteractionDialogAPI dialog) {
        if (spec == null || spec.isEmpty() || ship == null) return;

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        FactionAPI remmy = Global.getSector().getFaction(Factions.DERELICT);

        if (!Global.getSettings().getFactionSpec(spec).getKnownShips().contains(ship.getHullSpec().getBaseHullId())) {
            return;
        }

        List<String> facships = new ArrayList<>();
        for (String shipId : Global.getSettings().getFactionSpec(spec).getKnownShips()) {
            if (Global.getSettings().getHullSpec(shipId).hasTag(Tags.AUTOMATED_RECOVERABLE)) {
                facships.add(shipId);
            }
        }

        List<String> remmyships = new ArrayList<>();
        for (String shipId : remmy.getKnownShips()) {
            if (Global.getSettings().getFactionSpec(spec).getKnownShips().contains(shipId)) {
                remmyships.add(shipId);
            }
        }

        List<String> facweaps = new ArrayList<>();
        for (String weapId : Global.getSettings().getFactionSpec(spec).getKnownWeapons()) {
            WeaponSpecAPI specWeap = Global.getSettings().getWeaponSpec(weapId);
            if (!specWeap.getAIHints().contains(WeaponAPI.AIHints.SYSTEM) &&
                    specWeap.getType() != WeaponType.DECORATIVE) {
                facweaps.add(weapId);
            }
        }

        List<String> facwings = new ArrayList<>();
        for (String wingId : Global.getSettings().getFactionSpec(spec).getKnownFighters()) {
            if (Global.getSettings().getFighterWingSpec(wingId).hasTag(Tags.AUTOMATED_FIGHTER)) {
                facwings.add(wingId);
            }
        }

        showFactionInfo(spec, dialog);

        float ratio = facships.isEmpty() ? 0f : (float) remmyships.size() / (float) facships.size();

        if (ratio >= 0.5f && !mem.contains("$xdp_" + spec + "weapons")) {
            for (String weapId : facweaps) {
                remmy.addKnownWeapon(weapId, false);
            }
            Global.getSoundPlayer().playUISound("ui_char_spent_story_point", 1f, 1f);
            dialog.getTextPanel().addPara("The Explorarium now know 50% or more of this faction's automated hulls.");
            dialog.getTextPanel().addPara("Iterative analysis of their systems now allows the Explorarium to use all of this faction's known weapons.");
            dialog.getTextPanel().addPara("These will also be available through production orders.");
            dialog.getTextPanel().setFontSmallInsignia();
            dialog.getTextPanel().addPara("Fighter blueprints will be unlocked at 75%.");
            mem.set("$xdp_" + spec + "weapons", true, 0);
        }

        if (ratio >= 0.75f && !mem.contains("$xdp_" + spec + "wings")) {
            for (String wingId : facwings) {
                remmy.addKnownFighter(wingId, false);
            }
            Global.getSoundPlayer().playUISound("ui_char_spent_story_point", 1f, 1f);
            dialog.getTextPanel().addPara("The Explorarium now know 75% or more of this faction's automated hulls.");
            dialog.getTextPanel().addPara("Comprehensive analysis of their systems now allows the Explorarium to use all of this faction's known fighters.");
            dialog.getTextPanel().addPara("These will also be available through production orders.");
            dialog.getTextPanel().setFontSmallInsignia();
            if (factionHasCores) {
                dialog.getTextPanel().addPara("Explorarium Motherships will offer this faction's AI cores at 90%.");
            }
            mem.set("$xdp_" + spec + "wings", true, 0);
        }

        if (ratio >= 0.90f && !mem.contains("$xdp_" + spec + "cores") && factionHasCores) {
            Global.getSoundPlayer().playUISound("ui_char_spent_story_point", 1f, 1f);
            dialog.getTextPanel().addPara("The Explorarium now know 90% or more of this faction's automated hulls.");
            dialog.getTextPanel().addPara("Thorough reconstruction of their behaviors now allows any Mothership to offer some of the faction's AI cores.");
            dialog.getTextPanel().addPara("These are available through the supply cargo picker.");
            dialog.getTextPanel().setFontSmallInsignia();
            mem.set("$xdp_" + spec + "cores", true, 0);

            List<SectorEntityToken> nexii = Global.getSector().getCustomEntitiesWithTag("derelict_mothership");
            switch (spec) {
                case "rat_abyssals_deep":
                    for (SectorEntityToken nexus : nexii) {
                        nexus.getCargo().addCommodity("rat_chronos_core", MathUtils.getRandomNumberInRange(5f, 7f));
                        nexus.getCargo().addCommodity("rat_cosmos_core", MathUtils.getRandomNumberInRange(5f, 7f));
                        nexus.getCargo().addCommodity("rat_seraph_core", MathUtils.getRandomNumberInRange(3f, 5f));
                    }
                    break;
                case "tahlan_legiodaemons":
                    for (SectorEntityToken nexus : nexii) {
                        nexus.getCargo().addCommodity("tahlan_daemoncore", MathUtils.getRandomNumberInRange(6f, 9f));
                        nexus.getCargo().addCommodity("tahlan_archdaemoncore", MathUtils.getRandomNumberInRange(3f, 5f));
                    }
                    break;
                case "vestige":
                    for (SectorEntityToken nexus : nexii) {
                        nexus.getCargo().addCommodity("vestige_core", MathUtils.getRandomNumberInRange(5f, 8f));
                        nexus.getCargo().addCommodity("volantian_core", MathUtils.getRandomNumberInRange(5f, 8f));
                    }
                    break;
            }
        }
    }

    private void showFactionInfo(String spec, InteractionDialogAPI dialog) {
        if (spec == null || spec.isEmpty()) return;

        FactionAPI remmy = Global.getSector().getFaction(Factions.DERELICT);
        List<String> facships = new ArrayList<>();
        for (String shipId : Global.getSettings().getFactionSpec(spec).getKnownShips()) {
            if (Global.getSettings().getHullSpec(shipId).hasTag(Tags.AUTOMATED_RECOVERABLE)) {
                facships.add(shipId);
            }
        }

        List<String> remmyships = new ArrayList<>();
        for (String shipId : remmy.getKnownShips()) {
            if (Global.getSettings().getFactionSpec(spec).getKnownShips().contains(shipId)) {
                remmyships.add(shipId);
            }
        }

        String facname = Global.getSettings().getFactionSpec(spec).getDisplayName();
        Color namecolor = Global.getSettings().getFactionSpec(spec).getBrightUIColor();

        LabelAPI para = dialog.getTextPanel().addPara("This ship's origin, the " + facname +
                ", knows " + facships.size() + " automated ships.");
        para.setHighlight(facname, String.valueOf(facships.size()));
        para.setHighlightColors(namecolor, Misc.getHighlightColor());

        dialog.getTextPanel().addPara("The Explorarium know " + remmyships.size() + " of them.");
    }

    private void showRecountInfo(InteractionDialogAPI dialog) {
        if (dialog == null) return;

        dialog.getTextPanel().clear();
        dialog.setXOffset(Global.getSettings().getScreenWidth() / 6f);
        dialog.setTextWidth(Global.getSettings().getScreenWidth() / 3f);
        dialog.setTextHeight(Global.getSettings().getScreenHeight() / 1.4f);
        dialog.getTextPanel().updateSize();

        dialog.getTextPanel().addPara("The following is a list of all factions that use automated ships which can be recovered.");
        dialog.getTextPanel().addPara("Note that some automated ships may be absent from this list if they don't have an associated faction.");

        FactionAPI remmy = Global.getSector().getFaction(Factions.DERELICT);
        List<String> banlist = new ArrayList<>();
        banlist.add("sotf_dustkeepers_burnouts");
        banlist.add("rat_abyssals");
        banlist.add("ai_all");

        List<String> factionlist = new ArrayList<>();

        for (FactionAPI fac : Global.getSector().getAllFactions()) {
            for (String shipId : Global.getSettings().getFactionSpec(fac.getId()).getKnownShips()) {
                if (!factionlist.contains(fac.getId()) && !banlist.contains(fac.getId()) &&
                        Global.getSettings().getHullSpec(shipId).hasTag(Tags.AUTOMATED_RECOVERABLE) &&
                        !"Explorarium".equals(Global.getSettings().getHullSpec(shipId).getManufacturer())) {
                    factionlist.add(fac.getId());
                }
            }
        }

        for (String facid : factionlist) {
            List<String> facships = new ArrayList<>();
            for (String shipId : Global.getSettings().getFactionSpec(facid).getKnownShips()) {
                if (Global.getSettings().getHullSpec(shipId).hasTag(Tags.AUTOMATED_RECOVERABLE)) {
                    facships.add(shipId);
                }
            }

            List<String> remmyships = new ArrayList<>();
            for (String shipId : remmy.getKnownShips()) {
                if (facships.contains(shipId)) {
                    remmyships.add(shipId);
                }
            }

            int cent = facships.isEmpty() ? 0 : Math.round(((float) remmyships.size() / (float) facships.size()) * 100f);
            String facname = Global.getSettings().getFactionSpec(facid).getDisplayName();
            Color namecolor = Global.getSettings().getFactionSpec(facid).getBrightUIColor();
            String facicon = Global.getSettings().getFactionSpec(facid).getCrest();

            TooltipMakerAPI tip = dialog.getTextPanel().beginTooltip();
            tip.addSectionHeading(facname,
                    Global.getSettings().getFactionSpec(facid).getBrightUIColor(),
                    Global.getSettings().getFactionSpec(facid).getDarkUIColor(),
                    Alignment.MID, 10f);

            tip.addImage(facicon, 64f, 5f);
            tip.addPara("Collection Progress: " + cent + "%", 5f);
            tip.addPara("This faction knows " + facships.size() + " compatible automated ships.", 5f);
            tip.addPara("The Explorarium currently know " + remmyships.size() + " of them.", 5f);
            dialog.getTextPanel().addTooltip();
        }

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Return", "xdp_nexusBack");
    }

    private void getShowRaidTarget(InteractionDialogAPI dialog, MarketAPI target, ArrayList<Integer> rewards) {
        if (dialog == null) return;

        String basename = "";
        String spec = targetmarket != null ? targetmarket.getFactionId() : null;

        if (target == null) {
            WeightedRandomPicker<String> factionlist = new WeightedRandomPicker<>();
            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                String factionId = faction.getId();
                if (!factionId.equals(Factions.DERELICT) && !factionId.equals("nex_derelict") &&
                        !factionId.equals(Factions.REMNANTS) && !factionId.equals(Factions.OMEGA) &&
                        !factionId.equals(Factions.TRITACHYON) && !factionId.equals(Factions.PLAYER)) {
                    List<MarketAPI> markets = Misc.getFactionMarkets(factionId);
                    boolean hasMilitaryMarket = false;
                    for (MarketAPI m : markets) {
                        if (!m.isHidden() && m.hasSpaceport() &&
                                (m.hasIndustry(Industries.MILITARYBASE) || m.hasIndustry(Industries.HIGHCOMMAND))) {
                            hasMilitaryMarket = true;
                            break;
                        }
                    }
                    if (hasMilitaryMarket) {
                        factionlist.add(factionId);
                    }
                }
            }
            spec = factionlist.pick();

            WeightedRandomPicker<MarketAPI> list = new WeightedRandomPicker<>();
            for (MarketAPI m : Misc.getFactionMarkets(spec)) {
                if (!m.isHidden() && m.hasSpaceport() &&
                        (m.hasIndustry(Industries.MILITARYBASE) || m.hasIndustry(Industries.HIGHCOMMAND))) {
                    list.add(m);
                }
            }
            targetmarket = list.pick();
        }

        if (targetmarket.hasIndustry(Industries.MILITARYBASE)) {
            basename = "military base";
        } else {
            basename = "high command";
        }

        String facname = Global.getSettings().getFactionSpec(spec).getDisplayName();
        Color faccolor = Global.getSettings().getFactionSpec(spec).getBaseUIColor();

        if (rewards.isEmpty()) {
            float defense = targetmarket.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).computeEffective(0f);
            rewardlist.add(0, Math.round(defense / 100f));
            rewardlist.add(1, Math.round(defense / 500f));
            rewardlist.add(2, Math.round(defense / 1000f));
            rewardlist.add(3, Math.round(defense * 1000f));
        }

        int numgamma = rewardlist.get(0);
        int numbeta = rewardlist.get(1);
        int numalpha = rewardlist.get(2);
        int numcredits = rewardlist.get(3);

        dialog.getVisualPanel().showMapMarker(targetmarket.getPrimaryEntity(), targetmarket.getName(),
                faccolor, false, null, "Competitor Military Operations", null);

        dialog.getTextPanel().setFontVictor();
        dialog.getTextPanel().addPara("RECONSTRUCTING MESSAGE - // waiting ... //");
        dialog.getTextPanel().setFontInsignia();
        dialog.getTextPanel().addPara("Voluminous greetings, caretaker.");

        dialog.getTextPanel().addPara("Regrettable activity within a " + facname +
                " " + basename + " leads us to purveying this request to be received within your flock.");

        dialog.getTextPanel().addPara("Please initiate protocol \"heartfelt fireworks celebration\" and visit upon " +
                targetmarket.getName() + " to shower joy breadth skies.");
        dialog.getTextPanel().addPara("Butlers will be invited to assist in decoration and management of guests. Forth the apex of annihilation, promptly disperse and recollect within the Mothership.");
        dialog.getTextPanel().addPara("To be dispensed includes " + numgamma + " chocolate mousse, " +
                numbeta + " lemon zest, " + numalpha + " blueberry gummies and " + numcredits + " credits will be given ");

        dialog.getTextPanel().setFontSmallInsignia();
        dialog.getTextPanel().addPara("Bombard the colony of " + targetmarket.getName() +
                " to disrupt their military operations and return to a Mothership to receive your reward. Explorarium fleets will be dispatched to assist you. Expect resistance.");
        dialog.getTextPanel().addPara("Rewards are " + numgamma + " gamma core, " + numbeta +
                " beta core, " + numalpha + " alpha core and " + numcredits + " credits.");
        dialog.getTextPanel().setFontInsignia();

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Accept the request", "xdp_nexusPartyTimeInit");
        dialog.getOptionPanel().addOption("Decline", "xdp_nexusBack");
    }

    private void doSetup(InteractionDialogAPI dialog) {
        xdp_MotherShipRaidIntel raidIntel = new xdp_MotherShipRaidIntel(targetmarket, rewardlist);
        Global.getSector().getIntelManager().addIntel(raidIntel, false, dialog.getTextPanel());
        Global.getSector().getListenerManager().addListener(raidIntel);

        for (int i = 0; i < 2; i++) {
            try {
                CampaignFleetAPI remmy = MagicCampaign.createFleetBuilder()
                        .setFleetName("Chauffeurs")
                        .setFleetFaction(Factions.DERELICT)
                        .setAssignmentTarget(Global.getSector().getPlayerFleet())
                        .setAssignment(FleetAssignment.ORBIT_PASSIVE)
                        .setMinFP(Global.getSector().getPlayerFleet().getFleetPoints() / 2)
                        .setIsImportant(true)
                        .setSpawnLocation((SectorEntityToken)dialog.getInteractionTarget())
                        .setTransponderOn(false)
                        .setQualityOverride(2f)
                        .create();

                remmy.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOLD_VS_STRONGER, true);
                remmy.getMemoryWithoutUpdate().set(MemFlags.DO_NOT_TRY_TO_AVOID_NEARBY_FLEETS, true);
                remmy.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
                remmy.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FORCE_TRANSPONDER_OFF, true);

                remmy.removeFirstAssignment();
                remmy.addAssignment(FleetAssignment.ATTACK_LOCATION, targetmarket.getPrimaryEntity(), 30f);
            } catch (Exception e) {
                Global.getLogger(this.getClass()).warn("Failed to create raid support fleet", e);
            }
        }

        dialog.getTextPanel().addPara("Raid request accepted. Support fleets have been dispatched.");
        dialog.getTextPanel().addPara("Check your intel for details.");
        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Continue", "xdp_nexusBack");
    }

    private void getRaidReward(InteractionDialogAPI dialog) {
        if (dialog == null) return;

        IntelInfoPlugin intel = Global.getSector().getIntelManager().getFirstIntel(xdp_MotherShipRaidIntel.class);

        if (intel instanceof xdp_MotherShipRaidIntel) {
            xdp_MotherShipRaidIntel raidIntel = (xdp_MotherShipRaidIntel) intel;
            ArrayList<Integer> rewards = raidIntel.getRewardList();

            int numgamma = rewards.get(0);
            int numbeta = rewards.get(1);
            int numalpha = rewards.get(2);
            int numcredits = rewards.get(3);

            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
            CargoAPI cargo = playerFleet.getCargo();

            cargo.addCommodity(Commodities.GAMMA_CORE, (float) numgamma);
            cargo.addCommodity(Commodities.BETA_CORE, (float) numbeta);
            cargo.addCommodity(Commodities.ALPHA_CORE, (float) numalpha);
            cargo.getCredits().add((float) numcredits);

            dialog.getTextPanel().setFontVictor();
            dialog.getTextPanel().addPara("RECONSTRUCTING MESSAGE - // waiting ... //");
            dialog.getTextPanel().setFontInsignia();
            dialog.getTextPanel().addPara("Voluminous greetings, caretaker.");
            dialog.getTextPanel().addPara(numgamma + " chocolate mousse, " + numbeta +
                    " lemon zest, " + numalpha + " blueberry gummies and " + numcredits + " credits are given");

            dialog.getTextPanel().setFontSmallInsignia();
            dialog.getTextPanel().addPara(numgamma + " gamma core, " + numbeta +
                    " beta core, " + numalpha + " alpha core and " + numcredits + " credits are rewarded");
            dialog.getTextPanel().setFontInsignia();

            SectorAPI sector = Global.getSector();
            sector.getListenerManager().removeListenerOfClass(xdp_MotherShipRaidIntel.class);

            List<IntelInfoPlugin> toRemove = new ArrayList<>();
            for (IntelInfoPlugin i : sector.getIntelManager().getIntel()) {
                if (i instanceof xdp_MotherShipRaidIntel) {
                    toRemove.add(i);
                }
            }

            for (IntelInfoPlugin i : toRemove) {
                sector.getIntelManager().removeIntel(i);
            }

            targetmarket = null;
            rewardlist.clear();
            sector.getMemoryWithoutUpdate().set("$xdp_nexusPartyTimeout", true, 180f);
            sector.getMemoryWithoutUpdate().set("$xdp_nexusParty", 2, 0);
            sector.getMemoryWithoutUpdate().unset("$xdp_nexusParty");
        }

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Continue", "xdp_nexusBack");
    }

    /**
     * Ensure the storage entity exists
     */
    private void ensureStorageEntityExists() {
        if (Global.getSector().getEntityById("xdp_nexusStorage") == null) {
            try {
                // Create a simple entity in hyperspace
                SectorEntityToken storage = Global.getSector().getHyperspace().createToken(0, 0);
                storage.setId("xdp_nexusStorage");
                storage.setName("Explorarium Storage Network");
                storage.setFaction(Factions.DERELICT);

                // Add to hyperspace
                Global.getSector().getHyperspace().addEntity(storage);

                Global.getLogger(this.getClass()).info("Created storage entity: xdp_nexusStorage");
            } catch (Exception e) {
                Global.getLogger(this.getClass()).warn("Failed to create storage entity", e);
            }
        }
    }

    // Nexus build script
    public static class xdp_nexusBuildScript implements Script {
        private CampaignFleetAPI source;
        private EntityLocation loc;

        public xdp_nexusBuildScript(CampaignFleetAPI source, EntityLocation loc) {
            this.source = source;
            this.loc = loc;
        }

        @Override
        public void run() {
            StarSystemAPI system = source.getStarSystem();
            if (system == null) return;

            Random random = Misc.random;
            Global.getSector().getCampaignUI().getMessageDisplay().addMessage(
                    "Mothership construction finished in " + system.getName());

            SectorEntityToken fleet = source.getContainingLocation().addCustomEntity(
                    null, null, "derelict_mothership", Factions.DERELICT);

            float orbitRadius = Misc.getDistance(source, system.getCenter());
            float orbitDays = orbitRadius / (20f + random.nextFloat() * 5f);
            float angle = Misc.getAngleInDegrees(system.getCenter().getLocation(), source.getLocation());

            fleet.setCircularOrbit(system.getCenter(), angle, orbitRadius, orbitDays);
            system.addTag(Tags.THEME_DERELICT_MOTHERSHIP);

            CargoAPI cargo = addNexusCargo();
            if (cargo != null) {
                fleet.getCargo().addAll(cargo);
            }

            if (Global.getSettings().getModManager().isModEnabled("all_the_domain_drones+Vanilla")) {
                try {
                    Class<?> managerClass = Class.forName("data.scripts.MothershipFleetManager");
                    java.lang.reflect.Constructor<?> constructor = managerClass.getConstructor(
                            SectorEntityToken.class, float.class, int.class, int.class, float.class, int.class, int.class);
                    Object manager = constructor.newInstance(fleet, 5f, 4, 6, 15f, 5, 20);
                    if (manager instanceof EveryFrameScript) {
                        system.addScript((EveryFrameScript) manager);
                    }
                } catch (Exception e) {
                    Global.getLogger(this.getClass()).warn("Failed to add MothershipFleetManager", e);
                }
            }

            source.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, fleet, 999f);
        }

        private CargoAPI addNexusCargo() {
            CargoAPI cargo = Global.getFactory().createCargo(false);
            cargo.addCommodity(Commodities.GAMMA_CORE, (float) Math.round(8 + Math.random() * 7));
            cargo.addCommodity(Commodities.BETA_CORE, (float) Math.round(5 + Math.random() * 4));
            cargo.addCommodity(Commodities.ALPHA_CORE, (float) Math.round(4 + Math.random() * 3));
            cargo.addCommodity(Commodities.FUEL, (float) Math.round(9000 + Math.random() * 6000));
            cargo.addCommodity(Commodities.SUPPLIES, (float) Math.round(3000 + Math.random() * 2000));

            if (Math.random() > 0.97f) {
                cargo.addCommodity(Commodities.OMEGA_CORE, 1f);
            }

            return cargo;
        }
    }

    // Nexus build picker
    private void showNexusBuildPicker(InteractionDialogAPI dialog) {
        List<SectorEntityToken> nexusList = Global.getSector().getCustomEntitiesWithTag("derelict_mothership");
        List<StarSystemAPI> bannedSystemsList = new ArrayList<>();

        for (SectorEntityToken token : nexusList) {
            if (token.getStarSystem() != null) {
                bannedSystemsList.add(token.getStarSystem());
            }
        }

        List<StarSystemAPI> validSystemList = new ArrayList<>();
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.isEnteredByPlayer() && system.isProcgen() && !system.isDeepSpace() &&
                    !system.hasTag(Tags.THEME_HIDDEN) && !bannedSystemsList.contains(system)) {
                validSystemList.add(system);
            }
        }

        if (validSystemList.isEmpty()) {
            dialog.getTextPanel().addPara("No valid systems found for construction.");
            dialog.getTextPanel().addPara("You need to discover more procedurally generated systems first.");
            handleConstructMenu(dialog);
            return;
        }

        List<SectorEntityToken> centers = new ArrayList<>();
        for (StarSystemAPI system : validSystemList) {
            centers.add(system.getCenter());
        }

        dialog.showCampaignEntityPicker("Select System", "Build", "Cancel",
                Global.getSector().getFaction(Factions.DERELICT), centers,
                new BaseCampaignEntityPickerListener() {
                    @Override
                    public void cancelledEntityPicking() {
                        dialog.getTextPanel().addPara("Construction cancelled.");
                        handleConstructMenu(dialog);
                    }

                    @Override
                    public float getFuelRangeMult() {
                        return 0f;
                    }

                    @Override
                    public void pickedEntity(SectorEntityToken entity) {
                        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
                        cargo.removeCommodity(Commodities.SUPPLIES, SUPPLIES_PER_NEXUS);
                        cargo.removeCommodity(Commodities.METALS, METALS_PER_NEXUS);
                        cargo.removeCommodity(Commodities.RARE_METALS, RARE_METALS_PER_NEXUS);

                        AddRemoveCommodity.addCommodityLossText(Commodities.SUPPLIES,
                                Math.round(SUPPLIES_PER_NEXUS), dialog.getTextPanel());
                        AddRemoveCommodity.addCommodityLossText(Commodities.METALS,
                                Math.round(METALS_PER_NEXUS), dialog.getTextPanel());
                        AddRemoveCommodity.addCommodityLossText(Commodities.RARE_METALS,
                                Math.round(RARE_METALS_PER_NEXUS), dialog.getTextPanel());

                        StarSystemAPI system = entity.getStarSystem();
                        EntityLocation loc = BaseThemeGenerator.pickCommonLocation(Misc.random, system, 200f, true, null);

                        Global.getSector().getMemoryWithoutUpdate().set("$xdp_nexusBuildTimeout", true, 90f);

                        try {
                            SectorEntityToken token = system.createToken(MathUtils.getRandomPointOnCircumference(
                                    entity.getLocation(), 6000f));

                            CampaignFleetAPI constructorFleet = MagicCampaign.createFleetBuilder()
                                    .setFleetFaction(Factions.DERELICT)
                                    .setFleetName("Construction Fleet")
                                    .setFleetType(FleetTypes.SUPPLY_FLEET)
                                    .setAssignment(FleetAssignment.GO_TO_LOCATION)
                                    .setAssignmentTarget(token)
                                    .setSpawnLocation(dialog.getInteractionTarget())
                                    .setIsImportant(true)
                                    .setMinFP(200)
                                    .create();

                            constructorFleet.removeFirstAssignment();
                            constructorFleet.addAssignment(FleetAssignment.GO_TO_LOCATION, token, 999f);
                            constructorFleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, token, 30f,
                                    "Constructing Mothership", new xdp_nexusBuildScript(constructorFleet, loc));

                            dialog.getTextPanel().addPara("A fleet has been dispatched to " + system.getName() + ".");
                            dialog.getTextPanel().addPara("Once it arrives, it will take 30 days to construct the Mothership.");
                        } catch (Exception e) {
                            Global.getLogger(this.getClass()).warn("Failed to create construction fleet", e);
                            dialog.getTextPanel().addPara("Construction failed: MagicLib may not be installed correctly.");
                        }

                        dialog.getOptionPanel().clearOptions();
                        dialog.getOptionPanel().addOption("Continue", "xdp_nexusBack");
                    }

                    @Override
                    public boolean canConfirmSelection(SectorEntityToken entity) {
                        return entity != null;
                    }

                    @Override
                    public void createInfoText(TooltipMakerAPI info, SectorEntityToken entity) {
                        if (entity == null) return;
                        info.addPara("Selected system: " + entity.getStarSystem().getName(), 5f);
                        if (entity.getStarSystem().getConstellation() != null) {
                            info.addPara(entity.getStarSystem().getConstellation().getName(), 5f);
                        }
                        float ly = Misc.getDistanceToPlayerLY(entity);
                        info.addPara(String.format("%.1f light years away from your location.", ly), 5f);
                    }

                    @Override
                    public String getMenuItemNameOverrideFor(SectorEntityToken entity) {
                        return entity != null && entity.getStarSystem() != null ?
                                entity.getStarSystem().getName() : "Location";
                    }
                });
    }

    // Storage script inner class
    public static class NexusStorageScript implements EveryFrameScript {
        private SectorEntityToken nexus;
        private InteractionDialogAPI dialog;
        private boolean cancel = false;
        private boolean ranStorage = false;
        private IntervalUtil canceller = new IntervalUtil(0.03f, 0.03f);
        private SectorEntityToken storageEntity;

        public NexusStorageScript(SectorEntityToken nexus, InteractionDialogAPI dialog) {
            this.nexus = nexus;
            this.dialog = dialog;
            this.storageEntity = Global.getSector().getEntityById("xdp_nexusStorage");
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean runWhilePaused() {
            return true;
        }

        @Override
        public void advance(float amount) {
            if (storageEntity == null) {
                Global.getSector().removeTransientScriptsOfClass(NexusStorageScript.class);
                return;
            }

            if (!cancel) {
                canceller.advance(amount);
                if (canceller.intervalElapsed()) {
                    cancel = true;

                    if (dialog.getPlugin() instanceof FleetInteractionDialogPluginImpl) {
                        FleetInteractionDialogPluginImpl plugin = (FleetInteractionDialogPluginImpl) dialog.getPlugin();
                        if (plugin.getContext() instanceof FleetEncounterContext) {
                            FleetEncounterContext context = (FleetEncounterContext) plugin.getContext();
                            context.applyAfterBattleEffectsIfThereWasABattle();
                            BattleAPI b = context.getBattle();
                            if (b != null) {
                                b.leave(Global.getSector().getPlayerFleet(), false);
                                b.finish(BattleSide.NO_JOIN, false);
                            }
                        }
                    }
                    dialog.dismiss();
                    return;
                }
            }

            if (cancel && !ranStorage && !Global.getSector().getCampaignUI().isShowingDialog()) {
                // Show the storage dialog - use the entity's plugin directly
                Global.getSector().getCampaignUI().showInteractionDialog((InteractionDialogPlugin) storageEntity.getCustomInteractionDialogImageVisual(), null);
                ranStorage = true;
                return;
            }

            if (ranStorage && !Global.getSector().getCampaignUI().isShowingDialog() &&
                    Global.getCurrentState() == GameState.CAMPAIGN) {
                // Return to nexus dialog - use the entity's plugin directly
                Global.getSector().getCampaignUI().showInteractionDialog((InteractionDialogPlugin) nexus.getCustomInteractionDialogImageVisual(), null);
                Global.getSector().removeTransientScriptsOfClass(NexusStorageScript.class);
            }
        }
    }
}