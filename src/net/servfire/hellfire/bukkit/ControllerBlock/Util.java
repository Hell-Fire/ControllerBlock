package net.servfire.hellfire.bukkit.ControllerBlock;

import net.servfire.hellfire.bukkit.ControllerBlock.Config.Option;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;


public class Util {
	public static double getDistanceBetweenLocations(Location l1, Location l2) {
		if (!l1.getWorld().equals(l2.getWorld())) return -1;
		return Math.sqrt(
				Math.pow(l1.getX() - l2.getX(), 2) + 
				Math.pow(l1.getY() - l2.getY(), 2) +
				Math.pow(l1.getZ() - l2.getZ(), 2)
				);
	}
	
	public static Block getBlockAtLocation(Location l) {
		return getBlockAtLocation(l, 0, 0, 0);
	}
	
	public static Block getBlockAtLocation(Location l, Integer x, Integer y, Integer z) {
		return l.getWorld().getBlockAt(l.getBlockX() + x, l.getBlockY() + y, l.getBlockZ() + z);
	}
	
	public static String formatBlockCount(CBlock c) {
		if (c.getParent().getConfig().getInt(Option.MaxBlocksPerController) > 0) {
			return "(" + c.numBlocks() + "/" + c.getParent().getConfig().getInt(Option.MaxBlocksPerController) + " blocks)";
		} else {
			return "(" + c.numBlocks() + " blocks)";
		}
	}
	
	public static String formatLocation(Location l) {
		return "<" + l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + ">";
	}
	
	public static boolean typeEquals(Material t1, Material t2) {
		if (
				t1.equals(Material.DIRT) || t1.equals(Material.GRASS) &&
				t2.equals(Material.DIRT) || t2.equals(Material.GRASS)
			) return true;
		
		if (
				t1.equals(Material.REDSTONE_TORCH_ON) || t1.equals(Material.REDSTONE_TORCH_OFF) &&
				t2.equals(Material.REDSTONE_TORCH_ON) || t2.equals(Material.REDSTONE_TORCH_OFF)
			) return true;
		
		return t1.equals(t2);
		
	}
	
	public static boolean locEquals(Location l1, Location l2) {
		if (
				l1.getWorld().getName() == l2.getWorld().getName() &&
				l1.getBlockX() == l2.getBlockX() &&
				l1.getBlockY() == l2.getBlockY() &&
				l1.getBlockZ() == l2.getBlockZ()
				) return true;
		return false;
	}
}
