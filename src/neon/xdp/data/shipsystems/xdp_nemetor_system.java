package neon.xdp.data.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.util.MagicRender;
import neon.xdp.data.scripts.util.XDP_Util;
import org.dark.shaders.distortion.WaveDistortion;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.magiclib.plugins.MagicTrailPlugin;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * System script for the Saviour : a fancy-pancy teleporter
 */
public class xdp_nemetor_system extends BaseShipSystemScript {
    //These *must* match the stats in ship_systems.csv : I can't easily grab them from there so it was easier to
    //input them here at the top of the config instead
    //  Note that all other times set in the script except the buff times are expected to add up to this
    private static final float CHARGEUP = 5.1f;
    private static final float CHARGEDOWN = 3.1f;

    //How long does the "normal" phasing take, and what is the opacity for the ship there?
    private static final float PHASE_TIME = 0.6f;
    private static final float PHASE_OPACITY = 0.2f;

    //How long does the various phases in the ship's teleportation take?
    private static final float DEEPPHASE_FADE_IN_TIME = 2.5f; //As in, fade time for the "in" state
    private static final float DISAPPEARANCE_TIME = 2f;
    private static final float DEEPPHASE_FADE_OUT_TIME = 2.5f; //As in, fade time for the "out" state
    private static final float BUFF_TIME_FULL = 10f; // How long the buff lasts without starting to fade out the bonuses
    private static final float BUFF_TIME_FADE = 10f; // How long the buff lasts while the bonuses fades out over time

    // How long should the cooldown of the system be when "recasting" it? (i.e. when using it again while the buff is still active)
    private static final float RECAST_COOLDOWN = 1f;

    // At what proportion of the buff duration should the recast start getting range falloff, and at what should recasting be impossible?
    private static final float RECAST_MAX_RANGE_LIMIT = 0.5f;
    private static final float RECAST_LIMIT = 0.1f;

    // What's the highest range penalty possible by the recast range penalty? Defined as a percentage reduction (so 33
    // would be a maximum range decrease of 33% just before hitting the recast timeout)
    private static final float RECAST_MAX_RANGE_PENALTY_PERCENT = 66;

    // Actual buff values after jumping
    //      _MULT are multiplier modifiers, while _FLAT are flat increases. Both of these decrease over the buff duration
    //      (see BUFF_TIME_FULL and BUFF_TIME_FADE)
    private static final float BUFF_ROF_MULT = 1.25f;
    private static final float BUFF_WEAPON_FLUX_MULT = 0.5f;
    private static final float BUFF_BEAM_DAMAGE_MULT = 1.25f;
    private static final float BUFF_WEAPON_RANGE_MULT = 1.25f;
    private static final float BUFF_SHIELD_TURNSPEED_MULT = 1f;
    private static final float BUFF_SHIELD_RAISE_SPEED_MULT = 1f;
    private static final float BUFF_SHIP_MOBILITY_MULT = 2f; // This includes turn speed and turn acceleration

    //ID of the sprite to use for the ship's phase glow
    //Expected to be loaded in settings.json under the category "ttc_combat"
   // private static final String GLOW_1_SPRITE_ID = "ttc_savior_glow1";
    //private static final String GLOW_2_SPRITE_ID = "ttc_savior_glow2";

    //Colors of the ship's phase glows
    //      I've set them to vanilla values for now, but feel free to change of course
    private static final Color GLOW_1_COLOR = new Color(255, 175, 255, 255);
    private static final Color GLOW_2_COLOR = new Color(255, 0, 255, 150);

    //Glow color for the ship's weapons while buffed
    private static final Color WEAPON_GLOW_COLOR = new Color(255, 0, 255, 150);

    //The "in" and "out" sound effects to play for the system, when entering and exiting deepphase
    // - Leave as "null" to not use the sound
    private static final String IN_SOUND = "ttc_shockjump_start";
    private static final String OUT_SOUND = "ttc_shockjump_exit";

    // The sound effect to loop while we have the buff
    // - The pitch/volume settings smoothly change from their _HIGH variants to their _LOW variants as the buff falls off
    // - Leave as "null" to not use the sound effect
    private static final String BUFF_SOUND = "system_fortress_shield_loop";
    private static final float BUFF_SOUND_VOLUME_HIGH = 0.8f;
    private static final float BUFF_SOUND_VOLUME_LOW = 0.4f;
    private static final float BUFF_SOUND_PITCH_HIGH = 1f;
    private static final float BUFF_SOUND_PITCH_LOW = 0.8f;

    //Function variable for the rate at which distortion ripples spawn
    //      Higher values means a sharper curve, while lower values give a smoother peak
    //      A 1f means using a simple linear function, 2f is quadratic etc. Lower values than 0f has unforeseen consequences
    private static final float RIPPLE_FUNCTION_VAR = 0.5f;

    //Maximum spawn rate of ripples from the system
    //      Expressed as ripples/second
    private static final float MAX_RIPPLES_PER_SECOND = 3f;

    //Different stats for the ripples themselves
    private static final float RIPPLE_MAX_SIZE = 550f; //Max size of the ripple
    private static final float RIPPLE_DURATION = 3f; //Duration of an individual ripple
    private static final float RIPPLE_DEPTH = 20f; //The "displacement intensity" of an individual ripple

    //Stats for jitter. Note that jitter scales just like ripple frequency does
    private static final float JITTER_DURATION = 0.03f;
    private static final float JITTER_COPIES_PER_SECOND = 140f;
    private static final float JITTER_MAX_SIZE = 15f;
    private static final float JITTER_OPACITY = 0.5f;
    private static final float JITTER_EXTRAJITTER = 2f; //This one is a bit odd, play around with it

    //This is how much the ship is "pushed" by when overlapping with other ships.
    //Finicky variable, play around a bit with it to get it good.
    //1f approximately represents getting completely shunted out over 1 second, 2f half a second etc.
    private static final float BASE_PUSH_FORCE = 5f;

    //"Safe flux level" for the buff : while below this flux level, the ship won't try and vent while it still has
    //its system buff applied
    private static final float SAFE_FLUX_LEVEL = 0.65f;



    private static final String CHARGEUP_SOUND = "xdp_phasetunneleractivate";

    private static final float DAMAGE_MOD_VS_CAPITAL = 0.2f;

    private static final float DAMAGE_MOD_VS_CRUISER = 0.4f;

    private static final float DAMAGE_MOD_VS_DESTROYER = 1f;

    private static final float DAMAGE_MOD_VS_FIGHTER = 0.7f;

    private static final float DAMAGE_MOD_VS_FRIGATE = 0.8f;

    private static final float DISTORTION_BLAST_RADIUS = 1500f;
    private static final Color EXPLOSION_COLOR = new Color(255, 255, 255);

