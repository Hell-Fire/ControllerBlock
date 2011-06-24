package net.servfire.hellfire.bukkit.ControllerBlock;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import net.servfire.hellfire.bukkit.ControllerBlock.Config.Option;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.material.MaterialData;


public class BlockListener extends org.bukkit.event.block.BlockListener implements Runnable {

	private ControllerBlock parent;
		
	private HashMap<Player, CBlock> map = new HashMap<Player, CBlock>();

	public BlockListener(ControllerBlock controllerBlock) {
		parent = controllerBlock;
	}
	
	public Player getPlayerEditing(CBlock c) {
		for (Entry<Player, CBlock> e : map.entrySet()) {
			if (e.getValue().equals(c))
				return e.getKey();
		}
		return null;
	}
	
	public void removePlayersEditing(CBlock c) {
		Player p;
		while ((p = getPlayerEditing(c)) != null) {
			map.remove(p);
		}
	}
	
	public boolean isRedstone(Block b) {
		Material t = b.getType();
		if (
				t.equals(Material.REDSTONE_WIRE) ||
				t.equals(Material.REDSTONE_TORCH_ON) ||
				t.equals(Material.REDSTONE_TORCH_OFF)
				) return true;
		return false;
	}
	
	/*
	 * Block destruction events - two possibles we want to catch
	 * 1. ControllerBlock broken, remove ControllerBlock and all data
	 * 2. Controlled Block broken, protect it (cancel event) or if editing, let it go
	 */
    public void onBlockBreak(BlockBreakEvent e) {
    	if (e.isCancelled()) return;
    	Player player = e.getPlayer();
    	Block b = e.getBlock();
    	CBlock conBlock = parent.getCBlock(b.getLocation());
    	if (conBlock != null) {
	    	if (!parent.getPerm().canDestroy(player, conBlock)) {
	    		player.sendMessage("You're not allowed to destroy this ControllerBlock");
	    		e.setCancelled(true);
	    		return;
	    	}
	    	conBlock = parent.destroyCBlock(b.getLocation());
	    	if (conBlock != null) {
	    		player.sendMessage("Destroyed controller block");
	    		removePlayersEditing(conBlock);
	    	}
    	}
    	
    	conBlock = map.get(player);
		if (conBlock != null && conBlock.hasBlock(b.getLocation()) && conBlock.getType().equals(b.getType())) {
			if (conBlock.delBlock(b)) player.sendMessage("Block removed from controller " + Util.formatBlockCount(conBlock));
		} else if ((conBlock = parent.getControllerBlockFor(null, b.getLocation(), b.getType(), null)) != null) {
			switch ((BlockProtectMode)parent.getConfig().getOpt(Option.BlockProtectMode)) {
			case protect:
				if (conBlock.protectedLevel == 0 || (!conBlock.isOn() && conBlock.protectedLevel != 2)) {
					player.sendMessage("This block is controlled by a controller block at " + 
							conBlock.getLoc().getBlockX() + ", " +
							conBlock.getLoc().getBlockY() + ", " +
							conBlock.getLoc().getBlockZ());
					e.setCancelled(true);
				}
				break;
			case remove:
				conBlock.delBlock(b);
				break;
			case none:
				break;
			}

		}
    }
	
