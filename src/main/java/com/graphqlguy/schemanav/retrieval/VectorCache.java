package com.graphqlguy.schemanav.retrieval;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * A small on-disk cache for corpus embeddings, so a 25,000-coordinate schema is
 * embedded once and every later run (a benchmark sweep, a format comparison, one
 * more search while playing around) loads vectors in milliseconds. The key hashes
 * the model, the prefixes, and every corpus text, so any change re-embeds.
 *
 * File format: entry count, then per entry the dimension and the raw floats.
 */
final class VectorCache {

    private static final Path DIR = Path.of(".embcache");

    private VectorCache() {
    }

    static List<float[]> load(String key, int expectedCount) {
        Path file = DIR.resolve(key + ".bin");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file);
             DataInputStream data = new DataInputStream(in.markSupported() ? in : new java.io.BufferedInputStream(in))) {
            int count = data.readInt();
            if (count != expectedCount) {
                return null;
            }
            List<float[]> vectors = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int dim = data.readInt();
                float[] vector = new float[dim];
                for (int d = 0; d < dim; d++) {
                    vector[d] = data.readFloat();
                }
                vectors.add(vector);
            }
            System.err.println("  embeddings loaded from cache " + file);
            return vectors;
        } catch (IOException e) {
            return null;
        }
    }

    static void store(String key, List<float[]> vectors) {
        try {
            Files.createDirectories(DIR);
            Path file = DIR.resolve(key + ".bin");
            try (OutputStream out = Files.newOutputStream(file);
                 DataOutputStream data = new DataOutputStream(new java.io.BufferedOutputStream(out))) {
                data.writeInt(vectors.size());
                for (float[] vector : vectors) {
                    data.writeInt(vector.length);
                    for (float v : vector) {
                        data.writeFloat(v);
                    }
                }
            }
            System.err.println("  embeddings cached to " + file);
        } catch (IOException e) {
            System.err.println("  embedding cache write failed (continuing without): " + e.getMessage());
        }
    }

    static String sha256(String material) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