    private static final float EXPLOSION_DAMAGE_AMOUNT = 5000f;

    private static final DamageType EXPLOSION_DAMAGE_TYPE = DamageType.ENERGY;

    private static final float EXPLOSION_DAMAGE_VS_ALLIES_MODIFIER = .25f;

    private static final float EXPLOSION_EMP_DAMAGE_AMOUNT = 5000f;

    private static final float EXPLOSION_EMP_VS_ALLIES_MODIFIER = .25f;

    private static final float EXPLOSION_FORCE_VS_ALLIES_MODIFIER = .3f;

    private static final float EXPLOSION_PUSH_RADIUS = 1000f;

    private static final float EXPLOSION_VISUAL_RADIUS = 1500f;

    private static final float FORCE_VS_ASTEROID = 1500f;

    private static final float FORCE_VS_CAPITAL = 200f;

    private static final float FORCE_VS_CRUISER = 350f;

    private static final float FORCE_VS_DESTROYER = 900f;

    private static final float FORCE_VS_FIGHTER = 1250f;

    private static final float FORCE_VS_FRIGATE = 1000f;

    private static final int MAX_PARTICLES_PER_FRAME = 30;

    private static final Color PARTICLE_COLOR = new Color(243, 225, 255);

    private static final float PARTICLE_OPACITY = 0.5f;

    private static final float PARTICLE_RADIUS = 600f;

    private static final float PARTICLE_SIZE = 10f;

    public static final Color RIPPLE_COLOR = new Color(174, 55, 255, 200);

    public static final Color AFTERIMAGE_COLOR = new Color(255, 196, 19, 20);

    private static final Vector2f ZERO = new Vector2f();


    private static final float TELEPORT_SPEED = 4000f;
    private static final float MIN_TELEPORT_DISTANCE = 100f;
    private static final float MAX_TELEPORT_DISTANCE = 4000;
    private static final float TELEPORT_ACCELERATION = 0.2f;


    //Internal script variables
    public static final String BUFF_ACTIVE_KEY = "TTC_SAVIOR_BUFF_ACTIVE_KEY";
    private boolean hasSpawnedFadeInGlow = false;
    private boolean hasSpawnedFadeOutGlow = false;
    private boolean hasPlayedInSound = false;
    private boolean hasPlayedOutSound = false;
    private float rippleTimer = 0f;
    private float jitterTimer = 0f;
    private Float lockedFacing = null;
    private int turningWiggleFrames = 3;
    private boolean buffActive = false;
    private float buffLevel = 0f;
    private boolean systemReactivated = false;
    private boolean shouldReduceCooldown = false;
    private float bankedCooldown = 0f;

    private final IntervalUtil interval = new IntervalUtil(0.035f, 0.035f);
    private final IntervalUtil interval2 = new IntervalUtil(0.015f, 0.015f);
    private final IntervalUtil teleportInterval = new IntervalUtil(0.016f, 0.016f);
    private boolean isActive = false;
    private StandardLight light = null;
    private Vector2f novaLocation = null;
    private float novaTime = -1f;
    private SoundAPI sound = null;


    private Vector2f teleportStartLocation = null;
    private Vector2f teleportTargetLocation = null;
    private Vector2f teleportVelocity = null;
    private float teleportProgress = 0f;
    private boolean teleportInProgress = false;
    private float teleportStartTime = 0f;
    private float teleportDuration = 0f;

    private boolean isInReactivationSpan() {
        return buffActive && buffLevel > RECAST_LIMIT;
    }

    @SuppressWarnings("deprecation")
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        //Don't run when paused


        ShipAPI ship = (ShipAPI) stats.getEntity();
        CombatEngineAPI engine = Global.getCombatEngine();

        if (state == State.IN) {
            handleTeleportMovement(ship, engine, effectLevel);


            if (!teleportInProgress || teleportProgress < 0.5f) {
                if (!isActive) {
                    isActive = true;
                    sound = Global.getSoundPlayer().playSound(CHARGEUP_SOUND, 1f, 2f, ship.getLocation(), ship.getVelocity());

                    light = new StandardLight();
                    light.setIntensity(1.25f);
                    light.setSize(EXPLOSION_VISUAL_RADIUS);
                    light.setColor(PARTICLE_COLOR);
                    light.fadeIn(1.95f);
                    light.setLifetime(0.1f);
                    light.setAutoFadeOutTime(0.17f);
                    LightShader.addLight(light);
                }

                Vector2f loc = new Vector2f(ship.getLocation());
                loc.x -= 70f * FastTrig.cos(ship.getFacing() * Math.PI / 180f);
                loc.y -= 70f * FastTrig.sin(ship.getFacing() * Math.PI / 180f);
                if (light != null) {
                    light.setLocation(loc);
                }

                interval2.advance(engine.getElapsedInLastFrame());
                if (interval2.intervalElapsed()) {
                    Vector2f particlePos, particleVel;
                    int numParticlesThisFrame = Math.round(effectLevel * MAX_PARTICLES_PER_FRAME);
                    for (int x = 0; x < numParticlesThisFrame; x++) {
                        particlePos = MathUtils.getRandomPointOnCircumference(ship.getLocation(), PARTICLE_RADIUS);
                        particleVel = Vector2f.sub(ship.getLocation(), particlePos, null);
                        //engine.addSmokeParticle(particlePos, particleVel, PARTICLE_SIZE, PARTICLE_OPACITY,
                            //    1f, PARTICLE_COLOR);
                    }
                }
            }
        } else if (state == State.OUT) {
            completeTeleport(ship, engine);

            if (isActive) {
                engine.spawnExplosion(ship.getLocation(), ship.getVelocity(), EXPLOSION_COLOR, EXPLOSION_VISUAL_RADIUS,
                        0.2f);
                engine.spawnExplosion(ship.getLocation(), ship.getVelocity(), EXPLOSION_COLOR, EXPLOSION_VISUAL_RADIUS
                        / 2f, 0.2f);

                Vector2f loc = new Vector2f(ship.getLocation());
                loc.x -= 70f * FastTrig.cos(ship.getFacing() * Math.PI / 180f);
                loc.y -= 70f * FastTrig.sin(ship.getFacing() * Math.PI / 180f);

                light = new StandardLight(loc, ZERO, ZERO, null);
                light.setIntensity(2f);
                light.setSize(EXPLOSION_VISUAL_RADIUS * 3f);
                light.setColor(EXPLOSION_COLOR);
                light.fadeOut(2.35f);
                LightShader.addLight(light);

                final WaveDistortion wave = new WaveDistortion();
                wave.setLocation(loc);
                wave.setSize(1200.0f);
                wave.setIntensity(85.0f);
                wave.fadeInSize(1.2f);
                wave.fadeOutIntensity(0.9f);
                wave.setSize(262.5f);
                DistortionShader.addDistortion(wave);

                final StandardLight light = new StandardLight();
                light.setLocation(loc);
                light.setIntensity(0.35f);
                light.setSize(950.0f);
                light.fadeOut(1.0f);
                LightShader.addLight(light);

                novaLocation = loc;
                novaTime = 0f;
                engine.addHitParticle(loc, ZERO, 500f, 1f, 0.3f, EXPLOSION_COLOR);
                engine.spawnExplosion(loc, ZERO, EXPLOSION_COLOR, 1000f, 0.09f);
                Global.getSoundPlayer().playSound("xdp_phasetunnelerblast", 1f, 2f, loc, ZERO);

                try {
                    if (sound != null) {
                        sound.setLocation(ship.getLocation().x, ship.getLocation().y);
                    }
                } catch (Exception ex) {
                    Global.getSoundPlayer().playSound(CHARGEUP_SOUND, 2f, 2f, ship.getLocation(), ship.getVelocity());
                }


                applyExplosionEffects(ship, engine);
                ship.getFluxTracker().decreaseFlux(ship.getMaxFlux() / 4);

                isActive = false;
            }
        }

