package com.inventory.util;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * ZBar-based barcode decoder with ZXing fallback.
 *
 * <h3>Strategy</h3>
 * <ol>
 *   <li>Try to locate {@code zbarimg.exe} on PATH or in the project's
 *       {@code lib/zbar/} folder (place it there to avoid PATH issues).</li>
 *   <li>If found: write the frame to a temp PNG, invoke {@code zbarimg},
 *       parse its stdout for the barcode value, delete the temp file.</li>
 *   <li>If NOT found: fall back to the enhanced ZXing multi-pass decoder,
 *       which now includes rotation and inversion passes for better accuracy.</li>
 * </ol>
 *
 * <h3>How to install ZBar on Windows</h3>
 * <ol>
 *   <li>Download the Win64 installer from
 *       <a href="https://sourceforge.net/projects/zbar/files/zbar/0.10/">
 *       sourceforge.net/projects/zbar</a>.</li>
 *   <li>Install it (or just extract {@code zbarimg.exe} and its DLLs).</li>
 *   <li>Either add its folder to PATH <em>or</em> copy
 *       {@code zbarimg.exe} + DLLs into
 *       {@code <project_root>/lib/zbar/}.</li>
 * </ol>
 */
public final class ZBarScanner {

    /** Relative path (from project root) where we look for the bundled EXE. */
    private static final String BUNDLED_EXE_PATH = "lib/zbar/zbarimg.exe";

    /** Timeout for one ZBar subprocess call (milliseconds). */
    private static final long ZBAR_TIMEOUT_MS = 4_000;

    /** Cached: absolute path to zbarimg.exe, or null if not found. */
    private static volatile String zbarimgPath = null;

    /** True after we have completed one availability check. */
    private static volatile boolean checkedAvailability = false;

    private ZBarScanner() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Decode a barcode from a {@link BufferedImage}.
     *
     * <p>Tries ZBar first (if installed), then falls back to the enhanced
     * multi-pass ZXing decoder.</p>
     *
     * @param image source image (may be colour or grayscale)
     * @return decoded barcode string, or {@code null} if none found
     */
    public static String decode(BufferedImage image) {
        if (image == null) return null;

        // ── Try ZBar ──────────────────────────────────────────────────────────
        String zbarExe = resolveZBarExe();
        if (zbarExe != null) {
            String result = decodeWithZBar(image, zbarExe);
            if (result != null) {
                System.out.println("[ZBar] decoded: " + result);
                return result;
            }
        }

        // ── Fall back to enhanced ZXing ───────────────────────────────────────
        return decodeWithZXing(image);
    }

    /**
     * Returns {@code true} if {@code zbarimg.exe} was found on this machine.
     * Useful for showing a status hint in the UI.
     */
    public static boolean isZBarAvailable() {
        return resolveZBarExe() != null;
    }

    // ── ZBar subprocess ───────────────────────────────────────────────────────

    /**
     * Writes {@code image} to a temp PNG, runs {@code zbarimg --raw <file>},
     * and returns the first barcode value parsed from stdout.
     */
    private static String decodeWithZBar(BufferedImage image, String exePath) {
        File tmp = null;
        try {
            // Write frame to temp file
            tmp = File.createTempFile("zbar_frame_", ".png");
            ImageIO.write(image, "PNG", tmp);

            // Build command:
            // zbarimg --raw --quiet --xml=no <file>
            // --raw  → prints only the barcode payload (no "QR-Code:" prefix)
            // --quiet → suppress "scanned N barcode symbols" summary line
            ProcessBuilder pb = new ProcessBuilder(
                    exePath, "--raw", "--quiet", tmp.getAbsolutePath());
            pb.redirectErrorStream(false); // keep stderr separate

            Process proc = pb.start();

            // Read stdout on a separate thread so we never block
            Future<String> stdoutFuture = Executors.newSingleThreadExecutor()
                    .submit(() -> {
                        try (BufferedReader r = new BufferedReader(
                                new InputStreamReader(proc.getInputStream()))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = r.readLine()) != null) {
                                if (!line.isBlank()) sb.append(line.trim()).append("\n");
                            }
                            return sb.toString().trim();
                        }
                    });

            // Wait with timeout to avoid blocking forever (Windows freeze quirk)
            boolean finished = proc.waitFor(ZBAR_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                proc.destroyForcibly();
                System.err.println("[ZBar] timed out — process killed");
                return null;
            }

            String raw = stdoutFuture.get(500, TimeUnit.MILLISECONDS);

