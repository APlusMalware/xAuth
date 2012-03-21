package com.cypherx.xauth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentWrapper;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.cypherx.xauth.database.Table;

public class PlayerDataHandler {
	private final xAuth plugin;

	public PlayerDataHandler(final xAuth plugin) {
		this.plugin = plugin;
	}

	public void storeData(xAuthPlayer xp, Player p) {
		PlayerInventory pInv = p.getInventory();
		Location loc = p.getHealth() > 0 ? p.getLocation() : null;

		String strItems = buildItemString(pInv.getContents());
		String strArmor = buildItemString(pInv.getArmorContents());
		String strLoc = null;
		if (loc != null)
			strLoc = loc.getWorld().getName() + ":" + loc.getX() + ":" + loc.getY() + ":" + loc.getZ() + ":" + loc.getYaw() + ":" + loc.getPitch();
		String strPotFx = buildPotFxString(p.getActivePotionEffects());

		xp.setInventory(pInv);
		xp.setLocation(loc);
		xp.setPotEffects(p.getActivePotionEffects().size() > 0 ? p.getActivePotionEffects() : null);

		Connection conn = plugin.getDbCtrl().getConnection();
		PreparedStatement ps = null;
		try {
			// Store player inventory, location, and potion effects in database.
			String sql;
			if (plugin.getDbCtrl().isMySQL())
				sql = String.format("INSERT IGNORE INTO `%s` VALUES (?, ?, ?, ?, ?)",
						plugin.getDbCtrl().getTable(Table.PLAYERDATA));
			else
				sql = String.format("INSERT INTO `%s` SELECT ?, ?, ?, ?, ? FROM DUAL WHERE NOT EXISTS (SELECT * FROM `%s` WHERE `playername` = ?)",
						plugin.getDbCtrl().getTable(Table.PLAYERDATA), plugin.getDbCtrl().getTable(Table.PLAYERDATA));

			ps = conn.prepareStatement(sql);
			ps.setString(1, p.getName());
			ps.setString(2, strItems);
			ps.setString(3, strArmor);
			ps.setString(4, strLoc);
			ps.setString(5, strPotFx);
			if (!plugin.getDbCtrl().isMySQL())
				ps.setString(6, p.getName());
			ps.executeUpdate();
		} catch (SQLException e) {
			xAuthLog.severe("Failed to insert player data into database!", e);
		} finally {
			plugin.getDbCtrl().close(conn, ps);
		}

		// clear inventory/armor
		pInv.clear();
		pInv.setHelmet(null);
		pInv.setChestplate(null);
		pInv.setLeggings(null);
		pInv.setBoots(null);

		// hide location
		p.teleport(plugin.getLocMngr().getLocation(p.getWorld()));

		// clear potion effects
		plugin.getPlyrMngr().clearPotionEffects(p);
	}

	private String buildItemString(ItemStack[] items) {
		StringBuilder sbItems = new StringBuilder();
		StringBuilder sbAmount = new StringBuilder();
		StringBuilder sbDurability = new StringBuilder();
		StringBuilder sbEnchants = new StringBuilder();

		for (ItemStack item : items) {
			int itemId = 0;
			int amount = 0;
			short durability = 0;
			Map<Enchantment, Integer> enchants = null;

			if (item != null) {
				itemId = item.getTypeId();
				amount = item.getAmount();
				durability = item.getDurability();
				enchants = item.getEnchantments();

				if (!enchants.keySet().isEmpty()) {
					for (Enchantment enchant : enchants.keySet()) {
						int id = enchant.getId();
						int level = enchants.get(enchant);
						sbEnchants.append(id + ":" + level + "-");				
					}

					sbEnchants.deleteCharAt(sbEnchants.lastIndexOf("-"));
				}
			}

			sbItems.append(itemId).append(",");
			sbAmount.append(amount).append(",");
			sbDurability.append(durability).append(",");
			sbEnchants.append(",");
		}

		sbItems.deleteCharAt(sbItems.lastIndexOf(","));
		sbAmount.deleteCharAt(sbAmount.lastIndexOf(","));
		sbDurability.deleteCharAt(sbDurability.lastIndexOf(","));
		sbEnchants.deleteCharAt(sbEnchants.lastIndexOf(","));
		return sbItems.append(";").append(sbAmount).append(";").append(sbDurability).append(";").append(sbEnchants).toString();
	}

	private String buildPotFxString(Collection<PotionEffect> effects) {
		if (effects.size() < 1)
			return null;

		StringBuilder sbType = new StringBuilder();
		StringBuilder sbDur = new StringBuilder();
		StringBuilder sbAmp = new StringBuilder();

		for (PotionEffect effect : effects) {
			sbType.append(effect.getType().getId()).append(",");
			sbDur.append(effect.getDuration()).append(",");
			sbAmp.append(effect.getAmplifier()).append(",");
		}

		sbType.deleteCharAt(sbType.lastIndexOf(","));
		sbDur.deleteCharAt(sbDur.lastIndexOf(","));
		sbAmp.deleteCharAt(sbAmp.lastIndexOf(","));
		return sbType.append(";").append(sbDur).append(";").append(sbAmp).toString();
	}

