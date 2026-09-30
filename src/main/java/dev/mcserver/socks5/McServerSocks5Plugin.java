package dev.mcserver.socks5;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.InetAddress;
import java.util.logging.Level;

public final class McServerSocks5Plugin extends JavaPlugin {
    private Socks5Server socks5Server;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String bindAddress = getConfig().getString("bind-address", "127.0.0.1");
        int port = getConfig().getInt("port", 1080);
        int connectTimeout = getConfig().getInt("connect-timeout-ms", 10_000);
        if (port < 1 || port > 65_535) {
            getLogger().severe("Invalid SOCKS5 port: " + port);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            socks5Server = new Socks5Server(InetAddress.getByName(bindAddress), port, connectTimeout, getLogger());
            socks5Server.start();
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Could not start SOCKS5 server on " + bindAddress + ':' + port, exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (socks5Server != null) {
            socks5Server.close();
            socks5Server = null;
        }
    }
}
