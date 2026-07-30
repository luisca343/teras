package es.boffmedia.teras.audio;

import es.boffmedia.teras.Teras;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The server's record collection: {@code config/teras/discos/}.
 *
 * <p>Tracks are files on the server, never URLs on a disc. A disc carries a <b>name</b> and the
 * name resolves here, which is what makes the download the single moderation checkpoint: whoever
 * can run {@code /disco descargar} decides what exists, and everything downstream can only pick
 * from that. It also means a dead link cannot fail in-world halfway through a song.</p>
 */
public final class AudioLibrary {
    private AudioLibrary() {}

    /** Formats we can decode. Order is the search order when a name is given without one. */
    public static final List<String> EXTENSIONS = List.of("mp3", "wav");

    /** Refused above this. A music file is a few MB; anything far larger is not a song. */
    public static final long MAX_BYTES = 32L * 1024 * 1024;

    private static final int TIMEOUT_MILLIS = 20_000;

    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve("teras").resolve("discos");
    }

    /** The file backing {@code name}, or {@code null} if no supported format is present. */
    public static Path find(String name) {
        String track = TrackName.normalize(name);
        if (track == null) {
            return null;
        }
        for (String extension : EXTENSIONS) {
            Path candidate = directory().resolve(track + "." + extension);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static boolean exists(String name) {
        return find(name) != null;
    }

    /** Every track in the library, normalised and sorted. Never throws — an unreadable directory is empty. */
    public static List<String> list() {
        Path directory = directory();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> entries = Files.list(directory)) {
            entries.filter(Files::isRegularFile).forEach(file -> {
                String fileName = file.getFileName().toString();
                int dot = fileName.lastIndexOf('.');
                if (dot <= 0) {
                    return;
                }
                if (!EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT))) {
                    return;
                }
                String track = TrackName.normalize(fileName.substring(0, dot));
                if (track != null && !names.contains(track)) {
                    names.add(track);
                }
            });
        } catch (IOException e) {
            Teras.LOGGER.warn("Discos: could not list {}: {}", directory, e.getMessage());
            return List.of();
        }
        names.sort(String::compareTo);
        return List.copyOf(names);
    }

    /**
     * Fetches {@code url} into the library as {@code name}.
     *
     * <p><b>Blocking — never call this on the server thread.</b> {@code DiscosCommand} runs it on a
     * worker and reports back. Downloads to a temporary file and moves it into place only once it
     * is whole, so an interrupted download cannot leave a half-written track that decodes to noise.</p>
     *
     * @return the resting path of the downloaded track
     * @throws IOException on a refusal (bad name, bad URL, wrong type, too big) or any transport error
     */
    public static Path download(String url, String name) throws IOException {
        String track = TrackName.normalize(name);
        if (track == null) {
            throw new IOException("'" + name + "' is not a usable track name");
        }

        URI uri = parse(url);
        String extension = extensionOf(uri);
        Path directory = directory();
        Files.createDirectories(directory);
        Path target = directory.resolve(track + "." + extension);
        Path temporary = directory.resolve(track + "." + extension + ".part");

        HttpURLConnection connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        // Some hosts serve a 403 to the default Java agent.
        connection.setRequestProperty("User-Agent", "Teras/1.21.1");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("The server answered HTTP " + status);
            }
            long declared = connection.getContentLengthLong();
            if (declared > MAX_BYTES) {
                throw new IOException("The file is " + declared / (1024 * 1024) + " MB; the limit is "
                        + MAX_BYTES / (1024 * 1024) + " MB");
            }

            try (InputStream in = connection.getInputStream()) {
                // Capped while copying as well as up front: Content-Length is whatever the remote
                // server chose to claim, and may be absent or a lie.
                long copied = Files.copy(new BoundedStream(in, MAX_BYTES), temporary,
                        StandardCopyOption.REPLACE_EXISTING);
                if (copied == 0) {
                    throw new IOException("The download was empty");
                }
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            // Every other extension for this name is now a stale duplicate that find() might pick
            // ahead of what was just downloaded.
            for (String other : EXTENSIONS) {
                if (!other.equals(extension)) {
                    Files.deleteIfExists(directory.resolve(track + "." + other));
                }
            }
            Teras.LOGGER.info("Discos: downloaded '{}' ({} bytes) from {}", track, Files.size(target), uri.getHost());
            return target;
        } finally {
            Files.deleteIfExists(temporary);
            connection.disconnect();
        }
    }

    /** Deletes {@code name} from the library. */
    public static boolean delete(String name) throws IOException {
        Path file = find(name);
        if (file == null) {
            return false;
        }
        Files.delete(file);
        TrackCache.invalidate(TrackName.normalize(name));
        return true;
    }

    /** Only http(s), and only with a host — no {@code file:} reads off the server's own disk. */
    private static URI parse(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IOException("That is not a URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IOException("Only http and https URLs can be downloaded");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IOException("That URL has no host");
        }
        return uri;
    }

    /**
     * The format to save as, taken from the URL path.
     *
     * <p>Read from the URL rather than the response's {@code Content-Type} because the extension
     * decides which decoder runs, and a mislabelled MP3 should fail loudly at decode time rather
     * than be silently trusted from a header the remote host controls.</p>
     */
    private static String extensionOf(URI uri) throws IOException {
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) {
            if (path.endsWith("." + extension)) {
                return extension;
            }
        }
        throw new IOException("The URL must end in " + String.join(" or ", EXTENSIONS)
                + " — streaming sites are not supported, download the file first");
    }

    /** Fails the copy rather than filling the disk when a response runs past {@link #MAX_BYTES}. */
    private static final class BoundedStream extends InputStream {
        private final InputStream delegate;
        private final long limit;
        private long read;

        BoundedStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        private void count(int amount) throws IOException {
            read += amount;
            if (read > limit) {
                throw new IOException("The file is larger than the " + limit / (1024 * 1024) + " MB limit");
            }
        }
    }
}
