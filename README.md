# BastionMCSpawn
Plugin Paper/Purpur 1.21.11 (Java 21).

- `/setspawn`: OP, guarda ubicación y orientación en `plugins/BastionMCSpawn/spawn.yml`.
- `/spawn`: todos los jugadores; Minecraft autocompleta el comando.
- Primera entrada: envía al jugador al spawn guardado.
- Respawn: usa el spawn guardado, incluso si el jugador tenía cama o ancla de reaparición.
- Mensajes configurables en `config.yml`.

Compila con GitHub Actions o `mvn clean package`. Descarga el artefacto JAR e instálalo en `plugins/`. Reinicia el servidor y usa `/setspawn`.

Nota: si otro plugin también gestiona spawn/respawn o intercepta teletransportes, puede haber conflictos de prioridad.
