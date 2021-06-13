package nl.thedutchmc.harotorch.commands.torchSubCmds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World.Environment;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import dev.array21.bukkitreflectionlib.ReflectionUtil;
import net.md_5.bungee.api.ChatColor;
import nl.thedutchmc.harotorch.HaroTorch;
import nl.thedutchmc.harotorch.commands.SubCommand;
import nl.thedutchmc.harotorch.config.ConfigManifest.TorchRangeShape;
import nl.thedutchmc.harotorch.lang.LangHandler;
import nl.thedutchmc.harotorch.torch.TorchHandler;

// ReflectionUtil.getNmsClass(String) is deprecated, kept for backwards compatibility with MC:1.16 and older.
@SuppressWarnings("deprecation")
public class HighlightAreaOfEffectExecutor implements SubCommand {

	private static HashMap<UUID, Long> lastCommandTimestamps = new HashMap<>();
	
	private static Class<?> packetPlayOutWorldParticleClass;
	private static Class<?> packetPlayOutWorldParticleInterfaceClass;
	private static Class<?> craftPlayerClass;
	private static Class<?> craftParticleClass;
	private static Class<?> particleParamClass;
	
	static {
		try {
			if(ReflectionUtil.isUseNewSpigotPackaging()) {
				packetPlayOutWorldParticleClass = ReflectionUtil.getMinecraftClass("network.protocol.game.PacketPlayOutWorldParticles");
			} else {
				packetPlayOutWorldParticleClass = ReflectionUtil.getNmsClass("PacketPlayOutWorldParticles");
			}
		
			packetPlayOutWorldParticleInterfaceClass = packetPlayOutWorldParticleClass.getInterfaces()[0];
			
			craftPlayerClass = ReflectionUtil.getBukkitClass("entity.CraftPlayer");
			craftParticleClass = ReflectionUtil.getBukkitClass("CraftParticle");
			
			if(ReflectionUtil.isUseNewSpigotPackaging()) {
				particleParamClass = ReflectionUtil.getMinecraftClass("core.particles.ParticleParam");
			} else {
				particleParamClass = ReflectionUtil.getNmsClass("ParticleParam");
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public boolean run(HaroTorch plugin, CommandSender sender, String[] args) {
		
		Integer commandCooldown = plugin.getConfigManifest().commandCooldown;
		if(commandCooldown != null && commandCooldown > 0) {
			Long lastCommandUseTimestamp = lastCommandTimestamps.get(((Player) sender).getUniqueId());
			if(lastCommandUseTimestamp != null) {
				if(lastCommandUseTimestamp >= System.currentTimeMillis()) {
					sender.sendMessage(HaroTorch.getMessagePrefix() + ChatColor.GOLD + LangHandler.activeLang.getLangMessages().get("commandCooldown"));
					return true;
				}
			}
			
			lastCommandTimestamps.put(((Player) sender).getUniqueId(), System.currentTimeMillis() + (commandCooldown * 1000));
		}
		
		String msg = LangHandler.activeLang.getLangMessages().get("startingAoe").replaceAll("%SECONDS%", ChatColor.RED + String.valueOf(plugin.getConfigManifest().torchHighlightTime) + ChatColor.GOLD);
		sender.sendMessage(HaroTorch.getMessagePrefix() + ChatColor.GOLD + msg);
		
		List<Location> nearbyTorches = TorchHandler.getTorchLocationsNearPlayer((Player) sender, plugin.getConfigManifest().torchHighlightRange);
		
		List<TorchParticleObject> torchParticles;
		if(plugin.getConfigManifest().getTorchRangeShape() == TorchRangeShape.CIRCLE) {
			torchParticles = getParticlesCircular(nearbyTorches, plugin);
		} else {
			torchParticles = getParticlesSquare(nearbyTorches, plugin);
		}
		
		System.out.println("ParticlesSize: " + torchParticles.size());
		
		final BukkitTask particle = new BukkitRunnable() {
			
			@Override
			public void run() {
				for(TorchParticleObject torchParticle : torchParticles) {
				
					final Location torchLoc = torchParticle.getTorch();
					
					List<Object> particlePackets = new ArrayList<>();
					particlePackets.add(getParticlePacket(torchLoc.getX() + 0.5d, torchLoc.getY() + 1.5d, torchLoc.getZ() + 0.5d, 0f, 0f, 0f, 0.005f, 10, false, new Particle.DustOptions(torchParticle.getTorchParticleColor(), 1)));
					
					for(Location l : torchParticle.getCircleLocations()) {	
						
						for(int i = 0; i < plugin.getConfigManifest().torchAoeParticleHeight; i++) {
							particlePackets.add(getParticlePacket(l.getX() + 0.5d, l.getY() + 0.5d + i, l.getZ() + 0.5d, 0f, 0f, 0f, 0.005f, 5, false, new Particle.DustOptions(torchParticle.getTorchParticleColor(), 1)));
						}
						
						if(torchLoc.getWorld().getEnvironment() == Environment.NETHER) {
							for(int i = 1; i < plugin.getConfigManifest().torchAoeParticleHeight -1; i++) {
								particlePackets.add(getParticlePacket(l.getX() + 0.5d, l.getY() + 0.5d - i, l.getZ() + 0.5d, 0f, 0f, 0f, 0.005f, 5, false, new Particle.DustOptions(torchParticle.getTorchParticleColor(), 1)));
							}
						}
					}
					
					spawnParticles(particlePackets, (Player) sender);
				}	
			}
			
		}.runTaskTimer(plugin, 60L, 10L);
		
		new BukkitRunnable() {
			
			@Override
			public void run() {
				particle.cancel();
				sender.sendMessage(HaroTorch.getMessagePrefix() + ChatColor.GOLD + LangHandler.activeLang.getLangMessages().get("endingAoe"));
			}
		}.runTaskLater(plugin, 30L * 20L);
		
		return true;
	}
	
	/**
	 * Get all TorchParticleObject's in a square range
	 * @param nearbyTorches Locations of the Torches
	 * @param plugin HaroTorch instance
	 * @return
	 */
	public List<TorchParticleObject> getParticlesSquare(List<Location> nearbyTorches, HaroTorch plugin) {
		List<TorchParticleObject> result = new ArrayList<>();
		final int range = plugin.getConfigManifest().torchRange;

		nearbyTorches.parallelStream().forEach((lTorch) -> {
			List<Location> locs = new ArrayList<>();

			Location xNeg = lTorch.clone();
			xNeg.setX(lTorch.getX() - range);
			locs.add(xNeg);
			
			Location xPos = lTorch.clone();
			xPos.setX(xPos.getX() + range);
			locs.add(xPos);
			
			Location zNeg = lTorch.clone();
			zNeg.setZ(lTorch.getZ() - range);
			locs.add(zNeg);
			
			Location zPos = lTorch.clone();
			zPos.setZ(lTorch.getZ() + range);
			locs.add(zPos);
			
			// xNeg + 
			for(int i = 1; i <= range; i++) {
				Location l = xNeg.clone();
				l.setZ(xNeg.getZ() + i);
				locs.add(l);
			}
			
			// xNeg -
			for(int i = -1; i >= -range; i--) {
				Location l = xNeg.clone();
				l.setZ(xNeg.getZ() + i);
				locs.add(l);
			}
			
			// xPos +
			for(int i = 1; i <= range; i++) {
				Location l = xPos.clone();
				l.setZ(xPos.getZ() + i);
				locs.add(l);
			}
			
			// xPos -
			for(int i = -1; i >= -range; i--) {
				Location l = xPos.clone();
				l.setZ(xPos.getZ() + i);
				locs.add(l);
			}
			
			// zNeg +
			for(int i = 1; i <= range; i++) {
				Location l = zNeg.clone();
				l.setX(zNeg.getX() + i);
				locs.add(l);
			}
			
			// zNeg -
			for(int i = -1; i >= -range; i--) {
				Location l = zNeg.clone();
				l.setX(zNeg.getX() + i);
				locs.add(l);
			}
			
			// zPos +
			for(int i = 1; i <= range; i++) {
				Location l = zPos.clone();
				l.setX(zPos.getX() + i);
				locs.add(l);
			}
			
			// zPos -
			for(int i = -1; i >= -range; i--) {
				Location l = zPos.clone();
				l.setX(zPos.getX() + i);
				locs.add(l);
			}
			
			final int r = (int) (Math.random() * 256D);
			final int g = (int) (Math.random() * 256D);
			final int b = (int) (Math.random() * 256D);
			result.add(new TorchParticleObject(r, g, b, locs, lTorch));
		});
		
		return result;
	}
	
	/**
	 * Get all TorchParticleObjects in a circular range
	 * @param nearbyTorches Locations of the Torches
	 * @param plugin HaroTorch instance
	 * @return
	 */
	public List<TorchParticleObject> getParticlesCircular(List<Location> nearbyTorches, HaroTorch plugin) {
		List<TorchParticleObject> result = new ArrayList<>();

		nearbyTorches.parallelStream().forEach((l) -> {
			final List<Location> blocksOnTorchRadius = new ArrayList<>();

			final int radius = plugin.getConfigManifest().torchRange;
			final int cx = l.getBlockX();
			final int cz = l.getBlockZ();
			final World w = l.getWorld();
			
			for(int i = 0; i < 360; i++) {
				
				final double rad = i * ((2 * Math.PI)/360);
				
				final int x = (int) (cx + (radius * Math.cos(rad)));
				final int z = (int) (cz + (radius * Math.sin(rad)));
				
				int y;
				if(w.getEnvironment() == Environment.NETHER) {
					y = l.getBlockY();
				} else {
					y = w.getHighestBlockAt(x, z).getY() + 1;
				}
				
				blocksOnTorchRadius.add(new Location(w, x, y, z));
			}
			
			final int r = (int) (Math.random() * 256D);
			final int g = (int) (Math.random() * 256D);
			final int b = (int) (Math.random() * 256D);
			
			result.add(new TorchParticleObject(r, g, b, blocksOnTorchRadius, l));
		});
		
		return result;
	}
	
	/**
	 * Create a PacketPlayOutWorldParticle packet for Redstone particles
	 * @param pX The X position of the particle
	 * @param pY The Y position of the particle
	 * @param pZ The Z position of the particle
	 * @param oX The X offset of the particle
	 * @param oY The Y offset of the particle
	 * @param oZ The Z offset of the particle
	 * @param extra Extra data for the particle. Unknown what this does. 0.005f seems to work
	 * @param count The amount of particles to spawn
	 * @param force Unknown what this does. False seems to work
	 * @param dustOptions DustOptions for the particle
	 * @return
	 */
	private Object getParticlePacket(double pX, double pY, double pZ, float oX, float oY, float oZ, float extra, int count, boolean force, DustOptions dustOptions) {
		try {
			Object nmsParticleData = particleDataToNms(Particle.REDSTONE, dustOptions);			
			Object particlePacket = ReflectionUtil.invokeConstructor(packetPlayOutWorldParticleClass, 
					new Class<?>[] { particleParamClass, boolean.class, double.class, double.class, double.class, float.class, float.class, float.class, float.class, int.class}, 
					new Object[] { nmsParticleData, force, pX, pY, pZ, oX, oY, oZ, extra, count });
			
			return particlePacket;
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private void spawnParticles(List<Object> particlePackets, Player player) {
		try {
			Object entityPlayerObject = ReflectionUtil.invokeMethod(craftPlayerClass, player, "getHandle");
			Object playerConnectionObject;
			if(ReflectionUtil.isUseNewSpigotPackaging()) {
				playerConnectionObject = ReflectionUtil.getObject(entityPlayerObject, "b");
			} else {
				playerConnectionObject = ReflectionUtil.getObject(entityPlayerObject, "playerConnection");
			}		
			for(Object packet : particlePackets) {
				ReflectionUtil.invokeMethod(playerConnectionObject, "sendPacket", 
						new Class<?>[] { packetPlayOutWorldParticleInterfaceClass }, 
						new Object[] { packet });
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private Object particleDataToNms(Particle a, DustOptions b) {
		try {
			Object dataAsNMS = ReflectionUtil.invokeMethod(craftParticleClass, null, "toNMS", new Class<?>[] { Particle.class, Object.class}, new Object[] {a, b});
			return dataAsNMS;
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private class TorchParticleObject {
		
		private Color particleColor;
		private List<Location> circleLocations;
		private Location torch;
		
		public TorchParticleObject(int r, int g, int b, List<Location> circleLocations, Location torch) {
			this.particleColor = Color.fromRGB(r, g, b);
			this.circleLocations = circleLocations;
			this.torch = torch;
		}
		
		public Color getTorchParticleColor() {
			return this.particleColor;
		}
		
		public List<Location> getCircleLocations() {
			return this.circleLocations;
		}
		
		public Location getTorch() {
			return this.torch;
		}
	}
	
}
