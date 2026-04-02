package neon.xdp.data.shipsystems.ai;

import java.util.ArrayList;
import java.util.List;

import neon.xdp.data.shipsystems.xdp_EnergyLashSystemScript;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.fs.starfarer.api.impl.combat.threat.EnergyLashActivatedSystem;

public class xdp_EnergyLashSystemAI implements ShipSystemAIScript {

	protected ShipAPI ship;
	protected CombatEngineAPI engine;
	protected ShipwideAIFlags flags;
	protected ShipSystemAPI system;
	protected xdp_EnergyLashSystemScript systemScript; // Changed type to the main script

	protected IntervalUtil tracker = new IntervalUtil(0.5f, 1f);

	public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
		this.ship = ship;
		this.flags = flags;
		this.engine = engine;
		this.system = system;

		// Store the system script properly
		if (system.getScript() instanceof xdp_EnergyLashSystemScript) {
			this.systemScript = (xdp_EnergyLashSystemScript) system.getScript();
		}
	}

	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		tracker.advance(amount);

		if (tracker.intervalElapsed()) {
			if (system.getCooldownRemaining() > 0) return;
			if (system.isOutOfAmmo()) return;
			if (system.isActive()) return;
			if (ship.getFluxTracker().isOverloadedOrVenting()) return;

			ShipAPI pick = getWeightedTargets(target).getItemWithHighestWeight();
			if (pick != null) {
				ship.getAIFlags().setFlag(AIFlags.CUSTOM1, 1.5f, pick);
				ship.giveCommand(ShipCommand.USE_SYSTEM, null, 0);
			}
		}
	}

	public List<ShipAPI> getPossibleTargets() {
		List<ShipAPI> result = new ArrayList<>();
		CombatEngineAPI engine = Global.getCombatEngine();

		List<ShipAPI> ships = engine.getShips();
		for (ShipAPI other : ships) {
			if (other == ship) continue;
			// Use the system script's methods
			if (systemScript != null) {
				if (!systemScript.isValidLashTarget(ship, other)) continue;
				if (!systemScript.isInRange(ship, other)) continue;
			} else {
				// Fallback range check if script is null
				float range = xdp_EnergyLashSystemScript.getRange(ship);
				float dist = Misc.getDistance(ship.getLocation(), other.getLocation());
				float radSum = ship.getCollisionRadius() + other.getCollisionRadius();
				if (dist > range + radSum) continue;

				// Basic validity check
				if (other.isHulk() || other.getOwner() == 100) continue;
				if (other.isShuttlePod()) continue;
				if (other.isFighter()) continue;
			}
			result.add(other);
		}
		return result;
	}

	public WeightedRandomPicker<ShipAPI> getWeightedTargets(ShipAPI shipTarget) {
		WeightedRandomPicker<ShipAPI> picker = new WeightedRandomPicker<>();

		for (ShipAPI other : getPossibleTargets()) {
			float w = 0f;
			if (ship.getOwner() == other.getOwner()) {
				// Friendly target
				if (other.getSystem() == null) continue;
				if (!(other.getSystem().getScript() instanceof EnergyLashActivatedSystem)) continue;
				if (other.getSystem().getCooldownRemaining() > 0) continue;
				if (other.getSystem().isActive()) continue;
				if (other.getFluxTracker().isOverloadedOrVenting()) continue;

				// Weight based on the friendly ship's needs
				w = 1.0f; // Base weight

				// Prioritize ships with high flux
				float fluxLevel = other.getFluxTracker().getFluxLevel();
				w += fluxLevel * 2.0f;

				// Prioritize ships under fire
				if (other.getFluxTracker().getHardFlux() > 0) {
					w += 0.5f;
				}
			} else {
				// Enemy target
				ShieldAPI targetShield = other.getShield();
				boolean targetShieldsFacingUs = targetShield != null &&
						targetShield.isOn() &&
						Misc.isInArc(targetShield.getFacing(), Math.max(30f, targetShield.getActiveArc()),
								other.getLocation(), ship.getLocation());

				// Don't target shielded enemies if damage is too low
				if (targetShieldsFacingUs && xdp_EnergyLashSystemScript.DAMAGE <= 0) continue;

				float dist = Misc.getDistance(ship.getLocation(), other.getLocation());
				dist -= ship.getCollisionRadius() + other.getCollisionRadius();
				if (dist < 0) dist = 0;

				// Weight based on distance and target priority
				float range = xdp_EnergyLashSystemScript.getRange(ship);
				if (dist < range) {
					w = 1.0f - (dist / range);
				}

				// Bonus for being the current target
				if (other == shipTarget) {
					w += 0.25f;
				}

				// Bonus for high-value targets
				if (other.isCapital()) w += 0.3f;
				if (other.isCruiser()) w += 0.2f;

				// Bonus for targets with shields down
				if (targetShield == null || !targetShield.isOn()) {
					w += 0.4f;
				}

				w += 0.01f; // Minimum weight
			}

			if (w > 0) {
				picker.add(other, w);
			}
		}
		return picker;
	}
}