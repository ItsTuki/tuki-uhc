# TukiUHC

Plugin de **UHC por episodios** para servidores **Paper 26.3** (Java 25) y, con la edición **Legacy**, de **1.8.8 a 26.3**, totalmente configurable desde el juego. **No necesita** TAB ni SkinRestorer: incluye su propio scoreboard, tab con vida y rotación de skins.

> Creado por **ItsTuki**. Inspirado en el formato de las series UHC por episodios (sin regeneración natural, partes de 20 minutos, cabezas en picas).

## Ediciones

| Jar | Servidores | Java |
|---|---|---|
| `TukiUHC-<versión>.jar` (**completa**) | Paper 26.1 – 26.3 | 25 |
| `TukiUHC-Legacy-<versión>.jar` (**Legacy**) | Spigot / PaperSpigot / Paper **1.8.8 – 26.3** | 8 o superior (lo que pida tu servidor) |

La edición **Legacy** tiene los mismos modos, opciones, comandos, lobby, scoreboard y textos. Diferencias:

- Sin **Era del Cobre**, barra localizadora, diamantes de puesto avanzado, botín de campamento ni límite de la Ciudad Antigua (no existen en versiones antiguas).
- **Rotación de skins**: solo oculta los nombres (`???`), no cambia las skins.
- El maniquí y las estatuas del podio son **armor stands** con la cabeza del jugador; los textos flotantes también.
- Opciones que dependen de la versión se ocultan solas: tridentes (1.13+), cajas de shulker (1.11+), aliento de dragón y manzana encantada fabricable (1.9+).
- En 1.8–1.17 el lobby se construye más abajo si `lobby.y` supera la altura máxima del mundo.
- Selectores en comandos: nombre, `@a` o `@s`.
- Pregeneración: en Paper 1.13+ va en paralelo; en Spigot y en 1.8–1.12 se hace por tramos en el hilo principal (más lenta).

Probada con partidas completas en 1.8.8, 1.12.2, 1.16.5, 1.18.2, 1.20.4, 1.21.4 y 26.3.

## Instalación

1. Descarga desde [Releases](../../releases) el jar de tu edición (ver tabla).
2. Cópialo a `plugins/` y reinicia.
3. Entra como OP y usa **`/uhcconfig`**.

## Cómo se juega una partida

1. **`/uhcconfig`** → elige modo, equipos y opciones.
2. **`/uhcprepare`** → reparte a todos por el mapa (juntos por equipo si está activo), los congela y les da un bote.
3. **`/uhcstart`** → cuenta atrás de 10 s (o **`/uhcforcestart`** si falta alguien).
4. La partida avanza por **partes** (episodios) o **por tiempo** (ver abajo). El scoreboard muestra parte o tiempo restante, fase (PVE/PVP/PVD), compañeros con vida y distancia, tiempo y borde.
5. Al terminar se anuncian las estadísticas y se guarda una **imagen PNG** en `plugins/TukiUHC/stats/`.

## Formato: por episodios o por tiempo

En `/uhcconfig` → Ajustes generales → **Formato de la partida**:

- **Por episodios**: partes de X minutos con título, aviso de fin y descanso opcional. El borde se cierra durante la parte elegida.
- **Por tiempo**: eliges la **duración total** (por ejemplo 2 horas) y el **minuto en que empieza a cerrarse el borde**. Desde ahí el borde se cierra **sin parar** hasta el radio final justo al acabar el tiempo. Las opciones que dependen de una "parte" (PvP, brújula, shulker…) usan tramos internos de la duración de cada parte (20 min por defecto), sin títulos ni descansos.
- Si se acaba el tiempo con más de un equipo vivo empieza la **muerte súbita**: PvP activo, el borde baja a un círculo pequeño en 0 0 (`timed.sudden-death-radius`, 10 bloques en 3 min) y todos brillan hasta que quede un ganador.

## Mapa: pregeneración, más minerales y cuevas grandes

En `/uhcconfig` → **Mapa**, o con **`/uhcpregen start|stop`**:

