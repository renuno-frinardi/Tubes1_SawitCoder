# Tugas Besar 1 IF2211 Strategi Algoritma Semester II 2025/2026
> Pemanfaatan Algoritma _Greedy_ dalam Pembuatan _Bot_ Permainan Battlecode 2025

### Anggota Kelompok @SawitCoder
| NIM      | Nama               |
| -------- | ------------------ |
| 13524028 | Muhammad Nur Majid   |
| 13524080 | Renuno Yuqa Frinardi   |
| 13524106 | Michael James Liman   |

### Deskripsi Singkat
Repositori ini berisi implementasi *bot* untuk permainan [MIT Battlecode 2025](https://battlecode.org/) yang dikembangkan menggunakan **Algoritma Greedy** untuk memenuhi Spesifikasi Tugas Besar 1 Mata Kuliah IF2211 Strategi Algoritma. Tujuan utama *bot* ini adalah mengambil keputusan lokal terbaik atau greedy guna memenangkan pertandingan secara optimal.

### Struktur Repositori Utama
- `src/` : Kumpulan kode sumber (*source code*) Java implementasi dari ketiga agen *bot*.
- `client/` : GUI pertandingan bawaan hasil build
- `matches/` : Kumpulan rekaman permainan untuk di-buka (replay) di *Client*.
- `build.gradle` / `gradlew` / `gradlew.bat` : *Scripts build system* bawaan *engine* Battlecode 2025.
- `doc/` : Dokumentasi lengkap (Laporan)

### Kebutuhan Sistem (Prerequisites)
- **Java Development Kit (JDK)** versi 17 atau ke atas.
- Modul Gradle (Tersedia serangkai (*wrapper*) di repositori ini).

### Cara Kompilasi dan Menjajal Percobaan (Run)
1. Lakukan *build* proyek sekaligus mengunduh dependensi (Gunakan Command Prompt, OS terminal, VSC, dll):
   * **Windows**: `.\gradlew build`
   * **Mac / Linux**: `./gradlew build`
   
2. Buka Battlecode 2025 *Client App* yang tersedia. Buka *Runner* untuk menjalankan pertandingan dan _Queue_ untuk menjalankan replay pertandingan.