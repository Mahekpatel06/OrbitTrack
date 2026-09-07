// OrbitTrack Mission Operations Console - Main Application Controller

// State Management
let map;
let issMarker = null;
let groundStationMarkers = [];
let geofenceCircles = [];
let allGroundStationsData = [];
let projectedOrbitPolylines = [];
let orbitArrowMarkers = [];
let terminatorPolygon = null;
let subsolarMarker = null;

let autoCenter = true;
let showGeofences = true;
let showStations = true;
let showTerminator = true;
let showProjectedOrbit = true;
let isSatelliteMode = false;

let darkLayerGroup, satelliteLayerGroup;
let recentTelemetryHistory = [];
let lastKnownHeadingIsAscending = null; // Persistent heading: true for North (ascending), false for South (descending)

// Custom Satellite SVG Icon
const issIcon = L.divIcon({
    className: 'iss-marker-container',
    html: `
        <div class="iss-pulse-beacon"></div>
        <svg class="iss-icon-svg" viewBox="0 0 100 100">
            <rect x="6" y="28" width="28" height="44" rx="3" fill="#1e3a8a" stroke="#38bdf8" stroke-width="2"/>
            <line x1="6" y1="42" x2="34" y2="42" stroke="#38bdf8" stroke-width="1.5"/>
            <line x1="6" y1="58" x2="34" y2="58" stroke="#38bdf8" stroke-width="1.5"/>
            <line x1="20" y1="28" x2="20" y2="72" stroke="#38bdf8" stroke-width="1.5"/>
            <rect x="34" y="47" width="32" height="6" fill="#64748b"/>
            <circle cx="50" cy="50" r="9" fill="#f8fafc" stroke="#38bdf8" stroke-width="2.5"/>
            <circle cx="50" cy="50" r="4" fill="#38bdf8"/>
            <rect x="66" y="28" width="28" height="44" rx="3" fill="#1e3a8a" stroke="#38bdf8" stroke-width="2"/>
            <line x1="66" y1="42" x2="94" y2="42" stroke="#38bdf8" stroke-width="1.5"/>
            <line x1="66" y1="58" x2="94" y2="58" stroke="#38bdf8" stroke-width="1.5"/>
            <line x1="80" y1="28" x2="80" y2="72" stroke="#38bdf8" stroke-width="1.5"/>
        </svg>
    `,
    iconSize: [48, 48],
    iconAnchor: [24, 24]
});

// Sun Subsolar Icon
const sunIcon = L.divIcon({
    className: 'subsolar-marker',
    html: `☀️`,
    iconSize: [28, 28],
    iconAnchor: [14, 14]
});

// Application Lifecycle
document.addEventListener('DOMContentLoaded', () => {
    initClocks();
    initMap();
    loadInitialData();
    setupSse();
    setupControls();
    setupExcelImportModal();
    setupFlyoverPredictorModal();
    setupFlyoverAlertModal();

    // 3-second live telemetry polling - user NEVER needs to refresh page!
    setInterval(autoFetchTelemetry, 3000);

    // 6-second refresh for mission statistics and station pass alerts
    setInterval(loadStatistics, 6000);
    setInterval(loadAlerts, 8000);

    // Recalculate astronomical solar terminator every 60 seconds
    setInterval(updateDayNightTerminator, 60000);
});

// Clocks in Indian Standard Time (IST) & UTC
function initClocks() {
    function update() {
        const now = new Date();

        // Format in Indian Standard Time (IST, UTC+5:30)
        const istString = now.toLocaleTimeString('en-IN', {
            timeZone: 'Asia/Kolkata',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: true
        });
        document.getElementById('ist-clock-display').textContent = istString;

        // Format in UTC
        const utcString = now.toUTCString().slice(17, 25);
        document.getElementById('utc-clock-display').textContent = utcString;
    }
    update();
    setInterval(update, 1000);
}

// Initialize Leaflet Map
function initMap() {
    map = L.map('map', {
        center: [20, 0],
        zoom: 3,
        minZoom: 2,
        maxZoom: 18,
        worldCopyJump: true,
        zoomControl: false
    });

    // 1. Dark Gray Canvas (Default: Clean, NO watermark, NO API key required)
    const darkBase = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}', {
        attribution: '&copy; Esri & OpenStreetMap',
        maxZoom: 16
    });
    const darkRef = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}', {
        maxZoom: 16
    });
    darkLayerGroup = L.layerGroup([darkBase, darkRef]).addTo(map);

    // 2. Real Earth Satellite Photography (NASA / Esri World Imagery, NO watermark)
    const satBase = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
        attribution: '&copy; Esri, Maxar, Earthstar Geographics',
        maxZoom: 17
    });
    const satRef = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}', {
        maxZoom: 17
    });
    satelliteLayerGroup = L.layerGroup([satBase, satRef]);

    // Zoom control at bottom right
    L.control.zoom({ position: 'bottomright' }).addTo(map);

    // Dedicated Ground Station Markers Pane (Guarantees stations are above all overlays & geofences)
    const stationPane = map.createPane('stationMarkersPane');
    stationPane.style.zIndex = '650';

    // Add Solar Day/Night Terminator Shadow
    updateDayNightTerminator();

    // Cancel auto-centering if user drags map manually
    map.on('dragstart', () => {
        autoCenter = false;
        document.getElementById('btn-center').classList.remove('active');
    });
}

// Compute & Draw Solar Day/Night Terminator Shadow
function updateDayNightTerminator() {
    if (terminatorPolygon) {
        map.removeLayer(terminatorPolygon);
    }
    if (subsolarMarker) {
        map.removeLayer(subsolarMarker);
    }

    const now = new Date();

    // Day of Year
    const startOfYear = new Date(Date.UTC(now.getUTCFullYear(), 0, 0));
    const diff = now - startOfYear;
    const dayOfYear = Math.floor(diff / (1000 * 60 * 60 * 24));

    // Solar Declination (axial tilt ~23.44°)
    const declinationDeg = -23.44 * Math.cos((360 / 365) * (dayOfYear + 10) * (Math.PI / 180));
    const declinationRad = declinationDeg * (Math.PI / 180);

    // Subsolar Point Longitude (Solar Noon)
    const utcHours = now.getUTCHours() + now.getUTCMinutes() / 60 + now.getUTCSeconds() / 3600;
    let subsolarLon = (12 - utcHours) * 15;
    if (subsolarLon > 180) subsolarLon -= 360;
    if (subsolarLon < -180) subsolarLon += 360;

    // Place Glowing Sun Icon at Subsolar Point
    subsolarMarker = L.marker([declinationDeg, subsolarLon], { icon: sunIcon }).addTo(map);
    subsolarMarker.bindPopup(`<b>☀️ Solar Subsolar Point</b><br>Sun directly overhead at Latitude ${declinationDeg.toFixed(1)}°, Longitude ${subsolarLon.toFixed(1)}°`);

    // Compute Terminator boundary curve
    const terminatorPoints = [];
    const isNorthSun = declinationDeg >= 0;

    for (let lon = -180; lon <= 180; lon += 2) {
        const deltaLonRad = (lon - subsolarLon) * (Math.PI / 180);
        const tanLat = -Math.cos(deltaLonRad) / Math.tan(declinationRad);
        let lat = Math.atan(tanLat) * (180 / Math.PI);
        lat = Math.max(-85, Math.min(85, lat)); // Clamp to Mercator limits
        terminatorPoints.push([lat, lon]);
    }

    // Close polygon around the dark pole
    const darkPoleLat = isNorthSun ? -90 : 90;
    const polygonCoords = [
        ...terminatorPoints,
        [darkPoleLat, 180],
        [darkPoleLat, -180]
    ];

    terminatorPolygon = L.polygon(polygonCoords, {
        color: 'transparent',
        fillColor: '#020617',
        fillOpacity: isSatelliteMode ? 0.45 : 0.38,
        interactive: false
    });

    if (showTerminator) {
        terminatorPolygon.addTo(map);
    }
}

