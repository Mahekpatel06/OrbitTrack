# 🛰️ OrbitTrack — ISS Telemetry & Ground Geofencing System

> A real-time spaceflight telemetry streaming and orbital geofencing platform built with **Spring Boot 3.5**, **Java 21**, and **PostgreSQL**.

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg?style=flat-square&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen.svg?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-blue.svg?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Orekit](https://img.shields.io/badge/Orekit-13.0-0052CC.svg?style=flat-square)](https://www.orekit.org/)
[![SSE](https://img.shields.io/badge/Stream-SSE%20Live-FF6C37.svg?style=flat-square)](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](LICENSE)

---

## 📌 Overview

**OrbitTrack** is an aerospace-focused mission control platform that tracks the **International Space Station (ISS)** in real time (~27,600 km/h / Mach 22.5). 

Beyond simple API tracking, it integrates an **astrodynamics physics engine (Orekit / SGP4)** for self-healing orbital propagation, monitors a **46-station global ground network** inside a 2,000 km radio geofence, pushes live telemetry through **Server-Sent Events (SSE)**, sends **automated flyover email alerts**, and generates structured **PDF & Excel audit reports**.

---

## ✨ Key Features

- **🛰️ Live Telemetry & Self-Healing Propagator**  
  Polls satellite position every 10 seconds. If external APIs time out or hit rate limits, the system automatically falls back to **Orekit (SGP4)** to calculate high-precision orbital coordinates and velocity from CelesTrak TLE ephemeris.

- **📡 2,000 km Ground Station Geofencing**  
  Monitors 46+ global stations (ISRO, NASA DSN, ESA, JAXA, KSAT). Tracks Acquisition of Signal (AOS entry) and Loss of Signal (LOS exit) in real time using a thread-safe state machine, logging contact durations to PostgreSQL.

- **⚡ Reactive Server-Sent Events (SSE)**  
  Pushes live telemetry snapshots and pass alerts directly to connected browsers at `/api/stream`, eliminating client polling overhead.

- **🔭 Flyover Predictor Engine**  
  Calculates upcoming passes over any city (1–5 day window), computing topocentric elevation angles, AOS/LOS timestamps, and sighting categories (*Naked-Eye Visible*, *Daylight*, or *Night Radio*).

- **🔔 Automated Email Alert Dispatcher**  
  A background daemon checks subscriber locations every 10 minutes and sends responsive HTML email notifications **2 hours before the ISS passes overhead** via Spring Mail / SMTP.

- **📊 Mission PDF & Excel Export**  
  One-click generation of official multi-page **Mission Audit PDFs** (OpenPDF) and multi-sheet **Excel Workbooks** (Apache POI), plus bulk station import via `.xlsx`.

- **🗺️ Interactive Radar Dashboard**  
  Dark-mode glassmorphism interface featuring dual map layers (Dark Radar / Satellite imagery), live Day/Night solar shadow, projected sine ground track, and dual IST/UTC mission clocks.

---

## 🔄 How It Works

1. **Ingest & Self-Heal** ➔ Ingests telemetry every 10s; automatically cascades to **Orekit (SGP4)** if external APIs fail or timeout.
2. **Spatial Geofencing** ➔ Calculates 3D Euclidean distances to 46+ global tracking stations within a 2,000 km radio horizon.
3. **Reactive Streaming** ➔ Broadcasts live telemetry & pass events instantly to connected browsers via **Server-Sent Events (SSE)**.
4. **Predict & Dispatch** ➔ Evaluates upcoming flyovers and sends automated **email alerts** 2 hours prior to arrival.

---

## 🛠️ Tech Stack

| Domain | Technologies |
| :--- | :--- |
| **Backend** | Java 21, Spring Boot 3.5, Spring Data JPA, Spring Mail |
| **Astrodynamics** | Orekit 13.0, Hipparchus (SGP4 Propagation, WGS-84) |
| **Database** | PostgreSQL 15+ |
| **Real-Time** | Server-Sent Events (`SseEmitter`), REST APIs |
| **Reporting** | Apache POI (Excel `.xlsx`), OpenPDF (Audit PDFs) |
| **Frontend** | HTML5, CSS3 (Glassmorphism), Vanilla JavaScript, Leaflet.js |

---

## 🔌 API Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/stream` | Real-time SSE stream for telemetry and geofence alerts |
| `GET` | `/api/telemetry/latest` | Latest satellite coordinates, altitude, velocity, and visibility |
| `GET` | `/api/statistics` | Aggregated mission statistics (distance, pass counts, daylight %) |
| `GET` | `/api/passes/predict` | Flyover forecast for target latitude, longitude, and duration |
| `GET` | `/api/reports/mission-audit-pdf` | Download official Mission Audit PDF report |
| `GET` | `/api/reports/mission-audit-excel` | Download multi-sheet Mission Audit Excel workbook |
| `POST`| `/api/ground-stations/upload` | Bulk upload ground stations from an `.xlsx` file |
| `POST`| `/api/notifications/subscribe` | Subscribe to automated flyover email alerts |
| `POST`| `/api/notifications/test-email` | Trigger an instant test flyover alert email |

---

## 🚀 Quick Start

### 1. Prerequisites
- **Java 21+**
- **PostgreSQL** running locally
- **Maven** (or use the included `./mvnw`)

### 2. Configure Database
Create a database named `telemetrydb`:
```sql
CREATE DATABASE telemetrydb;
```

Update `src/main/resources/application.properties` with your PostgreSQL username & password:
```properties
spring.datasource.username=postgres
spring.datasource.password=your_password
```

*(Optional)* To send live emails, add your Gmail address and 16-character App Password to `spring.mail.username` and `spring.mail.password`. If left blank, the system runs in simulation mode.

### 3. Run Application
```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

### 4. Access Dashboard
Open your browser at:
```
http://localhost:8080
```

#### A ready-to-test sample file is included in `samples/sample_ground_stations.xlsx`
for quick drag-and-drop testing.
---

## 👤 Author & License

**Mahek Patel** — [GitHub Profile](https://github.com/Mahekpatel06)  
Project Repository: [OrbitTrack](https://github.com/Mahekpatel06/ISS-Telemetry-Data-Tracker)

This project is licensed under the [MIT License](LICENSE).
