package neon.xdp.data.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.CampaignPlugin;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import neon.xdp.data.plugins.secgen.XDP_InvictaSystemGen;
import neon.xdp.data.scripts.*;
import org.dark.shaders.util.ShaderLib;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class XDPModPlugin extends BaseModPlugin {
    public static boolean hasMagicLib = false;
    public static final String XDP_LUMINANCE_MISSILE_ID = "xdp_luminancemissile";
    public static Logger log = Logger.getLogger(XDPModPlugin.class);
    public static boolean HAS_GRAPHICSLIB = false;
    public static boolean hasParadeigmaSkill = false;

    // Store reference to our campaign plugin
    private static xdp_CampaignPlugin campaignPlugin;

    @Override
    public void onGameLoad(boolean newGame) {
        log.info("XDPModPlugin onGameLoad called");

        // Register campaign plugin if not already registered
        registerCampaignPlugin();

        // NSP initialization
        if (!Global.getSector().getListenerManager().hasListenerOfClass(DerelictOddityTracker.class)) {
            Global.getSector().getListenerManager().addListener(new DerelictOddityTracker(), true);
        }

        try {
            SectorAPI sector = Global.getSector();
            InvictaCampaignPluginImpl plugin = new InvictaCampaignPluginImpl();
            sector.registerPlugin(plugin);
        } catch (Throwable t) {
            log.error("Failed to register InvictaCampaignPluginImpl", t);
        }

        // Add skill tracker script
        if (Global.getSector() != null) {
            Global.getSector().addTransientScript(new ParadeigmaSkillTracker());
        }
    }

    @Override
    public void onNewGameAfterEconomyLoad() {
        log.info("XDPModPlugin onNewGameAfterEconomyLoad called");
        registerCampaignPlugin();

        CustomFleetsXDP_Templar.spawnTemplar();
        CustomFleetsXDP_Gate.spawnNemetor();
        new XDP_People().xdp_createPeople();
    }

    @Override
    public void onNewGameAfterProcGen() {
        log.info("XDPModPlugin onNewGameAfterProcGen called");

        // NSP content
        SectorAPI sector = Global.getSector();
        ArrayList<String> tagBL = new ArrayList<>();
        tagBL.add(Tags.THEME_HIDDEN);
        tagBL.add(Tags.SYSTEM_ALREADY_USED_FOR_STORY);
        tagBL.add(Tags.SYSTEM_ABYSSAL);
        tagBL.add(Tags.STAR_HIDDEN_ON_MAP);
        tagBL.add("theme_d");

        new XDP_gen().generate(Global.getSector());
        new XDP_InvictaSystemGen().generate(Global.getSector());
    }

    @Override
    public void onApplicationLoad() throws Exception {
        log.info("XDPModPlugin onApplicationLoad called");

        boolean hasGraphicsLib = Global.getSettings().getModManager().isModEnabled("shaderLib");
        if (hasGraphicsLib) {
            HAS_GRAPHICSLIB = true;
            ShaderLib.init();
            log.info("XDP shaders active");
        }
        hasMagicLib = Global.getSettings().getModManager().isModEnabled("MagicLib");


        log.info("Welcome to XDP! I'm in your hulls...");
    }

    @Override
    public PluginPick<MissileAIPlugin> pickMissileAI(MissileAPI missile, ShipAPI launchingShip) {
        if (missile.getProjectileSpecId() != null &&
                missile.getProjectileSpecId().equals(XDP_LUMINANCE_MISSILE_ID)) {
            return new PluginPick<MissileAIPlugin>(
                    new xdp_luminancebay(missile, launchingShip),
                    CampaignPlugin.PickPriority.MOD_SPECIFIC
            );
        }
        return null;
    }

    /**
     * Register the campaign plugin with the sector
     */
    private void registerCampaignPlugin() {
        if (Global.getSector() == null) {
            log.warn("Cannot register campaign plugin - sector is null");
            return;
        }

        if (campaignPlugin == null) {
            log.info("registered xdp_CampaignPlugin");
            campaignPlugin = new xdp_CampaignPlugin();
            Global.getSector().registerPlugin(campaignPlugin);
        }
    }

    /**
     * Get the registered campaign plugin instance
     */
    public static xdp_CampaignPlugin getCampaignPlugin() {
        return campaignPlugin;
    }

    // Skill tracker script
    public static class ParadeigmaSkillTracker implements EveryFrameScript {
        private boolean done = false;
        private float checkDelay = 1f;
        private float timer = 0f;

        @Override
        public void advance(float amount) {
            if (Global.getSector() == null || Global.getSector().getPlayerPerson() == null) return;

            timer += amount;
            if (timer < checkDelay) return;
            timer = 0f;

            PersonAPI player = Global.getSector().getPlayerPerson();
            boolean hasSkill = player.getStats().hasSkill("xdp_paradeigma");

            // If skill was just acquired, initialize content
            if (hasSkill && !XDPModPlugin.hasParadeigmaSkill) {
                XDPModPlugin.hasParadeigmaSkill = true;
                log.info("got skill, add stuff");
            }
            // If skill was lost (shouldn't happen normally, but just in case)
            else if (!hasSkill && XDPModPlugin.hasParadeigmaSkill) {
                XDPModPlugin.hasParadeigmaSkill = false;
                log.info("no skill, no stuff");
            }
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean runWhilePaused() {
            return true;
        }
    }
}