// Draw the Projected Sinusoidal Orbit Route (Accurate Heading Matching Real Trackers)
function updateProjectedOrbit(currentLat, currentLon) {
    projectedOrbitPolylines.forEach(p => map.removeLayer(p));
    projectedOrbitPolylines = [];
    orbitArrowMarkers.forEach(a => map.removeLayer(a));
    orbitArrowMarkers = [];

    if (!showProjectedOrbit) return;

    // Orbital Parameters for ISS
    const inclination = 51.64; // degrees
    const orbitalPeriodMinutes = 92.8; // ~5568 seconds
    const earthRotationDegPerMin = 360 / 1440; // 0.25 deg/min

    // Heading detection: scan backward for the two most recent points with distinct latitude
    for (let i = recentTelemetryHistory.length - 1; i >= 1; i--) {
        const pCurrent = recentTelemetryHistory[i];
        const pPrevious = recentTelemetryHistory[i - 1];
        const deltaLat = pCurrent.latitude - pPrevious.latitude;
        if (Math.abs(deltaLat) > 0.0001) {
            lastKnownHeadingIsAscending = (deltaLat > 0);
            break;
        }
    }

    // Stable heading: use persistent state; if still uninitialized, default safely
    let isAscending = (lastKnownHeadingIsAscending !== null) ? lastKnownHeadingIsAscending : (currentLat >= 0);

    // Compute exact phase angle theta0
    let sinVal = Math.max(-1, Math.min(1, currentLat / inclination));
    let theta0;
    if (isAscending) {
        theta0 = Math.asin(sinVal); // Heading North (ascending)
    } else {
        theta0 = Math.PI - Math.asin(sinVal); // Heading South (descending)
    }

    const points = [];
    // Project 1 full orbit ahead (+93 min) and 0.5 orbit back (-40 min)
    for (let t = -40; t <= 94; t += 1.0) {
        const frac = t / orbitalPeriodMinutes;
        const theta = theta0 + frac * 2 * Math.PI;

        const lat = inclination * Math.sin(theta);
        let lon = currentLon + (frac * 360) - (t * earthRotationDegPerMin);

        // Normalize longitude to [-180, 180]
        lon = ((((lon + 180) % 360) + 360) % 360) - 180;
        points.push([lat, lon]);
    }

    // Split into continuous segments at the 180° date line
    const segments = splitPathAtDateLine(points);

    segments.forEach(seg => {
        const poly = L.polyline(seg, {
            color: '#facc15',
            weight: 3.0,
            opacity: 0.95,
            dashArray: '8, 6'
        }).addTo(map);
        projectedOrbitPolylines.push(poly);
    });

    // Add navigation direction arrow markers along the wave
    for (let i = 15; i < points.length; i += 20) {
        const pt = points[i];
        const nextPt = points[i + 1];
        if (!pt || !nextPt) continue;

        const angle = Math.atan2(nextPt[0] - pt[0], nextPt[1] - pt[1]) * (180 / Math.PI);
        const arrowIcon = L.divIcon({
            className: 'orbit-arrow-icon',
            html: `<div style="transform: rotate(${90 - angle}deg); color: #38bdf8; font-weight: bold;">▲</div>`,
            iconSize: [16, 16],
            iconAnchor: [8, 8]
        });
        const arrowMarker = L.marker(pt, { icon: arrowIcon, interactive: false }).addTo(map);
        orbitArrowMarkers.push(arrowMarker);
    }
}

// Antimeridian Wrap Fix: Splits coordinates at 180° longitude
function splitPathAtDateLine(coordList) {
    const segments = [];
    if (!coordList || coordList.length === 0) return segments;

    let currentSegment = [coordList[0]];

    for (let i = 1; i < coordList.length; i++) {
        const prev = coordList[i - 1];
        const curr = coordList[i];

        if (Math.abs(curr[1] - prev[1]) > 180) {
            segments.push(currentSegment);
            currentSegment = [curr];
        } else {
            currentSegment.push(curr);
        }
    }

    if (currentSegment.length > 0) {
        segments.push(currentSegment);
    }
    return segments;
}