- **Pregenerar** genera todos los chunks dentro del borde (más un margen) antes de jugar, de adentro hacia afuera, con barra de progreso y tiempo restante. Baja el ritmo sola si los TPS caen. Se detiene al preparar la partida y se puede reanudar.
- **Más minerales**: vetas extra de diamante, oro, hierro, lapislázuli y redstone. El valor es el porcentaje extra (100 = el doble).
- **Cuevas más grandes**: salas amplias y túneles largos bajo tierra, sin abrir agua ni lava.
- Las mejoras se aplican a cada chunk **una sola vez**, al generarse o al pregenerar, y se recuerdan en `plugins/TukiUHC/map/`. Lo ideal es activarlas y pregenerar en un mundo nuevo. Minerales, alturas y fuerza de las cuevas se ajustan en `config.yml` → `map`.

## Escenarios

En `/uhcconfig` → **Escenarios** (se combinan entre sí y se anuncian al empezar):

| Escenario | Qué hace |
|---|---|
| CutClean | Minerales fundidos, grava → pedernal, comida cocinada |
| Timber | Romper un tronco tala el árbol entero (agachado rompe uno) |
| HasteyBoys | Picos, hachas y palas con Eficiencia III e Irrompibilidad III |
| Doble / Triple mineral | Los minerales dan ×2 o ×3 |
| LuckyLeaves | Las hojas pueden soltar una manzana dorada |
| Fireless · NoFall | Sin daño de fuego/lava · sin daño de caída |
| NoClean | Tras una kill, 20 s invulnerable a jugadores (se quita si atacas) |
| BloodDiamonds | Picar diamante quita medio corazón |
| Diamondless · Goldless | El diamante / el oro no sueltan nada (en Goldless cada eliminado suelta 8 lingotes) |
| Bowless · Rodless · Horseless | Sin arcos · sin cañas · sin montar caballos |

## Lobby y resultados

- Al entrar al servidor todos aparecen en un **mini lobby flotante** (sobre 0 0, a altura 300) con un **maniquí de ItsTuki** con su skin. No se puede romper ni recibir daño.
- Al terminar la partida, a los 8 segundos se construye la **zona de resultados** (no existe antes, así que no se ve desde el lobby) y todos pasan a ella: podio de oro, hierro y cobre con maniquíes de los 3 mejores jugadores, **top de kills y de supervivencia** de la partida, y **tops históricos** de victorias y kills (guardados en `leaderboard.yml`).
- `/sos` devuelve a todos al lobby y desmonta la zona de resultados. Posición, mundo, usuario del maniquí y demora se cambian en `config.yml` → `lobby`.

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
| Trolls y Ratas | Mínimo 3 jugadores, equipos de 3 con roles secretos: **Troll** (no puede dañar a su equipo; tiene una **Antorcha Troll** que cada 5 min puede hacer que la Rata se delate con un ruido), **Rata** (elimina a su equipo solo de forma indirecta, chat privado con `#`; en la penúltima parte las Ratas se revelan y forman equipo, y ganan si solo quedan ellas) y **Normal** (puede ejecutar sospechosos; si mata al Troll pierde la mitad de la vida). |
| Resource Rush | Conseguir 12 objetos en orden de dificultad. Agáchate 3 veces seguidas para ver la lista. Editable en el juego o en `resource-rush.yml`. |

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
| Más minerales | Vetas extra al generar el mapa (% extra) | No (100 %) |
| Cuevas más grandes | Salas y túneles más grandes al generar el mapa | No |

## Comandos

