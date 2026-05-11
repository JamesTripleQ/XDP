package neon.xdp.data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.List;

public class XDP_domain_era extends BaseHullMod {

	//buffs for enigma hulls

	//code taken from LOST_SECTOR, all credit goes to original authors

	public static final float SPEED_PENALTY = 5f;
	public static final float FLUX_BONUS = 15f;
	public static final float MORE_SUPPLIES = 2.5f;
	public static final float DEGRADE_INCREASE_PERCENT = 33f;
	public static final float EMP_REDUCTION = 25f;
	public static final float REPAIR_BONUS = 25f;
	public static final float COST_REDUCTION_L = 4;
	public static final float COST_REDUCTION_M = 2;
	public static final float COST_REDUCTION_S = 1;
	public static final float FUCK_CAPITALS = 20f;
	public static final float CR_PENALTY = 15f;

	public static float SHIELD_BONUS_TURN = 100f;
	public static float SHIELD_BONUS_UNFOLD = 100f;

	public static float DMOD_EFFECT_MULT = 0.3f;
	public static float DMOD_AVOID_CHANCE = 35f;

	public static float MISSILE_DAMAGE_REDUCTION = 0.5f;

	public static float MISSILE_ROF_BONUS = 2f;

	public static float NON_SHIELD_FLUX_LEVEL = 50f;

	public static float VENT_RATE_BONUS = 25f;

	public static float HARD_FLUX_DISS_BONUS = 2.5f;


	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {

		stats.getDynamic().getMod(Stats.DMOD_ACQUIRE_PROB_MOD).modifyMult(id, (1f - DMOD_AVOID_CHANCE * 0.01f));
		stats.getDynamic().getStat(Stats.DMOD_EFFECT_MULT).modifyMult(id, DMOD_EFFECT_MULT);

		stats.getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyFlat(id, 1000f);
		stats.getAcceleration().modifyMult(id, 1f - (2f * SPEED_PENALTY) * 0.01f);
		stats.getDeceleration().modifyMult(id, 1f - (2f * SPEED_PENALTY) * 0.01f);
		stats.getTurnAcceleration().modifyMult(id, 1f - (2f * SPEED_PENALTY) * 0.01f);
		stats.getMaxTurnRate().modifyMult(id, 1f - (2f * SPEED_PENALTY) * 0.01f);
		stats.getMaxSpeed().modifyMult(id, 1f - SPEED_PENALTY * 0.01f);
		stats.getMaxSpeed().modifyFlat(id, -SPEED_PENALTY);

		stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f - FLUX_BONUS * 0.01f);
		stats.getEnergyWeaponFluxCostMod().modifyMult(id, 1f - FLUX_BONUS * 0.01f);

		stats.getCombatEngineRepairTimeMult().modifyMult(id, 1f - REPAIR_BONUS * 0.01f);
		stats.getCombatWeaponRepairTimeMult().modifyMult(id, 1f - REPAIR_BONUS * 0.01f);

		stats.getCRLossPerSecondPercent().modifyPercent(id, DEGRADE_INCREASE_PERCENT);
		stats.getSuppliesPerMonth().modifyMult(id, MORE_SUPPLIES);

		stats.getEmpDamageTakenMult().modifyMult(id, 1f - EMP_REDUCTION * 0.01f);

		stats.getDamageToCapital().modifyMult(id, 1f + FUCK_CAPITALS * 0.01f);

		stats.getDynamic().getMod(Stats.LARGE_BALLISTIC_MOD).modifyFlat(id, -COST_REDUCTION_L);
		stats.getDynamic().getMod(Stats.LARGE_MISSILE_MOD).modifyFlat(id, -COST_REDUCTION_L);
		stats.getDynamic().getMod(Stats.LARGE_ENERGY_MOD).modifyFlat(id, -COST_REDUCTION_L);
		stats.getDynamic().getMod(Stats.MEDIUM_BALLISTIC_MOD).modifyFlat(id, -COST_REDUCTION_M);
		stats.getDynamic().getMod(Stats.MEDIUM_MISSILE_MOD).modifyFlat(id, -COST_REDUCTION_M);
		stats.getDynamic().getMod(Stats.MEDIUM_ENERGY_MOD).modifyFlat(id, -COST_REDUCTION_M);
		stats.getDynamic().getMod(Stats.SMALL_BALLISTIC_MOD).modifyFlat(id, -COST_REDUCTION_S);
		stats.getDynamic().getMod(Stats.SMALL_MISSILE_MOD).modifyFlat(id, -COST_REDUCTION_S);
		stats.getDynamic().getMod(Stats.SMALL_ENERGY_MOD).modifyFlat(id, -COST_REDUCTION_S);

		//stats.getVentRateMult().modifyPercent(id, VENT_RATE_BONUS);

		stats.getMissileRoFMult().modifyMult(id, MISSILE_ROF_BONUS);

		float MissileNerf = MISSILE_DAMAGE_REDUCTION;
		stats.getMissileWeaponDamageMult().modifyPercent(id, MissileNerf);

		stats.getShieldTurnRateMult().modifyPercent(id, SHIELD_BONUS_TURN);
		stats.getShieldUnfoldRateMult().modifyPercent(id, SHIELD_BONUS_UNFOLD);

		if (stats.getVariant().hasHullMod(HullMods.SAFETYOVERRIDES)) {
			stats.getMaxCombatReadiness().modifyFlat(id, -CR_PENALTY * 0.01f);
		}
	}

	@Override
	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {

	}

	@Override
	public void advanceInCombat(ShipAPI ship, float amount) {
		//MutableShipStatsAPI stats = ship.getMutableStats();

		ship.getMutableStats().getFluxDissipation().modifyPercent("superderelict_flux", (ship.getHardFluxLevel() * 100f) * HARD_FLUX_DISS_BONUS);

		if(ship.equals(Global.getCombatEngine().getPlayerShip())) {
			Global.getCombatEngine().maintainStatusForPlayerShip(this.getClass(), Global.getSettings().getSpriteName("ui", "icon_op"), "Flux Diss Bonus", Misc.getRoundedValue(ship.getMutableStats().getFluxDissipation().getModifiedValue()), false);
		}

		//float fluxLevel = ship.getHardFluxLevel();


		//if (ship.getShield() == null && !ship.getHullSpec().isPhase() &&
		//(ship.getPhaseCloak() == null || !ship.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.PHASE))) {
		//	fluxLevel = NON_SHIELD_FLUX_LEVEL * 0.01f;
		//}

	}


	@Override
	public boolean affectsOPCosts() {
		return true;
	}

	@Override
	public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
		tooltip.addPara("This vessel is, at glance, a member of the %s. There are, however, glaring differences.", 5f, Color.ORANGE, "Domain Explorarium");

		tooltip.addPara("Negative effects of D-Mods are reduced by %s, and the vessel is less likely to acquire them in combat.", 5f, Color.ORANGE, "30%");

		tooltip.addPara("Weapon costs are reduced by %s/%s/%s, in order of Large, Medium and Small, respectively.", 5f, Color.ORANGE, "4", "2", "1");

		tooltip.addPara("Missile fire rate is increased by %s, and missile damage is reduced by %s.", 5f, Color.ORANGE, "double", "50%");

		tooltip.addPara("%s when this ship falls below %s of it's hull level, it will engage a temporay emergency repair protocol. Max speed, energy weapon damage, shield speed and raise rate are all augmented during this period. This system cannot activate if the vessel is %s.", 5f, Color.ORANGE, "Emergency Repair:", "33%", "overloaded");
	}
}

