package neon.xdp.data.scripts.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import neon.xdp.data.campaign.intel.misc.XDPInvictaConvIntel;

import java.util.List;
import java.util.Map;

public class xdp_InvictaContactCMD extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        TextPanelAPI text = dialog.getTextPanel();

        // Create the intel - this will also add Invicta as a contact
        Global.getSector().getIntelManager().addIntel(new XDPInvictaConvIntel(), false);
        // Progress the mission stage
        return true;
    }
}