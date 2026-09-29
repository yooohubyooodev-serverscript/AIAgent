# AI Agent — Android

แอป AI Agent ที่ช่วยทำงานอัตโนมัติบน Android โดยใช้:
- **Accessibility Service** — มองเห็นและควบคุม UI (แตะ, ปัด, คลิกตามข้อความ)
- **MediaProjection** — จับภาพหน้าจอ
- **Floating Overlay** — ปุ่มลอยสำหรับควบคุม

## สิ่งที่ทำแล้ว

1. **หน้าตั้งค่าสิทธิ์** (`MainActivity`)
   - ตรวจ Accessibility / Overlay / Screen Capture / Battery
   - ส่ง MediaProjection result ไปให้ Service

2. **Floating Bubble** (`FloatingService`)
   - ลากได้
   - ปุ่ม AI ● = ดูสถานะ
   - ปุ่ม 📷 = จับภาพหน้าจอ
   - ปุ่ม × = หยุด

3. **Accessibility helpers** (`AIAccessibilityService`)
   - `tap(x, y)`, `swipe(...)`, `performBack/Home`
   - `findNodeByText`, `clickByText`
   - `dumpVisibleText()` — ดึงข้อความบนจอสำหรับส่ง AI

## วิธีรัน

1. เปิดโปรเจกต์ใน Android Studio
2. แก้ `local.properties` ให้ชี้ `sdk.dir` ของเครื่องคุณ
3. Build & Run บนเครื่องจริง (emulator อาจจำกัด Overlay / Accessibility)
4. เปิดสิทธิ์ทีละข้อตามหน้าจอ แล้วกด **เริ่มทำงาน**

## ขั้นตอนถัดไป (ยังไม่ทำ)

- เชื่อม Vision / LLM API (เช่น OpenAI GPT-4o, Gemini) ส่ง screenshot + ข้อความบนจอ
- แปลงคำสั่ง AI เป็น action (tap / swipe / clickByText)
- ช่องใส่ prompt ใน floating UI
- บันทึกประวัติการทำงาน

## โครงสร้าง

```
app/src/main/java/com/aiagent/
  MainActivity.java          — ตั้งค่าสิทธิ์ + เริ่ม service
  FloatingService.java       — overlay + จับภาพ
  AIAccessibilityService.java — gesture + node search
```
