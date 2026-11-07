# TukiUHC

Plugin de **UHC por episodios** para servidores **Paper 26.3** (Java 25), totalmente configurable desde el juego. **No necesita** TAB ni SkinRestorer: incluye su propio scoreboard, tab con vida y rotación de skins.

> Creado por **ItsTuki**. Inspirado en el formato de las series UHC por episodios (sin regeneración natural, partes de 20 minutos, cabezas en picas).

## Instalación

1. Descarga `TukiUHC-<versión>.jar` desde [Releases](../../releases).
2. Cópialo a `plugins/` en un servidor Paper 26.3 con Java 25 y reinicia.
3. Entra como OP y usa **`/uhcconfig`**.

## Cómo se juega una partida

1. **`/uhcconfig`** → elige modo, equipos y opciones.
2. **`/uhcprepare`** → reparte a todos por el mapa (juntos por equipo si está activo), los congela y les da un bote.
3. **`/uhcstart`** → cuenta atrás de 10 s (o **`/uhcforcestart`** si falta alguien).
4. La partida avanza por **partes** (episodios). El scoreboard muestra parte, fase (PVE/PVP/PVD), compañeros con vida y distancia, tiempo y borde.
5. Al terminar se anuncian las estadísticas y se guarda una **imagen PNG** en `plugins/TukiUHC/stats/`.

## Mecánicas fijas

- Sin regeneración natural: solo manzanas doradas, cabezas y pociones.
- Al morir, la cabeza del jugador queda **clavada en una pica** sobre un bloque útil aleatorio.
- **Cabeza Dorada** (8 lingotes + cabeza de jugador) y **Calavera Dorada** (con cabeza de esqueleto wither): Regeneración II (4 ❤) + 2 ❤ de absorción.
- Crafteos UHC: melón reluciente (bloque de oro + rodaja), zanahoria dorada (8 lingotes), manzana encantada (8 bloques de oro), ghast seco.
- Los ghasts sueltan oro en vez de lágrimas; la sopa sospechosa solo da brillo.
- Borde que se cierra hacia **0 0** y **batalla final** en la última parte.

## Modos de juego

| Modo | Descripción |
|---|---|
| PvP por equipos | Gana el último equipo con vida. |
| Solitarios | Todos contra todos. |
| Dragón (PvD) | Portal al End en 0 0 con 9 de 12 ojos. Gana quien remate a la dragona antes de que acabe el tiempo. Sirve para un jugador. |
| Trolls y Ratas | Equipos de 3 con roles secretos: **Troll** (no puede dañar a su equipo), **Rata** (elimina a su equipo solo de forma indirecta, chat privado con `#`) y **Normal** (puede ejecutar sospechosos; si mata al Troll pierde la mitad de la vida). |
| Resource Rush | Conseguir 12 objetos en orden de dificultad. Lista editable en el juego o en `resource-rush.yml`. |

## Equipos

- **Manuales**: menú para crear/borrar equipos y grupos y asignar jugadores, o `/uhcteam`.
- **Aleatorios**: todos empiezan solos y los equipos se revelan en la parte elegida, con cuenta atrás.
- **Amor a primera vista**: los equipos se forman al juntarse (aviso de distancia y brillo a menos de 100 bloques).
- **Chats**: normal = equipo, `!` = todos, `@` = grupo, `#` = Ratas. Los muertos hablan entre ellos.

## Opciones (`/uhcconfig` → Opciones)

Clic izquierdo activa/desactiva, clic derecho cambia el valor (episodio, %, segundos o bloques).

| Opción | Qué hace | Por defecto |
|---|---|---|
| Brújula de compañero | Brújula que apunta al compañero | Parte 2 |
| Empezar junto al compañero | Los equipos aparecen juntos | Sí |
| Resurrección | Cristal de Resurrección sobre bedrock revive a un compañero (1 vez) | No |
| Modo escolta | Separarse más de X bloques quita 1 ❤ cada 10 s | No (100) |
| Episodio extra | +1 parte | No |
| Descanso | Pausa entre partes | No (5 s) |
| Localizador | Barra localizadora de jugadores | Última parte |
| Rotación de skins | Intercambio de skins y nombres ocultos; golpear revela | No |
| Caña roba-cabezas | Enganchar a un enemigo le roba la cabeza | No |
| Tridentes | Los ahogados sueltan tridente (%) | No (50 %) |
| Diamantes en puestos | 1-2 diamantes en puestos de saqueadores (%) | No (50 %) |
| Campamentos | Cofres secretos con mazo, tridente o élitros (%) | No (50 %) |
| Limitar ciudades antiguas | Sin pociones de regeneración ni manzanas encantadas | Sí |
| Pacto de caballeros | Sin PvP hasta la parte indicada | No (4) |
| Shulker Box | Todos reciben una (aviso 30 s antes) | Parte 4 |
| Aumento de vida | +10 ❤ y Resistencia II | Última parte |
| Edad de Cobre | Estatua de Golem de Cobre: Área de Bendición con regeneración; si un enemigo la rompe, el equipo pierde 3 ❤ | No |
| Tumbas | Lista de dónde murió cada uno | No (4) |
| Super e Hiper manzanas | Super (+4 ❤ al instante) e Hiper (+2 ❤ máx., hasta 5) | No |
| Aliento de dragón | Crafteable con poción arrojadiza | No |

## Comandos

| Comando | Permiso | |
|---|---|---|
| `/uhchelp` | — | Ayuda |
| `/uhcconfig` | `tukiuhc.admin` | Menú de configuración |
| `/uhcprepare` · `/uhcstart` · `/uhcforcestart` | `tukiuhc.admin` | Preparar e iniciar |
| `/uhcstop` · `/sos` | `tukiuhc.admin` | Terminar / reiniciar y recargar |
| `/uhcteam create\|delete\|add\|remove\|random\|clear\|list` (y `groupcreate`, `groupadd`…) | `tukiuhc.admin` | Equipos y grupos |
| `/heal <jugador>` · `/uhcrevive <jugador>` · `/uhcgive <ítem>` | `tukiuhc.admin` | Utilidades |
| `/afk` | `tukiuhc.afk` | Invulnerable hasta moverte |
| — | `tukiuhc.spectator` | No participa en las partidas (staff que solo mira) |
| `/renameteam <nombre>` | `tukiuhc.renameteam` | Renombrar tu equipo |
| `/uhcrole [revelar]` | — | Tu rol en Trolls y Ratas |

## Idiomas

Español e inglés (`/uhcconfig` → Idioma). Todos los textos están en `plugins/TukiUHC/lang/` en formato MiniMessage.

## Compilar

```bash
./gradlew build        # jar en build/libs/
./gradlew runServer    # servidor Paper 26.3 de prueba
```
