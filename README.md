# Verifikasi File dengan SHA3-512 (Java)

Tugas 5 Kriptografi, Kelompok 4, Kriptografi - A, Program Studi Teknologi Informasi, Fakultas Ilmu Komputer, Universitas Brawijaya.

Program command-line berbahasa **Java** untuk menghitung dan memverifikasi checksum file memakai **SHA3-512**. Program hanya memakai library standar (`java.security.MessageDigest`), tanpa dependensi eksternal dan tanpa Maven atau Gradle.

**Disusun oleh:** Muhammad Iqbal Azzahir, Rafi Kamasyamsi, I Kadek Miko Suardana

## Fitur
- **`hash`**: menghitung SHA3-512 satu file atau lebih, dicetak dalam heksadesimal huruf kecil dengan format `<digest>  <namafile>` (mirip `sha3sum`).
- **`verify`**: membandingkan digest file dengan digest yang diharapkan, hasilnya `MATCH` atau `MISMATCH`. Perbandingan memakai `MessageDigest.isEqual` (constant-time). Digest masukan dinormalisasi (trim dan huruf kecil) dan divalidasi (harus 128 karakter heksadesimal).
- **Streaming**: file dibaca per potongan 64 KB sehingga file besar tidak dimuat utuh ke memori.
- Menampilkan **ukuran file dan waktu proses** (ke stderr, jadi keluaran utama tetap bersih).
- Pesan error dalam Bahasa Indonesia dengan exit code yang jelas.

## Prasyarat
**JDK 9 atau lebih baru.** SHA3 tersedia di `MessageDigest` sejak Java 9. Program dikembangkan dengan target Java 17 dan diuji di Eclipse Temurin JDK 21.0.13.

Cek dengan:
```
java -version
javac -version
```

## Build dan penggunaan
Jalankan perintah dari folder yang berisi `src` dan `samples`.

```
# Build
javac -d out src/Sha3Checksum.java

# Hitung hash (boleh banyak file)
java -cp out Sha3Checksum hash samples/hello.txt
java -cp out Sha3Checksum hash samples/hello.txt samples/kosong.txt

# Verifikasi
java -cp out Sha3Checksum verify samples/hello.txt <digest_128_karakter>

# Bantuan
java -cp out Sha3Checksum help
```

### Exit code
| Kode | Arti |
|------|------|
| 0 | Sukses atau hasil verifikasi `MATCH` |
| 1 | Hasil verifikasi `MISMATCH` |
| 2 | Kesalahan penggunaan atau I/O (argumen salah, file tidak ada, digest tidak valid) |

### Contoh keluaran
Hasil asli dari pengujian di Windows (PowerShell):

```
> java -cp out Sha3Checksum hash samples/hello.txt samples/kosong.txt
1c8cacf94f1e1ca52d2a7bf1c7c7b5c8ce94c639bcc26d39fd8aa11d57fe13321cb8f5a70deb1a48af34cb71f6218e4bd16acbf45dda5b2e953ccfa099b530ff  samples/hello.txt
  [samples/hello.txt] ukuran: 60 byte, waktu: 17 ms
a69f73cca23a9ac5c8b567dc185a756e97c982164fe25859e0d1dcc1475c80a615b2123af1f5f94c11e3e9402c3ac558f500199d95b6d3e301758586281dcd26  samples/kosong.txt
  [samples/kosong.txt] ukuran: 0 byte, waktu: 0 ms
```

Mode `verify`:
```
> java -cp out Sha3Checksum verify samples/hello.txt 1c8cacf9...b530ff
MATCH
  Digest hitung    : 1c8cacf9...b530ff
  Digest diharapkan: 1c8cacf9...b530ff
```
(Digest dipotong di sini agar ringkas. Program mencetak digest lengkap 128 karakter.)

Contoh pesan error:
```
Error: File tidak ditemukan: tidak_ada.txt
Error: Path ini adalah direktori, bukan file: samples
Error: Digest harus 128 karakter hex, tetapi yang diberikan 6 karakter.
```

## Tentang SHA3-512
- Standar **NIST FIPS 202** (2015), berbasis algoritma **Keccak**, pemenang kompetisi SHA-3.
- Memakai **konstruksi sponge**: data diserap (*absorb*) ke state 1600 bit, lalu digest diperas (*squeeze*). Untuk SHA3-512, *rate* = 576 bit dan *capacity* = 1024 bit. State diacak dengan permutasi Keccak-f[1600] sebanyak 24 putaran.
- Digest **512 bit = 64 byte = 128 karakter heksadesimal**.
- Berbeda dengan SHA-2 (Merkle-Damgård), SHA-3 tahan terhadap **length-extension attack** karena bagian *capacity* tidak pernah keluar sebagai output.

## Alur program
1. Validasi mode, argumen, dan file (harus ada, bukan direktori, dapat dibaca).
2. `MessageDigest.getInstance("SHA3-512")`.
3. Baca file per potongan 64 KB (`FileInputStream` dalam try-with-resources), lalu `md.update(buffer, 0, n)`.
4. `md.digest()` menghasilkan array 64 byte yang diubah menjadi string heksadesimal huruf kecil.
5. Mode `verify`: digest masukan dinormalisasi dan divalidasi, diubah ke byte, lalu dibandingkan dengan `MessageDigest.isEqual`.

## Hasil pengujian
Pengujian dilakukan manual di **Windows (PowerShell), Eclipse Temurin JDK 21.0.13**, dengan **OpenSSL bawaan Git for Windows** sebagai pembanding independen. Build memakai `javac -Xlint:all` dan tidak menghasilkan warning.

