package net.servfire.hellfire.bukkit.ControllerBlock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.servfire.hellfire.bukkit.ControllerBlock.Config.Option;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;


public class PermissionHandler {
	
	private ControllerBlock parent = null;
	
	private com.nijikokun.bukkit.Permissions.Permissions nijikokunPermissions = null;
	
	private List<String> builtinAdminPlayers = new ArrayList<String>();
	
	public PermissionHandler(ControllerBlock p) {
		parent = p;
	}
	
	public boolean checkNijikokunPermissions(Player p, String perm) {
		if (nijikokunPermissions == null) {
			Plugin plug = parent.getServer().getPluginManager().getPlugin("Permissions");
			if (plug != null) {
				nijikokunPermissions = (com.nijikokun.bukkit.Permissions.Permissions) plug;
				parent.log.debug("Nijikokun Permissions detected and enabled");
			}
		}
		if (nijikokunPermissions != null) {
			parent.log.debug("Running Nijikokun Permissions check on " + p.getName() + " for " + perm);
			return nijikokunPermissions.getHandler().has(p, perm);
		} else {
			return false;
		}
	}
	
	public void addBuiltinAdminPlayer(String name) {
		builtinAdminPlayers.add(name);
	}
	
	public boolean isAdminPlayer(Player p) {
		parent.log.debug("Checking if " + p.getName() + " is a CB admin");
		if (parent.getConfig().getBool(Option.ServerOpIsAdmin) && p.isOp()) {
			parent.log.debug(p.getName() + " is a server operator, and serverOpIsAdmin is set");
			return true;
		}
		
		if (checkNijikokunPermissions(p, "controllerblock.admin")) {
			parent.log.debug("Nijikokun Permissions said " + p.getName() + " has admin permissions");
			return true;
		}
		
		String pn = p.getName();
		Iterator<String> i = builtinAdminPlayers.iterator();
		while (i.hasNext()) {
			if (i.next().equals(pn)) {
				parent.log.debug(p.getName() + " is listed in the ControllerBlock.ini as an admin");
				return true;
			}
		}
		parent.log.debug(p.getName() + " isn't an admin");
		return false;
	}
	
	public boolean canCreate(Player p) {
		if (isAdminPlayer(p)) {
			parent.log.debug(p.getName() + " is an admin, can create");
			return true;
		}
		
		if (checkNijikokunPermissions(p, "controllerblock.create")) {
			parent.log.debug("Nijikokun Permissions said " + p.getName() + " can create");
			return true;
		}
		
		if (parent.getConfig().getBool(Option.AnyoneCanCreate)) {
			parent.log.debug("Anyone is allowed to create, letting " + p.getName() + " create");
		}
		return parent.getConfig().getBool(Option.AnyoneCanCreate);
	}
	
	public boolean canModify(Player p) {
		if (isAdminPlayer(p)) {
			parent.log.debug(p.getName() + " is an admin, can modify");
			return true;
		}
		
		if (checkNijikokunPermissions(p, "controllerblock.modifyOther")) {
			parent.log.debug("Nijikokun Permissions says " + p.getName() + " has global modify permissions");
			return true;
		}
		
		if (parent.getConfig().getBool(Option.AnyoneCanModifyOther)) {
			parent.log.debug("Anyone is allowed to modify anyones blocks, allowing " + p.getName() + " to modify");
		}
		return parent.getConfig().getBool(Option.AnyoneCanModifyOther);
	}
	
	public boolean canModify(Player p, CBlock c) {
		if (p.getName().equals(c.getOwner())) {
			parent.log.debug(p.getName() + " owns this controller, allowing to modify");
			return true;
		}
		return canModify(p);
	}
	
	public boolean canDestroy(Player p) {
		if (isAdminPlayer(p)) {
			parent.log.debug(p.getName() + " is an admin, allowing destroy");
			return true;
		}
		
		if (checkNijikokunPermissions(p, "controllerblock.destroyOther")) {
			parent.log.debug("Nijikokun Permissions says " + p.getName() + " has global destroy permissions");
			return true;
		}
		
		if (parent.getConfig().getBool(Option.AnyoneCanDestroyOther)) {
			parent.log.debug("Anyone is allowed to destroy anyones blocks, allowing " + p.getName() + " to destroy");
		}
		return parent.getConfig().getBool(Option.AnyoneCanDestroyOther);
	}
	
	public boolean canDestroy(Player p, CBlock c) {
		if (p.getName().equals(c.getOwner())) {
			parent.log.debug(p.getName() + "owns this controller, allowing them to destroy it");
			return true;
		}
		return canDestroy(p);
	}

}