| Comando | Permiso | |
|---|---|---|
| `/uhchelp` | — | Ayuda |
| `/uhcconfig` | `tukiuhc.command.config` | Menú de configuración |
| `/uhcprepare` | `tukiuhc.command.prepare` | Repartir a los jugadores |
| `/uhcstart` · `/uhcforcestart` | `tukiuhc.command.start` | Iniciar |
| `/uhcstop` | `tukiuhc.command.stop` | Terminar sin ganador |
| `/sos` (`/uhcreset`) | `tukiuhc.command.reset` | Reiniciar la partida y recargar |
| `/uhcreload` | `tukiuhc.command.reload` | Recargar config.yml e idiomas |
| `/uhcpregen start\|stop\|status` | `tukiuhc.command.pregen` | Pregenerar el mapa |
| `/uhcteam create\|delete\|add\|remove\|random\|force\|clear\|list` (y `groupcreate`, `groupadd`…) | `tukiuhc.command.team` | Equipos y grupos |
| `/heal` · `/uhcrevive` · `/uhcgive` | `tukiuhc.command.heal` · `.revive` · `.give` | Utilidades |
| `/uhchub` (y `/hub`, `/lobby` con proxy) | `tukiuhc.hub` | Volver al lobby de la network |
| `/uhcdev info\|skip\|time\|pvp` | `tukiuhc.dev` (no viene con OP; lo da el modo dev de TukiCore) | Herramientas de desarrollo |
| `/afk` | `tukiuhc.afk` | Invulnerable hasta moverte (en partida, solo en el descanso) |
| `/renameteam <nombre>` | `tukiuhc.renameteam` | Renombrar tu equipo |
| `/uhcrole [revelar]` | — | Tu rol en Trolls y Ratas |

### Permisos agrupados

| Permiso | Incluye |
|---|---|
| `tukiuhc.admin` (OP) | Todos los `tukiuhc.command.*` y `tukiuhc.staff` |
| `tukiuhc.staff` | `tukiuhc.chat.spy` (ver chats de equipo como espectador) y `tukiuhc.join.spectate` |
| `tukiuhc.spectator` | No participa en las partidas: mira como espectador |
| `tukiuhc.join.spectate` | Entrar a mirar con la partida en curso cuando `proxy.block-late-joins` está activo |

## Network y compatibilidades

Todo es opcional: TukiUHC funciona solo.

- **[TukiCore](https://github.com/ItsTuki/tuki-core)** (recomendado): rangos, prefijos, chat y moderación. Si está instalado, TukiUHC usa sus prefijos en el chat de la partida (`<rank>`), y en el lobby el chat y el tab los maneja TukiCore. Los silencios y el anti-spam de TukiCore también valen en partida.
- **LuckPerms**: sin TukiCore, TukiUHC muestra el prefijo de LuckPerms en el chat y en el tab del lobby (`luckperms.tab-prefix`).
- **PlaceholderAPI**: registra `%tukiuhc_<x>%` y permite `<papi:nombre>` en cualquier texto de `lang/*.yml` (por ejemplo en `scoreboard.footer`).
  - Globales: `state`, `mode`, `team_mode`, `team_size`, `match_name`, `format`, `episode`, `episodes`, `phase`, `pvp`, `time`, `time_left`, `sudden_death`, `alive`, `alive_teams`, `participants`, `border`.
  - Por jugador: `kills`, `is_alive`, `is_playing`, `team`, `team_color`, `team_alive`, `role`, `hearts`, `wins`, `total_kills`, `games`.
- **BungeeCord / Velocity** (`proxy.enabled`): `/uhchub` y `/hub` mandan al servidor `proxy.lobby-server`. Al terminar, todos vuelven al lobby y el servidor se reinicia solo (`end-action: RESET`) o se apaga (`SHUTDOWN`). Con `block-late-joins` quien entra a una partida empezada vuelve al lobby. El MOTD muestra el estado de la partida (`motd.<estado>` en lang).
- **Inicio automático** (`autostart`): con `min-players` conectados empieza una cuenta atrás, reparte e inicia sola.
- **Recompensas** (`rewards`): comandos de consola al ganar, matar, ser eliminado o participar (`{player}`, `{killer}`, `{kills}`…), por ejemplo `economy give {player} gold-coin 5`.
- **API para otros plugins**: eventos `UhcPrepareEvent` (cancelable), `UhcStartEvent`, `UhcEpisodeStartEvent`, `UhcPlayerEliminatedEvent` y `UhcGameEndEvent` en `cl.itstuki.uhc.api.event`.

## Idiomas

Español e inglés (`/uhcconfig` → Idioma). Todos los textos están en `plugins/TukiUHC/lang/` en formato MiniMessage.

## Compilar

```bash
./gradlew build        # completa en build/libs/ y Legacy en legacy/build/libs/
./gradlew runServer    # servidor Paper 26.3 de prueba
```