| No | Skenario | Hasil yang diharapkan | Bukti | Hasil |
|----|----------|-----------------------|-------|-------|
| 1 | Vektor NIST string kosong | Digest sama dengan vektor NIST | [Gambar 4.2](docs/screenshots/gambar-4-2-hash-hello-kosong.png) | Lulus |
| 2 | Vektor NIST `abc` | Digest sama dengan vektor NIST | [Gambar 4.3](docs/screenshots/gambar-4-3-vektor-nist-abc.png) | Lulus |
| 3 | Pembanding OpenSSL (3 file) | Identik dengan `openssl dgst -sha3-512` | [Gambar 4.4](docs/screenshots/gambar-4-4-pembanding-openssl.png) | Lulus |
| 4 | Banyak file sekaligus | Satu baris `<digest>  <namafile>` per file | [Gambar 4.2](docs/screenshots/gambar-4-2-hash-hello-kosong.png) | Lulus |
| 5 | `verify` cocok | `MATCH`, exit code 0 | [Gambar 4.5](docs/screenshots/gambar-4-5-verify-match-mismatch.png) | Lulus |
| 6 | `verify` tidak cocok | `MISMATCH`, exit code 1 | [Gambar 4.5](docs/screenshots/gambar-4-5-verify-match-mismatch.png) | Lulus |
| 7 | Digest huruf besar | Tetap `MATCH` | [Gambar 4.6](docs/screenshots/gambar-4-6-verify-huruf-besar-digest-salah.png) | Lulus |
| 8 | Digest 6 karakter | Pesan error, exit code 2 | [Gambar 4.6](docs/screenshots/gambar-4-6-verify-huruf-besar-digest-salah.png) | Lulus |
| 9 | File tidak ada | Pesan error, exit code 2 | [Gambar 4.7](docs/screenshots/gambar-4-7-pesan-error.png) | Lulus |
| 10 | Path berupa direktori | Pesan error, exit code 2 | [Gambar 4.7](docs/screenshots/gambar-4-7-pesan-error.png) | Lulus |
| 11 | File acak 100 MB dengan `-Xmx16m` | Identik dengan OpenSSL, tanpa OutOfMemory (708 ms) | [Gambar 4.8](docs/screenshots/gambar-4-8-file-100mb.png) | Lulus |
| 12 | Sensitivitas `abc` vs `abd` | Digest berubah total (120 dari 128 karakter hex berbeda) | [Gambar 4.9](docs/screenshots/gambar-4-9-efek-avalanche.png) | Lulus |

Screenshot versi Java dan build: [Gambar 4.1](docs/screenshots/gambar-4-1-versi-java-dan-build.png).

### Vektor uji resmi NIST
| Masukan | Digest SHA3-512 |
|---------|-----------------|
| String kosong | `a69f73cca23a9ac5c8b567dc185a756e97c982164fe25859e0d1dcc1475c80a615b2123af1f5f94c11e3e9402c3ac558f500199d95b6d3e301758586281dcd26` |
| `abc` | `b751850b1a57168a5693cd924b6b096e08f621827444f70d884f5d0240d2712e10e116e9192af3c91a7ec57647e3934057340b4cf408d5a56592f8274eec53f0` |

Untuk membuat file `abc` tanpa newline di PowerShell:
```
[IO.File]::WriteAllText("$PWD\abc.txt", "abc")
```
Di Linux atau macOS: `printf 'abc' > abc.txt`.

### Membandingkan dengan OpenSSL
```
openssl dgst -sha3-512 samples/hello.txt
```
Digest yang dihasilkan harus sama dengan keluaran program ini.

## Skrip pengujian otomatis (opsional)
`test.sh` menjalankan serangkaian pemeriksaan otomatis: vektor NIST, perbandingan dengan OpenSSL, berkas 100 MB dengan heap 16 MB, sensitivitas, mode `verify`, dan kasus error. Skrip ini ditulis untuk **Linux atau WSL** dan membutuhkan `bash` serta `openssl`.
```
bash test.sh
```
Skrip ini juga dijalankan di Linux (OpenJDK 21.0.12) dan seluruh 24 pemeriksaannya lulus. Di Windows, hasil pengujian utama ada pada tabel di atas.

## Struktur proyek
```
.
├── src/Sha3Checksum.java        kode program (satu berkas)
├── samples/                     berkas contoh (hello.txt, kosong.txt)
├── docs/screenshots/            bukti pengujian di Windows
├── test.sh                      skrip pengujian otomatis (Linux/WSL)
├── .gitattributes               menjaga akhir baris berkas contoh dan skrip
├── .gitignore
└── README.md
```

## Keterbatasan dan asumsi
- Mode `verify` hanya menerima digest mentah 128 karakter heksadesimal, bukan berkas checksum berformat `sha3sum -c`.
- Waktu proses yang ditampilkan mencakup pembacaan file dan hashing, tetapi tidak termasuk waktu start JVM. Eksekusi pertama sedikit lebih lambat karena pemanasan JVM.
- Info ukuran dan waktu dicetak ke stderr, sehingga keluaran utama di stdout tetap `<digest>  <namafile>` dan dapat di-pipe.
- Pada mode `hash` dengan banyak file, semua file diperiksa lebih dulu. Jika ada yang bermasalah, program berhenti dengan exit code 2 sebelum mencetak digest apa pun.
- Digest berbasis byte persis. Karena itu berkas di `samples/` dijaga agar akhir barisnya tidak diubah oleh Git (lihat `.gitattributes`). Jika `hello.txt` berubah menjadi CRLF, digest-nya ikut berubah.
