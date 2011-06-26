package net.servfire.hellfire.bukkit.ControllerBlock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.servfire.hellfire.bukkit.ControllerBlock.Config.Option;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;


public class ControllerBlock extends JavaPlugin implements Runnable {
	
	private static String configFile = "ControllerBlock.ini";
	private static String saveDataFile = "ControllerBlock.dat";

	public Logger log = new Logger(this, "Minecraft");
	private Config config = new Config();
	private PermissionHandler permissionHandler = new PermissionHandler(this);
	private final BlockListener blockListener = new BlockListener(this);
	private final CBlockRedstoneCheck checkRunner = new CBlockRedstoneCheck(this);
	
	public boolean blockPhysicsEditCheck = false;
	private boolean beenLoaded = false;
	private boolean beenEnabled = false;
	
	@Override
	public void onDisable() {
		
	}

	public void onLoad() {
		if (!beenLoaded) {
			log.info(this.getDescription().getVersion() + " by Hell_Fire");
			checkPluginDataDir();
			loadConfig();
			
			beenLoaded = true;
		}
	}
	
	@Override
	public void onEnable() {
		if (!beenEnabled) {
			PluginManager pm = getServer().getPluginManager();
			
			log.debug("Registering events:");
			// The "UI", checks for hits against controller blocks
			log.debug(" - BLOCK_DAMAGE");
			pm.registerEvent(Event.Type.BLOCK_DAMAGE, blockListener, Event.Priority.Highest, this);
			// Block protection/controller cleanup/block editing
			log.debug(" - BLOCK_BREAK");
			pm.registerEvent(Event.Type.BLOCK_BREAK, blockListener, Event.Priority.Highest, this);
			// Block editing
			log.debug(" - BLOCK_PLACE");
			pm.registerEvent(Event.Type.BLOCK_PLACE, blockListener, Event.Priority.Monitor, this);
			// Block protection/fallback anti-dupe while editing
			log.debug(" - BLOCK_PHYSICS");
			pm.registerEvent(Event.Type.BLOCK_PHYSICS, blockListener, Event.Priority.Highest, this);
			// Block protection
			log.debug(" - BLOCK_FROMTO"); // TODO: Change this to LIQUID_DESTROY
			pm.registerEvent(Event.Type.BLOCK_FROMTO, blockListener, Event.Priority.Highest, this);
			
			
			
			log.debug("Scheduling tasks:");
			
			// New hotness for anti-dupe while editing, more accurate than BLOCK_PHYSICS
			log.debug(" - Anti-dupe/changed-block edit check");
			if (getServer().getScheduler().scheduleSyncRepeatingTask(this, blockListener, 1, 1) == -1) {
				log.warning("Scheduling BlockListener anti-dupe check failed, falling back to old BLOCK_PHYSICS event");
				blockPhysicsEditCheck = true;
			}
			if (config.getBool(Option.DisableEditDupeProtection)) {
				log.warning("Edit dupe protection has been disabled, you're on your own from here");
			}
			
			// New redstone check, the more natural check
			if (!config.getBool(Option.QuickRedstoneCheck)) {
				log.debug(" - Redstone check");
				log.info("Enabling full redstone check");
				if (getServer().getScheduler().scheduleSyncRepeatingTask(this, checkRunner, 1, 1) == -1) {
					log.warning("Scheduling CBlockRedstoneCheck task failed, falling back to quick REDSTONE_CHANGE event");
					config.setOpt(Option.QuickRedstoneCheck, true);
				}
			}
			// Fallback/quick REDSTONE_CHANGE check
			if (config.getBool(Option.QuickRedstoneCheck)) {
				log.info("Enabling 'quick' redstone check - this mode of operation is depreciated and may be removed later");
				pm.registerEvent(Event.Type.REDSTONE_CHANGE, blockListener, Event.Priority.Monitor, this);
			}
			
			if (getServer().getScheduler().scheduleSyncDelayedTask(this, this, 1) == -1) {
				log.severe("Failed to schedule loadData, loading now, will probably not work with multiworld plugins");
				loadData();
			}
			
			log.info("Events registered");
			
			beenEnabled = true;
		}
	}
	
