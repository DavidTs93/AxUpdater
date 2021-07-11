package me.DMan16.AxUpdater;

import me.Aldreda.AxUtils.AxUtils;
import me.Aldreda.AxUtils.Utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public class AxUpdater extends JavaPlugin implements CommandExecutor,Listener {
	private static Connection connection = null;
	
	public void onEnable() {
		saveDefaultConfig();
		File configFile = new File("plugins/" + this.getName() + "/versions.yml");
		YamlConfiguration config;
		try {
			if (!configFile.exists()) configFile.createNewFile();
			config = YamlConfiguration.loadConfiguration(configFile);
			if (connection == null) {
				connection = DriverManager.getConnection("jdbc:mysql://" + getConfig().getString("mysql.host") + ":" + getConfig().getInt("mysql.port") + "/" +
						getConfig().getString("mysql.database") + "?autoReconnect=true&useSSL=false",getConfig().getString("mysql.username"),getConfig().getString("mysql.password"));
			}
		} catch (Exception e) {
			this.getLogger().severe("MySQL error: ");
			e.printStackTrace();
			Bukkit.getPluginManager().disablePlugin(this);
			Bukkit.shutdown();
			return;
		}
		boolean updated = false;
		try {
			connection.createStatement().execute("CREATE TABLE IF NOT EXISTS Updates (Plugin VARCHAR(100) NOT NULL UNIQUE, Version TEXT NOT NULL, URL TEXT NOT NULL);");
		} catch (Exception e) {e.printStackTrace();}
		try {
			ResultSet result = connection.createStatement().executeQuery("SELECT * FROM Updates;");
			while (result.next()) try {
				String plugin = result.getString("Plugin").trim().replace(" ","_");
				if (plugin.isEmpty()) continue;
				String version = result.getString("Version");
				String URL = result.getString("URL");
				String current = config.getString(plugin,null);
				String msg = "&aPlugin &b" + plugin + "&a: ";
				if (current == null || !version.equalsIgnoreCase(current)) try {
					URL download = new URL(URL);
					BufferedInputStream in = null;
					FileOutputStream out = null;
					try {
						Utils.chatColorsLogPlugin("&fPlugin &b" + plugin + "&f: " + (current == null ? "downloading" : "updating from version &b" + current));
						in = new BufferedInputStream(download.openStream());
						out = new FileOutputStream("plugins/" + plugin + ".jar");
						byte[] data = new byte[1024];
						int count;
						while ((count = in.read(data,0,1024)) != -1) out.write(data,0,count);
						msg += "updated to version &b" + version + "&a!";
						current = version;
						updated = true;
					} catch (Exception e) {
						getLogger().severe("Error updating plugin " + plugin + "! Error:");
						e.printStackTrace();
					} finally {
						if (in != null) in.close();
						if (out != null) out.close();
					}
				} catch (Exception e) {
					getLogger().warning("Error while working on plugin " + plugin + ". Error:");
					e.printStackTrace();
					msg = null;
				} else msg += "up-to-date";
				if (msg != null) Utils.chatColorsLogPlugin(msg);
				if (current != null) config.set(plugin,current);
			} catch (Exception e) {e.printStackTrace();}
		} catch (Exception e) {e.printStackTrace();}
		try {
			config.save(configFile);
		} catch (IOException e) {e.printStackTrace();}
		Utils.chatColorsLogPlugin("&fFinished checking for updates");
		if (updated) {
			Utils.chatColorsLogPlugin("&fPlugins have been updated - restarting!");
			Bukkit.spigot().restart();
			return;
		}
		try {
			Statement statement = connection.createStatement();
			DatabaseMetaData data = connection.getMetaData();
			statement.execute("CREATE TABLE IF NOT EXISTS Players (UUID VARCHAR(36) NOT NULL UNIQUE,Name TEXT NOT NULL);");
			if (!data.getColumns(null,null,"Players","UUID").next())
				statement.execute("ALTER TABLE Players ADD UUID VARCHAR(36) NOT NULL UNIQUE;");
			if (!data.getColumns(null,null,"Players","Name").next())
				statement.execute("ALTER TABLE Players ADD Name TEXT NOT NULL;");
			statement.close();
		} catch (Exception e) {}
		getCommand("axupdater").setExecutor(this);
		getServer().getPluginManager().registerEvents(this,this);
	}
	
	public static final Connection getConnection() {
		return connection;
	}
	
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length >= 2) try {
			String plugin = args[0].trim().replace(" ","_");
			if (connection.createStatement().executeQuery("SELECT * FROM Updates WHERE Plugin=\"" + plugin + "\";").next()) {
				String version = String.join(" ", Arrays.copyOfRange(args,1,args.length));
				connection.createStatement().executeUpdate("UPDATE Updates SET Version=\"" + version + "\" WHERE Plugin=\"" + plugin + "\";");
				Utils.chatColors(sender, "&aPlugin &b" + args[0] + "&a: version updated to &b" + version);
			}
		} catch (Exception e) {}
		return true;
	}
	
	@EventHandler
	public void updatePlayerDatabase(AsyncPlayerPreLoginEvent event) {
		try {
			PreparedStatement statement = AxUtils.getConnection().prepareStatement("SELECT * FROM Players WHERE UUID=?;");
			statement.setString(1,event.getUniqueId().toString());
			if (statement.executeQuery().next()) statement = connection.prepareStatement("UPDATE Players SET Name=? WHERE UUID=?;");
			else statement = connection.prepareStatement("INSERT INTO Players (Name,UUID) VALUES (?,?);");
			statement.setString(1,event.getName());
			statement.setString(2,event.getUniqueId().toString());
			try {
				statement.executeUpdate();
			} catch (SQLException e) {e.printStackTrace();}
			statement.close();
		} catch (SQLException e) {
			event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,Component.translatable("multiplayer.aldreda.login_error",NamedTextColor.RED));
		}
	}
	
	public static UUID getPlayerUUIDByName(String name) {
		Objects.requireNonNull(name,"Name can't be null");
		UUID ID = null;
		try {
			Statement statement = connection.createStatement();
			ResultSet result = statement.executeQuery("SELECT * FROM Players;");
			while (result.next()) try {
				if (result.getString("Name").equalsIgnoreCase(name)) {
					ID = UUID.fromString(result.getString("UUID"));
					break;
				}
			} catch (Exception e) {}
			result.close();
			statement.close();
		} catch (SQLException e) {}
		return ID;
	}
	
	public static String getPlayerNameByUUID(UUID ID) {
		Objects.requireNonNull(ID,"ID can't be null");
		String name = null;
		try {
			Statement statement = connection.createStatement();
			ResultSet result = statement.executeQuery("SELECT * FROM Players;");
			while (result.next()) try {
				if (ID.toString().equals(result.getString("UUID"))) {
					name = result.getString("Name");
					break;
				}
			} catch (Exception e) {}
			result.close();
			statement.close();
		} catch (SQLException e) {}
		return name;
	}
}