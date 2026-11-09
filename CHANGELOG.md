# Changelog

## 1.2.1 — 2026-11-08

Para las dos ediciones.

### Nuevo
- **Espectadores fantasma**: al morir (o al entrar a mirar) vuelas, eres invisible para los vivos y no puedes tocar nada. La **brújula** abre la lista de jugadores vivos: clic izquierdo para ir con uno, clic derecho para ver su inventario, vida, comida y efectos sin poder cambiarlos. Con `spectators.ghost: false` se usa el modo espectador normal.
- **Cofre de muerte**: las cosas del jugador quedan en un cofre bajo la pica con su cabeza (dos cofres si no caben; el bloque útil va dentro). Se desactiva con `death.chest: false`.

### Arreglos
- Al terminar la partida se quita el borde, que ya no tapa la zona de resultados.
- `/uhcrevive` hace aparecer al jugador en un punto al azar dentro del borde, como en el reparto.

## 1.2.0 — 2026-11-08

Para las dos ediciones: completa (Paper 26.x) y Legacy (1.8.8 – 26.3).

### Nuevo
- **Partida por tiempo**: duración total configurable (ej. 2 horas) y un borde que se cierra **sin parar** desde el minuto elegido hasta el final. Si se acaba el tiempo, **muerte súbita**: PvP, borde a 10 bloques del centro y todos brillan.
- **Pregeneración del mapa** (`/uhcpregen`, menú Mapa): genera los chunks dentro del borde con progreso, tiempo restante y control de TPS.
- **Más minerales** y **cuevas más grandes** al generar o pregenerar (vetas y alturas configurables en `map`).
- **Escenarios**: CutClean, Timber, HasteyBoys, Doble/Triple mineral, LuckyLeaves, Fireless, NoFall, NoClean, BloodDiamonds, Diamondless, Goldless, Bowless, Rodless y Horseless.
- Placeholders `%tukiuhc_format%` y `%tukiuhc_sudden_death%`; `%tukiuhc_time_left%` muestra el tiempo total restante por tiempo.

## 1.1.1 Legacy — 2026-11-08

### Nuevo
- **Edición Legacy** (`TukiUHC-Legacy-1.1.1.jar`): funciona en Spigot, PaperSpigot y Paper desde **1.8.8 hasta 26.3**, con los mismos modos, opciones, comandos, lobby y textos que la edición completa.
- Materiales, sonidos y efectos se adaptan a cada versión (XSeries); los textos usan Adventure incluido en el jar.
- Lo que no existe en versiones antiguas se oculta solo del menú (Era del Cobre, barra localizadora, tridentes antes de 1.13, etc.).
- La rotación de skins en Legacy oculta los nombres sin cambiar las skins; el maniquí y el podio usan armor stands.

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