	/*
	 * Accessors to some helper classes
	 */
	public Config getConfig() {
		return config;
	}
	
	public PermissionHandler getPerm() {
		return permissionHandler;
	}
	


	/*
	 * CBlock handling, creation, destruction, finding, checking, etc
	 */
	public List<CBlock> blocks = new ArrayList<CBlock>();

	public CBlock createCBlock(Location l, String o, byte pl) {
		CBlock c = new CBlock(this, l, o, pl);
		blocks.add(c);
		return c;
	}
	
	public CBlock destroyCBlock(Location l) {
		CBlock block = getCBlock(l);
		if (block == null) return block;
		block.destroy();
		blocks.remove(block);
		saveData();
		return block;
	}
	
	public CBlock getCBlock(Location l) {
		for (Iterator<CBlock> i = blocks.iterator(); i.hasNext(); ) {
			CBlock block = i.next();
			if (Util.locEquals(block.getLoc(), l)) return block;
		}
		return null;
	}
	
	public boolean isControlBlock(Location l) {
		if (getCBlock(l) == null) return false;
		return true;
	}
	
	public boolean isControlledBlock(Location l) {
		if (getControllerBlockFor(null, l, null, null) == null) return false;
		return true;
	}
	
	public boolean isControlledBlock(Location l, Material m) {
		if (getControllerBlockFor(null, l, m, null) == null) return false;
		return true;
	}
	
	public CBlock getControllerBlockFor(CBlock c, Location l, Material m, Boolean o) {
		for (Iterator<CBlock> i = blocks.iterator(); i.hasNext(); ) {
			CBlock block = i.next();
			if (
					c != block && 
					(m == null || m.equals(block.getType())) &&
					(o == null || o.equals(block.isOn())) &&
					block.hasBlock(l)
					) return block;
		}
		return null;
	}

	/*
	 * Data dir/file handling
	 */
	private void checkPluginDataDir() {
		log.debug("Checking plugin data directory " + this.getDataFolder());
		File dir = this.getDataFolder();
		if (!dir.isDirectory()) {
			log.debug("Isn't a directory");
			if (!dir.mkdir()) {
				log.severe("Couldn't create plugin data directory " + this.getDataFolder());
				return;
			}
			// Old config/data cleanup, move it where it should be >.<
			// TODO: remove this sometime soonish, everyones config should be moved by now
			File ini = new File("ControllerBlock.ini");
			if (ini.isFile()) {
				log.warning("Moving ControllerBlock.ini to " + dir.getPath());
				ini.renameTo(new File(dir.getPath() + "/ControllerBlock.ini"));
			}
			File dat = new File("ControllerBlock.dat");
			if (dat.isFile()) {
				log.warning("Moving ControllerBlock.dat to " + dir.getPath());
				dat.renameTo(new File(dir.getPath() + "/ControllerBlock.dat"));
			}
		}
	}
	
