package io.github.stairman06.arewepatchworkyet.forge;

import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import net.patchworkmc.patcher.Patchwork;
import net.patchworkmc.patcher.util.MinecraftVersion;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;

public class ForgeInstaller {
    public static final File INSTALLER_FILE = new File("data/installforge/installer.jar");

    public static void downloadInstaller() throws Exception {
        // TODO: Harcoded
        FileUtils.copyURLToFile(new URL("http://files.minecraftforge.net/maven/net/minecraftforge/forge/1.16.4-35.1.28/forge-1.16.4-35.1.28-installer.jar"), INSTALLER_FILE);
    }

    private static void remapPatchedMinecraft() throws Exception {
        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createTinyMappingProvider(new BufferedReader(new FileReader("./data/voldemap-bridged-1.16.4.tiny")), "srg", "intermediary"))
                .rebuildSourceFilenames(true)
                .build();

        Path original = Paths.get("./data/mcpatched-srg.jar");
        Path destination = Paths.get("./data/mcpatched-intermediary.jar");

        try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(destination).build()) {
            outputConsumer.addNonClassFiles(original, NonClassCopyMode.SKIP_META_INF, remapper);
            remapper.readInputs(original);
            remapper.apply(outputConsumer);
        } finally {
            remapper.finish();
        }
    }


    private static void remapForge() throws Exception {
        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createTinyMappingProvider(new BufferedReader(new FileReader("./data/voldemap-bridged-1.16.4.tiny")), "srg", "intermediary"))
                .rebuildSourceFilenames(true)
                .build();

        Path original = Paths.get("./data/forge-srg.jar");
        Path destination = Paths.get("./data/forge-intermediary.jar");

        try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(destination).build()) {
            outputConsumer.addNonClassFiles(original, NonClassCopyMode.SKIP_META_INF, remapper);
            remapper.readInputs(original);
            remapper.apply(outputConsumer);
        } finally {
            remapper.finish();
        }
    }

    public static void installForge() {
        try {
            System.out.println("AWPY is installing Forge. This is only required once.");
            downloadInstaller();

            File forgeDir = new File("./data/installforge/forge");
            forgeDir.mkdirs();

            FileWriter launcherProfiles = new FileWriter("./data/installforge/forge/launcher_profiles.json");
            launcherProfiles.write("{}"); // basic json object to get forge to not fail
            launcherProfiles.close();

            URLClassLoader installerLoader = new URLClassLoader(
                    new URL[]{INSTALLER_FILE.toURI().toURL()},
                    ClassLoader.getSystemClassLoader()
            );

            Class<?> installerUtilClass = Class.forName("net.minecraftforge.installer.json.Util", true, installerLoader);
            Class<?> installClass = Class.forName("net.minecraftforge.installer.json.Install", true, installerLoader);
            Class<?> monitorClass = Class.forName("net.minecraftforge.installer.actions.ProgressCallback", true, installerLoader);
            Class<?> clientInstallClass = Class.forName("net.minecraftforge.installer.actions.ClientInstall", true, installerLoader);

            try {
                Object install = installerUtilClass.getDeclaredMethod("loadInstallProfile").invoke(null);
                Object monitor = monitorClass.getDeclaredField("TO_STD_OUT").get(null);
                Constructor<?> clientInstallConstructor = clientInstallClass.getConstructor(installClass, monitorClass);
                Object clientInstall = clientInstallConstructor.newInstance(install, monitor);
                clientInstallClass.getDeclaredMethod("run", File.class, Predicate.class).invoke(clientInstall, new File("data/installforge/forge").getAbsoluteFile(), (Predicate<String>) s -> true);
                Files.move(Paths.get("./data/installforge/forge/libraries/net/minecraftforge/forge/1.16.4-35.1.28/forge-1.16.4-35.1.28-client.jar"), Paths.get("./data/mcpatched-srg.jar"));
                Files.move(Paths.get("./data/installforge/forge/libraries/net/minecraftforge/forge/1.16.4-35.1.28/forge-1.16.4-35.1.28-universal.jar"), Paths.get("./data/forge-srg.jar"));
            } catch (Exception e) {
                e.printStackTrace();
            }

            // create voldemap bridged
            System.out.println("Creating SRG->Intermediary bridge by calling Patcher...");
            Patchwork.create(Paths.get("./data/patcher-input"), Paths.get("./data/patcher-output"), Paths.get("./data/patcher-data"), MinecraftVersion.V1_16_4).patchAndFinish();
            System.out.println("Created SRG->Intermediary bridge successfully.");

            try {
                Files.move(Paths.get("./data/patcher-data/mappings/voldemap-bridged-1.16.4.tiny"), Paths.get("./data/voldemap-bridged-1.16.4.tiny"));
            } catch (Exception e) {
                e.printStackTrace();
            }

            remapPatchedMinecraft();
            remapForge();

            System.out.println("AWPY has finished installing Forge and remapping patched Minecraft.");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