	public void onBlockDamage(BlockDamageEvent e) {
		Player player = e.getPlayer();
		CBlock conBlock;
		if (e.isCancelled() && e.getBlock().getType().equals(Material.AIR)) {
			// Something fishy going on, WorldEdit, are you messing with our stuff?
			// It probably is if this runs, remove the ControllerBlock if we have to
			if ((conBlock = parent.destroyCBlock(e.getBlock().getLocation())) != null) {
				player.sendMessage("Destroyed controller block with superpickaxe?");
	    		removePlayersEditing(conBlock);
			}
		}
		if (e.isCancelled()) return;
		PlayerInventory inv = player.getInventory();
		Material item = inv.getItemInHand().getType();
		Block b = e.getBlock();
		conBlock = map.get(player);
		
		//if (e.getDamageLevel().equals(BlockDamageLevel.STARTED)) {
			// Ignore if the block was hit with some kind of pickaxe
			if (item.equals(Material.WOOD_PICKAXE) ||
					item.equals(Material.STONE_PICKAXE) ||
					item.equals(Material.IRON_PICKAXE) ||
					item.equals(Material.GOLD_PICKAXE) ||
					item.equals(Material.DIAMOND_PICKAXE)
					) return;
			// Player is already editing, check what's going on
			if (conBlock != null) {
				// Check if the player has hit a ControllerBlock while editing
				if (parent.isControlBlock(b.getLocation())) {
					conBlock.editBlock(false);
					map.remove(player);
					// Check if we've hit the block we're editing.
					if (Util.locEquals(conBlock.getLoc(), b.getLocation())) {
						player.sendMessage("Finished editing ControllerBlock");
						return; // Finish up here, don't need to process another ControllerBlock.
					} else {
						// Players looking to edit another ControllerBlock
						player.sendMessage("Finished editing previous ControllerBlock");
						conBlock = null;
					}
				}
			}
			// If the player isn't editing a ControllerBlock already
			if (conBlock == null) {
				// Check if what we hit isn't a ControllerBlock and if it's the right type for one	
				conBlock = parent.getCBlock(b.getLocation());
				if (conBlock == null) {
					if (!isRedstone(b.getRelative(BlockFace.UP))) return; // Require redstone on top of the block
					String cBTypeStr;
					byte cBType;
					if (b.getType() == parent.getCBlockType()) {
						cBTypeStr = "protected";
						cBType = 0;
					} else if (b.getType() == parent.getSemiProtectedCBlockType()) {
						cBTypeStr = "semi-protected";
						cBType = 1;
					} else if (b.getType() == parent.getUnProtectedCBlockType()) {
						cBTypeStr = "unprotected";
						cBType = 2;
					} else {
						return; // Don't know what type it's supposed to be
					}
					if (!parent.getPerm().canCreate(player)) {
						player.sendMessage("You're not allowed to create " + cBTypeStr + " ControllerBlocks");
						return;
					}
					if (parent.isControlledBlock(b.getLocation())) {
						player.sendMessage("This block is controlled, controlled blocks can't be controllers");
						return;
					}
					conBlock = parent.createCBlock(b.getLocation(), player.getName(), cBType);
					player.sendMessage("Created " + cBTypeStr + " controller block");
				}
				// See if what we've hit is a ControllerBlock (might have just been created)
				if (conBlock == null) return;
				if (!parent.getPerm().canModify(player, conBlock)) {
					player.sendMessage("You're not allowed to modify this ControllerBlock");
					return;
				}
				// We have one! Check if we can change the Material first.
				if (conBlock.numBlocks() == 0) {
					if (!parent.isValidMaterial(item)) {
						player.sendMessage("Can't set the ControllerBlock type to " + item);
						return;
					}
					conBlock.setType(item);
				}
				// Then check if we're editing it with the right type, tell the player what type it is.
				if (item != Material.AIR && item != conBlock.getType()) {
					player.sendMessage("This ControllerBlock needs to be edited with " + conBlock.getType());
					return;
				}
				// Finally, put us in edit mode, tell the player what we're editing with anyway.
				map.put(player, conBlock);
				conBlock.editBlock(true);
				player.sendMessage("You're now editing this block with " + conBlock.getType() + " " + Util.formatBlockCount(conBlock));
			}
		//}
	}
	
	public void onBlockPlace(BlockPlaceEvent e) {
		if (e.isCancelled() || !e.canBuild()) return;
		Player player = e.getPlayer();
		CBlock conBlock = map.get(player);
		if (conBlock == null) return;
		if (
			parent.getConfig().getInt(Option.MaxBlocksPerController) != 0 &&
			conBlock.numBlocks() >= parent.getConfig().getInt(Option.MaxBlocksPerController) &&
			!parent.getPerm().isAdminPlayer(player)
		) {
			player.sendMessage("Controller block is full " + Util.formatBlockCount(conBlock));
			return;
		}
		if (
			parent.getConfig().getInt(Option.MaxDistanceFromController) != 0 &&
			conBlock.getType().equals(e.getBlock().getType()) &&
			!parent.getPerm().isAdminPlayer(player) &&
			Util.getDistanceBetweenLocations(conBlock.getLoc(), e.getBlock().getLocation()) > parent.getConfig().getInt(Option.MaxDistanceFromController)
		) {
			player.sendMessage("This block is too far away from the controller block to be controlled");
			return;
		}

		if (conBlock.addBlock(e.getBlock())) {
			/*
			// Adjacent, same level
			conBlock.updateBlock(Util.getBlockAtLocation(e.getBlock().getLocation(), 1, 0, 0));
			conBlock.updateBlock(Util.getBlockAtLocation(e.getBlock().getLocation(), -1, 0, 0));
			conBlock.updateBlock(Util.getBlockAtLocation(e.getBlock().getLocation(), 0, 0, 1));
			conBlock.updateBlock(Util.getBlockAtLocation(e.getBlock().getLocation(), 0, 0, -1));
			// Adjacent, one level higher
			conBlock.updateBlock(Util.getBlockAtLocation(e.getBlock().getLocation(), 1, 1, 0));
			conBlock.updateBlock(Util.getBlockAtLocation(e.getBlock().getLocation(), -1, 1, 0));
			conBlock.updateBlock(Util.getBlockAtLocation(e.getBlock().getLocation(), 0, 1, 1));
			conBlock.updateBlock(Util.getBlockAtLocation(e.getBlock().getLocation(), 0, 1, -1));
			// Adjacent, one level lower
			conBlock.updateBlock(Util.getBlockAtLocation(e.getBlock().getLocation(), 1, -1, 0));
			conBlock.updateBlock(Util.getBlockAtLocation(e.getBlock().getLocation(), -1, -1, 0));
			conBlock.updateBlock(Util.getBlockAtLocation(e.getBlock().getLocation(), 0, -1, 1));
			conBlock.updateBlock(Util.getBlockAtLocation(e.getBlock().getLocation(), 0, -1, -1));
			*/
			player.sendMessage("Added block to controller " + Util.formatBlockCount(conBlock));
		}
	}

