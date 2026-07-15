package es.boffmedia.teras.util.file;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import es.boffmedia.teras.Teras;
import es.boffmedia.teras.model.config.TerasConfig;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class FileHelper {
    public static boolean writeFile(String ruta, Object o) {
        String folder = ruta.substring(0, ruta.lastIndexOf("/"));
        File folderFile = new File(folder);
        if(!folderFile.exists()){
            folderFile.mkdirs();
        }
        return writeFile(new File(ruta), o);
    }

    public static boolean writeStringFile(String ruta, String o) {
        String folder = ruta.substring(0, ruta.lastIndexOf("/"));
        File folderFile = new File(folder);
        if(!folderFile.exists()){
            folderFile.mkdirs();
        }
        return writeStringFile(new File(ruta), o);
    }


    public static boolean writeStringFile(File file, String o) {
        try {
            atomicWrite(file, o.getBytes(Charsets.UTF_8));
            return true;
        } catch (IOException e) {
            Teras.LOGGER.error("Failed to write file " + file.getPath(), e);
            return false;
        }
    }

    public static boolean writeFile(File file, Object o) {
        Gson gson = new Gson();
        try {
            atomicWrite(file, gson.toJson(o).getBytes(Charsets.UTF_8));
            return true;
        } catch (IOException e) {
            Teras.LOGGER.error("Failed to write file " + file.getPath(), e);
            return false;
        }
    }

    /**
     * Writes {@code data} to {@code target} atomically: serialize to a sibling temp file, then move it into
     * place. A crash mid-write leaves the original file intact rather than truncated/corrupted.
     */
    private static void atomicWrite(File target, byte[] data) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Path targetPath = target.toPath();
        Path tempPath = Files.createTempFile(
                parent != null ? parent.toPath() : targetPath.getParent(),
                target.getName() + ".", ".tmp");
        try {
            Files.write(tempPath, data);
            try {
                Files.move(tempPath, targetPath,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Fall back to a non-atomic replace if the filesystem can't do an atomic move.
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    public static Map getMapFromFile(String ruta) {
        Object o = readFile(ruta, Map.class);
        if(o == null){
            return new HashMap();
        }
        return (Map) o;
    }

    public static void writeNBT( String ruta, CompoundNBT nbt){
        try {
            File file = new File(ruta);
            File parentDir = file.getParentFile();
            if(parentDir != null && !parentDir.exists()){
                parentDir.mkdirs();
            }
            CompressedStreamTools.write(nbt, file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static CompoundNBT readNBT(String ruta){
        try {
            return CompressedStreamTools.read(new File(ruta));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean deleteFile(String ruta){
        return new File(ruta).delete();
    }

    public static boolean exists(String ruta){
        return new File(ruta).exists();
    }

    public static Object readFile(String ruta, Type token) {
        return readFile(new File(ruta), token);
    }

    private static <T> T readFile(File file, Type token) {
        Gson gson = new Gson();
        try {
            if(!file.exists()){
                // Seed an empty JSON object and deserialize it through the token so callers get a
                // correctly-typed (empty) instance instead of a bogus TypeToken object.
                atomicWrite(file, "{}".getBytes(Charsets.UTF_8));
                return gson.fromJson("{}", token);
            } else{
                try (BufferedReader reader = Files.newBufferedReader(Paths.get(file.getPath()))) {
                    return gson.fromJson(reader, token);
                }
            }
        } catch (IOException e) {
            Teras.LOGGER.error("Failed to read file " + file.getPath(), e);
            return null;
        }
    }

    public static Object readFile(String ruta, Class<?> clazz) {
        return readFile(new File(ruta), clazz);
    }

    public static Object readFile(File file, Class<?> clazz) {
        Gson gson = new Gson();
        try {
            if(!file.exists()){
                Object o = clazz.newInstance();
                writeFile(file, o);
                return o;
            } else{
                try (BufferedReader reader = Files.newBufferedReader(Paths.get(file.getPath()))) {
                    return gson.fromJson(reader, clazz);
                }
            }
        } catch (IOException | IllegalAccessException | InstantiationException e) {
            Teras.LOGGER.error("Failed to read file " + file.getPath(), e);
            return null;
        }
    }

    public static TerasConfig getConfig() {
        Gson gson = new Gson();
        File file = new File("config/teras/config.json");
        TerasConfig config;
        try{
            BufferedReader br = new BufferedReader(new java.io.FileReader(file));
            config = gson.fromJson(br, TerasConfig.class);
            Teras.config = config;
            return config;
        } catch (FileNotFoundException e) {
            config = new TerasConfig();
            config.setId(RandomStringUtils.random(8, true, true));
            config.setHome("http://localhost:8080");
            config.setAPI_URL("http://localhost:3000");
            config.setApiToken("");        // set a bearer token here to authenticate SmartRotom requests
            config.setRequireHttps(false); // set true once API_URL is HTTPS to fail-closed on plaintext

            Teras.config = config;
            writeFile(file, config);

            return config;
        }
    }

    public static File getSchematic(String name) {
        File file = new File("plugins/WorldEdit/schematics/" + name + ".schem");
        if(!file.exists()){
            try {
                file.createNewFile();
                return file;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return file;
    }

}

