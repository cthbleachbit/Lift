/*
 * This file is part of Lift.
 *
 * Copyright (c) ${project.inceptionYear}-2013, croxis <https://github.com/croxis/>
 *
 * Lift is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Lift is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Lift. If not, see <http://www.gnu.org/licenses/>.
 */
package net.croxis.plugins.lift;

import java.util.HashSet;
import java.util.Iterator;

import net.h31ix.anticheat.api.AnticheatAPI;
import net.h31ix.anticheat.manage.CheckType;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.getspout.spoutapi.SpoutManager;

public class BukkitElevatorManager extends ElevatorManager{
	private static BukkitLift plugin;
	public static HashSet<BukkitElevator> bukkitElevators = new HashSet<BukkitElevator>();
	public static HashSet<Entity> fallers = new HashSet<Entity>();
	public static HashSet<Player> flyers = new HashSet<Player>();
	

	public BukkitElevatorManager(BukkitLift plugin) {
		BukkitElevatorManager.plugin = plugin;
		taskid = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this, 2, 2);
	}
	
	public static BukkitElevator createLift(Block block){
		long startTime = System.currentTimeMillis();
		plugin.logDebug("Starting elevator gen");
		BukkitElevator bukkitElevator = new BukkitElevator();
		int yscan = block.getY() - 1;
		while(yscan >= -1){
			if (yscan == -1){ //Gone too far with no base abort!
				plugin.logDebug("No elevator base found");
				return null;
			}
			Block checkBlock = block.getWorld().getBlockAt(block.getX(), yscan, block.getZ());
			if (isValidShaftBlock(checkBlock)){
				// Do nothing keep going
			} else if (BukkitElevatorManager.isBaseBlock(checkBlock)) {
				bukkitElevator.baseBlockType = checkBlock.getType();
				bukkitElevator.speed = plugin.blockSpeeds.get(bukkitElevator.baseBlockType);
				scanBaseBlocks(checkBlock, bukkitElevator);
				for (Block b : bukkitElevator.baseBlocks){
					// This is for speed optimization for entering lift in use
					if (!bukkitElevator.chunks.contains(b.getChunk()))
						bukkitElevator.chunks.add(b.getChunk());
				}
				break;
			} else {
				// Something is obstructing the elevator so stop
				if (BukkitLift.debug){
					System.out.println("==Unknown Error==");
					System.out.println("Yscan: " + Integer.toString(yscan));
					System.out.println("Block: " + checkBlock.getType().toString());
					System.out.println("Is Valid Block: " + Boolean.toString(isValidShaftBlock(checkBlock)));
					System.out.println("Is Base Block: " + Boolean.toString(BukkitElevatorManager.isBaseBlock(checkBlock)));
				}
				return null;
			}
			yscan--;
		}
		plugin.logDebug("Base size: " + Integer.toString(bukkitElevator.baseBlocks.size()));
		
		constructFloors(bukkitElevator);
		
		//Elevator is constructed, pass off to check signs for floor destination, collect all people and move them
		plugin.logDebug("Elevator gen took: " + (System.currentTimeMillis() - startTime) + " ms.");
		return bukkitElevator;
	}
	
	//Checks if block is a valid elevator block SANS iron
	public static boolean isValidShaftBlock(Block checkBlock){
		if (checkBlock.getType() == plugin.floorBlock
				|| checkBlock.getType() == Material.AIR 
				|| checkBlock.getType() == Material.LADDER
				|| checkBlock.getType() == Material.SNOW
				|| checkBlock.getType() == Material.STATIONARY_WATER
				|| checkBlock.getType() == Material.STONE_BUTTON
				|| checkBlock.getType() == Material.TORCH 
				|| checkBlock.getType() == Material.VINE 
				|| checkBlock.getType() == Material.WALL_SIGN
				|| checkBlock.getType() == Material.WATER
				|| checkBlock.getType() == Material.WOOD_BUTTON)
			return true;
		return false;
	}
	
	//Recursive function that constructs our list of blocks
	//I'd rather it just return a hashset instead of passing elevator
	//But I can't figure out a clean way to do it
	public static void scanBaseBlocks(Block block, BukkitElevator bukkitElevator){
		if (bukkitElevator.baseBlocks.size() >= BukkitLift.liftArea)
			return; //5x5 max, prevents infinite loops
		else if (bukkitElevator.baseBlocks.contains(block))
			return; // We have that block already
		bukkitElevator.baseBlocks.add(block);
		if (block.getRelative(BlockFace.NORTH, 1).getType() == bukkitElevator.baseBlockType)
			scanBaseBlocks(block.getRelative(BlockFace.NORTH), bukkitElevator);
		if (block.getRelative(BlockFace.EAST, 1).getType() == bukkitElevator.baseBlockType)
			scanBaseBlocks(block.getRelative(BlockFace.EAST), bukkitElevator);
		if (block.getRelative(BlockFace.SOUTH, 1).getType() == bukkitElevator.baseBlockType)
			scanBaseBlocks(block.getRelative(BlockFace.SOUTH), bukkitElevator);
		if (block.getRelative(BlockFace.WEST, 1).getType() == bukkitElevator.baseBlockType)
			scanBaseBlocks(block.getRelative(BlockFace.WEST), bukkitElevator);
		return;
	}
	
	public static String constructFloors(BukkitElevator bukkitElevator){
		String message = "";
		int y1 = bukkitElevator.baseBlocks.iterator().next().getY();
		int maxY = y1 + BukkitLift.maxHeight;

		for (Block b : bukkitElevator.baseBlocks){
			int x = b.getX();
			int z = b.getZ();
			y1 = b.getY();
			int scanHeight = 0;
			
			World currentWorld = b.getWorld();
			
			while (true){
				y1 = y1 + 1;
				scanHeight += 1;
				if (scanHeight == BukkitLift.maxHeight + 2 || scanHeight >= maxY) {
					break;
				}
				Block testBlock = b.getWorld().getBlockAt(x, y1, z);
				if (!isValidShaftBlock(testBlock)){
					message += " | " + x + " " + y1 + " " + z + " of type "  + testBlock.getType().toString();
					maxY = y1;
					plugin.logDebug(" | " + x + " " + y1 + " " + z + " of type "  + testBlock.getType().toString());
					break;
				}
				
				if (testBlock.getType() == Material.STONE_BUTTON || testBlock.getType() == Material.WOOD_BUTTON){
					if (plugin.checkGlass)
						if (!scanFloorAtY(currentWorld, testBlock.getY() - 2, bukkitElevator)){
							break;
						}
					Floor floor = new Floor();
					floor.setY(y1);
					if (testBlock.getRelative(BlockFace.DOWN).getType() == Material.WALL_SIGN)
						floor.setName(((Sign) testBlock.getRelative(BlockFace.DOWN).getState()).getLine(1));
					if (testBlock.getRelative(BlockFace.UP).getType() == Material.WALL_SIGN)
						bukkitElevator.floormap.put(y1, floor);
					plugin.logDebug("Floor added at lift: " + b.getLocation());
					plugin.logDebug("Floor y: " + Integer.toString(y1));
				}				
			}
		}
		int floorNumber = 1;
		Iterator<Integer> floorIterator = bukkitElevator.floormap.keySet().iterator();
		while (floorIterator.hasNext()){
			if (floorIterator.next() >= maxY)
				floorIterator.remove();
		}
		for (Floor floor : bukkitElevator.floormap.values()){
			floor.setFloor(floorNumber);
			bukkitElevator.floormap2.put(floorNumber, floor);
			floorNumber = floorNumber + 1;
		}
		return message;
	}
	
	public static boolean scanFloorAtY(World world, int y, BukkitElevator bukkitElevator){
		for (Block block : bukkitElevator.baseBlocks){
			if (BukkitLift.debug){
				System.out.println("Scan glass block type: " + world.getBlockAt(block.getX(), y, block.getZ()).getType().toString());
				System.out.println("Is not glass?: " + Boolean.toString(world.getBlockAt(block.getX(), y, block.getZ()).getType() != plugin.floorBlock));
				System.out.println("Is not base?: " + Boolean.toString(!plugin.blockSpeeds.keySet().contains(world.getBlockAt(block.getX(), y, block.getZ()).getType())));
			}
			if (world.getBlockAt(block.getX(), y, block.getZ()).getType() != plugin.floorBlock && !plugin.blockSpeeds.keySet().contains(world.getBlockAt(block.getX(), y, block.getZ()).getType())){
				if (BukkitLift.debug)
					System.out.println("Invalid block type");
				return false;	
			}
		}
		return true;
	}
	
	public static void endLift(BukkitElevator bukkitElevator){
		plugin.logDebug("Halting lift");
		for (Block b : bukkitElevator.glassBlocks)
			b.setType(plugin.floorBlock);
		Iterator<Entity> passengerIterator = bukkitElevator.getPassengers();
		while (passengerIterator.hasNext()){
			Entity e = passengerIterator.next();
			fallers.remove(e);
			if (e instanceof Player)
				removePlayer((Player) e);
			passengerIterator.remove();
		}
		Iterator<Entity> holdersIterators = bukkitElevator.getHolders();
		while (holdersIterators.hasNext()){
			Entity passenger = holdersIterators.next();
			if (passenger instanceof Player){
				removePlayer((Player) passenger, holdersIterators);
			}
		}
		bukkitElevator.clear();
	}
	
	public static void removePlayer(Player player, Iterator<Entity> passengers){
		plugin.logDebug("Removing player " + player.getName() + " from El: " + bukkitElevators.toString());
		for (BukkitElevator bukkitElevator : bukkitElevators){
			plugin.logDebug("Scanning lift");
			if (bukkitElevator.isInLift(player)){
				plugin.logDebug("Removing player from lift");
				restorePlayer(player);			
				passengers.remove();
			}
		}
	}
	
	public static void removePlayer(Player player){
		plugin.logDebug("Removing player " + player.getName() + " from El: " + bukkitElevators.toString());
		for (BukkitElevator bukkitElevator : bukkitElevators){
			plugin.logDebug("Scanning lift");
			if (bukkitElevator.isInLift(player)){
				plugin.logDebug("Removing player from lift");
				restorePlayer(player);
				bukkitElevator.removePassenger(player);
			}
		}
	}
	
	public static void removePassenger(Entity passenger){
		if (isPassenger(passenger)){
			plugin.logDebug("Removing entity " + passenger.toString() + " from El: " + bukkitElevators.toString());
			if (passenger instanceof Player)
				removePlayer((Player) passenger);
			else
				for (BukkitElevator bukkitElevator : bukkitElevators){
					plugin.logDebug("Scanning lift");
					if (bukkitElevator.isInLift(passenger))
						bukkitElevator.removePassenger(passenger);
				}
		}
	}
	
	public static boolean isBaseBlock(Block block){
		if (plugin.blockSpeeds.containsKey(block.getType()))
			return true;
		return false;
	}
	
	public static boolean isPassenger(Entity entity){
		Iterator<BukkitElevator> iterator = bukkitElevators.iterator();
		while (iterator.hasNext()){
			BukkitElevator bukkitElevator = iterator.next();
			if (bukkitElevator.isInLift(entity))
				return true;
		}
		return false;
	}
	
	public static void setupPlayer(Player player){
		// Function which sets up a player for holding or passengering. Anti cheat stuff
		if (player.getAllowFlight()){
			BukkitElevatorManager.flyers.add(player);
			plugin.logDebug(player.getName() + " added to flying list");
		} else {
            BukkitElevatorManager.flyers.remove(player);
            //player.setAllowFlight(false);
            plugin.logDebug(player.getName() + " NOT added to flying list");
        }

		player.setAllowFlight(true);
		if (plugin.useAntiCheat)
			AnticheatAPI.exemptPlayer(player, CheckType.FLY);

		if (plugin.useSpout){
			SpoutManager.getPlayer(player).setGravityMultiplier(0);
			SpoutManager.getPlayer(player).setCanFly(true);				
		}
	}
	
	public static void restorePlayer(Player player){
		// Restores a player's previous stats.
		if (fallers.contains(player)){
			fallers.remove(player);
		}
		if (flyers.contains(player)){
			flyers.remove(player);
		} else {
			player.setAllowFlight(false);
			plugin.logDebug("Removing player from flight");
			if (plugin.useAntiCheat)
				AnticheatAPI.unexemptPlayer(player, CheckType.FLY);
			if (plugin.useSpout)
				SpoutManager.getPlayer(player).setCanFly(false);				
		}
		if (plugin.useSpout)
			SpoutManager.getPlayer(player).setGravityMultiplier(1);
	}
	
	public void run() {
		//Using while loop iterator so we can remove lifts in a sane way
		Iterator<BukkitElevator> eleviterator = bukkitElevators.iterator();
		// Various variables to reduce variable spawning
		BukkitElevator e;
		Iterator<Entity> passengers;
		Entity passenger;
		Entity holder;
		
		while (eleviterator.hasNext()){
			e = eleviterator.next();
			plugin.logDebug("Passengers: " + e.getPassengers().toString());
			passengers = e.getPassengers();
			if(!passengers.hasNext()){
				BukkitElevatorManager.endLift(e);
				eleviterator.remove();
				continue;
			}
			while (passengers.hasNext()){
				passenger = passengers.next();
				
				//Check if passengers have left the shaft
				if (!e.isInShaft(passenger) && passenger instanceof Player){
					plugin.logDebug("Player out of shaft");
					removePlayer((Player) passenger, passengers);
					continue;
				}
				
				//Re apply impulse as it does seem to run out
				if(e.destFloor.getFloor() > e.startFloor.getFloor())
					passenger.setVelocity(new Vector(0.0D, e.speed, 0.0D));
				else
					passenger.setVelocity(new Vector(0.0D, -e.speed, 0.0D));
				passenger.setFallDistance(0.0F);
				
				if((e.goingUp && passenger.getLocation().getY() > e.destFloor.getY() - 0.7)
						|| (!e.goingUp && passenger.getLocation().getY() < e.destFloor.getY()-0.1)){
					plugin.logDebug("Removing passenger: " + passenger.toString() + " with y " + Double.toString(passenger.getLocation().getY()));
					plugin.logDebug("Trigger status: Going up: " + Boolean.toString(e.goingUp));
					plugin.logDebug("Floor Y: " + Double.toString(e.destFloor.getY()));
					
					Location pLoc = passenger.getLocation().clone();
					pLoc.setY(e.destFloor.getY()-0.5);
					passenger.teleport(pLoc);
					
					moveToHolder(e, passengers, passenger, passenger.getLocation());
				}
			}
			
			Iterator<Entity> holders = e.getHolders();
			
			while (holders.hasNext()){
				holder = holders.next();
				plugin.logDebug("Holding: " + holder.toString() + " at " + e.getHolderPos(holder));
				holder.teleport(e.getHolderPos(holder));
				holder.setFallDistance(0.0F);
				holder.setVelocity(new Vector(0,0,0));
			}
		}
	}

	private void moveToHolder(BukkitElevator e, Iterator<Entity> passengers,
			Entity passenger, Location location) {
		passengers.remove();
		e.addHolder(passenger, location);
		passenger.setVelocity(new Vector(0,0,0));
		passenger.setFallDistance(0.0F);
	}
	
	public static void addHolder(BukkitElevator elevator, Entity holder, Location location){
		// Adds a new entity to lift to be held in position
		if (holder instanceof Player)
			setupPlayer((Player) holder);
		elevator.addHolder(holder, location);
		if (!elevator.goingUp) {
			BukkitElevatorManager.fallers.add(holder);
		}
	}
	
	public static void addPassenger(BukkitElevator elevator, Entity passenger){
		// Adds a new entity to lift to be held in position
		if (passenger instanceof Player)
			setupPlayer((Player) passenger);
		elevator.addPassenger(passenger);
		if (!elevator.goingUp) {
			BukkitElevatorManager.fallers.add(passenger);
		}
	}
}