	public void onBlockRedstoneChange(BlockRedstoneEvent e) {
		CBlock conBlock = null;
		if (parent.getConfig().getBool(Option.QuickRedstoneCheck) == true) {
			conBlock = parent.getCBlock(e.getBlock().getRelative(BlockFace.DOWN).getLocation());
		}
		if (conBlock == null) return;
		// TODO: All this below is munging the state because the state we get is the
		// 		 old current, not the new current, so make a state with the new current.
		BlockState s = e.getBlock().getState();
		if (s.getType().equals(Material.REDSTONE_WIRE)){ 
			MaterialData m = s.getData();
			m.setData((byte) e.getNewCurrent());
			s.setData(m);
		}
		conBlock.doRedstoneCheck(s);
	}
	
	public void onBlockPhysics(BlockPhysicsEvent e) {
		if (e.isCancelled()) return;
		CBlock conBlock = parent.getControllerBlockFor(null, e.getBlock().getLocation(), null, true);
		if (conBlock == null) return;
		if (conBlock.isBeingEdited()) {
			// Scheduled event should normally do this for us
			if (!parent.blockPhysicsEditCheck) return;
			
			// Ignore fences >.< They don't drop when this gets thrown
			if (e.getBlock().getType().equals(Material.FENCE)) return;
			
			Player player = getPlayerEditing(conBlock);
			
			if (!Util.typeEquals(conBlock.getType(), e.getChangedType())) {
				//player.sendMessage("from: " + e.getBlock().getType() + " to: " + e.getChangedType());
				parent.log.debug("Block at " + Util.formatLocation(e.getBlock().getLocation()) + " was changed to " + e.getChangedType() + " but is supposed to be " + conBlock.getType() + ", dupe!");
				conBlock.delBlock(e.getBlock());
				player.sendMessage("Removing block due to changed type while editing " + Util.formatBlockCount(conBlock));
			}
		} else {
			BlockProtectMode protect = (BlockProtectMode)parent.getConfig().getOpt(Option.BlockPhysicsProtectMode);
			if (protect.equals(BlockProtectMode.protect)) {
				e.setCancelled(true);
			} else if (protect.equals(BlockProtectMode.remove)) {
				conBlock.delBlock(e.getBlock());
			}
		}
	}
	
	public void onBlockFromTo(BlockFromToEvent e) {
		if (e.isCancelled()) return;
		CBlock conBlock = parent.getControllerBlockFor(null, e.getToBlock().getLocation(), null, true);
		if (conBlock == null) return;
		if (conBlock.isBeingEdited()) {
			// Scheduled event should normally do this for us
			if (!parent.blockPhysicsEditCheck) return;
			Player player = getPlayerEditing(conBlock);
			parent.log.debug("Block at " + Util.formatLocation(e.getToBlock().getLocation()) + " was drowned while editing and removed from a controller");
			conBlock.delBlock(e.getToBlock());
			player.sendMessage("Removing block due to change while editing " + Util.formatBlockCount(conBlock));
		} else {
			BlockProtectMode protect = (BlockProtectMode)parent.getConfig().getOpt(Option.BlockFlowProtectMode);
			if (protect.equals(BlockProtectMode.protect)) {
				e.setCancelled(true);
			} else if (protect.equals(BlockProtectMode.remove)) {
				conBlock.delBlock(e.getBlock());
			}
		}
	}

	public void run() {
		if (!parent.getConfig().getBool(Option.DisableEditDupeProtection)) {
			for (Entry<Player, CBlock> e : map.entrySet()) {
				Iterator<BlockDesc> i = e.getValue().getBlocks();
				while (i.hasNext()) {
					Block b = Util.getBlockAtLocation(i.next().blockLoc);
					if (!Util.typeEquals(b.getType(), e.getValue().getType())) {
						parent.log.debug("Block at " + Util.formatLocation(b.getLocation()) + " was " + b.getType() + " but expected " + e.getValue().getType() + ", dupe!");
						i.remove();
						e.getKey().sendMessage("Removing block due to changed while editing " + Util.formatBlockCount(e.getValue()));
						return;
					}
				}
			}
		}
		for (Entry<Player, CBlock> e : map.entrySet()) {
			Iterator<BlockDesc> i = e.getValue().getBlocks();
			while (i.hasNext()) {
				BlockDesc d = i.next();
				Block b = Util.getBlockAtLocation(d.blockLoc);
				d.blockData = b.getState().getData().getData();
			}
		}
	}
}
