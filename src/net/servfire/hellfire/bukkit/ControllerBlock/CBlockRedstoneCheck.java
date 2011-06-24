package net.servfire.hellfire.bukkit.ControllerBlock;

import java.util.Iterator;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class CBlockRedstoneCheck implements Runnable {
	
	private ControllerBlock parent = null;

	public CBlockRedstoneCheck(ControllerBlock c) {
		parent = c;
	}
	
	@Override
	public void run() {
		Iterator<CBlock> i = parent.blocks.iterator();
		while (i.hasNext()) {
			CBlock c = i.next();
			if (c.isBeingEdited()) continue;
			Block b = Util.getBlockAtLocation(c.getLoc());
			// Torch on top of the block takes priority.
			boolean on = c.isOn();
			if (b.getRelative(BlockFace.UP).getType().equals(Material.REDSTONE_TORCH_ON)) {
				if (on) {
					c.turnOff();
				}
			} else if (b.getRelative(BlockFace.UP).getType().equals(Material.REDSTONE_TORCH_OFF)) {
				if (!on) {
					c.turnOn();
				}
			} else {
				if (on && b.isBlockPowered()) {
					c.turnOff();
				} else if (!on && !b.isBlockPowered()) {
					c.turnOn();
				}
			}
		}
	}

}
