import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Tugas 5 Kriptografi (Kelompok 4)
 * Program verifikasi file (checksum) memakai SHA3-512.
 * Hanya memakai library standar Java: java.security.MessageDigest.
 *
 * Penggunaan:
 *   java Sha3Checksum hash   <file> [file2 ...]
 *   java Sha3Checksum verify <file> <digest_hex>
 *   java Sha3Checksum help
 *
 * Exit code: 0 = sukses/MATCH, 1 = MISMATCH, 2 = error penggunaan atau I/O.
 */
public class Sha3Checksum {

    private static final String ALGORITMA = "SHA3-512";
    private static final int UKURAN_BUFFER = 64 * 1024;   // 64 KB per pembacaan
    private static final int PANJANG_HEX = 128;           // 64 byte = 128 karakter hex

    public static final int EXIT_OK = 0;
    public static final int EXIT_MISMATCH = 1;
    public static final int EXIT_ERROR = 2;

    // Hasil penghitungan hash: digest + info ukuran file dan waktu proses
    private static class HasilHash {
        final byte[] digest;
        final long ukuranByte;
        final long waktuMs;

        HasilHash(byte[] digest, long ukuranByte, long waktuMs) {
            this.digest = digest;
            this.ukuranByte = ukuranByte;
            this.waktuMs = waktuMs;
        }
    }

    // Error yang pesannya sudah siap ditampilkan ke pengguna
    private static class ErrorPengguna extends Exception {
        private static final long serialVersionUID = 1L;

        ErrorPengguna(String pesan) {
            super(pesan);
        }
    }

    public static void main(String[] args) {
        System.exit(jalankan(args));
    }

    /** Memilih mode sesuai argumen pertama dan mengembalikan exit code. */
    static int jalankan(String[] args) {
        if (args.length == 0) {
            tampilkanUsage(System.err);
            return EXIT_ERROR;
        }
        String mode = args[0];
        try {
            switch (mode) {
                case "help":
                    tampilkanUsage(System.out);
                    return EXIT_OK;
                case "hash":
                    if (args.length < 2) {
                        throw new ErrorPengguna("Mode 'hash' membutuhkan minimal satu file.");
                    }
                    return modeHash(args);
                case "verify":
                    if (args.length != 3) {
                        throw new ErrorPengguna("Mode 'verify' membutuhkan <file> dan <digest_hex>.");
                    }
                    return modeVerify(args[1], args[2]);
                default:
                    throw new ErrorPengguna("Mode tidak dikenal: " + mode);
            }
        } catch (ErrorPengguna e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            tampilkanUsage(System.err);
            return EXIT_ERROR;
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error: algoritma " + ALGORITMA
                    + " tidak tersedia. Gunakan Java 9 atau yang lebih baru.");
            return EXIT_ERROR;
        } catch (IOException e) {
            System.err.println("Error I/O: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    /** Mode hash: cetak "<digest>  <namafile>" untuk tiap file (mirip sha3sum). */
    private static int modeHash(String[] args)
            throws NoSuchAlgorithmException, IOException, ErrorPengguna {
        // Cek semua file dulu supaya error ditemukan sebelum ada output
        for (int i = 1; i < args.length; i++) {
            periksaFile(args[i]);
        }
        for (int i = 1; i < args.length; i++) {
            HasilHash hasil = hitungHash(new File(args[i]));
            System.out.println(keHex(hasil.digest) + "  " + args[i]);
            System.err.println("  [" + args[i] + "] ukuran: " + hasil.ukuranByte
                    + " byte, waktu: " + hasil.waktuMs + " ms");
        }
        return EXIT_OK;
    }

    /** Mode verify: bandingkan digest file dengan digest yang diharapkan. */
    private static int modeVerify(String namaFile, String digestInput)
            throws NoSuchAlgorithmException, IOException, ErrorPengguna {
        byte[] diharapkan = parseDigestHex(digestInput);
        periksaFile(namaFile);

        HasilHash hasil = hitungHash(new File(namaFile));
        // isEqual membandingkan byte dalam waktu konstan (bukan String.equals)
        boolean cocok = MessageDigest.isEqual(hasil.digest, diharapkan);

        System.out.println(cocok ? "MATCH" : "MISMATCH");
        System.out.println("  Digest hitung    : " + keHex(hasil.digest));
        System.out.println("  Digest diharapkan: " + keHex(diharapkan));
        System.err.println("  [" + namaFile + "] ukuran: " + hasil.ukuranByte
                + " byte, waktu: " + hasil.waktuMs + " ms");
        return cocok ? EXIT_OK : EXIT_MISMATCH;
    }

    /**
     * Alur inti: baca file per chunk 64 KB -> update() -> digest().
     * File tidak pernah dimuat utuh ke memori.
     */
    private static HasilHash hitungHash(File file)
            throws NoSuchAlgorithmException, IOException {
        long mulai = System.nanoTime();
        MessageDigest md = MessageDigest.getInstance(ALGORITMA);
        byte[] buffer = new byte[UKURAN_BUFFER];
        long total = 0;

        try (InputStream in = new FileInputStream(file)) {
            int terbaca;
            while ((terbaca = in.read(buffer)) != -1) {
                md.update(buffer, 0, terbaca);
                total += terbaca;
            }
        }

        byte[] digest = md.digest();
        long waktuMs = (System.nanoTime() - mulai) / 1_000_000;
        return new HasilHash(digest, total, waktuMs);
    }

    /** Pastikan path ada, bukan direktori, dan bisa dibaca. */
    private static void periksaFile(String path) throws ErrorPengguna {
        File f = new File(path);
        if (!f.exists()) {
            throw new ErrorPengguna("File tidak ditemukan: " + path);
        }
        if (f.isDirectory()) {
            throw new ErrorPengguna("Path ini adalah direktori, bukan file: " + path);
        }
        if (!f.canRead()) {
            throw new ErrorPengguna("File tidak bisa dibaca (izin ditolak): " + path);
        }
    }

    /** Normalisasi (trim, lowercase), validasi 128 karakter hex, ubah ke byte. */
    private static byte[] parseDigestHex(String input) throws ErrorPengguna {
        String hex = input.trim().toLowerCase();
        if (hex.length() != PANJANG_HEX) {
            throw new ErrorPengguna("Digest harus " + PANJANG_HEX
                    + " karakter hex, tetapi yang diberikan " + hex.length() + " karakter.");
        }
        byte[] hasil = new byte[PANJANG_HEX / 2];
        for (int i = 0; i < hasil.length; i++) {
            int atas = Character.digit(hex.charAt(2 * i), 16);
            int bawah = Character.digit(hex.charAt(2 * i + 1), 16);
            if (atas < 0 || bawah < 0) {
                throw new ErrorPengguna("Digest mengandung karakter non-hex (hanya 0-9 dan a-f).");
            }
            hasil[i] = (byte) ((atas << 4) | bawah);
        }
        return hasil;
    }

    /** Ubah array byte menjadi string hex huruf kecil. */
    private static String keHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static void tampilkanUsage(PrintStream out) {
        out.println("Sha3Checksum - verifikasi file dengan SHA3-512");
        out.println();
        out.println("Penggunaan:");
        out.println("  java Sha3Checksum hash   <file> [file2 ...]");
        out.println("  java Sha3Checksum verify <file> <digest_hex>");
        out.println("  java Sha3Checksum help");
        out.println();
        out.println("Exit code: 0 = sukses/MATCH, 1 = MISMATCH, 2 = error");
    }
}
