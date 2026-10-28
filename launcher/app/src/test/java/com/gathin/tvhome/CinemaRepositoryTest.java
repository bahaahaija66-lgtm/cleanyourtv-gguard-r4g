package com.gathin.tvhome;

import org.json.JSONException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CinemaRepositoryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String TOKEN = "test-token-not-a-real-credential";
    private static final byte[] JPEG = {(byte)0xff, (byte)0xd8, (byte)0xff, 0, 0, 0, 0, 0};

    private static String movie(int id) {
        return "{\"id\":" + id + ",\"title\":\"Interstellar\",\"release_date\":\"2014-11-05\","
            + "\"overview\":\"Provider synopsis\",\"vote_average\":8.6,\"vote_count\":100,"
            + "\"genres\":[{\"name\":\"Adventure\"},{\"name\":\"Science Fiction\"}],"
            + "\"backdrop_path\":\"/real_backdrop.jpg\",\"poster_path\":\"/real_poster.jpg\"}";
    }

    @Test public void parsesProviderRatingAndImagePaths() throws Exception {
        CinemaRepository.Film film = CinemaRepository.parseFilm(movie(157336), 157336);
        assertEquals("Interstellar", film.title);
        assertEquals("2014", film.year);
        assertEquals("8.6 / 10 TMDB", film.rating);
        assertEquals("/real_backdrop.jpg", film.backdrop);
        assertEquals("/real_poster.jpg", film.poster);
        assertEquals("Adventure · Science Fiction", film.genres);
        assertTrue(film.fromTmdb);
    }

    @Test(expected = JSONException.class) public void rejectsWrongMovieIdentity() throws Exception {
        CinemaRepository.parseFilm(movie(155), 157336);
    }

    @Test public void noInventedRatingsWhenVotesAreAbsent() throws Exception {
        CinemaRepository.Film film = CinemaRepository.parseFilm(movie(155).replace("\"vote_count\":100", "\"vote_count\":0"), 155);
        assertEquals("", film.rating);
        for (CinemaRepository.Film placeholder : CinemaRepository.placeholders()) {
            assertEquals("", placeholder.rating);
            assertEquals("", placeholder.backdrop);
            assertFalse(placeholder.fromTmdb);
        }
        assertEquals("Interstellar", CinemaRepository.placeholders().get(0).title);
        assertEquals("Dune", CinemaRepository.placeholders().get(1).title);
        assertEquals("The Dark Knight", CinemaRepository.placeholders().get(2).title);
    }

    @Test public void rejectsForeignHostsAndTraversalInImagePaths() throws Exception {
        for (String invalid : new String[]{"https://other.example/a.jpg", "/../a.jpg", "/nested/a.jpg", "//other/a.jpg", "/a.jpg?token=x", "null", ""}) {
            assertFalse(invalid, CinemaRepository.validImagePath(invalid));
        }
        CinemaRepository.Film film = CinemaRepository.parseFilm(movie(155).replace("/real_backdrop.jpg", "https://other.example/a.jpg"), 155);
        assertEquals("", film.backdrop);
    }

    @Test public void coldStartWithoutTokenNeverCallsNetwork() throws Exception {
        CinemaRepository repository = new CinemaRepository(temporary.newFolder(), (url, token, limit) -> {
            fail("No requests without a user credential"); return null;
        });
        assertEquals(3, repository.catalog("", System.currentTimeMillis()).films.size());
        assertFalse(repository.configure(""));
        assertNull(repository.image("/missing.jpg", false, false, 0));
    }

    @Test public void savedCatalogSurvivesOfflineAndNeverContainsToken() throws Exception {
        File dir = temporary.newFolder();
        List<String> hosts = new ArrayList<>();
        CinemaRepository live = new CinemaRepository(dir, (url, token, limit) -> {
            hosts.add(url.getHost());
            assertEquals("api.themoviedb.org", url.getHost());
            assertEquals(TOKEN, token);
            int id = Integer.parseInt(url.getPath().substring(url.getPath().lastIndexOf('/') + 1));
            return movie(id).getBytes(StandardCharsets.UTF_8);
        });
        CinemaRepository.Catalog fresh = live.catalog(TOKEN, System.currentTimeMillis());
        assertEquals(3, hosts.size());
        for (CinemaRepository.Film film : fresh.films) assertTrue(film.fromTmdb);
        for (File file : dir.listFiles()) assertFalse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).contains(TOKEN));
        CinemaRepository offline = new CinemaRepository(dir, (url, token, limit) -> { throw new IOException("Offline"); });
        CinemaRepository.Catalog saved = offline.catalog(TOKEN, System.currentTimeMillis() + CinemaRepository.METADATA_TTL + 1000);
        for (CinemaRepository.Film film : saved.films) assertTrue(film.fromTmdb);
        assertTrue(saved.status.contains("Offline"));
        for (File file : dir.listFiles()) assertFalse(file.getName().endsWith(".tmp"));
    }

    @Test public void authorizationFailureIsExplainedWithoutExposingCredential() throws Exception {
        CinemaRepository repository = new CinemaRepository(temporary.newFolder(), (url, token, limit) -> {
            throw new IOException("TMDB authorization failed");
        });
        String status = repository.catalog(TOKEN, 0).status;
        assertTrue(status.contains("token not accepted"));
        assertFalse(status.contains(TOKEN));
    }

    @Test public void usesTmdbConfigurationAndNoBearerOnImageRequests() throws Exception {
        CinemaRepository repository = new CinemaRepository(temporary.newFolder(), (url, token, limit) -> {
            if (url.getHost().equals("api.themoviedb.org")) {
                assertEquals(TOKEN, token);
                return ("{\"images\":{\"secure_base_url\":\"https://image.tmdb.org/t/p/\","
                    + "\"backdrop_sizes\":[\"w1280\"],\"poster_sizes\":[\"w342\"]}}").getBytes(StandardCharsets.UTF_8);
            }
            assertEquals("image.tmdb.org", url.getHost());
            assertEquals("https", url.getProtocol());
            assertEquals("/t/p/w1280/backdrop.jpg", url.getPath());
            assertEquals("", token);
            return JPEG;
        });
        assertTrue(repository.configure(TOKEN));
        assertArrayEquals(JPEG, Files.readAllBytes(repository.image("/backdrop.jpg", false, true, 0).toPath()));
    }

    @Test public void rejectsUntrustedImageConfiguration() throws Exception {
        CinemaRepository repository = new CinemaRepository(temporary.newFolder(), (url, token, limit) ->
            "{\"images\":{\"secure_base_url\":\"https://other.example/\",\"backdrop_sizes\":[\"w1280\"],\"poster_sizes\":[\"w342\"]}}".getBytes(StandardCharsets.UTF_8));
        assertFalse(repository.configure(TOKEN));
    }

    @Test public void failedRefreshPreservesOfflineImage() throws Exception {
        File dir = temporary.newFolder();
        CinemaRepository live = new CinemaRepository(dir, (url, token, limit) -> JPEG);
        File image = live.image("/art.jpg", true, true, 0);
        assertTrue(image.setLastModified(1));
        CinemaRepository offline = new CinemaRepository(dir, (url, token, limit) -> { throw new IOException("Offline"); });
        assertEquals(image, offline.image("/art.jpg", true, true, CinemaRepository.IMAGE_TTL + 100));
        assertArrayEquals(JPEG, Files.readAllBytes(image.toPath()));
    }

    @Test public void invalidImageIsNotCommittedToCache() throws Exception {
        File dir = temporary.newFolder();
        CinemaRepository repository = new CinemaRepository(dir, (url, token, limit) -> "<html>error</html>".getBytes(StandardCharsets.UTF_8));
        try { repository.image("/art.jpg", false, true, 0); fail("Expected invalid image rejection"); }
        catch (IOException expected) { assertEquals(0, dir.listFiles().length); }
    }

    @Test public void corruptMetadataDoesNotCrashAndIsReplaced() throws Exception {
        File dir = temporary.newFolder();
        Files.write(new File(dir, "movie-157336.json").toPath(), "broken JSON".getBytes(StandardCharsets.UTF_8));
        CinemaRepository repository = new CinemaRepository(dir, (url, token, limit) -> {
            int id = Integer.parseInt(url.getPath().substring(url.getPath().lastIndexOf('/') + 1));
            return movie(id).getBytes(StandardCharsets.UTF_8);
        });
        assertTrue(repository.catalog(TOKEN, 0).films.get(0).fromTmdb);
    }

    @Test public void cacheEvictionRemainsWithinBudget() throws Exception {
        File dir = temporary.newFolder();
        File old = new File(dir, "w1280-old.jpg");
        try (java.io.RandomAccessFile sparse = new java.io.RandomAccessFile(old, "rw")) {
            sparse.setLength(CinemaRepository.CACHE_LIMIT);
        }
        assertTrue(old.setLastModified(1));
        CinemaRepository repository = new CinemaRepository(dir, (url, token, limit) -> JPEG);
        File current = repository.image("/current.jpg", false, true, 0);
        assertTrue(current.isFile());
        long total = 0;
        for (File file : dir.listFiles()) total += file.length();
        assertTrue(total <= CinemaRepository.CACHE_LIMIT);
        assertFalse(old.exists());
    }

    @Test(expected = IOException.class) public void boundsDownloadSize() throws Exception {
        CinemaRepository.readBounded(new ByteArrayInputStream(new byte[25]), 24);
    }
}
