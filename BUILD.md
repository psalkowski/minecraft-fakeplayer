# Build and Deployment Guide for Fakeplayer Plugin

## Prerequisites

1. **Java 21** - Required for building the plugin
2. **Maven** - For building the project
3. **Spigot BuildTools** - For building Minecraft server dependencies
4. **kubectl** - For deploying to Kubernetes cluster (optional)

## Build NMS Dependencies

Mojang does not allow anyone to publish the remapped NMS jar to any public repository,
so you need to build it yourself.

1. Download [BuildTools](https://www.spigotmc.org/wiki/buildtools/) or use wget:
   ```bash
   wget https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
   ```

2. Build the required Minecraft version:

   **For Java 21 (Minecraft 1.20.5+):**
   ```bash
   # Build Spigot 1.20.6 (supports MC 1.20.5 and 1.20.6)
   java -jar BuildTools.jar --rev 1.20.6 --remapped

   # Build Spigot 1.21.1 (supports MC 1.21, 1.21.0, 1.21.1, and 1.21.8)
   java -jar BuildTools.jar --rev 1.21.1 --remapped
   ```

   **For Java 17 (Minecraft 1.20.1-1.20.4):**
   ```bash
   # These versions require Java 17 due to SpecialSource compatibility
   # Build on GitHub Actions or use Java 17 locally
   java -jar BuildTools.jar --rev 1.20.1 --remapped
   java -jar BuildTools.jar --rev 1.20.2 --remapped
   java -jar BuildTools.jar --rev 1.20.4 --remapped
   ```

   Note: Versions 1.20.1-1.20.4 cannot be built with Java 21 due to SpecialSource incompatibility.

## Building the Plugin

### Quick Build Examples

**For Paper 1.21.8:**
```bash
# Build with all currently supported versions (1.20.5+)
mvn clean package -pl fakeplayer-api,fakeplayer-core,fakeplayer-v1_21_8,fakeplayer-dist -am -DskipTests
```

**For Paper 1.20.6:**
```bash
# Build with only 1.20.x support
mvn clean package -pl fakeplayer-api,fakeplayer-core,fakeplayer-v1_20_5,fakeplayer-v1_20_6,fakeplayer-dist -am -DskipTests
```

The built JAR will be located at: `target/fakeplayer-0.3.19.jar`

### Building for Different Minecraft Versions

1. **Identify your server version:**
   ```bash
   # Check server logs or use RCON
   grep "This server is running" server.log
   ```

2. **Build the appropriate version module:**
   - For 1.20.1: Use `fakeplayer-v1_20_1` (requires Java 17)
   - For 1.20.2: Use `fakeplayer-v1_20_2` (requires Java 17)
   - For 1.20.4: Use `fakeplayer-v1_20_4` (requires Java 17)
   - For 1.20.5: Use `fakeplayer-v1_20_5`
   - For 1.20.6: Use `fakeplayer-v1_20_6`
   - For 1.21/1.21.0: Use `fakeplayer-v1_21_1`
   - For 1.21.1: Use `fakeplayer-v1_21_1`
   - For 1.21.8: Use `fakeplayer-v1_21_8`

3. **Update the dist module to use your version:**
   Edit `fakeplayer-dist/pom.xml`:
   ```xml
   <dependency>
       <groupId>io.github.hello09x.fakeplayer</groupId>
       <artifactId>fakeplayer-v1_21_8</artifactId>  <!-- Change this to your version -->
       <version>${revision}</version>
   </dependency>
   ```

### Building All Versions

To build with all supported versions (requires all NMS dependencies):
```bash
mvn clean package -DskipTests
```

## Fixing Common Build Issues

## Manual Deployment

### To a Standard Minecraft Server

1. Copy the JAR to your server's plugins folder:
   ```bash
   cp target/fakeplayer-0.3.19.jar /path/to/server/plugins/
   ```

2. Restart the server or reload plugins

### To Kubernetes (Plains-Mountains Server)

1. **Get the current pod name:**
   ```bash
   kubectl get pods -n gaming | grep plains
   ```

2. **Copy the plugin to the server:**
   ```bash
   kubectl cp target/fakeplayer-0.3.19.jar gaming/<pod-name>:/data/plugins/fakeplayer.jar
   ```

3. **Clear Paper's plugin cache (important!):**
   ```bash
   kubectl exec -n gaming deployment/plains-mountains-minecraft-server -- rm -rf /data/plugins/.paper-remapped/
   ```

4. **Restart the server:**
   ```bash
   kubectl rollout restart -n gaming deployment/plains-mountains-minecraft-server
   ```

5. **Verify deployment:**
   ```bash
   # Check plugin status (should show in green if successful)
   kubectl exec -n gaming deployment/plains-mountains-minecraft-server -- rcon-cli plugins

   # Check logs for errors
   kubectl logs -n gaming deployment/plains-mountains-minecraft-server --tail=100 | grep fakeplayer

   # Test plugin commands
   kubectl exec -n gaming deployment/plains-mountains-minecraft-server -- rcon-cli fp help
   ```

## Adding Support for New Minecraft Versions

### Example: Adding support for 1.21.9

1. **Copy an existing version module:**
   ```bash
   cp -r fakeplayer-v1_21_8 fakeplayer-v1_21_9
   ```

2. **Update package names:**
   ```bash
   find fakeplayer-v1_21_9/src -name "*.java" -exec sed -i '' 's/v1_21_8/v1_21_9/g' {} \;
   ```

3. **Update the module's pom.xml:**
   - Change `<artifactId>` to `fakeplayer-v1_21_9`
   - Update `<nms.version>` to `1.21.9-R0.1-SNAPSHOT`

4. **Update NMSBridgeImpl.java:**
   ```java
   private final static Set<String> SUPPORTS = Set.of("1.21.9");
   ```

5. **Create service file:**
   ```bash
   mkdir -p fakeplayer-v1_21_9/src/main/resources/META-INF/services
   echo "io.github.hello09x.fakeplayer.v1_21_9.spi.NMSBridgeImpl" > \
     fakeplayer-v1_21_9/src/main/resources/META-INF/services/io.github.hello09x.fakeplayer.api.spi.NMSBridge
   ```

6. **Add to parent pom.xml:**
   ```xml
   <module>fakeplayer-v1_21_9</module>
   ```

7. **Build Spigot dependencies:**
   ```bash
   java -jar BuildTools.jar --rev 1.21.9 --remapped
   ```

## Troubleshooting

### "Unsupported Minecraft version" Error
- **Cause:** The server is running a version not supported by the built NMS implementation
- **Solution:** Build the correct version module for your server

### "Provider not found" Error
- **Cause:** Service loader can't find the NMS implementation class
- **Solutions:**
  - Verify the service file contains the correct provider
  - Ensure implementation classes are in the JAR
  - Check that only one version's service file is included

### Plugin Shows Red in Plugin List
- **Cause:** Plugin loaded but encountered initialization errors
- **Solution:** Check server logs for specific error messages

### "Ambiguous plugin name" Error
- **Cause:** Multiple versions of the plugin in plugins folder
- **Solution:**
  - Remove old versions
  - Clear `.paper-remapped/` cache
  - Keep only one fakeplayer JAR file

### Building Without Certain Dependencies
If you can't build certain Minecraft versions:
1. Comment out those modules in the parent `pom.xml`
2. Build only the modules you need using `-pl` flag
3. Ensure dist module only includes your version

### Java Version Compatibility
- **Java 21**: Required for Minecraft 1.20.5 and newer
- **Java 17**: Required for Minecraft 1.20.1-1.20.4 (due to SpecialSource compatibility)
- **BuildTools**: May fail on Java 21 for older MC versions; use GitHub Actions or Docker with Java 17 for those versions

## Project Structure

```
minecraft-fakeplayer/
├── fakeplayer-api/          # API interfaces
├── fakeplayer-core/         # Core plugin logic & commands
├── fakeplayer-dist/         # Distribution module (creates final JAR)
├── fakeplayer-v1_20_1/      # NMS for 1.20.1 (requires Java 17 to build)
├── fakeplayer-v1_20_2/      # NMS for 1.20.2 (requires Java 17 to build)
├── fakeplayer-v1_20_3/      # NMS for 1.20.3-1.20.4 (requires Java 17 to build)
├── fakeplayer-v1_20_4/      # NMS for 1.20.4 (requires Java 17 to build)
├── fakeplayer-v1_20_5/      # NMS for 1.20.5
├── fakeplayer-v1_20_6/      # NMS for 1.20.6
├── fakeplayer-v1_21/        # NMS for 1.21 (expects different artifact name)
├── fakeplayer-v1_21_1/      # NMS for 1.21/1.21.0/1.21.1
├── fakeplayer-v1_21_3/      # NMS for 1.21.3
├── fakeplayer-v1_21_8/      # NMS for 1.21.8
└── target/
    └── fakeplayer-0.3.19.jar  # Final shaded JAR (~1.4MB)
```

## Important Build Notes

### Service Files Management
- **Each version module** has its own service file at `fakeplayer-vX_XX/src/main/resources/META-INF/services/io.github.hello09x.fakeplayer.api.spi.NMSBridge`
- **DO NOT** add service files to `fakeplayer-core` or `fakeplayer-dist` modules
- The Maven Shade plugin with `ServicesResourceTransformer` handles merging automatically
- Only the service file from the included version module will be in the final JAR

### Key Files to Edit
- **Version compatibility:** `fakeplayer-vX_XX/src/main/java/.../spi/NMSBridgeImpl.java`
- **Service registration:** `fakeplayer-vX_XX/src/main/resources/META-INF/services/io.github.hello09x.fakeplayer.api.spi.NMSBridge`
- **Distribution config:** `fakeplayer-dist/pom.xml` (specify which version module to include)
- **Module list:** `pom.xml` (parent)

## Requirements

- **Server:** Paper/Spigot (not vanilla)
- **Java:** 21 or higher
- **Minecraft:** Version-specific NMS implementation required
- **Memory:** ~50MB heap usage per fake player
- **Permissions:** Op permissions or appropriate permission nodes
