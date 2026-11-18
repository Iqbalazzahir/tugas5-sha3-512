#!/usr/bin/env bash
# Skrip pengujian otomatis Sha3Checksum (SHA3-512).
# Jalankan dari folder proyek:  bash test.sh
cd "$(dirname "$0")" || exit 2

LULUS=0
GAGAL=0
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# Build (pakai javac jika ada; jika tidak, pakai modul compiler lewat java)
mkdir -p out
if command -v javac >/dev/null 2>&1; then
  javac -Xlint:all -d out src/Sha3Checksum.java || exit 2
else
  java -m jdk.compiler/com.sun.tools.javac.Main -Xlint:all -d out src/Sha3Checksum.java || exit 2
fi
echo "Build OK"
echo

run() { java -cp out Sha3Checksum "$@"; }

cek() {  # cek <nama> <kondisi: 0=lulus>
  if [ "$2" -eq 0 ]; then echo "[LULUS] $1"; LULUS=$((LULUS+1));
  else echo "[GAGAL] $1"; GAGAL=$((GAGAL+1)); fi
}

digest_dari() { run hash "$1" 2>/dev/null | awk '{print $1}'; }

KOSONG=a69f73cca23a9ac5c8b567dc185a756e97c982164fe25859e0d1dcc1475c80a615b2123af1f5f94c11e3e9402c3ac558f500199d95b6d3e301758586281dcd26
ABC=b751850b1a57168a5693cd924b6b096e08f621827444f70d884f5d0240d2712e10e116e9192af3c91a7ec57647e3934057340b4cf408d5a56592f8274eec53f0

echo "== 1. Vektor uji resmi NIST =="
: > "$TMP/kosong.txt"
printf 'abc' > "$TMP/abc.txt"
[ "$(digest_dari "$TMP/kosong.txt")" = "$KOSONG" ]; cek "String kosong sesuai NIST" $?
[ "$(digest_dari "$TMP/abc.txt")" = "$ABC" ];       cek "'abc' sesuai NIST" $?

echo; echo "== 2. Pembanding independen (OpenSSL) =="
for f in samples/hello.txt samples/kosong.txt "$TMP/abc.txt"; do
  ref=$(openssl dgst -sha3-512 "$f" | awk '{print $NF}')
  [ "$(digest_dari "$f")" = "$ref" ]; cek "Sama dengan OpenSSL: $(basename "$f")" $?
done

echo; echo "== 3. File besar (100 MB) dengan heap dibatasi 16 MB =="
head -c 104857600 /dev/urandom > "$TMP/besar.bin"
ref=$(openssl dgst -sha3-512 "$TMP/besar.bin" | awk '{print $NF}')
hasil=$(java -Xmx16m -cp out Sha3Checksum hash "$TMP/besar.bin" 2>"$TMP/err.txt" | awk '{print $1}')
[ "$hasil" = "$ref" ]; cek "100 MB sama dengan OpenSSL (-Xmx16m, tidak OutOfMemory)" $?
echo "     info: $(cat "$TMP/err.txt")"

echo; echo "== 4. Sensitivitas (ubah 1 karakter) =="
printf 'abc' > "$TMP/a1.txt"; printf 'abd' > "$TMP/a2.txt"
d1=$(digest_dari "$TMP/a1.txt"); d2=$(digest_dari "$TMP/a2.txt")
[ "$d1" != "$d2" ]; cek "abc vs abd menghasilkan digest berbeda" $?
beda=$(paste -d'\n' <(echo "$d1" | fold -w1) <(echo "$d2" | fold -w1) | paste - - | awk '$1!=$2' | wc -l)
echo "     info: $beda dari 128 karakter hex berbeda"

echo; echo "== 5. Mode verify =="
run verify "$TMP/abc.txt" "$ABC" >/dev/null 2>&1;                 cek "MATCH -> exit 0" $([ $? -eq 0 ] && echo 0 || echo 1)
run verify "$TMP/abc.txt" "$ABC" 2>/dev/null | head -1 | grep -qx MATCH; cek "Output 'MATCH'" $?
run verify "$TMP/abc.txt" "$KOSONG" >/dev/null 2>&1;              cek "MISMATCH -> exit 1" $([ $? -eq 1 ] && echo 0 || echo 1)
run verify "$TMP/abc.txt" "$KOSONG" 2>/dev/null | head -1 | grep -qx MISMATCH; cek "Output 'MISMATCH'" $?
run verify "$TMP/abc.txt" "$(echo "$ABC" | tr a-f A-F)" >/dev/null 2>&1; cek "Digest HURUF BESAR tetap MATCH" $([ $? -eq 0 ] && echo 0 || echo 1)
run verify "$TMP/abc.txt" "  $ABC  " >/dev/null 2>&1;             cek "Digest dengan spasi (trim) tetap MATCH" $([ $? -eq 0 ] && echo 0 || echo 1)
run verify "$TMP/abc.txt" "abc123" >/dev/null 2>&1;               cek "Digest terlalu pendek -> exit 2" $([ $? -eq 2 ] && echo 0 || echo 1)
run verify "$TMP/abc.txt" "${ABC}00" >/dev/null 2>&1;             cek "Digest terlalu panjang -> exit 2" $([ $? -eq 2 ] && echo 0 || echo 1)
run verify "$TMP/abc.txt" "$(printf 'z%.0s' $(seq 128))" >/dev/null 2>&1; cek "Digest non-hex (128 huruf z) -> exit 2" $([ $? -eq 2 ] && echo 0 || echo 1)

echo; echo "== 6. Kasus error =="
run hash tidak_ada.txt >/dev/null 2>&1;      cek "File tidak ada -> exit 2" $([ $? -eq 2 ] && echo 0 || echo 1)
run hash samples >/dev/null 2>&1;            cek "Direktori -> exit 2" $([ $? -eq 2 ] && echo 0 || echo 1)
run hash >/dev/null 2>&1;                    cek "Argumen kurang (hash tanpa file) -> exit 2" $([ $? -eq 2 ] && echo 0 || echo 1)
run >/dev/null 2>&1;                         cek "Tanpa argumen -> exit 2" $([ $? -eq 2 ] && echo 0 || echo 1)
run verify "$TMP/abc.txt" >/dev/null 2>&1;   cek "verify tanpa digest -> exit 2" $([ $? -eq 2 ] && echo 0 || echo 1)
run foo samples/hello.txt >/dev/null 2>&1;   cek "Mode tidak dikenal -> exit 2" $([ $? -eq 2 ] && echo 0 || echo 1)
run help >/dev/null 2>&1;                    cek "help -> exit 0" $([ $? -eq 0 ] && echo 0 || echo 1)

echo; echo "== 7. Beberapa file sekaligus =="
out=$(run hash "$TMP/abc.txt" "$TMP/kosong.txt" 2>/dev/null)
[ "$out" = "$ABC  $TMP/abc.txt
$KOSONG  $TMP/kosong.txt" ]; cek "Format '<digest>  <nama>' per baris" $?

echo
echo "RINGKASAN: $LULUS lulus, $GAGAL gagal"
[ "$GAGAL" -eq 0 ]
