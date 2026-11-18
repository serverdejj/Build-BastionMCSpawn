# BastionMCSpawn
Plugin Paper/Purpur 1.21.11 (Java 21).

- `/setspawn`: OP, guarda ubicación y orientación en `plugins/BastionMCSpawn/spawn.yml`.
- `/spawn`: todos los jugadores; Minecraft autocompleta el comando.
- Primera entrada: envía al jugador al spawn guardado.
- Respawn: usa el spawn guardado, incluso si el jugador tenía cama o ancla de reaparición.
- Mensajes configurables en `config.yml`.

Compila con GitHub Actions o `mvn clean package`. Descarga el artefacto JAR e instálalo en `plugins/`. Reinicia el servidor y usa `/setspawn`.

Nota: si otro plugin también gestiona spawn/respawn o intercepta teletransportes, puede haber conflictos de prioridad.

## Spawn al entrar
En `config.yml`, `join.teleport-to-spawn: true` envia a todos los jugadores al spawn establecido con `/setspawn` al conectarse. Con `false`, solo se teletransporta a los jugadores nuevos. Reinicia el servidor o usa `/bastionspawn reload` si ese comando recarga config.yml.


## Corrección primer ingreso (2026-11-18)
- `join.teleport-to-spawn: true`: **solo primer ingreso**, no cada reconexión.
- El teletransporte inicial coloca un indicador temporal en el jugador para evitar que BastionMCNameTangs monte un pasajero TextDisplay durante el traslado.
- Los jugadores existentes vuelven al punto en que se desconectaron.
- Requiere compilar y actualizar ambos plugins para la coordinación completa.
- `/spawn`, `/setspawn`, mensajes y demás funciones permanecen sin cambios.
