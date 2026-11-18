# E-UJIAN SMP Negeri 01 Banding Agung

Starter aplikasi Next.js + TypeScript + Tailwind CSS + Supabase. Dirancang untuk 1.000 siswa kelas VII–IX, input siswa manual, dan absensi QR.

## 1. Persiapan
- Install Node.js versi LTS.
- Install VS Code.
- Buat project Supabase baru.
- Buka folder ini di VS Code.

## 2. Instalasi
```bash
npm install
copy .env.example .env.local
npm run dev
```
Pada PowerShell, `copy` dapat digunakan. Isi `.env.local` dengan Project URL dan anon/publishable key dari Supabase Dashboard > Project Settings > API.

## 3. Siapkan database
1. Buka Supabase Dashboard > SQL Editor.
2. Buka `supabase/schema.sql`.
3. Salin seluruh isinya ke SQL Editor, lalu tekan Run.
4. Jangan pernah memasukkan `service_role` key ke browser atau file `.env.local` yang memakai prefix `NEXT_PUBLIC_`.

## 4. Buat akun admin pertama
1. Supabase Dashboard > Authentication > Users > Add user.
2. Buat akun admin dengan email dan password yang kuat.
3. Setelah user dibuat, buka SQL Editor dan jalankan:
```sql
update public.profiles
set full_name = 'Administrator Sekolah', role = 'admin'
where id = 'GANTI_DENGAN_UUID_USER_AUTH';
```
4. Login di `/login`.

## 5. Masukkan siswa
1. Login sebagai admin.
2. Pastikan rombel sudah dibuat pada tabel `classes` (seed contoh tersedia).
3. Buka menu Data Siswa.
4. Tambahkan NIS, nama, jenis kelamin, dan rombel.
5. Ulangi sampai jumlah maksimal 1.000 siswa.

## 6. Buat akun siswa dan tautkan
Untuk setiap siswa yang perlu login ujian, buat user melalui Authentication > Users. Setelah akun terbentuk, tautkan:
```sql
update public.students
set auth_user_id = 'UUID_AUTH_USER'
where nis = 'NIS_SISWA';
```
Ubah profil user siswa tetap role `student`. Jangan berikan hak admin melalui browser.

## 7. Cara kerja QR
- `qr_token` dibuat otomatis untuk tiap siswa.
- Scanner petugas menggunakan fungsi database `record_attendance`.
- Fungsi memeriksa role petugas, jadwal ujian, token valid, dan mencegah absensi ganda.
- Untuk rilis produksi, buat halaman kartu QR petugas yang mengambil token melalui RPC terbatas, bukan membuka kolom token untuk siswa.

## 8. Pengujian awal
- Login admin berhasil.
- Tambahkan rombel dan siswa.
- Buat mata pelajaran dan jadwal ujian.
- Pastikan pemindaian QR dilakukan saat ujian dalam rentang waktu.
- Uji QR salah dan pemindaian ganda.
- Uji akun siswa tidak bisa mengakses halaman admin.
- Jalankan `npm run build`.

## Catatan status
Starter ini sudah menyediakan login, dashboard, input siswa, pembuatan jadwal ujian, scanner QR, skema database dan RLS dasar. Modul pelaksanaan ujian (halaman peserta, bank soal lengkap, autosave jawaban, submit, penilaian esai, dan laporan cetak) perlu dilanjutkan sebelum digunakan untuk ujian resmi. Audit keamanan dan uji beban wajib dilakukan sebelum produksi.
