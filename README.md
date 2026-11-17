# PC Game Launcher Plus

Launcher Android native untuk mengatur koleksi game Windows (.exe) dan membuka runtime kompatibilitas yang telah dipasang.

## Fitur
- Impor file .exe melalui Android Storage Access Framework.
- Menyimpan daftar game dan izin akses file secara lokal.
- Mendeteksi aplikasi runtime berdasarkan nama aplikasi/package yang memuat Winlator, Ludashi, Mobox, Frost, GLIBC, atau Cmod.
- Memilih runtime utama.
- Memilih runtime berbeda untuk setiap profil game.
- Membuka aplikasi runtime yang dipilih dari profil game.
- Build APK debug otomatis melalui GitHub Actions.

## Batasan teknis
Aplikasi ini **bukan emulator Windows**. APK tidak menyertakan Wine, Box64, DXVK, driver GPU, atau runtime game. Deteksi runtime menggunakan daftar aplikasi yang terlihat oleh Android; hasilnya bergantung pada nama aplikasi/package dari varian yang dipasang.

Tombol **Buka emulator** membuka aplikasi runtime. Launcher belum dapat memilih container atau mengeksekusi EXE secara langsung karena Winlator dan turunannya tidak menyediakan satu kontrak intent publik yang seragam. Setelah runtime terbuka, pilih container dan jalankan EXE dari dalam runtime.

## Build APK
Buka tab **Actions** dan pilih workflow **Android APK Build**. Setelah build berhasil, unduh artifact **PCGameLauncherPlus-debug-apk** dari halaman hasil workflow.

## Teknologi
- Kotlin
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- Java 17
- compileSdk 35, minSdk 26
