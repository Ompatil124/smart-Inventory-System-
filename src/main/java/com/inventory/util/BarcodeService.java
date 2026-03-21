package com.inventory.util;

import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Stateless utility for barcode generation (CODE-128) and multi-format decoding.
 *
 * <p>Decoding is delegated to {@link ZBarScanner}, which tries the native
 * {@code zbarimg.exe} (ZBar) first, then falls back to an enhanced multi-pass
 * ZXing decode.  Generation still uses ZXing's {@link MultiFormatWriter}.</p>
 *
 * <p>All methods are static; do not instantiate.</p>
 */
public final class BarcodeService {

    private BarcodeService() {}

    // ── Generate ──────────────────────────────────────────────────────────────

    /**
     * Generates a CODE-128 barcode image for the given text.
     *
     * @param text the string to encode (product name, SKU, etc.)
     * @return a 300×100 BufferedImage of the barcode
     * @throws Exception (WriterException) if encoding fails
     */
    public static BufferedImage generateBarcode(String text) throws Exception {
        if (text == null || text.isBlank())
            throw new IllegalArgumentException("Barcode text must not be blank.");
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 2);
        BitMatrix matrix = new MultiFormatWriter()
                .encode(text, BarcodeFormat.CODE_128, 300, 100, hints);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    /**
     * Generates a compact CODE-128 barcode suitable for table cell display.
     *
     * @param text the string to encode
     * @return a 90×28 BufferedImage, or {@code null} if encoding fails
     */
    public static BufferedImage generateSmall(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 0);
            BitMatrix matrix = new MultiFormatWriter()
                    .encode(text, BarcodeFormat.CODE_128, 90, 28, hints);
            return MatrixToImageWriter.toBufferedImage(matrix);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Decode ────────────────────────────────────────────────────────────────

    /**
     * Decodes a barcode from a {@link BufferedImage}.
     *
     * <p>Delegates to {@link ZBarScanner#decode(BufferedImage)}, which first
     * tries the native {@code zbarimg.exe} (ZBar) and falls back to an
     * enhanced multi-pass ZXing decode if ZBar is not installed.</p>
     *
     * <p>Supports EAN-13, EAN-8, UPC-A, UPC-E, CODE-128, CODE-39, CODE-93,
     * ITF, QR Code, Data Matrix, PDF-417, and more.</p>
     *
     * @param image the image containing the barcode
     * @return the decoded text, or {@code null} if no barcode was found
     */
    public static String decodeBarcode(BufferedImage image) {
        return ZBarScanner.decode(image);
    }
}