// Haversine Distance (km) between two points
function calculateHaversineDistance(lat1, lon1, lat2, lon2) {
    const R = 6371.0;
    const dLat = (lat2 - lat1) * (Math.PI / 180);
    const dLon = (lon2 - lon1) * (Math.PI / 180);
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(lat1 * (Math.PI / 180)) * Math.cos(lat2 * (Math.PI / 180)) *
              Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

// Update Closest Ground Station Proximity Card
function updateClosestStationCard(currentLat, currentLon) {
    if (!allGroundStationsData || allGroundStationsData.length === 0) return;

    let closestStation = null;
    let minDistance = Infinity;

    allGroundStationsData.forEach(st => {
        const dist = calculateHaversineDistance(currentLat, currentLon, st.latitude, st.longitude);
        if (dist < minDistance) {
            minDistance = dist;
            closestStation = st;
        }
    });

    if (closestStation) {
        document.getElementById('hud-station-name').textContent = closestStation.name;
        const roundedDist = Math.round(minDistance);
        const proximityElem = document.getElementById('hud-station-proximity');
        const distElem = document.getElementById('hud-station-dist');

        if (minDistance <= 2000) {
            proximityElem.textContent = '🟢 IN AIRSPACE CONTACT';
            proximityElem.style.color = '#34d399';
            distElem.textContent = `${roundedDist.toLocaleString()} km away • Active Radio Link`;
            distElem.style.color = '#34d399';
        } else {
            proximityElem.textContent = 'LINE OF SIGHT RANGE';
            proximityElem.style.color = '#f59e0b';
            distElem.textContent = `${roundedDist.toLocaleString()} km away • En Route`;
            distElem.style.color = '#f59e0b';
        }
    }
}

// Helper to extract numbers safely across legacy & new schema keys
function extractNumber(...values) {
    for (let v of values) {
        if (v != null && v !== '') {
            const num = Number(v);
            if (!isNaN(num)) return num;
        }
    }
    return NaN;
}

// Load Initial Bootstrap Data
async function loadInitialData() {
    await Promise.all([
        loadGroundStations(),
        loadTelemetryHistory(),
        loadLatestTelemetry(),
        loadStatistics(),
        loadAlerts()
    ]);
}

// 1. Fetch Global Ground Stations with Adaptive Colors
async function loadGroundStations() {
    try {
        const res = await fetch('/api/ground-stations');
        if (!res.ok) return;
        allGroundStationsData = await res.json();

        // Clear existing markers
        groundStationMarkers.forEach(m => map.removeLayer(m));
        geofenceCircles.forEach(c => map.removeLayer(c));
        groundStationMarkers = [];
        geofenceCircles = [];

        // Blue on dark radar, Red on satellite photo
        const primaryColor = isSatelliteMode ? '#ef4444' : '#38bdf8';
        const circleBorder = isSatelliteMode ? '#dc2626' : '#0284c7';

        allGroundStationsData.forEach(st => {
            const lat = Number(st.latitude);
            const lon = Number(st.longitude);

            // Center Point Marker with HTML divIcon (Guaranteed high z-index in markerPane)
            const stationMarker = L.marker([lat, lon], {
                icon: createStationDivIcon(isSatelliteMode, false),
                title: st.name,
                riseOnHover: true,
                interactive: true
            }).addTo(map);

            stationMarker.stationData = st;

            // Bind Native Leaflet Popup using dynamic generator function
            stationMarker.bindPopup(() => generateStationPopupContent(st, lat, lon), {
                maxWidth: 320,
                offset: [0, -12],
                autoPan: true
            });

            // Ensure click explicitly opens popup
            stationMarker.on('click', (e) => {
                if (e && e.originalEvent) e.originalEvent.stopPropagation();
                stationMarker.openPopup();
            });

            groundStationMarkers.push(stationMarker);

            // 2,000 km Geofence Range Circle
            const circle = L.circle([lat, lon], {
                radius: 2000000,
                color: circleBorder,
                weight: 1.5,
                opacity: 0.45,
                fillColor: primaryColor,
                fillOpacity: isSatelliteMode ? 0.08 : 0.06,
                interactive: true
            }).addTo(map);

            circle.stationData = st;
            circle.on('click', (e) => {
                if (e && e.originalEvent) e.originalEvent.stopPropagation();
                stationMarker.openPopup();
            });
            geofenceCircles.push(circle);
        });

        // Update button badge
        document.getElementById('stations-count-label').textContent = `${allGroundStationsData.length} Ground Stations`;

        // If satellite marker exists, recalculate closest station & dynamic geofence colors
        if (issMarker) {
            const pos = issMarker.getLatLng();
            updateClosestStationCard(pos.lat, pos.lng);
            updateGeofenceActivation(pos.lat, pos.lng);
        }
    } catch (err) {
        console.error('Failed to load ground stations:', err);
    }
}

// Creates Custom Ground Station HTML Pin (Real DOM in Leaflet markerPane)
function createStationDivIcon(isSatellite, isActive) {
    const color = isSatellite ? (isActive ? '#fbbf24' : '#ef4444') : (isActive ? '#ef4444' : '#38bdf8');
    const shadow = isSatellite ? (isActive ? '0 0 12px #fbbf24' : '0 0 8px rgba(239, 68, 68, 0.8)') : (isActive ? '0 0 12px #ef4444' : '0 0 8px rgba(56, 189, 248, 0.8)');

    return L.divIcon({
        className: 'station-dot-container',
        html: `
            <div class="station-pin-hitbox" title="Click to inspect Ground Station">
                <div class="station-pulse-ring ${isActive ? 'active-pulse' : ''}" style="border-color: ${color};"></div>
                <div class="station-center-point" style="background-color: ${color}; box-shadow: ${shadow};"></div>
            </div>
        `,
        iconSize: [28, 28],
        iconAnchor: [14, 14],
        popupAnchor: [0, -14]
    });
}

// HTML Entity Sanitizer to prevent ReferenceError and XSS
function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// Generates Dynamic Popup HTML for Ground Stations
function generateStationPopupContent(st, lat, lon) {
    try {
        let distStr = 'Calculating range...';
        let statusBadge = '<span style="color: #94a3b8;">Out of Horizon Range</span>';
        if (issMarker) {
            const issPos = issMarker.getLatLng();
            const distKm = calculateHaversineDistance(issPos.lat, issPos.lng, lat, lon);
            distStr = `${Math.round(distKm).toLocaleString()} km away`;
            if (distKm <= 2000.0) {
                statusBadge = '<span style="color: #ef4444; font-weight: 700;">🟢 ACTIVE RADIO CONTACT</span>';
            }
        }

        const safeName = (st.name || 'Station').replace(/'/g, "\\'");
        return `
            <div class="popup-header">🛰️ ${escapeHtml(st.name)}</div>
            <div class="popup-row"><span>Country:</span> <span>${escapeHtml(st.country || 'Global Network')}</span></div>
            <div class="popup-row"><span>Coordinates:</span> <span>${Number(lat).toFixed(4)}°, ${Number(lon).toFixed(4)}°</span></div>
            <div class="popup-row"><span>Live Distance to ISS:</span> <span style="color: var(--cyan); font-weight: 600;">${distStr}</span></div>
            <div class="popup-row"><span>Radio Horizon:</span> <span>2,000 km Geofence</span></div>
            <div style="display: flex; gap: 6px; margin-top: 10px;">
                <button class="popup-btn" style="flex: 1; margin-top: 0; padding: 6px 8px; font-size: 0.72rem; justify-content: center;" onclick="openPredictorModalForStation('${safeName}', ${lat}, ${lon})">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                    Predict Passes
                </button>
                <button class="popup-btn" style="flex: 1; margin-top: 0; padding: 6px 8px; font-size: 0.72rem; justify-content: center; background: linear-gradient(135deg, rgba(139, 92, 246, 0.35) 0%, rgba(56, 189, 248, 0.25) 100%); border-color: rgba(168, 85, 247, 0.6); color: #c084fc;" onclick="openAlertModalForStation('${safeName}', ${lat}, ${lon})">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
                    Email Alert
                </button>
            </div>
        `;
    } catch (e) {
        console.error('Error generating station popup:', e);
        return `<div class="popup-header">${escapeHtml(st.name || 'Station')}</div><p>Coordinates: ${lat}, ${lon}</p>`;
    }
}

// Dynamically Update Geofence & Station Colors when ISS Enters / Exits Airspace
function updateGeofenceActivation(issLat, issLon) {
    if (!allGroundStationsData || allGroundStationsData.length === 0) return;

    for (let i = 0; i < allGroundStationsData.length; i++) {
        const st = allGroundStationsData[i];
        const marker = groundStationMarkers[i];
        const circle = geofenceCircles[i];
        if (!marker || !circle) continue;

        const distKm = calculateHaversineDistance(issLat, issLon, st.latitude, st.longitude);
        const isInAirspace = distKm <= 2000.0;

        if (isInAirspace) {
            // Active Contact!
            if (isSatelliteMode) {
                // In Satellite view, turn Neon Amber / Gold (#fbbf24)
                circle.setStyle({
                    color: '#f59e0b',
                    weight: 3.2,
                    opacity: 1.0,
                    fillColor: '#fbbf24',
                    fillOpacity: 0.32
                });
                marker.setIcon(createStationDivIcon(true, true));
            } else {
                // In Dark Radar view, turn Warning Crimson Red (#ef4444)
                circle.setStyle({
                    color: '#dc2626',
                    weight: 2.8,
                    opacity: 0.95,
                    fillColor: '#ef4444',
                    fillOpacity: 0.28
                });
                marker.setIcon(createStationDivIcon(false, true));
            }
        } else {
            // Normal Inactive Geofence
            if (isSatelliteMode) {
                circle.setStyle({
                    color: '#dc2626',
                    weight: 1.5,
                    opacity: 0.45,
                    fillColor: '#ef4444',
                    fillOpacity: 0.08
                });
                marker.setIcon(createStationDivIcon(true, false));
            } else {
                circle.setStyle({
                    color: '#0284c7',
                    weight: 1.5,
                    opacity: 0.45,
                    fillColor: '#38bdf8',
                    fillOpacity: 0.06
                });
                marker.setIcon(createStationDivIcon(false, false));
            }
        }
    }
}

// 2. Fetch History (Used strictly for heading detection)
async function loadTelemetryHistory() {
    try {
        const res = await fetch('/api/telemetry/history?limit=10');
        if (!res.ok) return;
        const history = await res.json();

        if (history && history.length > 0) {
            recentTelemetryHistory = [...history].reverse();
            if (!issMarker && recentTelemetryHistory.length > 0) {
                updateTelemetryUI(recentTelemetryHistory[recentTelemetryHistory.length - 1]);
            }
        }
    } catch (err) {
        console.error('Failed to load telemetry history:', err);
    }
}

// 3. Fetch Latest Telemetry Snapshot
async function loadLatestTelemetry() {
    try {
        const res = await fetch('/api/telemetry/latest');
        if (res.status === 200) {
            const data = await res.json();
            updateTelemetryUI(data);
        }
    } catch (err) {
        console.error('Failed to fetch latest telemetry:', err);
    }
}

// Automatic 3-second background polling
async function autoFetchTelemetry() {
    try {
        const res = await fetch('/api/telemetry/latest');
        if (res.status === 200) {
            const data = await res.json();
            updateTelemetryUI(data);
        }
    } catch (e) {
        // Silent poll catch
    }
}

// 4. Fetch Mission Statistics
async function loadStatistics() {
    try {
        const res = await fetch('/api/statistics');
        if (!res.ok) return;
        const stats = await res.json();

        if (stats) {
            const dist = stats.totalDistanceTravelledKm || 0;
            document.getElementById('stat-dist').textContent = `${dist.toLocaleString()} km`;

            const alertsCount = stats.totalAlertsLogged || 0;
            document.getElementById('stat-alerts-count').textContent = alertsCount;
            document.getElementById('alerts-count-badge').textContent = `${alertsCount} PASSES`;

            const pings = stats.totalTelemetryRecords || 0;
            document.getElementById('stat-records').textContent = `${pings} PINGS`;

            if (stats.mostVisitedGroundStation && stats.mostVisitedGroundStation !== "None") {
                document.getElementById('stat-top-station').textContent = `${stats.mostVisitedGroundStation} (${stats.mostVisitedGroundStationPassCount} passes)`;
            }

            const daylightPct = stats.daylightPercentage || 0;
            document.getElementById('stat-sunlight-pct').textContent = `${daylightPct}% Daylight`;
            document.getElementById('stat-sunlight-bar').style.width = `${daylightPct}%`;

            if (stats.latestAltitudeKm) {
                document.getElementById('hud-altitude').innerHTML = `${stats.latestAltitudeKm.toFixed(1)} <span class="hud-unit">km</span>`;
            }
            if (stats.latestVelocityKmS) {
                document.getElementById('hud-velocity').innerHTML = `${stats.latestVelocityKmS.toFixed(2)} <span class="hud-unit">km/s</span>`;
                const kmh = (stats.latestVelocityKmS * 3600).toLocaleString(undefined, { maximumFractionDigits: 0 });
                document.getElementById('hud-velocity-kmh').textContent = `${kmh} km/h • Mach 22.5`;
            }
        }
    } catch (err) {
        console.error('Failed to load statistics:', err);
    }
}

// 5. Fetch Historical Ground Station Pass Alerts
async function loadAlerts() {
    try {
        const res = await fetch('/api/alerts');
        if (!res.ok) return;
        const alerts = await res.json();
        renderAlertsList(alerts);
    } catch (err) {
        console.error('Failed to load alerts:', err);
    }
}

// Render Alerts List formatted in Indian Standard Time (IST)
function renderAlertsList(alerts) {
    const container = document.getElementById('alerts-container');
    if (!alerts || alerts.length === 0) {
        container.innerHTML = `
            <div style="text-align: center; padding: 24px; color: var(--text-muted); font-size: 0.78rem;">
                No ground station pass alerts recorded yet.
            </div>
        `;
        return;
    }

    container.innerHTML = '';
    alerts.slice(0, 40).forEach(alert => {
        container.appendChild(createAlertElement(alert, false));
    });
}

function createAlertElement(alert, isNew = false) {
    const card = document.createElement('div');
    card.className = `alert-card ${isNew ? 'new-arrival' : ''}`;

    const station = alert.stationName || 'Ground Station';
    const duration = alert.duration != null ? alert.duration : 0;
    const startLat = alert.startLatitude != null ? alert.startLatitude.toFixed(2) : '--';
    const startLon = alert.startLongitude != null ? alert.startLongitude.toFixed(2) : '--';
    const endLat = alert.endLatitude != null ? alert.endLatitude.toFixed(2) : '--';
    const endLon = alert.endLongitude != null ? alert.endLongitude.toFixed(2) : '--';

    let istTimeString = 'Recent Pass';
    if (alert.endTimestamp) {
        const d = new Date(alert.endTimestamp);
        istTimeString = d.toLocaleTimeString('en-IN', {
            timeZone: 'Asia/Kolkata',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: true
        }) + ' IST';
    }

    card.innerHTML = `
        <div class="alert-header">
            <div class="alert-station">
                <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="3"/></svg>
                <span>${station}</span>
            </div>
            <div class="alert-duration">${duration}s Pass</div>
        </div>
        <div class="alert-coords">
            <span>Entry: ${startLat}°, ${startLon}°</span>
            <span>Exit: ${endLat}°, ${endLon}°</span>
        </div>
        <div class="alert-time">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            Coverage Window Closed: ${istTimeString}
        </div>
    `;
    return card;
}

// Update UI & Map Position with fresh Telemetry
function updateTelemetryUI(data) {
    if (!data) return;

    const lat = extractNumber(data.latitude, data.satlatitude, data.lat);
    const lon = extractNumber(data.longitude, data.satlongitude, data.lon);
    const alt = extractNumber(data.altitude, data.sataltitude, data.alt, 420.0);
    const vel = extractNumber(data.velocity, data.speed, 7.66);
    const vis = data.visibility || (data.eclipsed === 'true' || data.eclipsed === true ? 'eclipsed' : 'daylight');

    // 1. Coordinates HUD
    if (!isNaN(lat) && !isNaN(lon)) {
        const latStr = `${Math.abs(lat).toFixed(2)}° ${lat >= 0 ? 'N' : 'S'}`;
        const lonStr = `${Math.abs(lon).toFixed(2)}° ${lon >= 0 ? 'E' : 'W'}`;
        document.getElementById('hud-coords').textContent = `${latStr}, ${lonStr}`;
        document.getElementById('hud-coords-raw').textContent = `Lat: ${lat.toFixed(4)} | Lon: ${lon.toFixed(4)}`;

        // Push to recent history only if coordinates have changed
        const lastEntry = recentTelemetryHistory[recentTelemetryHistory.length - 1];
        if (!lastEntry || Math.abs(lastEntry.latitude - lat) > 0.0001 || Math.abs(lastEntry.longitude - lon) > 0.0001) {
            recentTelemetryHistory.push({ latitude: lat, longitude: lon });
            if (recentTelemetryHistory.length > 15) {
                recentTelemetryHistory.shift();
            }
        }

        // Map Marker
        const latLng = [lat, lon];

        if (!issMarker) {
            issMarker = L.marker(latLng, { icon: issIcon }).addTo(map);
            issMarker.bindPopup(`
                <div class="popup-header">🛰️ INTERNATIONAL SPACE STATION</div>
                <div class="popup-row"><span>NORAD ID:</span> <span>25544</span></div>
                <div class="popup-row"><span>Velocity:</span> <span>${vel.toFixed(2)} km/s</span></div>
                <div class="popup-row"><span>Altitude:</span> <span>${alt.toFixed(1)} km</span></div>
                <div class="popup-row"><span>Lighting:</span> <span>${vis || 'Daylight'}</span></div>
            `);
            map.setView(latLng, 3);
        } else {
            issMarker.setLatLng(latLng);
        }

        // Closest Station Card & Dynamic Geofence Color Activation
        updateClosestStationCard(lat, lon);
        updateGeofenceActivation(lat, lon);

        // Projected Sinusoidal Orbit Route
        updateProjectedOrbit(lat, lon);

        // Auto-center map if enabled
        if (autoCenter) {
            map.panTo(latLng, { animate: true, duration: 0.8 });
        }
    }

    // 2. Velocity HUD
    if (!isNaN(vel)) {
        document.getElementById('hud-velocity').innerHTML = `${vel.toFixed(2)} <span class="hud-unit">km/s</span>`;
        const kmh = (vel * 3600).toLocaleString(undefined, { maximumFractionDigits: 0 });
        document.getElementById('hud-velocity-kmh').textContent = `${kmh} km/h • Mach 22.5`;
    }

    // 3. Altitude HUD
    if (!isNaN(alt)) {
        document.getElementById('hud-altitude').innerHTML = `${alt.toFixed(1)} <span class="hud-unit">km</span>`;
    }

    // 4. Visibility (Daylight / Eclipsed)
    const isDaylight = !vis || vis.toLowerCase() === 'daylight';
    const visElem = document.getElementById('hud-visibility');
    const visDetail = document.getElementById('hud-visibility-detail');
    visElem.textContent = isDaylight ? 'DAYLIGHT ☀️' : 'ECLIPSED 🌑';
    visElem.style.color = isDaylight ? '#fbbf24' : '#94a3b8';
    visDetail.textContent = isDaylight ? 'Direct Solar Illumination' : 'Transiting Earth Shadow';

    const nowIst = new Date().toLocaleTimeString('en-IN', { timeZone: 'Asia/Kolkata', hour12: true });
    document.getElementById('hud-last-sync').textContent = `Live: ${nowIst} IST`;
}

// Setup Server-Sent Events (SSE) Real-Time Connection
function setupSse() {
    const badge = document.getElementById('sse-badge');
    const statusText = document.getElementById('sse-status-text');

    try {
        const eventSource = new EventSource('/api/stream');

        eventSource.onopen = () => {
            badge.classList.remove('disconnected');
            statusText.textContent = 'LIVE STREAM ONLINE';
        };

        eventSource.addEventListener('connected', () => {
            badge.classList.remove('disconnected');
            statusText.textContent = 'LIVE STREAM ONLINE';
        });

        eventSource.addEventListener('heartbeat', () => {
            badge.classList.remove('disconnected');
            statusText.textContent = 'LIVE STREAM ONLINE';
        });

        eventSource.addEventListener('telemetry', (e) => {
            try {
                const telemetry = JSON.parse(e.data);
                updateTelemetryUI(telemetry);
            } catch (err) {
                console.error('[SSE] Failed to parse telemetry:', err);
            }
        });

        eventSource.addEventListener('alert', (e) => {
            try {
                const alert = JSON.parse(e.data);
                const container = document.getElementById('alerts-container');
                const alertEl = createAlertElement(alert, true);
                container.insertBefore(alertEl, container.firstChild);

                const currentCount = parseInt(document.getElementById('alerts-count-badge').textContent || '0') + 1;
                document.getElementById('alerts-count-badge').textContent = `${currentCount} PASSES`;
                document.getElementById('stat-alerts-count').textContent = currentCount;
            } catch (err) {
                console.error('[SSE] Failed to parse alert:', err);
            }
        });

        eventSource.onerror = () => {
            badge.classList.remove('disconnected');
            statusText.textContent = 'AUTO-STREAM (3s)';
        };
    } catch (e) {
        console.warn('SSE stream error, auto-sync polling remains active:', e);
    }
}

// Setup Map & Filter Controls
function setupControls() {
    // Re-center on ISS
    const btnCenter = document.getElementById('btn-center');
    btnCenter.addEventListener('click', () => {
        autoCenter = true;
        btnCenter.classList.add('active');
        if (issMarker) {
            map.setView(issMarker.getLatLng(), map.getZoom(), { animate: true });
        }
    });

    // Manual Sync Button
    const btnSync = document.getElementById('btn-sync');
    btnSync.addEventListener('click', async () => {
        btnSync.style.transform = 'rotate(180deg)';
        await Promise.all([loadLatestTelemetry(), loadStatistics(), loadAlerts(), loadGroundStations()]);
        setTimeout(() => { btnSync.style.transform = 'none'; }, 300);
    });

    // Toggle Map Tile Theme (Dark Radar vs Real Satellite Earth Photography)
    const btnBasemap = document.getElementById('btn-toggle-basemap');
    const basemapLabel = document.getElementById('basemap-label');
    btnBasemap.addEventListener('click', () => {
        isSatelliteMode = !isSatelliteMode;
        if (isSatelliteMode) {
            map.removeLayer(darkLayerGroup);
            map.addLayer(satelliteLayerGroup);
            basemapLabel.textContent = 'Switch: Dark Radar';
            btnBasemap.classList.add('active');
        } else {
            map.removeLayer(satelliteLayerGroup);
            map.addLayer(darkLayerGroup);
            basemapLabel.textContent = 'Switch: Satellite Photo';
            btnBasemap.classList.remove('active');
        }

        loadGroundStations();
        updateDayNightTerminator();
    });

    // Toggle Day/Night Solar Shadow (Terminator)
    const btnTerminator = document.getElementById('btn-toggle-terminator');
    btnTerminator.addEventListener('click', () => {
        showTerminator = !showTerminator;
        btnTerminator.classList.toggle('active', showTerminator);
        if (terminatorPolygon) {
            if (showTerminator) map.addLayer(terminatorPolygon);
            else map.removeLayer(terminatorPolygon);
        }
        if (subsolarMarker) {
            if (showTerminator) map.addLayer(subsolarMarker);
            else map.removeLayer(subsolarMarker);
        }
    });

    // Toggle Projected Orbit Route
    const btnProjected = document.getElementById('btn-toggle-projected-orbit');
    btnProjected.addEventListener('click', () => {
        showProjectedOrbit = !showProjectedOrbit;
        btnProjected.classList.toggle('active', showProjectedOrbit);
        if (showProjectedOrbit) {
            if (issMarker) {
                const pos = issMarker.getLatLng();
                updateProjectedOrbit(pos.lat, pos.lng);
            }
        } else {
            projectedOrbitPolylines.forEach(p => map.removeLayer(p));
            projectedOrbitPolylines = [];
            orbitArrowMarkers.forEach(a => map.removeLayer(a));
            orbitArrowMarkers = [];
        }
    });

    // Toggle Geofence Circles
    const btnRings = document.getElementById('btn-toggle-rings');
    btnRings.addEventListener('click', () => {
        showGeofences = !showGeofences;
        btnRings.classList.toggle('active', showGeofences);
        geofenceCircles.forEach(c => {
            if (showGeofences) map.addLayer(c);
            else map.removeLayer(c);
        });
    });

    // Toggle Ground Stations
    const btnStations = document.getElementById('btn-toggle-stations');
    if (btnStations) {
        btnStations.addEventListener('click', () => {
            showStations = !showStations;
            btnStations.classList.toggle('active', showStations);
            groundStationMarkers.forEach(m => {
                if (showStations) map.addLayer(m);
                else map.removeLayer(m);
            });
        });
    }

    // Phase C: Export Mission PDF Audit Report
    const btnPdf = document.getElementById('btn-export-pdf');
    if (btnPdf) {
        btnPdf.addEventListener('click', () => {
            btnPdf.disabled = true;
            btnPdf.innerHTML = '<span class="animate-spin">⏳</span> Generating PDF...';
            window.location.href = '/api/reports/mission-audit-pdf';
            setTimeout(() => {
                btnPdf.disabled = false;
                btnPdf.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg> Mission PDF`;
            }, 3000);
        });
    }

    // Phase C: Export Mission Excel (.xlsx) Audit Report
    const btnExcel = document.getElementById('btn-export-excel');
    if (btnExcel) {
        btnExcel.addEventListener('click', () => {
            btnExcel.disabled = true;
            btnExcel.innerHTML = '<span class="animate-spin">⏳</span> Generating Excel...';
            window.location.href = '/api/reports/mission-audit-excel';
            setTimeout(() => {
                btnExcel.disabled = false;
                btnExcel.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="8" y1="13" x2="16" y2="13"/><line x1="8" y1="17" x2="16" y2="17"/><polygon points="10 9 12 11 14 9"/></svg> Mission Excel`;
            }, 3000);
        });
    }
}

// Setup Excel Import Modal & File Upload Logic
function setupExcelImportModal() {
    const modal = document.getElementById('import-modal');
    const btnOpen = document.getElementById('btn-open-import');
    const btnOpenFloating = document.getElementById('btn-open-import-floating');
    const btnClose = document.getElementById('btn-close-modal');
    const dropZone = document.getElementById('upload-drop-zone');
    const fileInput = document.getElementById('excel-file-input');
    const btnDownloadTemplate = document.getElementById('btn-download-template');
    const statusDiv = document.getElementById('upload-status');

    function openModal() {
        modal.classList.add('open');
        statusDiv.className = 'upload-status';
        statusDiv.style.display = 'none';
        statusDiv.textContent = '';
    }

    function closeModal() {
        modal.classList.remove('open');
    }

    if (btnOpen) btnOpen.addEventListener('click', openModal);
    if (btnOpenFloating) btnOpenFloating.addEventListener('click', openModal);
    if (btnClose) btnClose.addEventListener('click', closeModal);

    // Close when clicking outside card
    modal.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });

    // Download Sample Template
    btnDownloadTemplate.addEventListener('click', () => {
        window.location.href = '/api/ground-stations/template';
    });

    // Dropzone Click triggers hidden file input
    dropZone.addEventListener('click', () => {
        fileInput.click();
    });

    // File Input change
    fileInput.addEventListener('change', (e) => {
        if (e.target.files && e.target.files[0]) {
            handleFileUpload(e.target.files[0]);
        }
    });

    // Drag & Drop events
    ['dragenter', 'dragover'].forEach(eventName => {
        dropZone.addEventListener(eventName, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropZone.classList.add('dragover');
        });
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropZone.classList.remove('dragover');
        });
    });

    dropZone.addEventListener('drop', (e) => {
        const dt = e.dataTransfer;
        if (dt.files && dt.files[0]) {
            handleFileUpload(dt.files[0]);
        }
    });

    async function handleFileUpload(file) {
        if (!file.name.endsWith('.xlsx') && !file.name.endsWith('.xls')) {
            showStatus('error', 'Invalid file type. Please select a Microsoft Excel (.xlsx or .xls) file.');
            return;
        }

        showStatus('success', `Uploading and analyzing '${file.name}' with Apache POI...`);

        const formData = new FormData();
        formData.append('file', file);

        try {
            const res = await fetch('/api/ground-stations/upload', {
                method: 'POST',
                body: formData
            });

            if (!res.ok) {
                const errText = await res.text();
                showStatus('error', `Upload failed: ${errText || res.statusText}`);
                return;
            }

            const result = await res.json();

            if (result.success) {
                let msg = `✅ ${result.message}`;
                if (result.errors && result.errors.length > 0) {
                    msg += `<br><br><b>Warnings / Skipped:</b><br>${result.errors.slice(0, 5).join('<br>')}`;
                }
                showStatus('success', msg);

                // Instantly refresh map ground stations & closest station card!
                await loadGroundStations();
            } else {
                let errMsg = `❌ ${result.message}`;
                if (result.errors && result.errors.length > 0) {
                    errMsg += `<br>${result.errors.slice(0, 5).join('<br>')}`;
                }
                showStatus('error', errMsg);
            }
        } catch (err) {
            showStatus('error', `Connection error: ${err.message}`);
        }
    }

    function showStatus(type, htmlContent) {
        statusDiv.className = `upload-status ${type}`;
        statusDiv.innerHTML = htmlContent;
        statusDiv.style.display = 'block';
    }
}

