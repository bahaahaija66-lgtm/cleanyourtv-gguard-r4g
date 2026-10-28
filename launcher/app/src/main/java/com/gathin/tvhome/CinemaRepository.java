package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** TMDB metadata and bounded, private, atomic disk cache. No credentials are cached here. */
final class CinemaRepository {
    static final int[] IDS = {157336, 438631, 155};
    static final long METADATA_TTL = 24L * 60 * 60 * 1000;
    static final long IMAGE_TTL = 7L * 24 * 60 * 60 * 1000;
    static final long CACHE_LIMIT = 32L * 1024 * 1024;
    static final int IMAGE_LIMIT = 8 * 1024 * 1024;
    private static final int JSON_LIMIT = 1024 * 1024;
    private static final String API = "https://api.themoviedb.org/3/";
    private static final String IMAGES = "https://image.tmdb.org/t/p/";

    interface Transport {
        byte[] get(URL url, String bearer, int limit) throws IOException;
    }

    static final class HttpsTransport implements Transport {
        @Override public byte[] get(URL url, String bearer, int limit) throws IOException {
            if (!"https".equals(url.getProtocol())) throw new IOException("HTTPS required");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json,image/*");
            // Never forward the API token to the image CDN or a redirect destination.
            if ("api.themoviedb.org".equals(url.getHost()) && !bearer.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + bearer);
            }
            try {
                int code = connection.getResponseCode();
                if (code == 401 || code == 403) throw new IOException("TMDB authorization failed");
                if (code != 200) throw new IOException("TMDB request failed (HTTP " + code + ")");
                try (InputStream stream = connection.getInputStream()) {
                    return readBounded(stream, limit);
                }
            } finally {
                connection.disconnect();
            }
        }
    }

    static final class Film {
        final int id;
        final String title, year, genres, description, backdrop, poster, rating;
        final boolean fromTmdb;

        Film(int id, String title, String year, String genres, String description,
             String backdrop, String poster, String rating, boolean fromTmdb) {
            this.id = id;
            this.title = title;
            this.year = year;
            this.genres = genres;
            this.description = description;
            this.backdrop = backdrop;
            this.poster = poster;
            this.rating = rating;
            this.fromTmdb = fromTmdb;
        }
    }

    static List<Film> placeholders() {
        return Arrays.asList(
            new Film(IDS[0], "Interstellar", "2014", "Science fiction · Adventure",
                "A journey beyond our solar system in search of a future for humanity.", "", "", "", false),
            new Film(IDS[1], "Dune", "2021", "Science fiction · Adventure",
                "On the desert world of Arrakis, a young heir faces an extraordinary destiny.", "", "", "", false),
            new Film(IDS[2], "The Dark Knight", "2008", "Crime · Drama",
                "Gotham's masked protector faces a criminal who turns the city against itself.", "", "", "", false));
    }

    static final class Catalog {
        final List<Film> films;
        final String status;
        Catalog(List<Film> films, String status) { this.films = films; this.status = status; }
    }

    private final File directory;
    private final Transport transport;

    CinemaRepository(File directory) { this(directory, new HttpsTransport()); }
    CinemaRepository(File directory, Transport transport) {
        this.directory = directory;
        this.transport = transport;
    }

    Catalog catalog(String token, long now) {
        List<Film> films = new ArrayList<>();
        boolean offline = false;
        boolean unauthorized = false;
        List<Film> defaults = placeholders();
        for (int i = 0; i < IDS.length; i++) {
            File cached = new File(directory, "movie-" + IDS[i] + ".json");
            Film film = readFilm(cached, IDS[i]);
            if (!token.isEmpty() && (film == null || now - cached.lastModified() >= METADATA_TTL)) {
                try {
                    byte[] body = transport.get(new URL(API + "movie/" + IDS[i] + "?language=en-US"), token, JSON_LIMIT);
                    Film fresh = parseFilm(new String(body, StandardCharsets.UTF_8), IDS[i]);
                    writeAtomic(cached, body);
                    film = fresh;
                } catch (IOException | JSONException e) {
                    offline = true;
                    unauthorized |= e.getMessage() != null && e.getMessage().contains("authorization");
                }
            }
            films.add(film == null ? defaults.get(i) : film);
        }
        String status = unauthorized ? "TMDB token not accepted · Open Artwork & TMDB"
            : token.isEmpty() ? "Saved collection · Connect TMDB for artwork and ratings"
            : offline ? "Offline · keeping saved artwork and metadata" : "Movie information from TMDB";
        return new Catalog(films, status);
    }

    /** Check TMDB's current image configuration before downloading new images. */
    boolean configure(String token) {
        if (token.isEmpty()) return false;
        try {
            JSONObject configuration = new JSONObject(new String(
                transport.get(new URL(API + "configuration"), token, JSON_LIMIT), StandardCharsets.UTF_8));
            JSONObject images = configuration.getJSONObject("images");
            return "https://image.tmdb.org/t/p/".equals(images.getString("secure_base_url"))
                && contains(images.getJSONArray("backdrop_sizes"), "w1280")
                && contains(images.getJSONArray("poster_sizes"), "w342");
        } catch (IOException | JSONException e) {
            return false;
        }
    }

    File image(String path, boolean poster, boolean allowNetwork, long now) throws IOException {
        if (!validImagePath(path)) return null;
        String size = poster ? "w342" : "w1280";
        File cached = new File(directory, size + "-" + path.substring(1));
        boolean usable = cached.isFile() && cached.length() > 0 && cached.length() <= IMAGE_LIMIT;
        if (usable && (!allowNetwork || now - cached.lastModified() < IMAGE_TTL)) return cached;
        if (!allowNetwork) return usable ? cached : null;
        try {
            byte[] body = transport.get(new URL(IMAGES + size + path), "", IMAGE_LIMIT);
            if (!isImage(body)) throw new IOException("Invalid image response");
            writeAtomic(cached, body);
            trim(cached);
            return cached;
        } catch (IOException e) {
            if (usable) return cached;
            throw e;
        }
    }

    static Film parseFilm(String json, int expectedId) throws JSONException {
        JSONObject value = new JSONObject(json);
        if (value.getInt("id") != expectedId) throw new JSONException("Movie identity mismatch");
        String title = value.getString("title").trim();
        if (title.isEmpty()) throw new JSONException("Missing title");
        String release = value.optString("release_date", "");
        String year = release.matches("\\d{4}-\\d{2}-\\d{2}") ? release.substring(0, 4) : "";
        JSONArray sourceGenres = value.optJSONArray("genres");
        List<String> genres = new ArrayList<>();
        if (sourceGenres != null) {
            for (int i = 0; i < Math.min(2, sourceGenres.length()); i++) {
                String genre = sourceGenres.getJSONObject(i).optString("name");
                if (!genre.isEmpty()) genres.add(genre);
            }
        }
        double score = value.optDouble("vote_average", Double.NaN);
        String rating = value.optInt("vote_count", 0) > 0 && Double.isFinite(score) && score >= 0 && score <= 10
            ? String.format(Locale.US, "%.1f / 10 TMDB", score) : "";
        String backdrop = value.optString("backdrop_path", "");
        String poster = value.optString("poster_path", "");
        return new Film(expectedId, title, year, String.join(" · ", genres), value.optString("overview", ""),
            validImagePath(backdrop) ? backdrop : "", validImagePath(poster) ? poster : "", rating, true);
    }

    static boolean validImagePath(String path) {
        return path != null && path.matches("/[A-Za-z0-9_]+\\.(jpg|png)");
    }

    private Film readFilm(File file, int id) {
        if (!file.isFile()) return null;
        try (InputStream stream = new FileInputStream(file)) {
            return parseFilm(new String(readBounded(stream, JSON_LIMIT), StandardCharsets.UTF_8), id);
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    static byte[] readBounded(InputStream stream, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (count > limit - out.size()) throw new IOException("Response exceeds cache limit");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private static boolean isImage(byte[] bytes) {
        return bytes.length >= 8 && ((bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8)
            || (bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'));
    }

    private static boolean contains(JSONArray array, String wanted) throws JSONException {
        for (int i = 0; i < array.length(); i++) if (wanted.equals(array.getString(i))) return true;
        return false;
    }

    private void writeAtomic(File file, byte[] body) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot prepare private cache");
        File temporary = new File(directory, file.getName() + ".tmp");
        try {
            try (FileOutputStream stream = new FileOutputStream(temporary)) {
                stream.write(body);
                stream.getFD().sync();
            }
            if (!temporary.renameTo(file)) throw new IOException("Cannot commit cache file");
        } finally {
            if (temporary.exists() && !temporary.delete()) temporary.deleteOnExit();
        }
    }

    private void trim(File keep) {
        File[] files = directory.listFiles((dir, name) -> name.startsWith("w") && !name.endsWith(".tmp"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long total = 0;
        for (File file : files) total += file.length();
        for (File file : files) {
            if (total <= CACHE_LIMIT) break;
            if (!file.equals(keep)) {
                long length = file.length();
                if (file.delete()) total -= length;
            }
        }
    }
}
