//////////////////////
//blendColors by theDragn from HTE
//BiasFunction by Sebastian Lague
//////////////////////
package neon.xdp.data.scripts.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.*;

import java.awt.*;
import java.util.*;
import java.util.List;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;

import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class XDP_superderelict_util {

    //

    public static final Color TT_ORANGE = new Color(255, 109, 31, 255);
    public static final Color BON_GREEN = new Color(142, 255, 21, 255);
    public static final Color NICE_YELLOW = new Color(255, 219, 36, 255);
    public static final Color EXOTICA_RED = new Color(141, 42, 38, 255);

    static void log(final String message) {
        Global.getLogger(XDP_superderelict_util.class).info(message);
    }

    public static int clamp255(int x) {
        return Math.max(0, Math.min(255, x));
    }


    public static Color blendColors(Color c1, Color c2, float ratio) {
        float iRatio = 1.0f - ratio;
        int a1 = c1.getAlpha();
        int r1 = c1.getRed();
        int g1 = c1.getGreen();
        int b1 = c1.getBlue();
        int a2 = c2.getAlpha();
        int r2 = c2.getRed();
        int g2 = c2.getGreen();
        int b2 = c2.getBlue();
        int a = (int)((float)a1 * iRatio + (float)a2 * ratio);
        int r = (int)((float)r1 * iRatio + (float)r2 * ratio);
        int g = (int)((float)g1 * iRatio + (float)g2 * ratio);
        int b = (int)((float)b1 * iRatio + (float)b2 * ratio);
        return new Color(r, g, b, a);
    }

    public static Color randomiseColor(Color inputColor, int rShift, int gShift, int bShift, int aShift, boolean addition){
        int rShift2 = rShift;
        int gShift2 = gShift;
        int bShift2 = bShift;
        int aShift2 = aShift;

        if (addition){
            rShift = 0;
            gShift = 0;
            bShift = 0;
            aShift = 0;
        } else {
            rShift = -rShift;
            gShift = -gShift;
            bShift = -bShift;
            aShift = -aShift;
        }

        int r = inputColor.getRed() + MathUtils.getRandomNumberInRange(rShift,rShift2);
        if (r>255){r = 255;} else if(r<0) r = 0;
        int g = inputColor.getGreen() + MathUtils.getRandomNumberInRange(gShift,gShift2);
        if (g>255){g = 255;} else if(g<0) g = 0;
        int b = inputColor.getBlue() + MathUtils.getRandomNumberInRange(bShift,bShift2);
        if (b>255){b = 255;} else if(b<0) b = 0;
        int a = inputColor.getAlpha() + MathUtils.getRandomNumberInRange(aShift,aShift2);
        if (a>255){a = 255;} else if(a<0) a = 0;

        return new Color(r,g,b,a);
    }

    public static Color shiftAlpha(Color color, float mult){
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        int a = Math.min(Math.round(color.getAlpha()*mult), 255);

        return new Color(r,g,b,a);
    }


    //public static boolean hasTag(Set<String> tags, String tag){
    //    boolean has = false;
    //    for (String t : tags){
    //        if (t.equals(tag)){
    //            has = true;
    //            break;
    //        }
    //    }
    //    return has;
    //}


    //backup for blacklist generation, this one just returns a random non-core system
    //backup for blacklist generation, this one just returns a random core system with a market



    public static PersonAPI setOfficerSkills(PersonAPI officer, Map<String, Integer> skills){

        //reset
        List<MutableCharacterStatsAPI.SkillLevelAPI> ogSkills = officer.getStats().getSkillsCopy();
        for (MutableCharacterStatsAPI.SkillLevelAPI s : ogSkills){
            officer.getStats().setSkillLevel(s.getSkill().getId(), 0.0f);
        }
        //set
        for (String s : skills.keySet()) {
            officer.getStats().setSkillLevel(s, (float)skills.get(s));
            //log("OFFICER skill "+ s + " lvl " + (float)skills.get(s));
        }

        officer.getStats().setLevel(skills.size());
        officer.getStats().refreshCharacterStatsEffects();

        return officer;
    }

    public static Map<String, Integer> createRandomSkills(int level, float eliteSkillChance, Random random){
        Set<String> allSkills = new HashSet<>();
        allSkills.add(Skills.POLARIZED_ARMOR);
        allSkills.add(Skills.ENERGY_WEAPON_MASTERY);
        allSkills.add(Skills.DAMAGE_CONTROL);
        allSkills.add(Skills.HELMSMANSHIP);
        allSkills.add(Skills.BALLISTIC_MASTERY);
        allSkills.add(Skills.ORDNANCE_EXPERTISE);
        allSkills.add(Skills.IMPACT_MITIGATION);
        allSkills.add(Skills.GUNNERY_IMPLANTS);
        allSkills.add(Skills.TARGET_ANALYSIS);
        allSkills.add(Skills.SYSTEMS_EXPERTISE);
        allSkills.add(Skills.MISSILE_SPECIALIZATION);
        allSkills.add(Skills.COMBAT_ENDURANCE);
        allSkills.add(Skills.FIELD_MODULATION);
        allSkills.add(Skills.POINT_DEFENSE);

        Map<String, Integer> skills = new HashMap<>();
        while (skills.size()<level) {
            float size = allSkills.size();
            for (String s : allSkills) {
                if (!skills.containsKey(s) && random.nextFloat()<1f/size){
                    skills.put(s, getEliteSkillChance(random, eliteSkillChance));
                    log("util added skill "+s);
                }
            }
        }
        return skills;
    }
    public static int getEliteSkillChance(Random random, float chance){
        if (random.nextFloat()<chance) return 1;
        return 2;
    }

    //Really fucking cursed workarounds to get enigma and prot ships
    //Because, ships lose all tags on save & reload ????????
    public static boolean isProtTech(FleetMemberAPI member){
        if (member.getVariant()==null) return false;
        if (member.getVariant().getHullMods()==null || member.getVariant().getHullMods().isEmpty())  return false;
        boolean prot = false;
        for (String m : member.getVariant().getHullMods()){
            if (m.equals("nskr_focused_shield") || m.equals("nskr_kaboom")){
                prot = true;
                break;
            }
        }
        return prot;
    }
    public static boolean isProtTech(ShipAPI ship){
        if (ship.getVariant()==null) return false;
        if (ship.getVariant().getHullMods()==null || ship.getVariant().getHullMods().isEmpty())  return false;
        boolean prot = false;
        for (String m : ship.getVariant().getHullMods()){
            if (m.equals("nskr_focused_shield") || m.equals("nskr_kaboom")){
                prot = true;
                break;
            }
        }
        return prot;
    }
    public static String protOrEnigma(ShipAPI ship){
        if (ship.getVariant()==null) return null;
        if (ship.getVariant().getHullMods()==null || ship.getVariant().getHullMods().isEmpty())  return null;
        //prot
        for (String m : ship.getVariant().getHullMods()){
            if (m.equals("nskr_lost_prot")){
                return "prot";
            }
        }
        //enigma
        for (String m : ship.getVariant().getHullMods()){
            if (m.equals("nskr_domain_era")){
                return "enigma";
            }
        }
        return null;
    }
    public static String protOrEnigma(FleetMemberAPI member){
        if (member.getVariant()==null) return null;
        if (member.getVariant().getHullMods()==null || member.getVariant().getHullMods().isEmpty())  return null;
        //prot
        for (String m : member.getVariant().getHullMods()){
            if (m.equals("nskr_lost_prot")){
                return "prot";
            }
        }
        //enigma
        for (String m : member.getVariant().getHullMods()){
            if (m.equals("nskr_domain_era")){
                return "enigma";
            }
        }
        return null;
    }

    public static float getDistanceFromNearestSystem(Vector2f loc){
        float shortestDist = Float.MAX_VALUE;
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.getHyperspaceAnchor( )== null) continue;
            if (system.hasTag(Tags.THEME_HIDDEN)) continue;
            float dist =  MathUtils.getDistance(loc, system.getHyperspaceAnchor().getLocationInHyperspace());
            if (dist>shortestDist) continue;
            shortestDist = dist;
        }
        return shortestDist;
    }

    public static StarSystemAPI getNearestSystem(Vector2f loc){
        float shortestDist = Float.MAX_VALUE;
        StarSystemAPI sys = null;
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.getHyperspaceAnchor() == null) continue;
            if (system.hasTag(Tags.THEME_HIDDEN)) continue;
            float dist =  MathUtils.getDistance(loc, system.getHyperspaceAnchor().getLocationInHyperspace());
            if (dist>shortestDist) continue;
            shortestDist = dist;
            sys = system;
            //log("sys "+sys.getName()+" dist "+dist);
        }
        return sys;
    }

    public static String parseCustom(String primary, String hl){
        int loopCount = 0;
        int indexInSequence = 0;
        while (primary.contains("%s")){
            String h = "";
            if (hl.contains("|")){
                if (loopCount==0){
                    h = (String)hl.subSequence(0, hl.indexOf("|")-1);
                    indexInSequence = hl.indexOf("|")+1;
                }
                if (loopCount>=1){
                    log("index1 "+indexInSequence);
                    if(hl.indexOf("|", indexInSequence)>0) {
                        log("index2 "+indexInSequence);
                        h = (String) hl.subSequence(indexInSequence+1, hl.indexOf("|", indexInSequence) - 1);
                        indexInSequence = hl.indexOf("|", indexInSequence)+1;
                        log("index3 "+indexInSequence);
                    } else {
                        h = (String) hl.subSequence(indexInSequence+1, hl.length());
                    }
                }
            } else h= hl;
            primary = primary.replaceFirst("%s", h);
            loopCount++;
        }

        return primary;
    }

    public static long getSeedParsed(){
        String seed = Global.getSector().getSeedString();
        String prefix = seed.substring(0,2);
        seed = seed.replace(prefix, "");

        //if (Global.getSector()!=null && Global.getSector().getClock()!=null) {
        //    String clockSeed = ""+Global.getSector().getClock().getTimestamp();
        //    clockSeed = clockSeed.replaceAll("-","");
        //    clockSeed = clockSeed.replaceAll("0","");
        //    seed = seed + clockSeed;
        //    //log("TIMESTAMP "+clockSeed);
        //}
        //while (seed.length()>18){
        //    seed = seed.replace(seed.substring(0,1), "");
        //}
        return Long.parseLong(seed);
    }

    public static Color setAlpha(Color color, int alpha) {
        return new Color(color.getRed(),color.getGreen(),color.getBlue(),clamp255(alpha));
    }

    public static final ArrayList<String> GREEK_LETTERS = new ArrayList<>();
    static {
        GREEK_LETTERS.add("alpha");
        GREEK_LETTERS.add("beta");
        GREEK_LETTERS.add("gamma");
        GREEK_LETTERS.add("delta");
        GREEK_LETTERS.add("epsilon");
        GREEK_LETTERS.add("zeta");
        GREEK_LETTERS.add("eta");
        GREEK_LETTERS.add("theta");
        GREEK_LETTERS.add("iota");
        GREEK_LETTERS.add("kappa");
        GREEK_LETTERS.add("lambda");
        GREEK_LETTERS.add("mu");
        GREEK_LETTERS.add("nu");
        GREEK_LETTERS.add("xi");
        GREEK_LETTERS.add("omicron");
        GREEK_LETTERS.add("pi");
        GREEK_LETTERS.add("rho");
        GREEK_LETTERS.add("sigma");
        GREEK_LETTERS.add("tau");
        GREEK_LETTERS.add("upsilon");
        GREEK_LETTERS.add("phi");
        GREEK_LETTERS.add("chi");
        GREEK_LETTERS.add("psi");
        GREEK_LETTERS.add("omega");
    }


    public static String capitalizeFirstLetter(String string) {
        String first = string.substring(0,1);
        String capitalized = first.toUpperCase();
        return string.replaceFirst(first, capitalized);
    }

    public static SectorEntityToken swapSalvageEntity(SectorEntityToken from, String to, Random random){
        StarSystemAPI sys = from.getStarSystem();
        SectorEntityToken focus = from.getOrbitFocus();
        float angle = from.getCircularOrbitAngle();
        float period = from.getCircularOrbitPeriod();
        float radius = from.getCircularOrbitRadius();
        float facing = from.getFacing();

        SectorEntityToken toEntity = BaseThemeGenerator.addSalvageEntity(random, from.getStarSystem().getStar().getContainingLocation(), to, Factions.NEUTRAL);
        toEntity.setCircularOrbitPointingDown(focus, angle, radius, period);
        toEntity.setFacing(facing);

        from.setExpired(true);
        sys.removeEntity(from);

        return toEntity;
    }

    public static SectorEntityToken swapEntity(SectorEntityToken from, String to){
        StarSystemAPI sys = from.getStarSystem();
        SectorEntityToken focus = from.getOrbitFocus();
        float angle = from.getCircularOrbitAngle();
        float period = from.getCircularOrbitPeriod();
        float radius = from.getCircularOrbitRadius();
        float facing = from.getFacing();

        BaseThemeGenerator.EntityLocation loc = new BaseThemeGenerator.EntityLocation();
        loc.location = from.getLocation();
        loc.type = BaseThemeGenerator.LocationType.NEAR_STAR;

        SectorEntityToken toEntity = BaseThemeGenerator.addNonSalvageEntity(from.getStarSystem().getStar().getContainingLocation(), loc, to, Factions.NEUTRAL).entity;
        toEntity.setCircularOrbitPointingDown(focus, angle, radius, period);
        toEntity.setFacing(facing);

        from.setExpired(true);
        sys.removeEntity(from);

        return toEntity;
    }

    public static boolean hasNeutronStar(StarSystemAPI sys) {
        for (SectorEntityToken e : sys.getAllEntities()){
            if (e instanceof PlanetAPI) {
                if (!e.isStar()) continue;
                if (((PlanetAPI) e).getTypeId()==null) continue;
                if (((PlanetAPI) e).getTypeId().equals(StarTypes.NEUTRON_STAR)) {
                    return true;
                }
            }
        }
        return false;
    }
    public static float getLinearMod(ShipAPI ship, float mult){
        float mod = 1f;
        switch (ship.getHullSize()){
            case FIGHTER:
                mod = 0.5f;
                break;
            case FRIGATE:
                mod = 1.0f;
                break;
            case DESTROYER:
                mod = 2.0f;
                break;
            case CRUISER:
                mod = 4.0f;
                break;
            case CAPITAL_SHIP:
                mod = 8.0f;
                break;
        }
        return mod * mult;
    }
}