	public void loadData() {
		int v = 1;
		String s = "";
		Integer i = 0;
		Integer l = 1;
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(this.getDataFolder() + "/ControllerBlock.dat"), "UTF-8"));
			String version = in.readLine().trim();
			if (version.equals("# v2")) {
				v = 2;
			} else if (version.equals("# v3")) {
				v = 3;
			} else if (version.equals("# v4")) {
				v = 4;
			} else {
				--l;
			}
			while ((s = in.readLine()) != null) {
				++l;
				if (s.trim().isEmpty()) continue;
				CBlock newBlock = new CBlock(this, v, s.trim());
				if (newBlock.getLoc() != null) {
					blocks.add(newBlock);
					++i;
				} else {
					log.severe("Error loading ControllerBlock on line " + l);
				}
			}
			in.close();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// Don't do anything if the file wasn't found
			//e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		log.info("Loaded v" + v + " data - " + i + " ControllerBlocks loaded");
	}
	
	public void saveData() {
		log.debug("Saving ControllerBlock data");
		String dump = "# v4";
		for (Iterator<CBlock> i = blocks.iterator(); i.hasNext(); ) {
			CBlock cblock = i.next();
			dump += "\n" + cblock.serialize();
		}
		try {
			Writer out = new OutputStreamWriter(new FileOutputStream(this.getDataFolder() + "/" + saveDataFile + ".tmp"), "UTF-8");
			out.write(dump);
			out.close();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			log.severe("ERROR: Couldn't open the file to write ControllerBlock data to!");
			log.severe("       Check your server installation has write access to " + this.getDataFolder());
			e.printStackTrace();
		} catch (IOException e) {
			log.severe("ERROR: Couldn't save ControllerBlock data! Possibly corrupted/incomplete data");
			log.severe("       Check if the disk is full, then edit/finish editing a ControllerBlock");
			log.severe("       in game to try to save again.");
			e.printStackTrace();
		}
	
		File newData = new File(this.getDataFolder() + "/" + saveDataFile + ".tmp");
		File curData = new File(this.getDataFolder() + "/" + saveDataFile);
		if (!newData.renameTo(curData)) {
			// Failed the first time, fallback onto delete/replace method
			if (!curData.delete()) {
				log.warning("Couldn't delete old save data during fallback, will probably error next");
			}
			if (!newData.renameTo(curData)) {
				log.severe("ERROR: Couldn't move temporary save file over current save file");
				log.severe("       Check that your server installation has write access to " + this.getDataFolder() + "/" + saveDataFile);	
			}
		}
	}
	
	/*
	 * Configuration handling
	 */
	private Material CBlockType;
	private Material semiProtectedCBlockType;
	private Material unProtectedCBlockType;
	private List<Material> DisallowedTypes = new ArrayList<Material>();
	
	public Material getCBlockType() {
		return CBlockType;
	}
	
	public Material getSemiProtectedCBlockType() {
		return semiProtectedCBlockType;
	}
	
	public Material getUnProtectedCBlockType() {
		return unProtectedCBlockType;
	}
	
	public boolean isValidMaterial(Material m) {
		if (!m.isBlock()) return false;
		Iterator<Material> i = DisallowedTypes.iterator();
		while (i.hasNext()) {
			if (i.next().equals(m)) return false;
		}
		return true;
	}


	
	private void loadError(String cmd, String arg, Integer line, String def) {
		if (def.length() != 0) {
			def = "defaulting to " + def;
		} else {
			def = "it has been skipped";
		}
		log.warning("Couldn't parse " + cmd + " " + arg + " on line " + line + ", " + def);
	}
	
	private void loadConfig() {		
		String s;
		Integer oldConfigLine = -1;
		Integer l = 0;
		ConfigSections c = ConfigSections.oldConfig;
		List<String> configText = new ArrayList<String>();
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(this.getDataFolder() + "/" + configFile), "UTF-8"));
			while ((s = in.readLine()) != null) {
				configText.add(s.trim());
				++l;
				if (s.trim().isEmpty()) continue;
				if (s.startsWith("#")) continue;
				if (s.toLowerCase().trim().equals("[general]")) {
					c = ConfigSections.general;
					continue;
				} else if (s.toLowerCase().trim().equals("[adminplayers]")) {
					c = ConfigSections.adminPlayers;
					continue;
				} else if (s.toLowerCase().trim().equals("[disallowed]")) {
					c = ConfigSections.disallowed;
					continue;
				}
				if (c.equals(ConfigSections.general)) {
					String line[] = s.split("=", 2);
					if (line.length < 2) continue;
					String cmd = line[0].toLowerCase();
					String arg = line[1];
					if (cmd.equals("ControllerBlockType".toLowerCase())) {
						CBlockType = Material.getMaterial(arg);
						if (CBlockType == null) {
							loadError("ControllerBlockType", arg, l, "IRON_BLOCK");
							CBlockType = Material.IRON_BLOCK;
						}
						config.setOpt(Option.ControllerBlockType, CBlockType);
					
					} else if (cmd.equals("SemiProtectedControllerBlockType".toLowerCase())) {
						semiProtectedCBlockType = Material.getMaterial(arg);
						if (semiProtectedCBlockType == null) {
							loadError("SemiProtectedControllerBlockType", arg, l, "GOLD_BLOCK");
							semiProtectedCBlockType = Material.GOLD_BLOCK;
						}
						config.setOpt(Option.SemiProtectedControllerBlockType, semiProtectedCBlockType);
					
					} else if (cmd.equals("UnProtectedControllerBlockType".toLowerCase())) {
						unProtectedCBlockType = Material.getMaterial(arg);
						if (unProtectedCBlockType == null) {
							loadError("UnProtectedControllerBlockType", arg, l, "DIAMOND_BLOCK");
							unProtectedCBlockType = Material.DIAMOND_BLOCK;
						}
						config.setOpt(Option.UnProtectedControllerBlockType, unProtectedCBlockType);
					
					} else if (cmd.equals("QuickRedstoneCheck".toLowerCase())) {
						config.setOpt(Option.QuickRedstoneCheck, Boolean.parseBoolean(arg));
						
					} else if (cmd.equals("BlockProtectMode".toLowerCase())) {
						config.setOpt(Option.BlockProtectMode, BlockProtectMode.valueOf(arg.toLowerCase()));
						
					} else if (cmd.equals("BlockEditProtectMode".toLowerCase())) {
						config.setOpt(Option.BlockEditProtectMode, BlockProtectMode.valueOf(arg.toLowerCase()));
						
					} else if (cmd.equals("BlockPhysicsProtectMode".toLowerCase())) {
						config.setOpt(Option.BlockPhysicsProtectMode, BlockProtectMode.valueOf(arg.toLowerCase()));
						
					} else if (cmd.equals("BlockFlowProtectMode".toLowerCase())) {
						config.setOpt(Option.BlockFlowProtectMode, BlockProtectMode.valueOf(arg.toLowerCase()));
						
					} else if (cmd.equals("DisableEditDupeProtection".toLowerCase())) {
						config.setOpt(Option.DisableEditDupeProtection, Boolean.parseBoolean(arg));
						
					} else if (cmd.equals("MaxBlocksPerController".toLowerCase())) {
						config.setOpt(Option.MaxBlocksPerController, Integer.parseInt(arg));
						
					} else if (cmd.equals("MaxDistanceFromController".toLowerCase())) {
						config.setOpt(Option.MaxDistanceFromController, Integer.parseInt(arg));
						
					} else if (cmd.equals("DisableNijikokunPermissions".toLowerCase())) {
						config.setOpt(Option.DisableNijikokunPermissions, Boolean.parseBoolean(arg));
						
					} else if (cmd.equals("ServerOpIsAdmin".toLowerCase())) {
						config.setOpt(Option.ServerOpIsAdmin, Boolean.parseBoolean(arg));
						
					} else if (cmd.equals("AnyoneCanCreate".toLowerCase())) {
						config.setOpt(Option.AnyoneCanCreate, Boolean.parseBoolean(arg));
						
					} else if (cmd.equals("AnyoneCanModifyOther".toLowerCase())) {
						config.setOpt(Option.AnyoneCanModifyOther, Boolean.parseBoolean(arg));
						
					} else if (cmd.equals("AnyoneCanDestroyOther".toLowerCase())) {
						config.setOpt(Option.AnyoneCanDestroyOther, Boolean.parseBoolean(arg));
						
					}

				} else if (c.equals(ConfigSections.adminPlayers)) {
					permissionHandler.addBuiltinAdminPlayer(s.trim());
					
				} else if (c.equals(ConfigSections.disallowed)){
					Material m = Material.getMaterial(s.trim());
					if (m == null) {
						loadError("disallowed type", s.trim(), l, "");
					} else {
						DisallowedTypes.add(m);
					}
				
				// Parse the old config format based on line number
				} else if (c.equals(ConfigSections.oldConfig)) {
					if (oldConfigLine == -1) {
						CBlockType = Material.getMaterial(s.trim());
						if (CBlockType == null) {
							log.warning("Couldn't parse ControllerBlock type " + s.trim() + ", defaulting to IRON_BLOCK");
							CBlockType = Material.IRON_BLOCK;
						}
						config.setOpt(Option.ControllerBlockType, CBlockType);
						++oldConfigLine;
					} else {
						Material m = Material.getMaterial(s.trim());
						if (m == null) {
							log.warning("Couldn't parse disallowed type " + s.trim() + ", it has been skipped");
						} else {
							DisallowedTypes.add(m);
							++oldConfigLine;
						}
					}
				}
			}
			writeConfig(configText);
			in.close();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// Don't do anything if the file wasn't found
			//e.printStackTrace();
			log.warning("No config found, using defaults, writing defaults out to " + configFile);
			writeConfig(null);
		} catch (IOException e) {
			e.printStackTrace();
		}
		CBlockType = (Material)config.getOpt(Option.ControllerBlockType);
		log.info("Using " + CBlockType + " (" + CBlockType.getId() + ") as ControllerBlock, loaded " + DisallowedTypes.size() + " disallowed types from config");

	}
	
	private String writePatch(ConfigSections c) {
		String dump = "";
		if (c == null) return dump;
		if (c.equals(ConfigSections.general)) {
			if (!config.hasOption(Option.ControllerBlockType)) {
				dump += "\n";
				dump += "# ControllerBlockType is the material allowed of new ControllerBlocks\n";
				dump += "# Doesn't affect already assigned ControllerBlocks\n";
				dump += "ControllerBlockType=" + config.getOpt(Option.ControllerBlockType) + "\n";
			}
			if (!config.hasOption(Option.SemiProtectedControllerBlockType)) {
				dump += "\n";
				dump += "# SemiProtectedControllerBlockType is the material that semi-protected\n";
				dump += "# Controller Blocks are made from, this block will turn on in a protected\n";
				dump += "# state, but when turned off, blocks controlled won't disappear, instead\n";
				dump += "# they lose their protection and can be destroyed\n";
				dump += "SemiProtectedControllerBlockType=" + config.getOpt(Option.SemiProtectedControllerBlockType) + "\n";
			}
			if (!config.hasOption(Option.UnProtectedControllerBlockType)) {
				dump += "\n";
				dump += "# UnProtectedControllerBlockType is the material that unprotected\n";
				dump += "# Controller Blocks are made from, blocks controlled by this will create\n";
				dump += "# when turned on, but won't disappear when turned off, much like the\n";
				dump += "# semi-protected controlled blocks, however, blocks controlled have no\n";
				dump += "# protection against being broken even in the on state\n";
				dump += "UnProtectedControllerBlockType=" + config.getOpt(Option.UnProtectedControllerBlockType) + "\n";
			}
			if (!config.hasOption(Option.QuickRedstoneCheck)) {
				dump += "\n";
				dump += "# QuickRedstoneCheck to false enables per-tick per-controllerblock isBlockPowered() checks\n";
				dump += "# This is potentially laggier, but blocks can be powered like regular redstone blocks\n";
				dump += "# If set to true, wire needs to be run on top of the controller block\n";
				dump += "QuickRedstoneCheck=" + config.getOpt(Option.QuickRedstoneCheck) + "\n";
			}
			if (!config.hasOption(Option.BlockProtectMode)) {
				dump += "\n";
				dump += "# BlockProtectMode changes how we handle destroying controlled blocks\n";
				dump += "# It has 3 modes:\n";
				dump += "# protect - default, tries to prevent controlled blocks from being destroyed\n";
				dump += "# remove - removes controlled blocks from controller if destroyed\n";
				dump += "# none - don't do anything, this effectively makes controlled blocks dupable\n";
				dump += "BlockProtectMode=" + config.getOpt(Option.BlockProtectMode) + "\n";
			}
			if (!config.hasOption(Option.BlockPhysicsProtectMode)) {
				dump += "\n";
				dump += "# BlockPhysicsProtectMode changes how we handle changes against controlled blocks\n";
				dump += "# It has 3 modes:\n";
				dump += "# protect - default, stops physics interactions with controlled blocks\n";
				dump += "# remove - removes controlled blocks from controller if changed\n";
				dump += "# none - don't do anything, could have issues with some blocks\n";
				dump += "BlockPhysicsProtectMode=" + config.getOpt(Option.BlockPhysicsProtectMode) + "\n";
			}
			if (!config.hasOption(Option.BlockFlowProtectMode)) {
				dump += "\n";
				dump += "# BlockFlowProtectMode changes how we handle water/lava flowing against controlled blocks\n";
				dump += "# It has 3 modes:\n";
				dump += "# protect - default, tries to prevent controlled blocks from being interacted\n";
				dump += "# remove - removes controlled blocks from controller if flow event on it\n";
				dump += "# none - don't do anything, things that drop when flowed over can be dupable\n";
				dump += "BlockFlowProtectMode=" + config.getOpt(Option.BlockFlowProtectMode) + "\n";
			}
			if (!config.hasOption(Option.DisableEditDupeProtection)) {
				dump += "\n";
				dump += "# DisableEditDupeProtection set to true disables all the checks for changes while in\n";
				dump += "# edit mode, this will make sure blocks placed in a spot will always be in that spot\n";
				dump += "# even if they get removed by some kind of physics/flow event in the meantime\n";
				dump += "DisableEditDupeProtection=" + config.getOpt(Option.DisableEditDupeProtection) + "\n";
			}
			if (!config.hasOption(Option.MaxDistanceFromController)) {
				dump += "\n";
				dump += "# MaxDistanceFromController sets how far away controlled blocks are allowed\n";
				dump += "# to be attached and controlled to a controller block - 0 for infinte/across worlds\n";
				dump += "MaxDistanceFromController=" + config.getOpt(Option.MaxDistanceFromController) + "\n";
			}
			if (!config.hasOption(Option.MaxBlocksPerController)) {
				dump += "\n";
				dump += "# MaxControlledBlocksPerController sets how many blocks are allowed to be attached\n";
				dump += "# to a single controller block - 0 for infinite\n";
				dump += "MaxBlocksPerController=" + config.getOpt(Option.MaxBlocksPerController) + "\n";
			}
			if (!config.hasOption(Option.DisableNijikokunPermissions)) {
				dump += "\n";
				dump += "# Nijikokun Permissions support\n";
				dump += "# The nodes for permissions are:\n";
				dump += "# controllerblock.admin - user isn't restricted by block counts or distance, able to\n";
				dump += "#                         create/modify/destroy other users controllerblocks\n";
				dump += "# controllerblock.create - user is allowed to setup controllerblocks\n";
				dump += "# controllerblock.modifyOther - user is allowed to modify other users controllerblocks\n";
				dump += "# controllerblock.destroyOther - user is allowed to destroy other users controllerblocks\n";
				dump += "#\n";
				dump += "# DisableNijikokunPermissions will disable any lookups against Permissions if you\n";
				dump += "# do have it installed, but want to disable this plugins use of it anyway\n";
				dump += "# Note: You don't have to do this, the plugin isn't dependant on Permissions\n";
				dump += "DisableNijikokunPermissions=" + config.getOpt(Option.DisableNijikokunPermissions) + "\n";
			}
			if (!config.hasOption(Option.ServerOpIsAdmin)) {
				dump += "\n";
				dump += "# Users listed in ops.txt (op through server console) counts as an admin\n";
				dump += "ServerOpIsAdmin=" + config.getOpt(Option.ServerOpIsAdmin) + "\n";
			}
			if (!config.hasOption(Option.AnyoneCanCreate)) {
				dump += "\n";
				dump += "# Everyone on the server can create new ControllerBlocks\n";
				dump += "AnyoneCanCreate=" + config.getOpt(Option.AnyoneCanCreate) + "\n";
			}
			if (!config.hasOption(Option.AnyoneCanModifyOther)) {
				dump += "\n";
				dump += "# Everyone can modify everyone elses ControllerBlocks\n";
				dump += "AnyoneCanModifyOther=" + config.getOpt(Option.AnyoneCanModifyOther) + "\n";
			}
			if (!config.hasOption(Option.AnyoneCanDestroyOther)) {
				dump += "\n";
				dump += "# Everyone can destroy everyone elses ControllerBlocks\n";
				dump += "AnyoneCanDestroyOther=" + config.getOpt(Option.AnyoneCanDestroyOther) + "\n";
			}
		}
		if (dump.length() != 0) {
			dump += "\n";
		}
		return dump;
	}
	
	private void writeConfig(List<String> prevConfig) {
		String dump = "";
		if (prevConfig == null) {
			dump = "# ControllerBlock configuration file\n";
			dump += "\n";
			dump += "# Blank lines and lines starting with # are ignored\n";
			dump += "# Material names can be found: http://javadoc.lukegb.com/Bukkit/d7/dd9/namespaceorg_1_1bukkit.html#ab7fa290bb19b9a830362aa88028ec80a\n";
			dump += "\n";
		}
		boolean hasGeneral = false;
		boolean hasAdminPlayers = false;
		boolean hasDisallowed = false;
		ConfigSections c = null;
		
		if (prevConfig != null) {
			Iterator<String> pci = prevConfig.listIterator();
			while (pci.hasNext()) {
				String line = pci.next();
				if (line.toLowerCase().trim().equals("[general]")) {
					dump += writePatch(c);
					c = ConfigSections.general;
					hasGeneral = true;
				} else if (line.toLowerCase().trim().equals("[adminplayers]")) {
					dump += writePatch(c);
					c = ConfigSections.adminPlayers;
					hasAdminPlayers = true;
				} else if (line.toLowerCase().trim().equals("[disallowed]")) {
					dump += writePatch(c);
					c = ConfigSections.disallowed;
					hasDisallowed = true;
				}
				dump += line + "\n";
			}
			pci = null;
			dump += writePatch(c);
		}
		
		if (!hasGeneral) {
			dump += "[general]\n";
			dump += writePatch(ConfigSections.general);
			dump += "\n";
		}
		if (!hasAdminPlayers) {
			dump += "[adminPlayers]\n";
			dump += "# One name per line, users listed here are admins, and can\n";
			dump += "# create/modify/destroy all ControllerBlocks on the server\n";
			dump += "# Block restrictions don't apply to admins\n";
			dump += "\n";
		}
		if (!hasDisallowed) {
			dump += "[disallowed]\n";
			dump += "# Add disallowed blocks here, one Material per line.\n";
			dump += "# Item IDs higher than 255 are excluded automatically due to failing Material.isBlock() check\n";
			dump += "#RED_ROSE\n#YELLOW_FLOWER\n#RED_MUSHROOM\n#BROWN_MUSHROOM\n";
			dump += "\n";
			Iterator<Material> i = DisallowedTypes.listIterator();
			while (i.hasNext()) {
				dump += i.next() + "\n";
			}
		}
		
		try {
			Writer out = new OutputStreamWriter(new FileOutputStream(this.getDataFolder() + "/" + configFile), "UTF-8");
			out.write(dump);
			out.close();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		loadData();
	}
}