            // ZBar with --raw prints one barcode per line
            if (raw != null && !raw.isBlank()) {
                String first = raw.lines().findFirst().orElse(null);
                return (first != null && !first.isBlank()) ? first : null;
            }
            return null;

        } catch (Exception e) {
            System.err.println("[ZBar] subprocess error: " + e.getMessage());
            return null;
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    // ── ZXing enhanced multi-pass decoder ────────────────────────────────────

    /**
     * Enhanced ZXing decoder that performs multiple passes:
     * <ul>
     *   <li>Pass 1 – original image (colour + grayscale both)</li>
     *   <li>Pass 2 – contrast-boosted grayscale</li>
     *   <li>Pass 3 – inverted image (helps with dark-on-light prints)</li>
     *   <li>Pass 4 – 90° rotation (for barcodes held sideways)</li>
     *   <li>Pass 5 – 270° rotation</li>
     * </ul>
     */
    public static String decodeWithZXing(BufferedImage image) {
        if (image == null) return null;

        MultiFormatReader reader = buildZXingReader();

        // Pass 1 — colour original
        String r = tryZXing(reader, image);
        if (r != null) return r;

        // Convert to grayscale for remaining passes
        BufferedImage gray = toGray(image);

        // Pass 2 — grayscale
        r = tryZXing(reader, gray);
        if (r != null) return r;

        // Pass 3 — contrast boosted
        r = tryZXing(reader, boostContrast(gray));
        if (r != null) return r;

        // Pass 4 — inverted
        r = tryZXing(reader, invert(gray));
        if (r != null) return r;

        // Pass 5 — 90° clockwise
        r = tryZXing(reader, rotate90(gray));
        if (r != null) return r;

        // Pass 6 — 270° clockwise (= 90° counter-clockwise)
        r = tryZXing(reader, rotate270(gray));
        return r; // null if nothing found
    }

    private static MultiFormatReader buildZXingReader() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.ITF,
                BarcodeFormat.QR_CODE,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.PDF_417
        ));
        MultiFormatReader r = new MultiFormatReader();
        r.setHints(hints);
        return r;
    }

    private static String tryZXing(MultiFormatReader reader, BufferedImage image) {
        if (image == null) return null;
        try {
            BinaryBitmap bmp = new BinaryBitmap(
                    new HybridBinarizer(new BufferedImageLuminanceSource(image)));
            Result res = reader.decodeWithState(bmp);
            if (res != null && res.getText() != null && !res.getText().isBlank()) {
                return res.getText().trim();
            }
        } catch (NotFoundException ignored) {
            // normal — no barcode in this pass
        } catch (Exception e) {
            System.err.println("[ZXing] decode error: " + e.getMessage());
        } finally {
            reader.reset();
        }
        return null;
    }

    // ── Image transformation helpers ──────────────────────────────────────────

    private static BufferedImage toGray(BufferedImage src) {
        BufferedImage g = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY);
        java.awt.Graphics2D gfx = g.createGraphics();
        gfx.drawImage(src, 0, 0, null);
        gfx.dispose();
        return g;
    }

    /**
     * Applies a simple contrast stretch / gamma boost to help ZXing read
     * low-contrast barcodes (e.g. worn labels, reflective surfaces).
     */
    private static BufferedImage boostContrast(BufferedImage src) {
        java.awt.image.RescaleOp op = new java.awt.image.RescaleOp(1.35f, 20, null);
        try {
            return op.filter(src, null);
        } catch (Exception e) {
            return src; // RescaleOp occasionally fails on odd colour models
        }
    }

    /** Inverts pixel values — helps when barcodes are printed light-on-dark. */
    private static BufferedImage invert(BufferedImage src) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRaster().getSample(x, y, 0);
                dst.getRaster().setSample(x, y, 0, 255 - p);
            }
        }
        return dst;
    }

    private static BufferedImage rotate90(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(h, w, src.getType());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dst.setRGB(h - 1 - y, x, src.getRGB(x, y));
            }
        }
        return dst;
    }

    private static BufferedImage rotate270(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(h, w, src.getType());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dst.setRGB(y, w - 1 - x, src.getRGB(x, y));
            }
        }
        return dst;
    }

    // ── ZBar EXE resolver ─────────────────────────────────────────────────────

    /**
     * Returns the path to {@code zbarimg.exe}, or {@code null} if not found.
     * Result is cached after the first call.
     */
    private static String resolveZBarExe() {
        if (checkedAvailability) return zbarimgPath;

        synchronized (ZBarScanner.class) {
            if (checkedAvailability) return zbarimgPath;

            // 1. Check project-bundled location: lib/zbar/zbarimg.exe
            Path bundled = Paths.get(BUNDLED_EXE_PATH);
            if (Files.exists(bundled)) {
                zbarimgPath = bundled.toAbsolutePath().toString();
                System.out.println("[ZBar] found bundled EXE: " + zbarimgPath);
                checkedAvailability = true;
                return zbarimgPath;
            }

            // 2. Check if 'zbarimg' is on the system PATH
            String pathExe = findOnPath("zbarimg.exe");
            if (pathExe == null) pathExe = findOnPath("zbarimg"); // Linux/Mac fallback
            if (pathExe != null) {
                zbarimgPath = pathExe;
                System.out.println("[ZBar] found on PATH: " + zbarimgPath);
                checkedAvailability = true;
                return zbarimgPath;
            }

            // 3. Common Windows install locations
            String[] winPaths = {
                "C:\\Program Files\\ZBar\\bin\\zbarimg.exe",
                "C:\\Program Files (x86)\\ZBar\\bin\\zbarimg.exe",
                System.getenv("LOCALAPPDATA") + "\\ZBar\\bin\\zbarimg.exe"
            };
            for (String p : winPaths) {
                if (p != null && Files.exists(Paths.get(p))) {
                    zbarimgPath = p;
                    System.out.println("[ZBar] found at: " + zbarimgPath);
                    checkedAvailability = true;
                    return zbarimgPath;
                }
            }

            System.out.println("[ZBar] zbarimg.exe not found — using ZXing fallback.");
            zbarimgPath = null;
            checkedAvailability = true;
            return null;
        }
    }

    /** Searches each directory in {@code PATH} for the given file name. */
    private static String findOnPath(String exeName) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(File.pathSeparator)) {
            Path candidate = Paths.get(dir, exeName);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
        }
        return null;
    }
}
