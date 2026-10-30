# Village Rise (Minecraft 26.3 · Fabric)

Requisitos: Java 25, Fabric Loader ≥ 0.19.5, Fabric API 0.161.0+26.3.

## Compilar
1. Ve a https://fabricmc.net/develop , elige Minecraft 26.3 y descarga la plantilla (trae el Gradle wrapper y la versión correcta de Loom).
2. Copia dentro de esa plantilla la carpeta `src/` de este proyecto (sustituye `fabric.mod.json` y el JSON de mixins).
3. `./gradlew build` → `build/libs/villagerise-*.jar` → a la carpeta `mods/`.

## Qué hace
- Reparación: guarda un "plano" de los bloques de casas/murallas cerca de los aldeanos y reconstruye lo que falta
  (los bloques que rompe un jugador NO se reconstruyen).
- Roles (según UUID): 1/3 constructores, 1/3 recolectores (cultivos, madera, piedra), 1/3 guerreros.
- Reproducción: con comida y camas libres nacen aldeanos; si faltan camas, construyen casas nuevas.
- Planificador: casas → huertos → torres con torretas → muralla (empieza por el lado más atacado) → torreón → casas de madera a piedra.
- Enemigos ("Hive"): oleadas nocturnas que eligen el lado más débil, se reparten en pinza, esperan y atacan a la vez,
  focalizan al defensor más débil, adaptan composición y escalan si la aldea resiste sin bajas.
- Todo se guarda en `<mundo>/villagerise_*.json`.

## Ajustes rápidos
- `Hive.DEBUG_FAST_WAVES = true` para probar oleadas rápido.
- Costes en `Util.cost`, tamaños en `Blueprints`, radios en `Village.plan`.
