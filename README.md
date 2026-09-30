# McServer SOCKS5

A Paper 1.21.11 plugin that starts a SOCKS5 `CONNECT` proxy alongside the Minecraft server.
It supports the SOCKS5 no-authentication method and IPv4, IPv6, and domain destinations.

## Install and configure

1. Build with `mvn package` (Java 21), then place the JAR from `target/` in Paper's `plugins/` directory.
2. Start the server once to create `plugins/McServerSocks5/config.yml`.
3. Set `bind-address`, `port`, `connect-timeout-ms`, and `buffer-size`, then restart the server.

`buffer-size` defaults to 65,536 bytes per relay direction and is also applied to the
TCP send/receive buffers. It may be set from 4,096 to 1,048,576 bytes; increase it
for high-throughput traffic, but remember that each concurrent connection uses two relay buffers.

The default bind address is `127.0.0.1`. This proxy has **no authentication**; keep it on a trusted network or protect it with firewall rules.