        if (Global.getCombatEngine().isPaused()) {
            return;
        }

        //Ensures we have a ship
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

        //We have different effects depending on where exactly we are in the activation sequence
        SystemStateTracker stateTracker = new SystemStateTracker();
        stateTracker.calculateState(state, effectLevel);
        if (stateTracker.systemState == SystemState.OFF) {
            // Handle our recast cooldown tracking
            systemReactivated = false;
            if (shouldReduceCooldown) {
                shouldReduceCooldown = false;
                bankedCooldown = Math.max(0f, (ship.getSystem().getCooldownRemaining()-RECAST_COOLDOWN));
                ship.getSystem().setCooldownRemaining(RECAST_COOLDOWN);
            }
            bankedCooldown -= Global.getCombatEngine().getElapsedInLastFrame();
            if (bankedCooldown < 0f) { bankedCooldown = 0f; }

            // Can we no longer reactivate? In that case, apply the banked cooldown (if we have any)
            if (!isInReactivationSpan() && bankedCooldown > 0f) {
                float intendedCooldown = Math.max(0f, Math.min(ship.getSystem().getCooldown(), ship.getSystem().getCooldownRemaining()+bankedCooldown));
                ship.getSystem().setCooldownRemaining(intendedCooldown);
                bankedCooldown = 0f;
            }

            // Run unapply
            unapply(stats, id);
        } else {
            if (stateTracker.systemState == SystemState.PHASE_IN) {
                turningWiggleFrames = 3;
                lockedFacing = null;

                // We're in the "in" state, so if we can still reactivate, this is a reactivation
                if (isInReactivationSpan()) {
                    systemReactivated = true;
                }
            }

            //Prevent weapon fire and shield use until the phase is entirely over
            //Also (hopefully?) prevent prefire by setting our collision class to NONE
            ship.blockCommandForOneFrame(ShipCommand.FIRE);
            ship.blockCommandForOneFrame(ShipCommand.VENT_FLUX);
            ship.blockCommandForOneFrame(ShipCommand.USE_SELECTED_GROUP);
            ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
            ship.setHoldFireOneFrame(true);
            ship.setCollisionClass(CollisionClass.NONE);

            //Keep adjusting our facing, it's been proven unstable. Only do this on the out-path
            if (state.equals(State.OUT)) {
                if (turningWiggleFrames > 0) {
                    facingAdjustment(ship);
                    turningWiggleFrames--;
                }
            }
            handleNovaEffects(ship, engine);
        }