// Setup Phase B: Flyover Predictor Modal & Calculation Engine
let countdownInterval = null;

function setupFlyoverPredictorModal() {
    const modal = document.getElementById('predictor-modal');
    const btnOpen = document.getElementById('btn-open-predictor');
    const btnOpenFloating = document.getElementById('btn-open-predictor-floating');
    const btnClose = document.getElementById('btn-close-pred-modal');
    const locationSelect = document.getElementById('pred-location-select');
    const latInput = document.getElementById('pred-lat-input');
    const lonInput = document.getElementById('pred-lon-input');
    const nameInput = document.getElementById('pred-name-input');
    const daysSelect = document.getElementById('pred-days-select');
    const btnCalculate = document.getElementById('btn-calculate-flyovers');
    const passesContainer = document.getElementById('pred-passes-container');
    const countdownBanner = document.getElementById('pred-countdown-banner');
    const countdownTime = document.getElementById('pred-countdown-time');
    const countdownDetail = document.getElementById('pred-countdown-detail');
    const totalCountBadge = document.getElementById('pred-total-count');

    if (!modal) return;

    function openModal() {
        populateLocationDropdown();
        modal.classList.add('open');
        modal.classList.add('active');
        executePrediction();
    }

    function closeModal() {
        modal.classList.remove('open');
        modal.classList.remove('active');
        if (countdownInterval) clearInterval(countdownInterval);
    }

    if (btnOpen) btnOpen.addEventListener('click', openModal);
    if (btnOpenFloating) btnOpenFloating.addEventListener('click', openModal);
    if (btnClose) btnClose.addEventListener('click', closeModal);

    modal.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });

    // Populate dropdown with predefined cities & ground stations
    function populateLocationDropdown() {
        if (!locationSelect) return;
        const currentVal = locationSelect.value;
        locationSelect.innerHTML = '';

        // Predefined Cities (India & Global)
        const predefined = [
            { name: 'Bengaluru, India', lat: 12.9716, lon: 77.5946, group: 'Major Cities (India)' },
            { name: 'New Delhi, India', lat: 28.6139, lon: 77.2090, group: 'Major Cities (India)' },
            { name: 'Mumbai, India', lat: 19.0760, lon: 72.8777, group: 'Major Cities (India)' },
            { name: 'Hyderabad, India', lat: 17.3850, lon: 78.4867, group: 'Major Cities (India)' },
            { name: 'Chennai, India', lat: 13.0827, lon: 80.2707, group: 'Major Cities (India)' },
            { name: 'Kolkata, India', lat: 22.5726, lon: 88.3639, group: 'Major Cities (India)' },
            { name: 'Ahmedabad, India', lat: 23.0225, lon: 72.5714, group: 'Major Cities (India)' },
            { name: 'London, United Kingdom', lat: 51.5074, lon: -0.1278, group: 'Global Capitals' },
            { name: 'New York, United States', lat: 40.7128, lon: -74.0060, group: 'Global Capitals' },
            { name: 'Tokyo, Japan', lat: 35.6762, lon: 139.6503, group: 'Global Capitals' },
            { name: 'Sydney, Australia', lat: -33.8688, lon: 151.2093, group: 'Global Capitals' }
        ];

        const groups = {};
        predefined.forEach(p => {
            if (!groups[p.group]) groups[p.group] = [];
            groups[p.group].push(p);
        });

        // Add Active Ground Stations
        if (allGroundStationsData && allGroundStationsData.length > 0) {
            groups['Active Tracking Ground Stations'] = allGroundStationsData.map(st => ({
                name: `${st.name} (${st.country || 'Global'})`,
                lat: Number(st.latitude),
                lon: Number(st.longitude)
            }));
        }

        // Build HTML optgroups
        for (let grpName in groups) {
            const optgroup = document.createElement('optgroup');
            optgroup.label = grpName;
            groups[grpName].forEach(item => {
                const opt = document.createElement('option');
                opt.value = `${item.lat},${item.lon}`;
                opt.textContent = item.name;
                optgroup.appendChild(opt);
            });
            locationSelect.appendChild(optgroup);
        }

        const customOpt = document.createElement('option');
        customOpt.value = 'CUSTOM';
        customOpt.textContent = '📍 Custom Coordinates...';
        locationSelect.appendChild(customOpt);

        if (currentVal) {
            locationSelect.value = currentVal;
        } else {
            // Default to Bengaluru
            locationSelect.value = '12.9716,77.5946';
            onLocationChange();
        }
    }

    function onLocationChange() {
        const val = locationSelect.value;
        if (val === 'CUSTOM') {
            nameInput.disabled = false;
            latInput.disabled = false;
            lonInput.disabled = false;
        } else {
            const parts = val.split(',');
            if (parts.length === 2) {
                latInput.value = parseFloat(parts[0]).toFixed(4);
                lonInput.value = parseFloat(parts[1]).toFixed(4);
                nameInput.value = locationSelect.options[locationSelect.selectedIndex].text;
                nameInput.disabled = true;
                latInput.disabled = true;
                lonInput.disabled = true;
            }
        }
    }

    locationSelect.addEventListener('change', onLocationChange);

    // Global hook for Station Click Popup
    window.openPredictorModalForStation = function(name, lat, lon) {
        openModal();
        let found = false;
        for (let opt of locationSelect.options) {
            if (opt.text.toLowerCase().includes(name.toLowerCase())) {
                locationSelect.value = opt.value;
                onLocationChange();
                found = true;
                break;
            }
        }
        if (!found) {
            locationSelect.value = 'CUSTOM';
            onLocationChange();
            nameInput.value = name;
            latInput.value = Number(lat).toFixed(4);
            lonInput.value = Number(lon).toFixed(4);
        }
        executePrediction();
    };

    btnCalculate.addEventListener('click', executePrediction);

    async function executePrediction() {
        const lat = parseFloat(latInput.value);
        const lon = parseFloat(lonInput.value);
        const name = nameInput.value || 'Observer Location';
        const days = parseInt(daysSelect.value) || 2;

        if (isNaN(lat) || isNaN(lon)) {
            passesContainer.innerHTML = '<div style="color: #fb7185; padding: 12px;">Please enter valid numeric latitude [-90, 90] and longitude [-180, 180].</div>';
            return;
        }

        passesContainer.innerHTML = '<div style="text-align: center; padding: 24px; color: var(--cyan);"><svg class="animate-spin" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 2a10 10 0 0 1 10 10"/></svg><br><br>Propagating SGP4 Orbit & Elevation Angles with Orekit...</div>';

        try {
            const url = `/api/passes/predict?lat=${lat}&lon=${lon}&name=${encodeURIComponent(name)}&days=${days}`;
            const res = await fetch(url);
            if (!res.ok) throw new Error('Prediction service failed');
            const data = await res.json();
            renderPredictionResults(data);
        } catch (err) {
            passesContainer.innerHTML = `<div style="color: #fb7185; padding: 12px;">Failed to calculate flyovers: ${err.message}</div>`;
        }
    }

    function renderPredictionResults(data) {
        if (countdownInterval) clearInterval(countdownInterval);

        const passes = data.passes || [];
        totalCountBadge.textContent = `${passes.length} FLYOVERS`;

        if (passes.length === 0) {
            countdownBanner.className = 'countdown-banner no-pass';
            countdownTime.textContent = 'NO PASSES FOUND';
            countdownDetail.textContent = `No passes exceeding 10° elevation over ${data.targetName} within this window.`;
            passesContainer.innerHTML = '<div style="text-align:center; padding: 20px; color: var(--text-muted);">No upcoming passes in range.</div>';
            return;
        }

        // Find next upcoming pass
        const nowMs = Date.now();
        const nextPass = passes.find(p => p.aosTimestamp > nowMs) || passes[0];

        countdownBanner.className = 'countdown-banner';
        updateCountdown(nextPass.aosTimestamp, data.targetName);
        countdownInterval = setInterval(() => updateCountdown(nextPass.aosTimestamp, data.targetName), 1000);

        passesContainer.innerHTML = '';
        passes.forEach(p => {
            const card = document.createElement('div');
            card.className = 'pass-card';

            let typeClass = 'radio';
            if (p.passType && p.passType.includes('VISIBLE')) typeClass = 'visible';
            else if (p.passType && p.passType.includes('DAYLIGHT')) typeClass = 'daylight';

            const isHigh = p.maxElevationDeg >= 40.0;

            card.innerHTML = `
                <div class="pass-card-header">
                    <span class="pass-type-badge ${typeClass}">${p.passType}</span>
                    <span class="pass-elev-badge ${isHigh ? 'high' : ''}">Max Elev: ${p.maxElevationDeg}° ${isHigh ? '★' : ''}</span>
                </div>
                <div class="pass-time-row">
                    <span>AOS: ${p.formattedAosIst}</span>
                    <span>LOS: ${p.formattedLosIst}</span>
                </div>
                <div class="pass-duration-sub">
                    <span>Duration: ${p.durationFormatted}</span>
                    <span style="color: ${isHigh ? '#34d399' : 'var(--text-muted)'};">${p.quality} PASS</span>
                </div>
            `;
            passesContainer.appendChild(card);
        });
    }

    function updateCountdown(targetMs, targetName) {
        const diff = targetMs - Date.now();
        if (diff <= 0) {
            countdownTime.textContent = 'PASS CURRENTLY IN PROGRESS!';
            countdownDetail.textContent = `ISS is currently above the 10° horizon over ${targetName}.`;
            return;
        }

        const hrs = Math.floor(diff / 3600000);
        const mins = Math.floor((diff % 3600000) / 60000);
        const secs = Math.floor((diff % 60000) / 1000);

        countdownTime.textContent = `NEXT PASS IN: ${hrs}h ${mins}m ${secs}s`;
        countdownDetail.textContent = `Target: ${targetName} • All flyover times formatted in Indian Standard Time (IST).`;
    }
}

