# Salat DND v1.1

Fitur:
- Menampilkan jadwal Subuh, Zuhur, Asar, Magrib, Isya dari lokasi perangkat menggunakan API Aladhan (metode 20 / Kementerian Agama RI).
- Mengatur durasi DND 1-180 menit.
- Penjadwalan otomatis dan toggle manual DND.
- Memperbarui kalender setiap hari dan mencoba menjadwalkan ulang setelah perangkat restart.

Build:
- Buka repositori di GitHub.
- Jalankan workflow `Build SalatDND APK` dari tab Actions.
- Unduh artifact `SalatDND-debug-apk`.

Catatan:
- Perlu internet, izin lokasi, dan akses Notification Policy (DND).
- Android/Samsung bisa membatasi alarm jika aplikasi dimasukkan ke daftar aplikasi tidur atau dibatasi baterainya.
- Waktu salat dihitung dengan metode API 20 (Kementerian Agama RI). Verifikasi dengan jadwal resmi setempat jika diperlukan.