        if (stateTracker.systemState == SystemState.PHASE_IN || stateTracker.systemState == SystemState.PHASE_OUT) {
            //Phase : if we haven't yet spawned our fade-in glow we spawn that (should only trigger on the first frame really)
            if (stateTracker.systemState == SystemState.PHASE_IN) {
                if (!hasSpawnedFadeInGlow) {
                    hasSpawnedFadeInGlow = true;
                //    SpriteAPI sprite1 = Global.getSettings().getSprite("ttc_combat", GLOW_1_SPRITE_ID);
                   // SpriteAPI sprite2 = Global.getSettings().getSprite("ttc_combat", GLOW_2_SPRITE_ID);
                    //MagicRender.objectspace(sprite1, ship, new Vector2f(0f, 0f), new Vector2f(0f, 0f),
                     //       new Vector2f(sprite1.getWidth(), sprite1.getHeight()), new Vector2f(0f, 0f),
                         //   180f, 0f, true, GLOW_1_COLOR, true,
                         //   0f, 0f, 0f, 0f, 0f, PHASE_TIME, 0f,
                          //  DEEPPHASE_FADE_IN_TIME, true, CombatEngineLayers.ABOVE_SHIPS_LAYER);
                   // MagicRender.objectspace(sprite2, ship, new Vector2f(0f, 0f), new Vector2f(0f, 0f),
                   //         new Vector2f(sprite2.getWidth(), sprite2.getHeight()), new Vector2f(0f, 0f),
                          //  180f, 0f, true, GLOW_2_COLOR, true,
                          //  0f, 0f, 0f, 0f, 0f, PHASE_TIME, 0f,
                           // DEEPPHASE_FADE_IN_TIME, true, CombatEngineLayers.ABOVE_SHIPS_LAYER);
                }

                //And, if in the "in" state, handle ripples
                float progress = stateTracker.timeInCurrentState / (PHASE_TIME + DEEPPHASE_FADE_IN_TIME);
                spawnRipples(ship, progress);
            }

            //And we also handle phase both for in and out phase
            handlePhase(ship, stateTracker.systemState.equals(SystemState.PHASE_IN) ? (stateTracker.timeInCurrentState / PHASE_TIME) : 1f - (stateTracker.timeInCurrentState / PHASE_TIME));
        } else if (stateTracker.systemState == SystemState.DEEPPHASE_IN) {
            if (!hasPlayedInSound) {
                hasPlayedInSound = true;
                if (IN_SOUND != null) {
                    Global.getSoundPlayer().playSound(IN_SOUND, 1f, 1f, ship.getLocation(), new Vector2f(0f, 0f));
                }
            }
            float progress = stateTracker.timeInCurrentState / DEEPPHASE_FADE_IN_TIME;
            phase(ship, Misc.interpolate(PHASE_OPACITY, 0f, progress));
            float rippleProgress = (PHASE_TIME + stateTracker.timeInCurrentState) / (PHASE_TIME + DEEPPHASE_FADE_IN_TIME);
            spawnRipples(ship, rippleProgress);
        } else if (stateTracker.systemState == SystemState.DEEPPHASE) {
            phase(ship, 0f);
        } else if (stateTracker.systemState == SystemState.DEEPPHASE_OUT) {
            float progress = stateTracker.timeInCurrentState / DEEPPHASE_FADE_OUT_TIME;
            //Deepphase : out. Spawn our fade-out glow if we haven't yet
            //  Also add our buffs here while we're at it
            //  NEW: also also adjust our turning to better face targets
            if (!hasSpawnedFadeOutGlow) {
                hasSpawnedFadeOutGlow = true;
                //SpriteAPI sprite1 = Global.getSettings().getSprite("ttc_combat", GLOW_1_SPRITE_ID);
                //SpriteAPI sprite2 = Global.getSettings().getSprite("ttc_combat", GLOW_2_SPRITE_ID);
               // MagicRender.objectspace(sprite1, ship, new Vector2f(0f, 0f), new Vector2f(0f, 0f),
               //         new Vector2f(sprite1.getWidth(), sprite1.getHeight()), new Vector2f(0f, 0f),
               //         180f, 0f, true, GLOW_1_COLOR, true,
               //         0f, 0f, 0f, 0f, 0f, DEEPPHASE_FADE_OUT_TIME, 0f,
               //         PHASE_TIME, true, CombatEngineLayers.ABOVE_SHIPS_LAYER);
               // MagicRender.objectspace(sprite2, ship, new Vector2f(0f, 0f), new Vector2f(0f, 0f),
                //        new Vector2f(sprite2.getWidth(), sprite2.getHeight()), new Vector2f(0f, 0f),
                //        180f, 0f, true, GLOW_2_COLOR, true,
                //        0f, 0f, 0f, 0f, 0f, DEEPPHASE_FADE_OUT_TIME, 0f,
                //        PHASE_TIME, true, CombatEngineLayers.ABOVE_SHIPS_LAYER);
                if (!systemReactivated) {
                    buffActive = true;
                    buffLevel = 1f;
                    shouldReduceCooldown = true;
                    Global.getCombatEngine().addPlugin(new SaviorBuff(id, ship, DEEPPHASE_FADE_OUT_TIME + PHASE_TIME));
                }
            }

            //Also start returning from deep-phase
            if (!hasPlayedOutSound) {
                hasPlayedOutSound = true;
                if (OUT_SOUND != null) {
                    Global.getSoundPlayer().playSound(OUT_SOUND, 1f, 1f, ship.getLocation(), new Vector2f(0f, 0f));
                }
            }
            phase(ship, Misc.interpolate(0f, PHASE_OPACITY, progress));
            spawnRipples(ship, progress);
            pushFromNearbyShips(ship);
        }
    }

    private void completeTeleport(ShipAPI ship, CombatEngineAPI engine) {

        if (teleportInProgress) {

            if (teleportTargetLocation != null) {
                ship.getLocation().set(teleportTargetLocation);
            }


            teleportInProgress = false;
            teleportStartLocation = null;
            teleportTargetLocation = null;
            teleportVelocity = null;
            teleportProgress = 0f;


            Object customData = ship.getCustomData();
            if (customData instanceof Map) {
                Map<String, Object> dataMap = (Map<String, Object>) customData;
                dataMap.remove("xdp_teleport_data");
            }
        }
    }

    private void createTeleportTrail(ShipAPI ship, CombatEngineAPI engine, float intensity) {

        Vector2f trailPos = MathUtils.getPointOnCircumference(
                ship.getLocation(),
                -ship.getCollisionRadius() * 0.8f,
                ship.getFacing() + 180f
        );


      //  engine.addSmoothParticle(
       //         trailPos,
        //        new Vector2f(),
        //        PARTICLE_SIZE * 2f * intensity,
        //        0.8f * intensity,
        //        0.5f,
            //    PARTICLE_COLOR
       // );


        for (int i = 0; i < 3; i++) {
            float angleOffset = (float) Math.random() * 60f - 30f;
            Vector2f sidePos = MathUtils.getPointOnCircumference(
                    trailPos,
                    ship.getCollisionRadius() * 0.3f,
                    ship.getFacing() + 90f + angleOffset
            );

           // engine.addSmoothParticle(
              //      sidePos,
              //      new Vector2f(),
              //      PARTICLE_SIZE * intensity,
                //    0.6f * intensity,
                  //  0.3f,
                    //PARTICLE_COLOR
            //);
        }
    }


    private void handleTeleportMovement(ShipAPI ship, CombatEngineAPI engine, float effectLevel) {

        if (!teleportInProgress) {
            Object customData = ship.getCustomData();
            if (customData instanceof Map) {
                Map<String, Object> dataMap = (Map<String, Object>) customData;
                Object teleportData = dataMap.get("xdp_teleport_data");

                if (teleportData instanceof Map) {
                    Map<String, Object> teleportMap = (Map<String, Object>) teleportData;
                    Float targetX = (Float) teleportMap.get("targetX");
                    Float targetY = (Float) teleportMap.get("targetY");

                    if (targetX != null && targetY != null) {
                        teleportTargetLocation = new Vector2f(targetX, targetY);
                        teleportStartLocation = new Vector2f(ship.getLocation());


                        float distance = MathUtils.getDistance(teleportStartLocation, teleportTargetLocation);

                        distance = MathUtils.clamp(distance, MIN_TELEPORT_DISTANCE, MAX_TELEPORT_DISTANCE);


                        teleportVelocity = Vector2f.sub(teleportTargetLocation, teleportStartLocation, null);
                        if (teleportVelocity.length() > 0) {
                            teleportVelocity.normalise();
                            teleportVelocity.scale(TELEPORT_SPEED);
                        }

                        teleportProgress = 0f;
                        teleportInProgress = true;
                        teleportStartTime = engine.getTotalElapsedTime(false);
                        teleportDuration = distance / TELEPORT_SPEED;


                        dataMap.remove("xdp_teleport_data");

                        {
                            SpriteAPI rippleSprite = Global.getSettings().getSprite("fx", "shield_ring");
                            MagicRender.battlespace(
                                    rippleSprite,
                                    ship.getLocation(),
                                    new Vector2f(0f, 0f),
                                    new Vector2f(50f, 50f),
                                    new Vector2f(RIPPLE_MAX_SIZE, RIPPLE_MAX_SIZE),
                                    ship.getFacing() - 90f,
                                    0f,
                                    RIPPLE_COLOR,
                                    true,
                                    0f,
                                    0.1f,
                                    0.3f,
                                    RIPPLE_DURATION,
                                    0f,
                                    0.1f,
                                    0.2f,
                                    0.5f,
                                    CombatEngineLayers.ABOVE_SHIPS_LAYER
                            );

                            for (int i = 0; i < 25; i++) {
                                Vector2f particlePos = MathUtils.getPointOnCircumference(
                                        ship.getLocation(),
                                        MathUtils.getRandomNumberInRange(0f, ship.getCollisionRadius()),
                                        MathUtils.getRandomNumberInRange(0f, 360f)
                                );
                                Vector2f particleVel = MathUtils.getRandomPointInCircle(new Vector2f(), 50f);

                                final WaveDistortion wave = new WaveDistortion();
                                final Vector2f loc = new Vector2f(ship.getLocation());
                                wave.setLocation(loc);
                                wave.setSize(950.0f);
                                wave.setIntensity(85.0f);
                                wave.fadeInSize(1.2f);
                                wave.fadeOutIntensity(0.9f);
                                wave.setSize(262.5f);
                                DistortionShader.addDistortion(wave);

                                final StandardLight light = new StandardLight();
                                light.setLocation(loc);
                                light.setIntensity(0.35f);
                                light.setSize(950.0f);
                              //  light.setColor(AFTERIMAGE_COLOR);
                                light.fadeOut(1.0f);
                                LightShader.addLight(light);

                                Global.getCombatEngine().addSmoothParticle(
                                        particlePos,
                                        particleVel,
                                        MathUtils.getRandomNumberInRange(5f, 15f),
                                        0.8f,
                                        MathUtils.getRandomNumberInRange(0.5f, 1.5f),
                                        RIPPLE_COLOR
                                );

                            }
                        }
                    }
                }
            }
        }



        if (teleportInProgress) {
            teleportInterval.advance(engine.getElapsedInLastFrame());


            if (teleportInterval.intervalElapsed() && teleportVelocity != null) {

                float progressRatio = teleportProgress;
                float accelerationFactor = 1f;

                if (progressRatio < 0.3f) {

                    accelerationFactor = progressRatio / 0.3f;
                } else if (progressRatio > 0.7f) {

                    accelerationFactor = 1f - ((progressRatio - 0.7f) / 0.3f);
                }


                Vector2f frameVelocity = new Vector2f(teleportVelocity);
                frameVelocity.scale(accelerationFactor * engine.getElapsedInLastFrame());


                Vector2f.add(ship.getLocation(), frameVelocity, ship.getLocation());


                float distanceTraveled = frameVelocity.length();
                float totalDistance = MathUtils.getDistance(teleportStartLocation, teleportTargetLocation);
                teleportProgress = MathUtils.clamp(teleportProgress + (distanceTraveled / totalDistance), 0f, 1f);


                createTeleportTrail(ship, engine, accelerationFactor);


                if (teleportProgress >= 0.95f) {

                    ship.getLocation().set(teleportTargetLocation);
                    teleportProgress = 1f;
                }
            }


            Object customData = ship.getCustomData();
            if (customData instanceof Map) {
                Map<String, Object> dataMap = (Map<String, Object>) customData;
                Object teleportData = dataMap.get("xdp_teleport_data");

                if (teleportData instanceof Map) {
                    Map<String, Object> teleportMap = (Map<String, Object>) teleportData;
                    Float targetFacing = (Float) teleportMap.get("facing");
                    if (targetFacing != null) {
                        float facingDiff = MathUtils.getShortestRotation(ship.getFacing(), targetFacing);
                        ship.setFacing(ship.getFacing() + facingDiff * 0.1f);
                    }
                }
            }
        }
    }


    //For handling "normal" phase-in and phase-out
    private void handlePhase(ShipAPI ship, float progress) {
        phase(ship, 1f - (progress * (1f - PHASE_OPACITY)));
    }

    //For quickly changing the ship's opacity and making it untargetable
    private void phase(ShipAPI ship, float opacity) {
        MagicTrailPlugin.cutTrailsOnEntity(ship);
        ship.setExtraAlphaMult(opacity);
        ship.setApplyExtraAlphaToEngines(true);
    }

    private void applyExplosionEffects(ShipAPI ship, CombatEngineAPI engine) {
        ShipAPI victim;
        Vector2f dir;
        float force, damage, emp, mod;

        for (CombatEntityAPI tmp : XDP_Util.getEntitiesWithinRange(ship.getLocation(), EXPLOSION_PUSH_RADIUS)) {
            if ((tmp == ship) || (tmp == null)) {
                continue;
            }

            mod = 1f - (MathUtils.getDistance(ship, tmp) / EXPLOSION_PUSH_RADIUS);
            force = FORCE_VS_ASTEROID * mod;
            damage = EXPLOSION_DAMAGE_AMOUNT * mod;
            emp = EXPLOSION_EMP_DAMAGE_AMOUNT * mod;

            if (tmp instanceof ShipAPI) {
                victim = (ShipAPI) tmp;

                if (null != victim.getHullSize()) {
                    switch (victim.getHullSize()) {
                        case FIGHTER:
                            force = FORCE_VS_FIGHTER * mod;
                            damage /= DAMAGE_MOD_VS_FIGHTER;
                            break;
                        case FRIGATE:
                            force = FORCE_VS_FRIGATE * mod;
                            damage /= DAMAGE_MOD_VS_FRIGATE;
                            break;
                        case DESTROYER:
                            force = FORCE_VS_DESTROYER * mod;
                            damage /= DAMAGE_MOD_VS_DESTROYER;
                            break;
                        case CRUISER:
                            force = FORCE_VS_CRUISER * mod;
                            damage /= DAMAGE_MOD_VS_CRUISER;
                            break;
                        case CAPITAL_SHIP:
                            force = FORCE_VS_CAPITAL * mod;
                            damage /= DAMAGE_MOD_VS_CAPITAL;
                            break;
                        default:
                            break;
                    }
                }

                if (victim.getOwner() == ship.getOwner()) {
                    damage *= EXPLOSION_DAMAGE_VS_ALLIES_MODIFIER;
                    emp *= EXPLOSION_EMP_VS_ALLIES_MODIFIER;
                    force *= EXPLOSION_FORCE_VS_ALLIES_MODIFIER;
                }

                float shipRadius = XDP_Util.effectiveRadius(victim);

                if (victim.getShield() != null && victim.getShield().isOn() && victim.getShield().isWithinArc(
                        ship.getLocation())) {
                    victim.getFluxTracker().increaseFlux(damage * 2, true);
                } else {
                    for (int x = 0; x < 5; x++) {
                        engine.spawnEmpArc(ship, MathUtils.getRandomPointInCircle(victim.getLocation(),
                                        shipRadius),
                                victim, victim, EXPLOSION_DAMAGE_TYPE, damage / 5,
                                emp / 5, EXPLOSION_PUSH_RADIUS, null, 2f,
                                EXPLOSION_COLOR, EXPLOSION_COLOR);
                    }
                }
            }

            if (tmp instanceof DamagingProjectileAPI) {
                DamagingProjectileAPI proj = (DamagingProjectileAPI) tmp;
                if (proj.getBaseDamageAmount() <= 0) {
                    continue;
                }
            }

            dir = VectorUtils.getDirectionalVector(ship.getLocation(), tmp.getLocation());
            dir.scale(force);

            Vector2f.add(tmp.getVelocity(), dir, tmp.getVelocity());
        }
    }


    //Handles spawning "ripples" of screen distortion
    private void spawnRipples(ShipAPI ship, float progress) {
        float progressAdjusted = progress * 2f;
        if (progress > 0.5f) {
            progressAdjusted = (1f - progress) * 2f;
        }

        progressAdjusted = (float) Math.pow(progressAdjusted, RIPPLE_FUNCTION_VAR);

        //Jitter : a first attempt using after-images
        jitterTimer += Global.getCombatEngine().getElapsedInLastFrame() * ship.getMutableStats().getTimeMult().getModifiedValue() * progressAdjusted;
        while (jitterTimer >= (1f / JITTER_COPIES_PER_SECOND)) {
            jitterTimer -= (1f / JITTER_COPIES_PER_SECOND);
            Vector2f jitterPos = MathUtils.getRandomPointInCircle(new Vector2f(0f, 0f), JITTER_MAX_SIZE*progressAdjusted);
            ship.addAfterimage(new Color(1f, 1f, 1f, JITTER_OPACITY), jitterPos.x, jitterPos.y,
                    0f, 0f, JITTER_EXTRAJITTER,0f, JITTER_DURATION, 0f, false,
                    true, false);

            //SpriteAPI sprite1 = Global.getSettings().getSprite("ttc_combat", GLOW_1_SPRITE_ID);
            //SpriteAPI sprite2 = Global.getSettings().getSprite("ttc_combat", GLOW_2_SPRITE_ID);
            //MagicRender.objectspace(sprite1, ship, jitterPos, new Vector2f(0f, 0f),
              //      new Vector2f(sprite1.getWidth(), sprite1.getHeight()), new Vector2f(0f, 0f), 180f,
                    //0f, true, Misc.interpolateColor(Misc.interpolateColor(GLOW_2_COLOR, Color.black, progress), Color.black, 1f-JITTER_OPACITY),
                    //true,0f, 0f, 0f, 0f, 0f, 0f,
                    //JITTER_DURATION, 0f, true, CombatEngineLayers.ABOVE_SHIPS_LAYER);
            //MagicRender.objectspace(sprite2, ship, jitterPos, new Vector2f(0f, 0f),
              //      new Vector2f(sprite2.getWidth(), sprite2.getHeight()), new Vector2f(0f, 0f), 180f,
                   // 0f, true, Misc.interpolateColor(Misc.interpolateColor(GLOW_2_COLOR, Color.black, progress), Color.black, 1f-JITTER_OPACITY),
                    //true,0f, 0f, 0f, 0f, 0f, 0f,
                    //JITTER_DURATION, 0f, true, CombatEngineLayers.ABOVE_SHIPS_LAYER);
        }

        //Actual ripple
        rippleTimer += Global.getCombatEngine().getElapsedInLastFrame() * ship.getMutableStats().getTimeMult().getModifiedValue() * progressAdjusted;
        while (rippleTimer >= (1f / MAX_RIPPLES_PER_SECOND)) {
            rippleTimer -= (1f / MAX_RIPPLES_PER_SECOND);
            RippleDistortion ripple = new RippleDistortion(new Vector2f(ship.getLocation()), Misc.ZERO);
            ripple.setCurrentFrame(0);
            ripple.setIntensity(RIPPLE_DEPTH);

            //Ensure the effect fades out properly
            ripple.setLifetime(RIPPLE_DURATION);
            ripple.fadeOutIntensity(RIPPLE_DURATION);

            //The ripple need needs to grow over time, or there's not much of a ripple!
            //Also adds animation
            ripple.setSize(RIPPLE_MAX_SIZE);
            ripple.fadeInSize(RIPPLE_DURATION);
            ripple.setFrameRate(120f / RIPPLE_DURATION);

            //And finally ensure the distortion is tracked
            DistortionShader.addDistortion(ripple);
        }
    }

    //"Pushes" the ships from any nearby ships, so that it can avoid collisions
    private void pushFromNearbyShips(ShipAPI ship) {
        float amount = Global.getCombatEngine().getElapsedInLastFrame() * ship.getMutableStats().getTimeMult().getModifiedValue();
        List<ShipAPI> otherShips = CombatUtils.getShipsWithinRange(ship.getLocation(), ship.getCollisionRadius());

        Vector2f moveSpeed = new Vector2f(0f, 0f);
        for (ShipAPI other : otherShips) {
            if (ship == other || other.getCollisionClass() == CollisionClass.NONE || other.getHullSize() == ShipAPI.HullSize.FIGHTER) {
                continue;
            }

            float moreAccurateDistance = MathUtils.getDistance(other.getLocation(), ship.getLocation())
                    - other.getCollisionRadius()
                    - Misc.getTargetingRadius(other.getLocation(), ship, false);
            if (moreAccurateDistance < 0f) {
                //We are overlapping : apply a force depending on overlap amount
                Vector2f forceToApply = VectorUtils.getDirectionalVector(other.getLocation(), ship.getLocation());
                forceToApply.x *= (moreAccurateDistance + 30f) * -1f * BASE_PUSH_FORCE;
                forceToApply.y *= (moreAccurateDistance + 30f) * -1f * BASE_PUSH_FORCE;
                moveSpeed = Vector2f.add(moveSpeed, forceToApply, new Vector2f(0f, 0f));
            }
        }

        ship.getLocation().x += moveSpeed.x * amount;
        ship.getLocation().y += moveSpeed.y * amount;
    }

    /**
     * Adjusts the ship's facing to improve on vanilla's behaviour
     */
    private void facingAdjustment(ShipAPI ship) {
        //If we don't have a locked facing, we need to get one
        if (lockedFacing == null) {
            //Only adjust if our ship target is not null
            //      Note that we MIGHT have a special "locked in" target from our AI as well: in that case, use that
            ShipAPI target = ship.getShipTarget();
            if (Global.getCombatEngine().getCustomData().get("LOA_SAVIOR_AI_TARGET_KEY"+ship.getId()) instanceof ShipAPI) {
                //Only count the locked target if we're not player controlled
                if (Global.getCombatEngine().getPlayerShip() != ship || Global.getCombatEngine().isUIAutopilotOn()) {
                    target = (ShipAPI) Global.getCombatEngine().getCustomData().get("LOA_SAVIOR_AI_TARGET_KEY"+ship.getId());
                }
                Global.getCombatEngine().getCustomData().remove("LOA_SAVIOR_AI_TARGET_KEY"+ship.getId());
            }
            if (target == null) {
                return;
            }
            lockedFacing = VectorUtils.getAngle(ship.getLocation(), target.getLocation());
        }

        ship.setFacing(lockedFacing);
    }


    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

        if (ship.getSystem().getEffectLevel() <= 0f) {
            hasPlayedInSound = false;
            hasPlayedOutSound = false;
            hasSpawnedFadeInGlow = false;
            hasSpawnedFadeOutGlow = false;
            ship.setExtraAlphaMult(1f);
            ship.setCollisionClass(CollisionClass.SHIP);
        }
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        SystemStateTracker stateTracker = new SystemStateTracker();
        stateTracker.calculateState(state, effectLevel);
        if (state.equals(State.IN)) {
            if (index == 0) {
                return new StatusData("Entering Deep Phase", false);
            }
        } else if (state.equals(State.OUT)) {
            if (index == 0) {
                return new StatusData("Exiting Deep Phase", false);
            }
        }
        return null;
    }

    //Keeps track of the system's state
    private static class SystemStateTracker {
        float timeInCurrentState;
        SystemState systemState;

        void calculateState(State state, float effectLevel) {
            if (state.equals(State.IN)) {
                float timePassed = effectLevel * CHARGEUP;
                if (timePassed <= PHASE_TIME) {
                    systemState = SystemState.PHASE_IN;
                    timeInCurrentState = timePassed;
                } else {
                    timePassed -= PHASE_TIME;
                    if (timePassed <= DEEPPHASE_FADE_IN_TIME) {
                        systemState = SystemState.DEEPPHASE_IN;
                        timeInCurrentState = timePassed;
                    } else {
                        timePassed -= DEEPPHASE_FADE_IN_TIME;
                        if (timePassed <= DISAPPEARANCE_TIME) {
                            systemState = SystemState.DEEPPHASE;
                            timeInCurrentState = timePassed;
                        } else {
                            systemState = SystemState.OFF; // Failsafe, should never happen
                        }
                    }
                }
            } else if (state.equals(State.OUT)) {
                float timePassed = (1f - effectLevel) * CHARGEDOWN;
                if (timePassed <= DEEPPHASE_FADE_OUT_TIME) {
                    systemState = SystemState.DEEPPHASE_OUT;
                    timeInCurrentState = timePassed;
                } else {
                    timePassed -= DEEPPHASE_FADE_OUT_TIME;
                    if (timePassed <= PHASE_TIME) {
                        systemState = SystemState.PHASE_OUT;
                        timeInCurrentState = timePassed;
                    } else {
                        systemState = SystemState.OFF;
                    }
                }
            } else {
                systemState = SystemState.OFF;
            }
        }
    }

    enum SystemState {
        PHASE_IN,
        PHASE_OUT,
        DEEPPHASE_IN,
        DEEPPHASE,
        DEEPPHASE_OUT,
        OFF
    }

    /**
     * Class for managing the status buffs applied by the system
     */
    private class SaviorBuff extends BaseEveryFrameCombatPlugin {
        private final String id;
        private final ShipAPI ship;
        private final float silentDuration;

        private float lifetime = 0f;

        SaviorBuff(String id, ShipAPI ship, float silentDuration) {
            this.id = id;
            this.ship = ship;
            this.silentDuration = silentDuration;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            Global.getCombatEngine().getCustomData().put(BUFF_ACTIVE_KEY+ship.getId(), true); // Needed so the AI knows what's going on
            if (Global.getCombatEngine().isPaused()) {
                return;
            }
            lifetime += amount * ship.getMutableStats().getTimeMult().getModifiedValue();
            if (lifetime < silentDuration) {
                return;
            }
            float remainingDuration = (BUFF_TIME_FADE + BUFF_TIME_FULL + silentDuration) - lifetime;

            //If the ship is not overfluxed, tell AI to not vent if the buff still applies
            if (ship.getFluxLevel() < SAFE_FLUX_LEVEL) {
                ship.getAIFlags().setFlag(ShipwideAIFlags.AIFlags.DO_NOT_VENT, 0.2f);
            } else {
                ship.getAIFlags().unsetFlag(ShipwideAIFlags.AIFlags.DO_NOT_VENT);
            }

            // Calculate current buff "level" (useful for recasting and jump range calculations, as well as SFX)
            buffLevel = 1f-((lifetime-silentDuration)/(BUFF_TIME_FADE+BUFF_TIME_FULL));

            // Modify jump range depending on the buff level
            float rangeReductionLevel = 0f;
            if (buffLevel < RECAST_MAX_RANGE_LIMIT && buffLevel >= RECAST_LIMIT) {
                rangeReductionLevel = Math.min(1f, Math.max(0f, 1f - ((buffLevel-RECAST_LIMIT) / (RECAST_MAX_RANGE_LIMIT-RECAST_LIMIT))));
            }
            if (rangeReductionLevel > 0f) {
                ship.getMutableStats().getSystemRangeBonus().modifyMult(id, 1f-(RECAST_MAX_RANGE_PENALTY_PERCENT*rangeReductionLevel/100f));
            } else {
                ship.getMutableStats().getSystemRangeBonus().unmodify(id);
            }

            //Remove after the lifetime OR if we overload/vent OR if we trigger a re-activation of the system
            if (remainingDuration <= 0f
                    || ship.getFluxTracker().isOverloadedOrVenting()
                    || systemReactivated) {
                Global.getCombatEngine().getCustomData().put(BUFF_ACTIVE_KEY+ship.getId(), false);
                ship.getAIFlags().unsetFlag(ShipwideAIFlags.AIFlags.DO_NOT_VENT);
                unapplyStats(ship, id);
                EnumSet<WeaponType> weaponTypesToGlow = EnumSet.allOf(WeaponType.class);
                ship.setWeaponGlow(0f, WEAPON_GLOW_COLOR, weaponTypesToGlow);
                buffActive = false;
                buffLevel = 0f;
                Global.getCombatEngine().removePlugin(this);
                return;
            }

            // Apply the stat changes with appropriate falloff
            float falloffProgress = 1f - (Math.max(0f, lifetime - silentDuration - BUFF_TIME_FULL) / BUFF_TIME_FADE);
            applyStats(falloffProgress, ship, id);

            // Maintain a status on the player ship
            if (ship.equals(Global.getCombatEngine().getPlayerShip())) {
                if (isInReactivationSpan()) {
                    if (rangeReductionLevel <= 0f) {
                        Global.getCombatEngine().maintainStatusForPlayerShip(id + "flux", "graphics/icons/hullsys/displacer.png",
                                "Reality Shunt", "Secondary jump ready", false);
                    } else {
                        Global.getCombatEngine().maintainStatusForPlayerShip(id + "flux", "graphics/icons/hullsys/displacer.png",
                                "Reality Shunt", "Secondary jump at " + Math.round((1f-rangeReductionLevel)*100f) + "% stability", false);
                    }
                }
                Global.getCombatEngine().maintainStatusForPlayerShip(id + "damage", "graphics/icons/hullsys/displacer.png",
                        "Reality Shunt", "Performance improved: "+(int)remainingDuration+" seconds remaining", false);
            }

            // Play a looping sound to indicate the buff is active
            if (BUFF_SOUND != null) {
                float vol = Misc.interpolate(BUFF_SOUND_VOLUME_LOW, BUFF_SOUND_VOLUME_HIGH, falloffProgress);
                float pitch = Misc.interpolate(BUFF_SOUND_PITCH_LOW, BUFF_SOUND_PITCH_HIGH, falloffProgress);
                Global.getSoundPlayer().playLoop(BUFF_SOUND, ship, pitch, vol, ship.getLocation(), ship.getVelocity());
            }

            // Add a bit of weapon glow, depending on our current falloff
            EnumSet<WeaponType> weaponTypesToGlow = EnumSet.allOf(WeaponType.class);
            ship.setWeaponGlow(falloffProgress, WEAPON_GLOW_COLOR, weaponTypesToGlow);
        }
    }

    private void applyStats(float falloffProgress, ShipAPI ship, String id) {
        // Calculate the actual buff values
        float rofMult = Misc.interpolate(1f, BUFF_ROF_MULT, falloffProgress);
        float fluxcostMult = Misc.interpolate(1f, BUFF_WEAPON_FLUX_MULT, falloffProgress);
        float beamdamageMult = Misc.interpolate(1f, BUFF_BEAM_DAMAGE_MULT, falloffProgress);
        float weprangeMult = Misc.interpolate(1f, BUFF_WEAPON_RANGE_MULT, falloffProgress);
        float shieldspeedMult = Misc.interpolate(1f, BUFF_SHIELD_TURNSPEED_MULT, falloffProgress);
        float shieldraiseMult = Misc.interpolate(1f, BUFF_SHIELD_RAISE_SPEED_MULT, falloffProgress);
        float mobilityMult = Misc.interpolate(1f, BUFF_SHIP_MOBILITY_MULT, falloffProgress);

        // Apply all our buff bonuses
        ship.getMutableStats().getEnergyRoFMult().modifyMult(id, rofMult);
        ship.getMutableStats().getBallisticRoFMult().modifyMult(id, rofMult);
        ship.getMutableStats().getMissileRoFMult().modifyMult(id, rofMult);
        ship.getMutableStats().getMissileWeaponFluxCostMod().modifyMult(id, fluxcostMult);
        ship.getMutableStats().getEnergyWeaponFluxCostMod().modifyMult(id, fluxcostMult);
        ship.getMutableStats().getBallisticWeaponFluxCostMod().modifyMult(id, fluxcostMult);
        ship.getMutableStats().getBeamWeaponDamageMult().modifyMult(id, beamdamageMult);
        ship.getMutableStats().getEnergyWeaponRangeBonus().modifyMult(id, weprangeMult);
        ship.getMutableStats().getBallisticWeaponRangeBonus().modifyMult(id, weprangeMult);
        ship.getMutableStats().getShieldTurnRateMult().modifyMult(id, shieldspeedMult);
        ship.getMutableStats().getShieldUnfoldRateMult().modifyMult(id, shieldraiseMult);
        ship.getMutableStats().getMaxTurnRate().modifyMult(id, mobilityMult);
        ship.getMutableStats().getTurnAcceleration().modifyMult(id, mobilityMult);
    }

    private void handleNovaEffects(ShipAPI ship, CombatEngineAPI engine) {
        if (novaTime >= 0f) {
            novaTime += engine.getElapsedInLastFrame() * engine.getTimeMult().getModifiedValue();
            interval.advance(engine.getElapsedInLastFrame() * engine.getTimeMult().getModifiedValue());

            if (interval.intervalElapsed()) {
                float offset = (float) Math.random() * 360f;
                for (int i = 0; i < (int) (novaTime * 5f) + 4; i++) {
                    float angle = i / ((novaTime * 5f) + 4f) * 360f + offset;
                    if (angle >= 360f) {
                        angle -= 360f;
                    }
                    float distance = (float) Math.random() * 100f + novaTime * 1500f;
                    Vector2f point1 = MathUtils.getPointOnCircumference(novaLocation, distance, angle);
                    Vector2f point2 = MathUtils.getPointOnCircumference(novaLocation, distance, angle + 360f
                            / ((novaTime * 5f) + 4f)
                            * ((float) Math.random()
                            + 1f));
                    engine.spawnEmpArc(ship, point1, new SimpleEntity(point1),
                            new SimpleEntity(point2), DamageType.ENERGY, 0f, 0f, 10000f,
                            null, 40f, EXPLOSION_COLOR, EXPLOSION_COLOR);
                }

                List<ShipAPI> targets = XDP_Util.getShipsWithinRange(novaLocation, novaTime * 1500f + 25f);
                for (ShipAPI target : targets) {
                    if (target == ship) {
                        continue;
                    }

                    float dist = MathUtils.getDistance(novaLocation, target.getLocation());
                    float dist2 = novaTime * 1500f + 50f;
                    if (dist - target.getCollisionRadius() <= dist2 && dist + target.getCollisionRadius() >= dist2) {
                        if (target.getOwner() == ship.getOwner()) {
                            engine.applyDamage(target, target.getLocation(), 300f,
                                    DamageType.ENERGY, 150f, false, false, ship, false);
                        } else {
                            engine.applyDamage(target, target.getLocation(), 3000f,
                                    DamageType.ENERGY, 1500f, false, false, ship, false);
                        }
                    }
                }
            }

            if (novaTime >= 1f) {
                novaTime = -1f;
            }
        }
    }

    private void unapplyStats(ShipAPI ship, String id) {
        // Unapply every bonus we applied in applyStats()
        ship.getMutableStats().getEnergyRoFMult().unmodify(id);
        ship.getMutableStats().getBallisticRoFMult().unmodify(id);
        ship.getMutableStats().getMissileRoFMult().unmodify(id);
        ship.getMutableStats().getMissileWeaponFluxCostMod().unmodify(id);
        ship.getMutableStats().getEnergyWeaponFluxCostMod().unmodify(id);
        ship.getMutableStats().getBallisticWeaponFluxCostMod().unmodify(id);
        ship.getMutableStats().getBeamWeaponDamageMult().unmodify(id);
        ship.getMutableStats().getEnergyWeaponRangeBonus().unmodify(id);
        ship.getMutableStats().getBallisticWeaponRangeBonus().unmodify(id);
        ship.getMutableStats().getShieldTurnRateMult().unmodify(id);
        ship.getMutableStats().getShieldUnfoldRateMult().unmodify(id);
        ship.getMutableStats().getMaxTurnRate().unmodify(id);
        ship.getMutableStats().getTurnAcceleration().unmodify(id);
    }
}