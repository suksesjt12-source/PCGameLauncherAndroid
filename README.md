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
4. Untuk game sederhana, pilih satu file `.exe`; launcher menyalinnya ke `Download/PCGameLauncher`.
5. Untuk game multi-file, salin dahulu seluruh folder instalasi ke `Download/PCGameLauncher` dengan struktur folder tetap utuh. Lalu pilih file `.exe` langsung dari folder itu. Launcher akan memakai path asli agar DLL dan file data di sekitarnya tetap dapat ditemukan.
6. Di kartu game, pilih emulator serta container tujuan, lalu tekan **Mainkan**.

Bila file `.exe` dipilih dari lokasi lain di luar folder tersebut, launcher menyalin EXE saja. File DLL, arsip, atau aset lain tidak ikut tersalin otomatis.

## Batasan
- Launcher bukan mesin emulasi dan tidak menyertakan Wine, Box64, FEX, DXVK, atau driver GPU.
- Direct launch hanya bekerja bila emulator memiliki Launcher Bridge yang cocok dan container telah dibuat.
- Winlator Mali+ menjalankan satu layar sesi game; tutup sesi yang sedang aktif sebelum memulai game lain.
- Tidak ada jaminan setiap game Windows kompatibel atau berjalan baik di perangkat Android.

## Build APK launcher
Buka tab **Actions**, pilih workflow **Android APK Build**, lalu unduh artifact **PCGameLauncherPlus-debug-apk** setelah workflow berhasil.
