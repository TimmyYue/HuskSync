package net.william278.husksync.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.william278.husksync.HuskSync;
import net.william278.husksync.data.UserDataSnapshot;
import net.william278.husksync.player.User;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.StringJoiner;
import java.util.logging.Level;

/**
 * Utility class for dumping {@link UserDataSnapshot}s to a file
 */
public class DataDumper {

    private static final String LOGS_SITE_ENDPOINT = "https://api.mclo.gs/1/log";

    private final HuskSync plugin;
    private final UserDataSnapshot dataSnapshot;
    private final User user;

    private DataDumper(@NotNull UserDataSnapshot dataSnapshot,
                       @NotNull User user, @NotNull HuskSync implementor) {
        this.dataSnapshot = dataSnapshot;
        this.user = user;
        this.plugin = implementor;
    }

    /**
     * Create a {@link DataDumper} of the given {@link UserDataSnapshot}
     *
     * @param dataSnapshot The {@link UserDataSnapshot} to dump
     * @param user         The {@link User} whose data is being dumped
     * @param plugin       The implementing {@link HuskSync} plugin
     * @return A {@link DataDumper} for the given {@link UserDataSnapshot}
     */
    public static DataDumper create(@NotNull UserDataSnapshot dataSnapshot,
                                    @NotNull User user, @NotNull HuskSync plugin) {
        return new DataDumper(dataSnapshot, user, plugin);
    }

    /**
     * Dumps the data snapshot to a string
     *
     * @return the data snapshot as a string
     */
    @Override
    @NotNull
    public String toString() {
        return plugin.getDataAdapter().toJson(dataSnapshot.userData(), true);
    }

    @NotNull
    public String toWeb() {
        try {
            final URL url = new URL(LOGS_SITE_ENDPOINT);
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            // Dispatch the request
            final byte[] messageBody = getWebContentField().getBytes(StandardCharsets.UTF_8);
            final int messageLength = messageBody.length;
            connection.setFixedLengthStreamingMode(messageLength);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.connect();
            try (OutputStream messageOutputStream = connection.getOutputStream()) {
                messageOutputStream.write(messageBody);
            }

            // Get the response
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                // Get the body as a json
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    final StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    // Parse the response as json
                    final JsonObject responseJson = JsonParser.parseString(response.toString()).getAsJsonObject();
                    if (responseJson.has("url")) {
                        return responseJson.get("url").getAsString();
                    }
                    return "(Failed to get URL from response)";
                }
            } else {
                return "(Failed to upload to logs site, got: " + connection.getResponseCode() + ")";
            }
        } catch (Exception e) {
            plugin.getLoggingAdapter().log(Level.SEVERE, "Failed to upload data to logs site", e);
        }
        return "(Failed to upload to logs site)";
    }

    @NotNull
    private String getWebContentField() {
        return "content=" + URLEncoder.encode(toString(), StandardCharsets.UTF_8);
    }

    /**
     * Dump the {@link UserDataSnapshot} to a file and return the file name
     *
     * @return the relative path of the file the data was dumped to
     */
    @NotNull
    public String toFile() throws IOException {
        final File filePath = getFilePath();

        // Write the data from #getString to the file using a writer
        try (final FileWriter writer = new FileWriter(filePath, StandardCharsets.UTF_8, false)) {
            writer.write(toString());
        } catch (IOException e) {
            throw new IOException("Failed to write data to file", e);
        }

        return "~/plugins/HuskSync/dumps/" + filePath.getName();
    }

    /**
     * Get the file path to dump the data to
     *
     * @return the file path
     * @throws IOException if the prerequisite dumps parent folder could not be created
     */
    @NotNull
    private File getFilePath() throws IOException {
        return new File(getDumpsFolder(), getFileName());
    }

    /**
     * Get the folder to dump the data to and create it if it does not exist
     *
     * @return the dumps folder
     * @throws IOException if the folder could not be created
     */
    @NotNull
    private File getDumpsFolder() throws IOException {
        final File dumpsFolder = new File(plugin.getDataFolder(), "dumps");
        if (!dumpsFolder.exists()) {
            if (!dumpsFolder.mkdirs()) {
                throw new IOException("Failed to create user data dumps folder");
            }
        }
        return dumpsFolder;
    }

    /**
     * Get the name of the file to dump the data snapshot to
     *
     * @return the file name
     */
    @NotNull
    private String getFileName() {
        return new StringJoiner("_")
                       .add(user.username)
                       .add(new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(dataSnapshot.versionTimestamp()))
                       .add(dataSnapshot.cause().name().toLowerCase())
                       .add(dataSnapshot.versionUUID().toString().split("-")[0])
               + ".json";
    }

}
