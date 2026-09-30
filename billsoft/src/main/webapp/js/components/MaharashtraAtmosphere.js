/**
 * MaharashtraAtmosphere.js
 * Rich Multi-Layered Environmental Atmosphere for Take a Break.
 * Features:
 * - Layered environmental illustrations tailored to Maharashtra archetypes and specific landmarks.
 * - Distinct, visually recognizable silhouettes (Sahyadri forts, river confluences, wada arches, sea forts).
 * - Layer 1: Atmospheric sky gradient & horizon.
 * - Layer 2: Prominent hero landmark silhouette.
 * - Layer 3: Secondary regional motifs (mists, water ripples, temple spires, soaring birds).
 * - Layer 4: Vignette & contrast depth mask ensuring 100% crystal-clear game board readability.
 * - Theme-responsive (adapts gracefully across light, dark, and sunset billing themes).
 */

(function (root, factory) {
  if (typeof define === 'function' && define.amd) {
    define(['react'], factory);
  } else if (typeof module === 'object' && module.exports) {
    module.exports = factory(require('react'));
  } else {
    root.MaharashtraAtmosphere = factory(root.React);
  }
}(typeof self !== 'undefined' ? self : this, function (React) {
  'use strict';

  function getArchetypeSvg(archetypeId, themeColor = '#38bdf8', locationId = '') {
    const primary = themeColor || '#38bdf8';

    switch (archetypeId) {
      // 1. HILL FORT / SAHYADRI CITADEL (Raigad, Sajjangad, Pratapgad, Sinhagad, Panhala)
      case 'ARCH_HILL_FORT':
        return `<svg viewBox="0 0 1600 700" preserveAspectRatio="xMidYMax slice" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="skyGradFort" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.12"/>
              <stop offset="50%" stop-color="${primary}" stop-opacity="0.05"/>
              <stop offset="100%" stop-color="transparent" stop-opacity="0"/>
            </linearGradient>
            <linearGradient id="ridgeGrad1" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stop-color="currentColor" stop-opacity="0.22"/>
              <stop offset="100%" stop-color="currentColor" stop-opacity="0.08"/>
            </linearGradient>
            <linearGradient id="fortGrad" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stop-color="currentColor" stop-opacity="0.32"/>
              <stop offset="100%" stop-color="currentColor" stop-opacity="0.15"/>
            </linearGradient>
          </defs>
          <rect width="100%" height="100%" fill="url(#skyGradFort)"/>

          <!-- Distant Sahyadri Mountain Range -->
          <path d="M0,700 L0,380 Q250,220 520,340 T1050,260 Q1300,160 1600,280 L1600,700 Z" fill="url(#ridgeGrad1)"/>
          
          <!-- Midground Jagged Mountain Massif -->
          <path d="M0,700 L0,460 L180,320 L320,380 L520,240 L700,310 L920,180 L1120,280 L1350,190 L1500,290 L1600,230 L1600,700 Z" fill="currentColor" opacity="0.18"/>

          <!-- Hero Hill Fort Citadel & Bastions (Left & Right Flanks) -->
          <!-- Left Bastion & Ramparts -->
          <path d="M0,700 L0,260 L60,260 L60,240 L90,240 L90,260 L140,260 L140,230 L170,230 L170,260 L230,260 L230,290 L340,340 L380,420 L480,500 L580,700 Z" fill="url(#fortGrad)"/>
          <!-- Right Main Fort Ramparts & Flagpole -->
          <path d="M1100,700 L1220,440 L1260,320 L1300,320 L1300,290 L1330,290 L1330,320 L1380,320 L1380,280 L1420,280 L1420,320 L1480,320 L1530,250 L1570,250 L1600,230 L1600,700 Z" fill="url(#fortGrad)"/>
          
          <!-- Saffron Flag Silhouette on Right Bastion -->
          <line x1="1400" y1="280" x2="1400" y2="210" stroke="${primary}" stroke-width="3" opacity="0.8"/>
          <path d="M1400,210 L1445,225 L1400,240 Z" fill="${primary}" opacity="0.85"/>

          <!-- Sahyadri Birds soaring -->
          <path d="M780,180 Q790,170 800,180 Q810,170 820,180" stroke="currentColor" stroke-width="2" fill="none" opacity="0.35"/>
          <path d="M830,150 Q838,142 846,150 Q854,142 862,150" stroke="currentColor" stroke-width="1.8" fill="none" opacity="0.3"/>
          <path d="M750,140 Q756,134 762,140 Q768,134 774,140" stroke="currentColor" stroke-width="1.5" fill="none" opacity="0.25"/>
        </svg>`;

      // 2. RIVER VALLEY / CONFLUENCE (Karad, Paithan, Sangli, Chiplun)
      case 'ARCH_RIVER_VALLEY':
        return `<svg viewBox="0 0 1600 700" preserveAspectRatio="xMidYMax slice" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="riverSky" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.14"/>
              <stop offset="60%" stop-color="${primary}" stop-opacity="0.04"/>
              <stop offset="100%" stop-color="transparent" stop-opacity="0"/>
            </linearGradient>
            <linearGradient id="riverGrad" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.25"/>
              <stop offset="50%" stop-color="${primary}" stop-opacity="0.4"/>
              <stop offset="100%" stop-color="${primary}" stop-opacity="0.15"/>
            </linearGradient>
          </defs>
          <rect width="100%" height="100%" fill="url(#riverSky)"/>

          <!-- Rolling Valley Slopes & Hills -->
          <path d="M0,700 L0,360 Q320,200 680,310 T1600,240 L1600,700 Z" fill="currentColor" opacity="0.14"/>
          <path d="M0,700 L0,440 Q400,290 850,380 T1600,320 L1600,700 Z" fill="currentColor" opacity="0.2"/>

          <!-- River Confluence & Curving Water Channels -->
          <path d="M200,700 Q500,520 780,480 Q1100,450 1450,560 L1600,700 L0,700 Z" fill="url(#riverGrad)"/>
          <path d="M780,480 Q620,400 480,360 L540,360 Q660,390 820,480 Z" fill="${primary}" opacity="0.3"/>

          <!-- Stone Riverfront Ghat Steps & Shikhara Silhouette on Left -->
          <path d="M80,700 L80,560 L120,560 L120,540 L160,540 L160,520 L200,520 L200,500 L240,500 L260,420 L275,340 L290,420 L310,500 L380,500 L380,700 Z" fill="currentColor" opacity="0.3"/>
          <line x1="275" y1="340" x2="275" y2="310" stroke="${primary}" stroke-width="2.5" opacity="0.75"/>
          <polygon points="275,310 295,320 275,330" fill="${primary}" opacity="0.8"/>

          <!-- Riverboat & Ripples -->
          <path d="M980,540 Q1040,560 1100,540 L1080,525 L1000,525 Z" fill="currentColor" opacity="0.45"/>
          <line x1="1040" y1="525" x2="1040" y2="475" stroke="currentColor" stroke-width="2" opacity="0.5"/>
          <path d="M1040,480 L1075,505 L1040,520 Z" fill="${primary}" opacity="0.6"/>

          <path d="M600,580 Q680,590 760,580" stroke="${primary}" stroke-width="2" fill="none" opacity="0.35"/>
          <path d="M1150,600 Q1240,612 1330,600" stroke="${primary}" stroke-width="2" fill="none" opacity="0.3"/>
        </svg>`;

      // 3. HISTORIC WADA / ROYAL ARCHITECTURE (Shaniwar Wada, Lal Mahal, Sawantwadi)
      case 'ARCH_HISTORIC_WADA':
        return `<svg viewBox="0 0 1600 700" preserveAspectRatio="xMidYMax slice" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="wadaSky" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.14"/>
              <stop offset="100%" stop-color="transparent" stop-opacity="0"/>
            </linearGradient>
          </defs>
          <rect width="100%" height="100%" fill="url(#wadaSky)"/>

          <!-- Left Flank: Multi-tiered Wada Facade & Wooden Jharokha Balconies -->
          <g opacity="0.32" fill="currentColor">
            <!-- Wada Rampart Base -->
            <rect x="0" y="320" width="340" height="380"/>
            <!-- Battlements -->
            <rect x="0" y="300" width="30" height="25"/>
            <rect x="50" y="300" width="30" height="25"/>
            <rect x="100" y="300" width="30" height="25"/>
            <rect x="150" y="300" width="30" height="25"/>
            <rect x="200" y="300" width="30" height="25"/>
            <rect x="250" y="300" width="30" height="25"/>
            <rect x="300" y="300" width="30" height="25"/>

            <!-- Wooden Balconies & Arches -->
            <path d="M40,420 Q90,370 140,420 L140,490 L40,490 Z" opacity="0.6"/>
            <path d="M180,420 Q230,370 280,420 L280,490 L180,490 Z" opacity="0.6"/>
            <!-- Ornate Jharokha Roof -->
            <polygon points="160,200 110,250 210,250" fill="${primary}" opacity="0.75"/>
            <rect x="130" y="250" width="60" height="50"/>
          </g>

          <!-- Right Flank: Great Dilli Darwaza Arched Fortified Gateway -->
          <g opacity="0.32" fill="currentColor">
            <rect x="1260" y="300" width="340" height="400"/>
            <rect x="1260" y="280" width="30" height="25"/>
            <rect x="1310" y="280" width="30" height="25"/>
            <rect x="1360" y="280" width="30" height="25"/>
            <rect x="1410" y="280" width="30" height="25"/>
            <rect x="1460" y="280" width="30" height="25"/>
            <rect x="1510" y="280" width="30" height="25"/>
            <rect x="1560" y="280" width="30" height="25"/>

            <!-- Huge Pointed Archway -->
            <path d="M1360,560 Q1430,420 1500,560 L1500,700 L1360,700 Z" opacity="0.7"/>
            <polygon points="1430,170 1370,230 1490,230" fill="${primary}" opacity="0.8"/>
            <rect x="1395" y="230" width="70" height="50"/>
          </g>

          <!-- Flying birds -->
          <path d="M680,220 Q690,210 700,220 Q710,210 720,220" stroke="currentColor" stroke-width="2" fill="none" opacity="0.3"/>
          <path d="M740,190 Q748,182 756,190 Q764,182 772,190" stroke="currentColor" stroke-width="1.8" fill="none" opacity="0.25"/>
        </svg>`;

      // 4. COASTAL FORT / ARABIAN SEA (Murud-Janjira, Sindhudurg, Vijaydurg, Kolaba)
      case 'ARCH_COASTAL_FORT':
        return `<svg viewBox="0 0 1600 700" preserveAspectRatio="xMidYMax slice" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="seaSky" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.16"/>
              <stop offset="100%" stop-color="transparent" stop-opacity="0"/>
            </linearGradient>
            <linearGradient id="seaWaves" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.35"/>
              <stop offset="100%" stop-color="${primary}" stop-opacity="0.12"/>
            </linearGradient>
          </defs>
          <rect width="100%" height="100%" fill="url(#seaSky)"/>

          <!-- Horizon Ocean Line & Waves -->
          <path d="M0,700 L0,520 Q200,500 400,520 T800,520 T1200,520 T1600,520 L1600,700 Z" fill="url(#seaWaves)"/>
          <path d="M0,700 L0,570 Q300,545 600,570 T1200,570 T1600,570 L1600,700 Z" fill="${primary}" opacity="0.22"/>

          <!-- Left Coastal Island Fort Citadel (Murud-Janjira/Sindhudurg style) -->
          <g fill="currentColor" opacity="0.35">
            <!-- Curved Sea Ramparts -->
            <path d="M0,700 L0,390 Q120,380 240,360 L240,330 L270,330 L270,360 L330,360 L330,320 L370,320 L370,360 L440,390 Q480,480 520,580 L0,700 Z"/>
            <circle cx="255" cy="440" r="14" opacity="0.5"/>
            <circle cx="350" cy="440" r="14" opacity="0.5"/>
          </g>

          <!-- Right Coastal Palms & Reef Horizon -->
          <g fill="currentColor" opacity="0.28">
            <path d="M1250,700 Q1380,480 1480,420 L1600,430 L1600,700 Z"/>
            <!-- Palm Silhouettes -->
            <path d="M1480,420 Q1460,330 1440,240 L1448,240 Q1468,330 1488,420 Z"/>
            <path d="M1440,240 Q1380,210 1340,240 Q1400,230 1440,240 Z" fill="${primary}" opacity="0.7"/>
            <path d="M1440,240 Q1440,160 1420,130 Q1435,180 1440,240 Z" fill="${primary}" opacity="0.7"/>
            <path d="M1440,240 Q1500,190 1540,210 Q1485,220 1440,240 Z" fill="${primary}" opacity="0.7"/>
          </g>

          <!-- Sea Fort Saffron Flag -->
          <line x1="300" y1="340" x2="300" y2="270" stroke="${primary}" stroke-width="2.5" opacity="0.8"/>
          <polygon points="300,270 335,282 300,295" fill="${primary}" opacity="0.9"/>
        </svg>`;

      // 5. CAVE TEMPLE / ROCK-CUT MONOLITH (Ellora Kailasa, Ajanta, Karla)
      case 'ARCH_CAVE_TEMPLE':
        return `<svg viewBox="0 0 1600 700" preserveAspectRatio="xMidYMax slice" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="caveSky" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.15"/>
              <stop offset="100%" stop-color="transparent" stop-opacity="0"/>
            </linearGradient>
          </defs>
          <rect width="100%" height="100%" fill="url(#caveSky)"/>

          <!-- Dramatic Basalt Cliff Horizon -->
          <path d="M0,700 L0,220 L180,240 L340,160 L580,220 L820,140 L1080,210 L1320,150 L1600,240 L1600,700 Z" fill="currentColor" opacity="0.18"/>

          <!-- Left Rock-Cut Chaitya Arch & Pillars -->
          <g fill="currentColor" opacity="0.32">
            <path d="M0,700 L0,280 L380,310 L440,700 Z"/>
            <!-- Horseshoe Chaitya Window Arch -->
            <path d="M120,480 Q200,340 280,480 L280,560 L120,560 Z" opacity="0.65"/>
            <!-- Monolithic Carved Elephant / Pillars -->
            <rect x="80" y="560" width="40" height="140" opacity="0.8"/>
            <rect x="280" y="560" width="40" height="140" opacity="0.8"/>
          </g>

          <!-- Right Monolithic Kailasa Shikhara & Stupa Pillar -->
          <g fill="currentColor" opacity="0.32">
            <path d="M1200,700 L1260,340 L1600,290 L1600,700 Z"/>
            <!-- Monolithic Deepstambha Pillar -->
            <rect x="1320" y="320" width="35" height="380" fill="${primary}" opacity="0.6"/>
            <!-- Carved Rock Temple Shikhara -->
            <polygon points="1480,210 1420,380 1540,380" fill="currentColor" opacity="0.75"/>
          </g>
        </svg>`;

      // 6. PILGRIMAGE GHAT / TEMPLE SHIKHARA (Pandharpur, Wai, Trimbakeshwar, Alandi)
      case 'ARCH_PILGRIMAGE_GHAT':
        return `<svg viewBox="0 0 1600 700" preserveAspectRatio="xMidYMax slice" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="ghatSky" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.16"/>
              <stop offset="100%" stop-color="transparent" stop-opacity="0"/>
            </linearGradient>
          </defs>
          <rect width="100%" height="100%" fill="url(#ghatSky)"/>

          <!-- River Reflections & Hills -->
          <path d="M0,700 L0,380 Q400,240 800,340 T1600,280 L1600,700 Z" fill="currentColor" opacity="0.14"/>
          <path d="M0,700 Q400,560 800,580 T1600,560 L1600,700 Z" fill="${primary}" opacity="0.2"/>

          <!-- Left Flank: Grand Hemadpanthi Temple Shikhara & Deepstambha -->
          <g fill="currentColor" opacity="0.34">
            <!-- Stepped Ghats -->
            <path d="M0,700 L0,520 L60,520 L60,545 L120,545 L120,570 L180,570 L180,595 L240,595 L240,700 Z"/>
            <!-- Main Temple Shikhara -->
            <polygon points="180,180 110,480 250,480"/>
            <line x1="180" y1="180" x2="180" y2="135" stroke="${primary}" stroke-width="2.5" opacity="0.85"/>
            <polygon points="180,135 210,147 180,160" fill="${primary}" opacity="0.9"/>

            <!-- Deepstambha (Lamp Pillar) -->
            <rect x="290" y="310" width="22" height="280" fill="${primary}" opacity="0.6"/>
            <rect x="278" y="340" width="46" height="8" fill="${primary}" opacity="0.8"/>
            <rect x="278" y="390" width="46" height="8" fill="${primary}" opacity="0.8"/>
            <rect x="278" y="440" width="46" height="8" fill="${primary}" opacity="0.8"/>
          </g>

          <!-- Right Flank: Riverfront Pavilions & Riverboat -->
          <g fill="currentColor" opacity="0.32">
            <polygon points="1440,240 1380,460 1500,460"/>
            <path d="M1250,600 Q1320,625 1400,600 L1380,575 L1270,575 Z" fill="${primary}" opacity="0.5"/>
            <line x1="1335" y1="575" x2="1335" y2="520" stroke="currentColor" stroke-width="2"/>
            <polygon points="1335,520 1370,545 1335,560" fill="${primary}" opacity="0.75"/>
          </g>
        </svg>`;

      // 7. PLATEAU FOREST & SANCTUARY (Mahabaleshwar, Tadoba, Kas, Amboli, Chikhaldara)
      case 'ARCH_PLATEAU_FOREST':
        return `<svg viewBox="0 0 1600 700" preserveAspectRatio="xMidYMax slice" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="forestSky" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.14"/>
              <stop offset="100%" stop-color="transparent" stop-opacity="0"/>
            </linearGradient>
          </defs>
          <rect width="100%" height="100%" fill="url(#forestSky)"/>

          <!-- High Sahyadri Tableland & Waterfall Gorge -->
          <path d="M0,700 L0,320 L380,320 L440,480 L520,480 L560,320 L1100,320 L1160,460 L1240,460 L1290,320 L1600,320 L1600,700 Z" fill="currentColor" opacity="0.18"/>

          <!-- Waterfall Cascade in Gorge -->
          <line x1="480" y1="480" x2="480" y2="680" stroke="${primary}" stroke-width="6" opacity="0.5" stroke-dasharray="8 4"/>
          <line x1="1200" y1="460" x2="1200" y2="680" stroke="${primary}" stroke-width="6" opacity="0.5" stroke-dasharray="8 4"/>

          <!-- Dense Evergreen Forest Canopy (Left & Right) -->
          <g fill="currentColor" opacity="0.32">
            <!-- Pine & Teak Tree Silhouettes -->
            <polygon points="80,240 40,360 120,360"/>
            <polygon points="140,210 90,350 190,350"/>
            <polygon points="220,250 170,370 270,370"/>
            <polygon points="290,220 240,360 340,360"/>
            
            <polygon points="1320,230 1270,360 1370,360"/>
            <polygon points="1400,200 1350,350 1450,350"/>
            <polygon points="1480,240 1430,370 1530,370"/>
          </g>

          <!-- Flying Hornbills / Birds -->
          <path d="M720,200 Q735,185 750,200 Q765,185 780,200" stroke="${primary}" stroke-width="2.2" fill="none" opacity="0.45"/>
          <path d="M800,165 Q812,152 825,165 Q838,152 850,165" stroke="${primary}" stroke-width="1.8" fill="none" opacity="0.35"/>
        </svg>`;

      // 8. METROPOLIS / HERITAGE CAPITAL (Mumbai, Aga Khan, Hazur Sahib, Deekshabhoomi)
      case 'ARCH_METROPOLIS':
      default:
        return `<svg viewBox="0 0 1600 700" preserveAspectRatio="xMidYMax slice" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="metroSky" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stop-color="${primary}" stop-opacity="0.14"/>
              <stop offset="100%" stop-color="transparent" stop-opacity="0"/>
            </linearGradient>
          </defs>
          <rect width="100%" height="100%" fill="url(#metroSky)"/>

          <!-- Coastal Horizon & Harbor Waves -->
          <path d="M0,700 L0,540 Q400,520 800,540 T1600,540 L1600,700 Z" fill="${primary}" opacity="0.18"/>

          <!-- Left Flank: Gateway of India Monumental Arch Silhouette -->
          <g fill="currentColor" opacity="0.34">
            <rect x="40" y="320" width="280" height="380"/>
            <rect x="40" y="290" width="40" height="30"/>
            <rect x="280" y="290" width="40" height="30"/>
            <!-- Central Grand Arch -->
            <path d="M110,540 Q180,380 250,540 L250,700 L110,700 Z" opacity="0.6"/>
            <!-- Small Side Arches -->
            <path d="M60,560 Q75,500 90,560 L90,650 L60,650 Z" opacity="0.6"/>
            <path d="M270,560 Q285,500 300,560 L300,650 L270,650 Z" opacity="0.6"/>
            <!-- Ornate Central Dome -->
            <polygon points="180,220 130,290 230,290" fill="${primary}" opacity="0.75"/>
          </g>

          <!-- Right Flank: Sea Link Cable Bridge Silhouette -->
          <g fill="currentColor" opacity="0.3">
            <polygon points="1350,220 1335,560 1365,560"/>
            <!-- Bridge Deck -->
            <path d="M1100,540 L1600,490 L1600,520 L1100,570 Z"/>
            <!-- Stay Cables -->
            <line x1="1350" y1="260" x2="1180" y2="540" stroke="${primary}" stroke-width="1.8" opacity="0.5"/>
            <line x1="1350" y1="300" x2="1240" y2="540" stroke="${primary}" stroke-width="1.8" opacity="0.5"/>
            <line x1="1350" y1="260" x2="1520" y2="500" stroke="${primary}" stroke-width="1.8" opacity="0.5"/>
            <line x1="1350" y1="300" x2="1460" y2="505" stroke="${primary}" stroke-width="1.8" opacity="0.5"/>
          </g>
        </svg>`;
    }
  }

  function MaharashtraAtmosphere({ location, gameType }) {
    if (!location) return null;

    const archetypeId = location.archetypeId || 'ARCH_HILL_FORT';
    const themeColor = location.themeColor || '#38bdf8';
    const locationId = location.id || '';

    const svgContent = React.useMemo(() => {
      return getArchetypeSvg(archetypeId, themeColor, locationId);
    }, [archetypeId, themeColor, locationId]);

    return React.createElement('div', {
      className: 'maharashtra-atmosphere-container',
      'aria-hidden': 'true',
      style: {
        position: 'absolute',
        inset: 0,
        overflow: 'hidden',
        pointerEvents: 'none',
        zIndex: 0,
        transition: 'opacity 0.8s cubic-bezier(0.16, 1, 0.3, 1)'
      }
    },
      // Layer 1-3: Layered SVG Illustration
      React.createElement('div', {
        className: 'maharashtra-atmosphere-svg-layer',
        style: {
          width: '100%',
          height: '100%',
          position: 'absolute',
          inset: 0
        },
        dangerouslySetInnerHTML: { __html: svgContent }
      }),

      // Layer 4: Radial Vignette Mask (ensures board center is 100% clean & matches active RupeeCRM theme)
      React.createElement('div', {
        className: 'maharashtra-atmosphere-vignette',
        style: {
          position: 'absolute',
          inset: 0,
          background: 'radial-gradient(ellipse at 50% 48%, var(--bg, #F1F5F9) 0%, var(--bg, #F1F5F9) 38%, transparent 100%)',
          pointerEvents: 'none',
          opacity: 0.92
        }
      })
    );
  }

  return MaharashtraAtmosphere;
}));
