# Changelog

## 1.1.1 — 2026-11-08

### Nuevo
- **Prefijo del rango sobre la cabeza** (TukiCore o LuckPerms) para quien no tiene equipo de UHC, con el tab ordenado por peso del rango (`ranks.nametag-prefix`).
- **`/uhcdev`**: `info`, `skip` (termina la parte), `time <segundo>` y `pvp` (fuerza el PvP). Requiere `tukiuhc.dev`, que no viene con OP: lo da el modo dev de TukiCore.

### Correcciones
- Si al archivo `lang/` del servidor le falta un texto nuevo, se usa el del jar en vez de mostrar la clave.

## 1.1.0 — 2026-11-07

TukiUHC queda solo para el UHC; la parte administrativa (rangos, chat, moderación) pasa a **TukiCore**, que es compatible.

### Nuevo
- Compatibilidad con **TukiCore**: prefijos de rango en el chat de la partida; en el lobby el chat y el tab son de TukiCore.
- **PlaceholderAPI**: `%tukiuhc_...%` y `<papi:nombre>` en los textos. Pie del scoreboard configurable (`scoreboard.footer`).
- **LuckPerms** (sin TukiCore): prefijos en chat y tab del lobby.
- **BungeeCord/Velocity**: `/uhchub` (`/hub`, `/lobby`), envío al lobby al terminar con reinicio o apagado automático, bloqueo de entradas tardías y MOTD con el estado.
- **Inicio automático** con mínimo de jugadores y cuenta atrás.
- **Recompensas** con comandos de consola (ganar, kill, eliminado, participación).
- **API**: eventos de preparación (cancelable), inicio, nueva parte, eliminación y final.
- Permisos por comando (`tukiuhc.command.*`), `tukiuhc.staff` con espía de chats y `/uhcreload`.
- Quien está en vanish (TukiCore, EssentialsX, SuperVanish…) no entra en la partida ni cuenta para el inicio automático.
- Eliminación de jugadores desconectados después de `death.offline-elimination-minutes` (sus ítems caen donde se fueron).

### Correcciones
- Quien se reconectaba durante la cuenta atrás quedaba congelado e inmortal para siempre; quien no estaba al repartir ahora va a su punto al volver.
- `/afk` daba inmunidad permanente en partida; ahora solo funciona en el descanso y se quita al pegar o al empezar la parte.
- Con rotación de skins, los jugadores sin equipo ya revelados no se podían golpear entre sí.
- El staff con `tukiuhc.spectator` caía del lobby y moría en bucle; ahora pasa a espectador sobre el mapa.
- Detener la partida mientras se repartía dejaba tareas pendientes que teletransportaban y congelaban a los jugadores.
- Las victorias se contaban para todos los vivos (dragón, Resource Rush, /uhcstop); ahora solo para los ganadores.
- Los equipos podían aparecer juntos: la distancia mínima entre puntos de inicio no se respetaba.
- Equipos con el mismo nombre se mezclaban en eliminaciones y estadísticas.
- El portal del modo Dragón (y los puntos de espera) se construían sobre el techo del lobby.
- La zona de la Era del Cobre nunca se acababa; el Crafter ignoraba las recetas desactivadas; Trolls y Ratas sin equipos no repartía roles.
- Al reiniciar se restauran la regla de reaparición inmediata y la vida/efectos de la partida anterior.
- El scoreboard ya no reenvía cada segundo datos que no cambiaron (menos paquetes con muchos equipos).
- Lectura segura de equipos y vivos desde el chat (asíncrono).

## 1.0.1 — 2026-11-07

Parche con las correcciones y mejoras tras las primeras pruebas.

### Correcciones
- Rotación de skins: los nombres "???" y los jugadores del tab ya no desaparecen tras cambiar las skins.
- El menú mostraba "menu.on/menu.off" y /afk no tenía textos (YAML leía on/off como booleanos).
- El reparto evitaba poco el agua y el congelamiento hundía a los jugadores.
- La Resistencia II del aumento de vida era infinita; ahora dura solo esa parte.
- El lobby seguía en el cielo durante la partida y quien entraba tarde aparecía allí.
- El enlace del maniquí se veía como código y salía dos veces.
- La zona de resultados era visible antes de terminar.
- "El PvP todavía no está activado" llenaba el chat; ahora es un aviso breve en la barra.
- El scoreboard mostraba "sin equipo" en partidas sin equipos.
- Los archivos de idioma viejos se actualizan solos (se guarda una copia .old).

### Mejoras
- Mini lobby con maniquí de ItsTuki y zona de resultados con podio, tops de la partida y tops históricos.
- Scoreboard con tu equipo, compañeros y lista de equipos; en modo Manual quien no tenga equipo se reparte al azar.
- Rotación de skins: al ser golpeado recuperas tu skin original y quedas revelado a todos; los demás salen como ??? en el tab, y tu equipo y los revelados se ven con nombre sobre la cabeza.
- Aviso de fin de parte a los 30, 10 y 5…1 segundos.
- Libro de configuración en el lobby para los organizadores.
- Ajustes rápidos: modo Directo (5 × 60 min) y modo Episodios (10 × 25 min).
- Modo escolta: la distancia máxima baja 10 bloques por parte hasta 40.
- Trolls y Ratas: mínimo 3 jugadores, equipos de 3, Antorcha Troll (pista con ruido, cada 5 min), las Ratas se revelan y se agrupan en la penúltima parte y ganan si solo quedan ellas.
- Resource Rush: sin objetivos duplicados entre niveles; agacharse 3 veces muestra la lista de objetivos.
- /uhcteam force agrupa al azar a los jugadores vivos sin equipo.

## 1.0.0 — 2026-11-07

Primera versión de TukiUHC para Paper 26.3 (Java 25).

- Partida por episodios con descanso opcional, episodio extra y tiempo extra.
- Reglas UHC: sin regeneración natural, cabeza en pica al morir, Cabeza y Calavera Dorada, crafteos modificados, ghasts con oro.
- Borde que se cierra hacia 0 0 y batalla final.
- Modos: PvP por equipos, Solitarios, Dragón (PvD / un jugador), Trolls y Ratas, Resource Rush.
- Equipos manuales, aleatorios (revelación con cuenta atrás) y "amor a primera vista"; grupos.
- Chats de equipo, global (!), grupo (@), ratas (#) y muertos.
- 20 opciones activables: brújula, escolta, resurrección, shulker, aumento de vida, localizador, tumbas, caña roba-cabezas, tridentes, botín de puestos y campamentos, ciudades antiguas, pacto de caballeros, Edad de Cobre, rotación de skins, super/hiper manzanas, aliento de dragón.
- Menú /uhcconfig completo, scoreboard y tab propios, idiomas ES/EN.
- Estadísticas finales en el chat e imagen PNG.
