package com.tmbu.tmbuclient.module;

import com.tmbu.tmbuclient.module.impl.combat.*;
import com.tmbu.tmbuclient.module.impl.render.*;
import com.tmbu.tmbuclient.module.impl.player.*;
import com.tmbu.tmbuclient.module.impl.misc.*;

/**
 * Single place to register all modules. Adding a new module is one line here —
 * no need to touch TmbuClient, ModuleManager, or any other class.
 */
public final class Modules {
	private Modules() {}

	public static void register() {
		ModuleRegistry.add(AutoAnchorExploder::new);
		ModuleRegistry.add(AutoCrystal::new);
		ModuleRegistry.add(AlwaysSprintModule::new);
		ModuleRegistry.add(EspModule::new);
		ModuleRegistry.add(NametagsModule::new);
		ModuleRegistry.add(ChatNotifierModule::new);
		ModuleRegistry.add(AutoTotemHover::new);
		ModuleRegistry.add(AutoTotemRefill::new);
		ModuleRegistry.add(PearlFeet::new);
		ModuleRegistry.add(AutoDoubleHand::new);
		ModuleRegistry.add(SafeAnchor::new);
		ModuleRegistry.add(ModuleList::new);
		ModuleRegistry.add(PopChams::new);
		ModuleRegistry.add(Trajectories::new);
		ModuleRegistry.add(AimAssist::new);
		ModuleRegistry.add(KeepSprint::new);
		ModuleRegistry.add(SuperKnockback::new);
		ModuleRegistry.add(TriggerBot::new);
		ModuleRegistry.add(SelfWeb::new);
		ModuleRegistry.add(AutoWeb::new);
		ModuleRegistry.add(HandView::new);
		ModuleRegistry.add(Ambience::new);
		ModuleRegistry.add(BlastTrajectory::new);
	}
}
