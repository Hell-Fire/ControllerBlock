package net.servfire.hellfire.bukkit.ControllerBlock;

import java.util.HashMap;

import org.bukkit.Material;

public class Config {
	
	public enum Option {
		ControllerBlockType,
		SemiProtectedControllerBlockType,
		UnProtectedControllerBlockType,
		ServerOpIsAdmin,
		AnyoneCanCreate,
		AnyoneCanModifyOther,
		AnyoneCanDestroyOther,
		MaxBlocksPerController,
		MaxDistanceFromController,
		BlockProtectMode,
		QuickRedstoneCheck,
		DisableNijikokunPermissions, 
		DisableEditDupeProtection,
		BlockEditProtectMode,
		BlockPhysicsProtectMode,
		BlockFlowProtectMode,
	}
	
	private HashMap<Option, Object> options = new HashMap<Option, Object>();

	public void setOpt(Option opt, Object arg) {
		options.put(opt, arg);
	}
	
	public boolean getBool(Option opt) {
		return (Boolean)getOpt(opt);
	}
	
	public Integer getInt(Option opt) {
		return (Integer)getOpt(opt);
	}
	
	public Object getOpt(Option opt) {
		if (!hasOption(opt)) {
			switch (opt) {
			case ControllerBlockType:
				return Material.IRON_BLOCK;
			case SemiProtectedControllerBlockType:
				return Material.GOLD_BLOCK;
			case UnProtectedControllerBlockType:
				return Material.DIAMOND_BLOCK;
			case QuickRedstoneCheck:
				return false;
			case BlockProtectMode:
				return BlockProtectMode.protect;
			case BlockPhysicsProtectMode:
				return BlockProtectMode.protect;
			case BlockFlowProtectMode:
				return BlockProtectMode.protect;
			case DisableEditDupeProtection:
				return false;
			case MaxBlocksPerController:
				return 0;
			case MaxDistanceFromController:
				return 0;
			case DisableNijikokunPermissions:
				return false;
			case ServerOpIsAdmin:
				return true;
			case AnyoneCanCreate:
				return true;
			case AnyoneCanModifyOther:
				return true;
			case AnyoneCanDestroyOther:
				return true;
			}
		}
		return options.get(opt);
	}

	public boolean hasOption(Option opt) {
		if (options.containsKey(opt)) return true;
		return false;
	}
}