// Setup Feature 5: Automated Flyover Email Alert Modal & Handlers
function setupFlyoverAlertModal() {
    const modal = document.getElementById('alert-modal');
    const btnOpen = document.getElementById('btn-open-alert');
    const btnClose = document.getElementById('btn-close-alert-modal');
    const emailInput = document.getElementById('alert-email-input');
    const citySelect = document.getElementById('alert-city-select');
    const minElevSelect = document.getElementById('alert-min-elev-select');
    const latInput = document.getElementById('alert-lat-input');
    const lonInput = document.getElementById('alert-lon-input');
    const btnSubscribe = document.getElementById('btn-subscribe-alerts');
    const btnTestAlert = document.getElementById('btn-send-test-alert');
    const btnUnsubscribe = document.getElementById('btn-unsubscribe-alerts');
    const feedbackBox = document.getElementById('alert-feedback');
    const logsContainer = document.getElementById('alert-logs-container');
    const btnRefreshLogs = document.getElementById('btn-refresh-alert-logs');

    if (!modal) return;

    function openModal() {
        populateCityDropdown();
        modal.classList.add('open');
        modal.classList.add('active');
        fetchLogs();
    }

    function closeModal() {
        modal.classList.remove('open');
        modal.classList.remove('active');
    }

    if (btnOpen) btnOpen.addEventListener('click', openModal);
    if (btnClose) btnClose.addEventListener('click', closeModal);
    modal.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });

    // Populate City Dropdown
    function populateCityDropdown() {
        if (!citySelect) return;
        const currentVal = citySelect.value;
        citySelect.innerHTML = '';

        const predefined = [
            { name: 'Bengaluru, India', lat: 12.9716, lon: 77.5946 },
            { name: 'New Delhi, India', lat: 28.6139, lon: 77.2090 },
            { name: 'Mumbai, India', lat: 19.0760, lon: 72.8777 },
            { name: 'Kolkata, India', lat: 22.5726, lon: 88.3639 },
            { name: 'Chennai, India', lat: 13.0827, lon: 80.2707 },
            { name: 'Hyderabad, India', lat: 17.3850, lon: 78.4867 },
            { name: 'Ahmedabad, India', lat: 23.0225, lon: 72.5714 },
            { name: 'London, United Kingdom', lat: 51.5074, lon: -0.1278 },
            { name: 'Houston, TX, USA (NASA JSC)', lat: 29.5593, lon: -95.0900 },
            { name: 'Tokyo, Japan', lat: 35.6762, lon: 139.6503 },
            { name: 'Sydney, Australia', lat: -33.8688, lon: 151.2093 }
        ];

        const groupCities = document.createElement('optgroup');
        groupCities.label = 'Major Global Cities';
        predefined.forEach(c => {
            const opt = document.createElement('option');
            opt.value = `${c.lat},${c.lon}`;
            opt.textContent = c.name;
            groupCities.appendChild(opt);
        });
        citySelect.appendChild(groupCities);

        if (allGroundStationsData && allGroundStationsData.length > 0) {
            const groupStations = document.createElement('optgroup');
            groupStations.label = 'Ground Tracking Stations';
            allGroundStationsData.forEach(st => {
                const opt = document.createElement('option');
                opt.value = `${st.latitude},${st.longitude}`;
                opt.textContent = `${st.name} (${st.country || 'Station'})`;
                groupStations.appendChild(opt);
            });
            citySelect.appendChild(groupStations);
        }

        citySelect.value = currentVal || `${predefined[0].lat},${predefined[0].lon}`;
        syncCoords();
    }

    function syncCoords() {
        if (!citySelect.value) return;
        const [lat, lon] = citySelect.value.split(',').map(Number);
        latInput.value = lat.toFixed(4);
        lonInput.value = lon.toFixed(4);
    }

    citySelect.addEventListener('change', syncCoords);

    // Global hook for Station Click Popup -> Email Alert
    window.openAlertModalForStation = function(name, lat, lon) {
        openModal();
        let found = false;
        if (citySelect) {
            for (let opt of citySelect.options) {
                if (opt.text.toLowerCase().includes(name.toLowerCase())) {
                    citySelect.value = opt.value;
                    syncCoords();
                    found = true;
                    break;
                }
            }
            if (!found) {
                const opt = document.createElement('option');
                opt.value = `${lat},${lon}`;
                opt.textContent = name;
                citySelect.appendChild(opt);
                citySelect.value = opt.value;
                syncCoords();
            }
        }
    };

    function showFeedback(type, text) {
        feedbackBox.style.display = 'block';
        if (type === 'success') {
            feedbackBox.style.background = 'rgba(16, 185, 129, 0.15)';
            feedbackBox.style.border = '1px solid rgba(16, 185, 129, 0.5)';
            feedbackBox.style.color = '#34d399';
        } else if (type === 'info') {
            feedbackBox.style.background = 'rgba(56, 189, 248, 0.15)';
            feedbackBox.style.border = '1px solid rgba(56, 189, 248, 0.5)';
            feedbackBox.style.color = '#38bdf8';
        } else {
            feedbackBox.style.background = 'rgba(239, 68, 68, 0.15)';
            feedbackBox.style.border = '1px solid rgba(239, 68, 68, 0.5)';
            feedbackBox.style.color = '#f87171';
        }
        feedbackBox.innerHTML = text;
    }

    // Subscribe Button
    btnSubscribe.addEventListener('click', async () => {
        const email = emailInput.value.trim();
        if (!email || !email.includes('@')) {
            showFeedback('error', 'Please enter a valid email address.');
            return;
        }

        const selectedText = citySelect.options[citySelect.selectedIndex]?.textContent || 'Custom Location';
        const [lat, lon] = citySelect.value.split(',').map(Number);
        const minElev = parseFloat(minElevSelect.value) || 15.0;

        btnSubscribe.disabled = true;
        btnSubscribe.innerHTML = '<span class="animate-spin">⏳</span> Subscribing...';

        try {
            const res = await fetch('/api/notifications/subscribe', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    email: email,
                    targetCity: selectedText,
                    latitude: lat,
                    longitude: lon,
                    minElevation: minElev
                })
            });

            if (!res.ok) throw new Error(`Server returned ${res.status}`);
            const data = await res.json();
            showFeedback('success', `✅ <b>Subscribed successfully!</b> You will receive email alerts whenever the Space Station passes within 2 hours over <b>${selectedText}</b> with elevation ≥ ${minElev}°.`);
            fetchLogs();
        } catch (err) {
            showFeedback('error', `Subscription failed: ${err.message}`);
        } finally {
            btnSubscribe.disabled = false;
            btnSubscribe.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="8.5" cy="7" r="4"/><line x1="20" y1="8" x2="20" y2="14"/><line x1="23" y1="11" x2="17" y2="11"/></svg> Subscribe to Alerts`;
        }
    });

    // Send Test Alert Email Button
    btnTestAlert.addEventListener('click', async () => {
        const email = emailInput.value.trim();
        if (!email || !email.includes('@')) {
            showFeedback('error', 'Please enter your email address to receive the test alert.');
            return;
        }

        const selectedText = citySelect.options[citySelect.selectedIndex]?.textContent || 'Custom Location';
        const [lat, lon] = citySelect.value.split(',').map(Number);

        btnTestAlert.disabled = true;
        btnTestAlert.innerHTML = '<span class="animate-spin">⏳</span> Generating & Sending...';

        try {
            const params = new URLSearchParams({
                email: email,
                city: selectedText,
                lat: lat,
                lon: lon
            });

            const res = await fetch(`/api/notifications/test-email?${params.toString()}`, { method: 'POST' });
            if (!res.ok) throw new Error(`Server returned ${res.status}`);
            const data = await res.json();

            if (data.deliveryMode === 'DELIVERED_SMTP') {
                showFeedback('success', `📬 <b>Real Email Sent!</b> Check your inbox at <b>${data.recipient}</b>.<br>ISS flyover alert for <b>${data.targetCity}</b> at <b>${data.upcomingPassIst}</b> (Max Elev: <b>${data.maxElevationDeg}°</b>) successfully delivered via Gmail SMTP.`);
            } else if (data.deliveryMode === 'SMTP_FAILED') {
                showFeedback('error', `⚠️ <b>SMTP Failed:</b> ${data.message}`);
            } else {
                showFeedback('info', `🚀 <b>Simulated Dispatch Logged!</b><br>Upcoming Pass over <b>${data.targetCity}</b> at <b>${data.upcomingPassIst}</b> (Max Elev: <b>${data.maxElevationDeg}°</b>).<br><small style="color: var(--text-muted);">${data.message}</small>`);
            }
            fetchLogs();
        } catch (err) {
            showFeedback('error', `Failed to send test email: ${err.message}`);
        } finally {
            btnTestAlert.disabled = false;
            btnTestAlert.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg> Send Test Alert Email Now`;
        }
    });

    // Unsubscribe Button
    btnUnsubscribe.addEventListener('click', async () => {
        const email = emailInput.value.trim();
        if (!email) {
            showFeedback('error', 'Please enter the email address to unsubscribe.');
            return;
        }

        try {
            const res = await fetch(`/api/notifications/unsubscribe?email=${encodeURIComponent(email)}`, { method: 'DELETE' });
            const msg = await res.text();
            showFeedback('info', `ℹ️ ${msg}`);
            fetchLogs();
        } catch (err) {
            showFeedback('error', `Unsubscribe error: ${err.message}`);
        }
    });

    // Fetch Logs
    async function fetchLogs() {
        try {
            const res = await fetch('/api/notifications/logs');
            if (!res.ok) return;
            const logs = await res.json();

            if (!logs || logs.length === 0) {
                logsContainer.innerHTML = '<div style="text-align: center; color: var(--text-muted); font-size: 0.75rem; padding: 8px;">No notifications dispatched yet.</div>';
                return;
            }

            logsContainer.innerHTML = '';
            logs.forEach(l => {
                const item = document.createElement('div');
                item.className = 'log-item';

                const isDelivered = l.status === 'DELIVERED_SMTP';
                const badgeClass = isDelivered ? 'delivered' : (l.status && l.status.includes('FAIL') ? 'failed' : 'simulated');
                const dateStr = l.dispatchedAt ? new Date(l.dispatchedAt).toLocaleTimeString('en-IN', { timeZone: 'Asia/Kolkata', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true }) : '--';

                item.innerHTML = `
                    <div>
                        <div style="font-weight: 600; color: #ffffff;">${l.targetCity} <span style="color: var(--text-muted); font-size: 0.68rem;">(${l.recipientEmail})</span></div>
                        <div style="color: var(--cyan); font-size: 0.68rem; margin-top: 2px;">Pass: ${l.passAosIst || '--'} • Elev: ${l.maxElevationDeg || '--'}°</div>
                    </div>
                    <div style="text-align: right;">
                        <span class="log-status-badge ${badgeClass}">${l.status}</span>
                        <div style="color: var(--text-muted); font-size: 0.65rem; margin-top: 3px;">${dateStr} IST</div>
                    </div>
                `;
                logsContainer.appendChild(item);
            });
        } catch (e) {
            console.error('Failed fetching notification logs:', e);
        }
    }

    if (btnRefreshLogs) {
        btnRefreshLogs.addEventListener('click', fetchLogs);
    }
}
