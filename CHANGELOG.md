# Changelog

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
