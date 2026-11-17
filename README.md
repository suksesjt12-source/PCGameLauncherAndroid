# PC Game Launcher Plus

Launcher Android untuk mengelola game Windows (.exe), memilih emulator dan container, lalu memulai game secara langsung melalui integrasi Winlator Mali+.

## Fitur
- Impor file .exe melalui Android Storage Access Framework.
- Menyalin game ke folder bersama `Download/PCGameLauncher`, yang dipetakan sebagai drive Download dalam container.
- Mendeteksi aplikasi Winlator yang terpasang.
- Memilih emulator dan container utama.
- Memilih emulator dan container untuk masing-masing game.
- Memanggil Launcher Bridge Winlator Mali+ untuk menjalankan EXE menggunakan container pilihan.
- Menyimpan profil game dan lokasi EXE secara lokal.

## Persyaratan untuk peluncuran langsung
Peluncuran langsung saat ini ditujukan untuk varian yang dibangun dari repository **WinlatorMaliPlus** milik proyek ini, setelah commit Launcher Bridge dipasang dan APK emulator dibangun ulang. Package ID flavor yang didukung:
- `com.winlator.mali.plus` (standard)
- `com.ludasi.benchmark` (Ludashi)
- `com.antutu.ABenchMark` (AnTuTu)
- `com.futuremark.dmandroid.application` (Futuremark)

Versi Winlator resmi, Frost, dan mod lain tanpa Launcher Bridge hanya dapat dibuka sebagai aplikasi emulator; launcher tidak dapat membaca container atau menjalankan EXE langsung dari varian tersebut.

## Cara menggunakan
1. Bangun dan pasang APK Winlator Mali+ terbaru dari repository WinlatorMaliPlus yang sudah memiliki Launcher Bridge.
2. Buka Winlator Mali+ sekurangnya sekali, pastikan image filesystem siap, lalu buat satu atau beberapa container.
3. Buka PC Game Launcher Plus, pilih emulator dan container utama.
4. Tambahkan file `.exe`. Saat diminta, pilih folder **Download** pada penyimpanan internal. Launcher membuat subfolder `PCGameLauncher` dan menyalin file ke sana.
5. Di kartu game, pilih emulator serta container tujuan, lalu tekan **Mainkan**.

Game harus mempunyai semua file pendukungnya (misalnya DLL dan folder data) dalam folder yang sama atau lokasi yang dapat diakses melalui drive container. Menyalin satu file EXE saja tidak otomatis menyalin seluruh folder instalasi.

## Batasan
- Launcher bukan mesin emulasi dan tidak menyertakan Wine, Box64, FEX, DXVK, atau driver GPU.
- Direct launch hanya bekerja bila emulator memiliki Launcher Bridge yang cocok dan container telah dibuat.
- Winlator Mali+ menjalankan satu layar sesi game; tutup sesi yang sedang aktif sebelum memulai game lain.
- Tidak ada jaminan setiap game Windows kompatibel atau berjalan baik di perangkat Android.

## Build APK launcher
Buka tab **Actions**, pilih workflow **Android APK Build**, lalu unduh artifact **PCGameLauncherPlus-debug-apk** setelah workflow berhasil.
