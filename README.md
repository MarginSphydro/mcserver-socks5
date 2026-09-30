# McServer SOCKS5

A Paper 1.21.11 plugin that starts a SOCKS5 `CONNECT` proxy alongside the Minecraft server.
It supports the SOCKS5 no-authentication method and IPv4, IPv6, and domain destinations.

## Install and configure

1. Build with `mvn package` (Java 21), then place the JAR from `target/` in Paper's `plugins/` directory.
2. Start the server once to create `plugins/McServerSocks5/config.yml`.
3. Set `bind-address`, `port`, and `connect-timeout-ms`, then restart the server.

The default bind address is `127.0.0.1`. This proxy has **no authentication**; keep it on a trusted network or protect it with firewall rules.
