# PC Game Launcher Plus

Aplikasi launcher Android native untuk mengatur koleksi game Windows (.exe) dan membantu membuka runtime kompatibilitas eksternal.

## Fitur saat ini
- Kotlin + XML dengan Material dark UI.
- Impor file .exe menggunakan Android Storage Access Framework.
- Penyimpanan daftar game lokal dan izin URI dokumen.
- Buka file melalui Android intent jika runtime menyediakan handler.
- Tombol untuk membuka Winlator pada package ID yang dikenal.
- Hapus entri game dari library.
- GitHub Actions membangun APK debug dan mengunggah artifact.

## Batasan teknis
Aplikasi ini **bukan emulator Windows**. APK tidak menyertakan Wine, Box64, DXVK, driver GPU, atau runtime game. Winlator/Mobox/GameHub tidak memiliki satu kontrak intent publik yang seragam; jika runtime tidak menerima file EXE melalui intent, pengguna perlu membuka runtime dan mengimpor file dari dalamnya. Versi awal ini belum mengeksekusi EXE secara mandiri.

## Build APK
Buka tab **Actions** lalu jalankan workflow **Android APK Build**, atau push perubahan ke branch `main`. Unduh artifact `PCGameLauncherPlus-debug-apk` dari hasil workflow.

## Teknologi
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- Java 17
- compileSdk 35, minSdk 26
