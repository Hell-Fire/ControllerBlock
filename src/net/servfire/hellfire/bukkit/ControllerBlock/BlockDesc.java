package net.servfire.hellfire.bukkit.ControllerBlock;

import org.bukkit.Location;

public class BlockDesc {
	public Location blockLoc;
	public byte blockData;
	
	public BlockDesc(Location l, Byte b) {
		blockLoc = l;
		blockData = b;
	}
}