	private ItemStack[] buildItemStack(String str) {
		ItemStack[] items = null;
		String[] itemSplit = str.split(";");
		String[] itemid = itemSplit[0].split(",");
		String[] amount = itemSplit[1].split(",");
		String[] durability = itemSplit[2].split(",");
		String[] enchants = itemSplit[3].split(",", -1);
		items = new ItemStack[itemid.length];

		for (int i = 0; i < items.length; i++) {
			items[i] = new ItemStack(Integer.parseInt(itemid[i]), Integer.parseInt(amount[i]), Short.parseShort(durability[i]));

			if (!enchants[i].isEmpty()) {
				String[] itemEnchants = enchants[i].split("-");
				for (String enchant : itemEnchants) {
					String[] enchantSplit = enchant.split(":");
					int id = Integer.parseInt(enchantSplit[0]);
					int level = Integer.parseInt(enchantSplit[1]);
					Enchantment e = new EnchantmentWrapper(id);
					items[i].addUnsafeEnchantment(e, level);
				}
			}
		}

		return items;
	}

	private Collection<PotionEffect> buildPotFx(String str) {
		String[] effectSplit = str.split(";");
		String[] type = effectSplit[0].split(",");
		String[] duration = effectSplit[1].split(",");
		String[] amplifier = effectSplit[2].split(",");
		Collection<PotionEffect> effects = new ArrayList<PotionEffect>();

		for (int i = 0; i < type.length; i++) {
			PotionEffectType potFxType = PotionEffectType.getById(Integer.parseInt(type[i]));
			int potFxDur = Integer.parseInt(duration[i]);
			int potFxAmp = Integer.parseInt(amplifier[i]);
			effects.add(new PotionEffect(potFxType, potFxDur, potFxAmp));
		}

		return effects;
	}

	public void restoreData(xAuthPlayer xp, Player p) {
		ItemStack[] items = null;
		ItemStack[] armor = null;
		Location loc = null;
		Collection<PotionEffect> potFx = null;

		PlayerInventory pInv = p.getInventory();
		PlayerInventory xpInv = xp.getInventory();
		Connection conn = plugin.getDbCtrl().getConnection();
		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			String sql = String.format("SELECT `items`, `armor`, `location`, `potioneffects` FROM `%s` WHERE `playername` = ?",
					plugin.getDbCtrl().getTable(Table.PLAYERDATA));
			ps = conn.prepareStatement(sql);
			ps.setString(1, p.getName());
			rs = ps.executeQuery();

			if (rs.next()) {
				items = buildItemStack(rs.getString("items"));
				armor = buildItemStack(rs.getString("armor"));

				String rsLoc = rs.getString("location");
				if (rsLoc != null) {
					String[] locSplit = rsLoc.split(":");
					loc = new Location(Bukkit.getWorld(locSplit[0]), Double.parseDouble(locSplit[1]), Double.parseDouble(locSplit[2]),
							Double.parseDouble(locSplit[3]), Float.parseFloat(locSplit[4]), Float.parseFloat(locSplit[5]));
				}

				String rsPotFx = rs.getString("potioneffects");
				if (rsPotFx != null)
					potFx = buildPotFx(rsPotFx);
			}
		} catch (SQLException e) {
			xAuthLog.severe("Failed to load playerdata from database for player: " + p.getName(), e);
		} finally {
			plugin.getDbCtrl().close(conn, ps, rs);
		}

		if (items != null) {
			// Fix for inventory extension plugins
			if (pInv.getSize() > items.length) {
				ItemStack[] newItems = new ItemStack[pInv.getSize()];
				for(int i = 0; i < items.length; i++)
					newItems[i] = items[i];

				items = newItems;
			}
			//End Fix for inventory extension plugins

			pInv.setContents(items);
		} else
			if (xpInv != null)
				pInv.setContents(xpInv.getContents());

		if (armor != null)
			pInv.setArmorContents(armor);
		else 		
			if (xpInv != null)
				pInv.setArmorContents(xpInv.getArmorContents());

		xp.setInventory(null);

		if (loc != null)
			p.teleport(loc);
		else {
			loc = xp.getLocation();
			if (loc != null)
				p.teleport(loc);
		}

		xp.setLocation(null);

		if (potFx != null) {
			p.addPotionEffects(potFx);
		} else {
			potFx = xp.getPotEffects();
			if (potFx != null)
				p.addPotionEffects(potFx);
		}

		xp.setPotEffects(null);

		conn = plugin.getDbCtrl().getConnection();
		try {
			String sql = String.format("DELETE FROM `%s` WHERE `playername` = ?",
					plugin.getDbCtrl().getTable(Table.PLAYERDATA));
			ps = conn.prepareStatement(sql);
			ps.setString(1, p.getName());
			ps.executeUpdate();
		} catch (SQLException e) {
			xAuthLog.severe("Could not delete playerdata record from database for player: " + p.getName(), e);
		} finally {
			plugin.getDbCtrl().close(conn, ps);
		}
	}
}