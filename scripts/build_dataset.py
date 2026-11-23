#!/usr/bin/env python3
"""
RupeeCRM 10-Firm Comprehensive Test Dataset Generator
------------------------------------------------------
Generates a realistic, relationally valid, interconnected 10-firm dataset
for RupeeCRM as an importable JSON file compatible with RupeeCRM's BackupDTO schema.
"""

import os
import sys
import json
import random
from datetime import datetime, date, timedelta
from decimal import Decimal, ROUND_HALF_UP

# Set deterministic random seed
random.seed(42)

def round_curr(val):
    return float(Decimal(str(val)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))

def round_qty(val):
    return float(Decimal(str(val)).quantize(Decimal("0.001"), rounding=ROUND_HALF_UP))

# -------------------------------------------------------------
# 1. 10 REALISTIC FICTIONAL FIRMS
# -------------------------------------------------------------
FIRMS = [
    {
        "id": 1,
        "firmName": "Apex Flow Solutions & Sanitaryware",
        "ownerName": "Vikramaditya Shinde",
        "addressLine1": "Plot 42, Sector 10, Bhosari MIDC",
        "addressLine2": "Near Quality Circle",
        "city": "Pune",
        "state": "Maharashtra",
        "pincode": "411038",
        "phone": "9822011234",
        "email": "contact@apexflow.example.com",
        "gstin": "27AAACA1001A1Z1",
        "bankName": "State Bank of India",
        "bankAccount": "30192837461",
        "bankIfsc": "SBIN0001245",
        "upiId": "apexflow@sbi",
        "prefix": "AFS",
        "pos": "27-Maharashtra",
        "themeColor": "#0284c7",
        "categories": [
            "PVC Pipes & Fittings", "CPVC Hot/Cold Pipes", "Brass Ball Valves",
            "Sanitaryware Ceramics", "Bathroom Faucets & Showers", "Water Storage Tanks",
            "Drainage Traps & Covers", "Pumps & Pressure Systems"
        ],
        "hsn": "8481",
        "base_items": [
            ("CPVC Pipe SDR 11 20mm (3m)", "CPVC Hot/Cold Pipes", "mtr", 220.0, 175.0, 18.0),
            ("CPVC Pipe SDR 11 25mm (3m)", "CPVC Hot/Cold Pipes", "mtr", 310.0, 245.0, 18.0),
            ("PVC Pressure Pipe 4-inch 6kg (6m)", "PVC Pipes & Fittings", "mtr", 540.0, 420.0, 18.0),
            ("Heavy Duty PVC Pipe 25mm", "PVC Pipes & Fittings", "mtr", 280.0, 220.0, 18.0),
            ("Brass Bib Cock with Wall Flange 15mm", "Brass Ball Valves", "pcs", 380.0, 290.0, 18.0),
            ("Brass Concealed Stop Cock 20mm", "Brass Ball Valves", "pcs", 620.0, 480.0, 18.0),
            ("Single Lever Basin Mixer Chrome Finish", "Bathroom Faucets & Showers", "pcs", 2450.0, 1850.0, 18.0),
            ("Overhead Rain Shower 8-inch Stainless Steel", "Bathroom Faucets & Showers", "pcs", 1250.0, 920.0, 18.0),
            ("Wall Hung Western Toilet Commode White", "Sanitaryware Ceramics", "pcs", 4800.0, 3600.0, 18.0),
            ("Counter Top Wash Basin Oval Ceramic", "Sanitaryware Ceramics", "pcs", 2200.0, 1650.0, 18.0),
            ("Triple Layer Water Storage Tank 1000L", "Water Storage Tanks", "pcs", 7500.0, 5800.0, 18.0),
            ("Commercial Floor Drain Trap Anti-Odor 100mm", "Drainage Traps & Covers", "pcs", 450.0, 320.0, 18.0),
            ("Submersible Domestic Water Pump 1HP", "Pumps & Pressure Systems", "pcs", 8900.0, 6900.0, 18.0),
            ("Industrial Safety Gloves (Pair)", "Pumps & Pressure Systems", "pcs", 95.0, 68.0, 18.0),
            ("Silicone Sealant Clear 300ml", "PVC Pipes & Fittings", "pcs", 210.0, 155.0, 18.0),
        ]
    },
    {
        "id": 2,
        "firmName": "Zenith Electricals & Grid Supplies",
        "ownerName": "Rajesh K. Mehta",
        "addressLine1": "B-12, GIDC Electronic Estate",
        "addressLine2": "Gandhinagar Highway",
        "city": "Ahmedabad",
        "state": "Gujarat",
        "pincode": "380015",
        "phone": "9879022345",
        "email": "sales@zenithelec.example.com",
        "gstin": "24AAACB2002B1Z2",
        "bankName": "HDFC Bank",
        "bankAccount": "50200039281746",
        "bankIfsc": "HDFC0000456",
        "upiId": "zenithelec@hdfcbank",
        "prefix": "ZE",
        "pos": "24-Gujarat",
        "themeColor": "#f59e0b",
        "categories": [
            "Modular Switches & Sockets", "FRLS Copper Wires & Cables", "MCB & Distribution Boards",
            "LED Commercial Lighting", "Industrial Switchgear", "Ceiling Fans & Ventilation",
            "Conduit Pipes & Accessories", "Earthing & Lightning Arresters"
        ],
        "hsn": "8536",
        "base_items": [
            ("2.5 sq mm FRLS Copper Wire 90m Red", "FRLS Copper Wires & Cables", "box", 2400.0, 1920.0, 18.0),
            ("4.0 sq mm FRLS Copper Wire 90m Yellow", "FRLS Copper Wires & Cables", "box", 3850.0, 3100.0, 18.0),
            ("16A 1-Way Modular Switch White", "Modular Switches & Sockets", "pcs", 85.0, 58.0, 18.0),
            ("6A 3-Pin Modular Socket with Shutter", "Modular Switches & Sockets", "pcs", 125.0, 88.0, 18.0),
            ("Double Pole MCB 32A C-Curve 10kA", "MCB & Distribution Boards", "pcs", 450.0, 340.0, 18.0),
            ("8-Way SPN Distribution Board Metal Door", "MCB & Distribution Boards", "pcs", 1350.0, 980.0, 18.0),
            ("36W LED 2x2 Square Panel Light 6500K", "LED Commercial Lighting", "pcs", 1150.0, 820.0, 18.0),
            ("18W LED Round Slim Downlight Warm White", "LED Commercial Lighting", "pcs", 380.0, 260.0, 18.0),
            ("Industrial 3-Phase Contactor 25A 220V", "Industrial Switchgear", "pcs", 1850.0, 1420.0, 18.0),
            ("Ceiling Fan High Speed 1200mm Gloss Brown", "Ceiling Fans & Ventilation", "pcs", 2150.0, 1680.0, 18.0),
            ("Heavy Duty Exhaust Fan 300mm Metal", "Ceiling Fans & Ventilation", "pcs", 1750.0, 1320.0, 18.0),
            ("PVC Rigid Conduit Pipe 25mm Medium (3m)", "Conduit Pipes & Accessories", "mtr", 85.0, 62.0, 18.0),
            ("Chemical Earthing Electrode Compound 25kg", "Earthing & Lightning Arresters", "pcs", 950.0, 720.0, 18.0),
            ("Standard Mounting Bracket Heavy Metal", "Modular Switches & Sockets", "pcs", 145.0, 95.0, 18.0),
            ("Industrial Safety Gloves (Pair)", "Industrial Switchgear", "pcs", 90.0, 65.0, 18.0),
        ]
    },
    {
        "id": 3,
        "firmName": "Bharat Buildcon & Hardware Mart",
        "ownerName": "Ganesh Narayan Patel",
        "addressLine1": "108 Loha Mandi",
        "addressLine2": "Dewas Naka",
        "city": "Indore",
        "state": "Madhya Pradesh",
        "pincode": "452001",
        "phone": "9425033456",
        "email": "info@bharatbuildcon.example.com",
        "gstin": "23AAACC3003C1Z3",
        "bankName": "Bank of Baroda",
        "bankAccount": "04810200009876",
        "bankIfsc": "BARB0INDORE",
        "upiId": "bharatbuildcon@barodampay",
        "prefix": "BBHM",
        "pos": "23-Madhya Pradesh",
        "themeColor": "#b45309",
        "categories": [
            "Structural Fasteners & Bolts", "Door Hardware & Locks", "Adhesives & Sealants",
            "Abrasives & Cutting Wheels", "Scaffolding & Props", "Wire Mesh & Binding Wire",
            "Waterproofing Compounds", "Hand Tools & Tapes"
        ],
        "hsn": "7318",
        "base_items": [
            ("Hex Head Bolt M12x50mm High Tensile 8.8", "Structural Fasteners & Bolts", "pcs", 28.0, 19.5, 18.0),
            ("Stainless Steel Anchor Fastener 10x100mm", "Structural Fasteners & Bolts", "pcs", 45.0, 31.0, 18.0),
            ("Heavy Duty PVC Pipe 25mm", "Scaffolding & Props", "mtr", 310.0, 240.0, 18.0),
            ("Mortise Door Lock Brass Antique Finish 6-Lever", "Door Hardware & Locks", "set", 1850.0, 1380.0, 18.0),
            ("Stainless Steel Door Hinges 4x3x3mm (Pair)", "Door Hardware & Locks", "set", 240.0, 175.0, 18.0),
            ("Silicone Weatherproof Sealant Clear 300ml", "Adhesives & Sealants", "pcs", 195.0, 145.0, 18.0),
            ("Epoxy Tile Grout 2-Part Waterproof 1kg", "Adhesives & Sealants", "pcs", 380.0, 275.0, 18.0),
            ("Thin Metal Cutting Wheel 4-inch 105x1.2mm", "Abrasives & Cutting Wheels", "pcs", 22.0, 15.0, 18.0),
            ("Flap Disc 4-inch Zirconia Grit 60", "Abrasives & Cutting Wheels", "pcs", 65.0, 46.0, 18.0),
            ("Galvanized Iron Binding Wire 18 Gauge 25kg", "Wire Mesh & Binding Wire", "kg", 88.0, 72.0, 18.0),
            ("Polymer Waterproofing Liquid Additive 20L", "Waterproofing Compounds", "pcs", 3200.0, 2450.0, 18.0),
            ("Steel Measuring Tape Auto-Lock 5m Heavy", "Hand Tools & Tapes", "pcs", 185.0, 130.0, 18.0),
            ("High Speed Drill Bit 10mm HSS-Co M35", "Hand Tools & Tapes", "pcs", 120.0, 85.0, 18.0),
            ("Industrial Safety Gloves (Pair)", "Structural Fasteners & Bolts", "pcs", 92.0, 66.0, 18.0),
            ("Standard Mounting Bracket Heavy Metal", "Structural Fasteners & Bolts", "pcs", 140.0, 92.0, 18.0),
        ]
    },
    {
        "id": 4,
        "firmName": "RevMax Auto Spares & Ancillaries",
        "ownerName": "Harvinder Singh Bedi",
        "addressLine1": "Shop 45-48, Transport Nagar",
        "addressLine2": "GT Road Bypass",
        "city": "Ludhiana",
        "state": "Punjab",
        "pincode": "141003",
        "phone": "9814044567",
        "email": "orders@revmaxauto.example.com",
        "gstin": "03AAACD4004D1Z4",
        "bankName": "Punjab National Bank",
        "bankAccount": "1029002100034567",
        "bankIfsc": "PUNB0029300",
        "upiId": "revmaxauto@pnb",
        "prefix": "RMX",
        "pos": "03-Punjab",
        "themeColor": "#dc2626",
        "categories": [
            "Brake Pads & Liners", "Engine Oil Filters & Air Filters", "Suspension Bushes & Arms",
            "Clutch Plates & Pressure Plates", "Automotive Lubricants & Coolants",
            "Headlights & Tail Lamp Assemblies", "Fan Belts & Timing Belts", "Spark Plugs & Glow Plugs"
        ],
        "hsn": "8708",
        "base_items": [
            ("Front Ceramic Brake Pads Swift Type-3", "Brake Pads & Liners", "set", 1450.0, 1080.0, 28.0),
            ("Heavy Commercial Brake Shoe Lining Set", "Brake Pads & Liners", "set", 2800.0, 2150.0, 28.0),
            ("Spin-On Engine Oil Filter Bolero mHawk", "Engine Oil Filters & Air Filters", "pcs", 280.0, 195.0, 18.0),
            ("High Flow Engine Air Filter Innova Crysta", "Engine Oil Filters & Air Filters", "pcs", 450.0, 320.0, 18.0),
            ("Hydraulic Gas Shock Absorber Rear (Pair)", "Suspension Bushes & Arms", "set", 3600.0, 2750.0, 28.0),
            ("Clutch Assembly Complete with Release Bearing", "Clutch Plates & Pressure Plates", "set", 5800.0, 4450.0, 28.0),
            ("Fully Synthetic Engine Oil 5W-30 3.5L Can", "Automotive Lubricants & Coolants", "pcs", 2250.0, 1720.0, 18.0),
            ("Concentrated Long-Life Radiator Coolant 1L", "Automotive Lubricants & Coolants", "pcs", 280.0, 195.0, 18.0),
            ("Clear Lens Headlamp Assembly Left-Hand", "Headlights & Tail Lamp Assemblies", "pcs", 2950.0, 2250.0, 28.0),
            ("EPDM Ribbed Fan Belt 6PK1220 Heavy Duty", "Fan Belts & Timing Belts", "pcs", 420.0, 290.0, 18.0),
            ("Iridium Tough Spark Plug Pack of 4", "Spark Plugs & Glow Plugs", "set", 1650.0, 1220.0, 18.0),
            ("Universal Wheel Bearing Grease 500g Tin", "Automotive Lubricants & Coolants", "pcs", 210.0, 150.0, 18.0),
            ("Standard Mounting Bracket Heavy Metal", "Suspension Bushes & Arms", "pcs", 155.0, 105.0, 18.0),
            ("Industrial Safety Gloves (Pair)", "Brake Pads & Liners", "pcs", 98.0, 70.0, 18.0),
            ("High Speed Drill Bit 10mm HSS-Co M35", "Suspension Bushes & Arms", "pcs", 115.0, 82.0, 18.0),
        ]
    },
    {
        "id": 5,
        "firmName": "Omkar Wholesale & FMCG Distribution",
        "ownerName": "Suresh Bhaskar Jagtap",
        "addressLine1": "Gala 14, APMC Commercial Complex",
        "addressLine2": "Sector 19, Vashi",
        "city": "Navi Mumbai",
        "state": "Maharashtra",
        "pincode": "400705",
        "phone": "9820055678",
        "email": "support@omkarwholesale.example.com",
        "gstin": "27AAACE5005E1Z5",
        "bankName": "ICICI Bank",
        "bankAccount": "001105012345",
        "bankIfsc": "ICIC0000011",
        "upiId": "omkarwholesale@icici",
        "prefix": "OWD",
        "pos": "27-Maharashtra",
        "themeColor": "#16a34a",
        "categories": [
            "Packaged Grocery Staples", "Beverages & Instant Mixes", "Personal Care & Soaps",
            "Surface Cleaners & Detergents", "Biscuits & Confectionery", "Edible Oils & Ghee",
            "Spices & Seasonings", "Paper & Disposable Products"
        ],
        "hsn": "2106",
        "base_items": [
            ("Refined Sunflower Oil 15L Commercial Tin", "Edible Oils & Ghee", "pcs", 2150.0, 1920.0, 5.0),
            ("Pure Cow Desi Ghee 1L Glass Jar", "Edible Oils & Ghee", "pcs", 680.0, 570.0, 12.0),
            ("Traditional Basmati Rice 25kg Woven Bag", "Packaged Grocery Staples", "pcs", 2850.0, 2450.0, 5.0),
            ("Toor Dal Superior Unpolished 10kg Bag", "Packaged Grocery Staples", "pcs", 1450.0, 1280.0, 5.0),
            ("Antibacterial Bathing Bar 125g Pack of 4", "Personal Care & Soaps", "box", 185.0, 150.0, 18.0),
            ("Concentrated Floor Cleaner Citrus 5L Can", "Surface Cleaners & Detergents", "pcs", 420.0, 310.0, 18.0),
            ("Active Washing Powder Detergent 5kg Bucket", "Surface Cleaners & Detergents", "pcs", 580.0, 440.0, 18.0),
            ("Instant Roast Coffee Powder 200g Jar", "Beverages & Instant Mixes", "pcs", 340.0, 280.0, 18.0),
            ("Assam CTC Leaf Tea 1kg Vacuum Pack", "Beverages & Instant Mixes", "pcs", 450.0, 370.0, 5.0),
            ("Chocolate Cream Sandwich Biscuits 24 Pack", "Biscuits & Confectionery", "box", 480.0, 390.0, 18.0),
            ("Kashmiri Red Chilli Powder 1kg Pouch", "Spices & Seasonings", "pcs", 380.0, 310.0, 5.0),
            ("2-Ply Kitchen Tissue Paper Roll Pack of 6", "Paper & Disposable Products", "box", 240.0, 180.0, 12.0),
            ("Industrial Safety Gloves (Pair)", "Surface Cleaners & Detergents", "pcs", 85.0, 60.0, 18.0),
            ("Hand Sanitizer Gel 5L Container", "Personal Care & Soaps", "pcs", 650.0, 480.0, 18.0),
            ("Paper Cups Biodegradable 150ml (Pack 100)", "Paper & Disposable Products", "box", 120.0, 85.0, 18.0),
        ]
    },
    {
        "id": 6,
        "firmName": "CyberPulse Electronics & Digital Hub",
        "ownerName": "Ananya Priyadarshini Rao",
        "addressLine1": "Unit 204, Brigade Plaza",
        "addressLine2": "MG Road",
        "city": "Bengaluru",
        "state": "Karnataka",
        "pincode": "560001",
        "phone": "9845066789",
        "email": "support@cyberpulse.example.com",
        "gstin": "29AAACF6006F1Z6",
        "bankName": "Axis Bank",
        "bankAccount": "919020038472619",
        "bankIfsc": "UTIB0000123",
        "upiId": "cyberpulse@axisbank",
        "prefix": "CP",
        "pos": "29-Karnataka",
        "themeColor": "#7c3aed",
        "categories": [
            "Laptops & Ultrabooks", "Computer Peripherals & Mice", "Audio & Noise-Cancelling Headphones",
            "Storage Devices & NVMe SSDs", "Smart Home & Networking Routers", "Charging Hubs & Power Banks",
            "Cables & Adapters", "Monitors & Mounts"
        ],
        "hsn": "8471",
        "base_items": [
            ("1TB NVMe PCIe Gen4 M.2 SSD 7000MB/s", "Storage Devices & NVMe SSDs", "pcs", 7200.0, 5600.0, 18.0),
            ("2TB External Portable Hard Drive USB 3.2", "Storage Devices & NVMe SSDs", "pcs", 5800.0, 4500.0, 18.0),
            ("Mechanical Gaming Keyboard RGB Brown Switch", "Computer Peripherals & Mice", "pcs", 3850.0, 2900.0, 18.0),
            ("Wireless Ergonomic Mouse Multi-Device 2.4GHz", "Computer Peripherals & Mice", "pcs", 1950.0, 1450.0, 18.0),
            ("Active Noise-Cancelling Bluetooth Headphones", "Audio & Noise-Cancelling Headphones", "pcs", 8900.0, 6800.0, 18.0),
            ("True Wireless Earbuds IPX5 with Charging Case", "Audio & Noise-Cancelling Headphones", "pcs", 2450.0, 1750.0, 18.0),
            ("27-inch 4K IPS Monitor with 65W USB-C PD", "Monitors & Mounts", "pcs", 24900.0, 19800.0, 18.0),
            ("Wi-Fi 6 AX3000 Dual-Band Gigabit Router", "Smart Home & Networking Routers", "pcs", 4200.0, 3150.0, 18.0),
            ("65W GaN Multi-Port Fast Charger Type-C", "Charging Hubs & Power Banks", "pcs", 1850.0, 1320.0, 18.0),
            ("20000mAh Power Bank 22.5W Fast Charge Metal", "Charging Hubs & Power Banks", "pcs", 2150.0, 1600.0, 18.0),
            ("USB-C to HDMI 4K Aluminum Adapter 2m", "Cables & Adapters", "pcs", 850.0, 580.0, 18.0),
            ("Braided Thunderbolt 4 Cable 100W 1m", "Cables & Adapters", "pcs", 1450.0, 980.0, 18.0),
            ("Gas Spring Single Monitor Desk Mount Arm", "Monitors & Mounts", "pcs", 2600.0, 1900.0, 18.0),
            ("Standard Mounting Bracket Heavy Metal", "Monitors & Mounts", "pcs", 160.0, 110.0, 18.0),
            ("Industrial Safety Gloves (Pair)", "Computer Peripherals & Mice", "pcs", 100.0, 75.0, 18.0),
        ]
    },
    {
        "id": 7,
        "firmName": "Kalyan Industrial Tools & Machining",
        "ownerName": "Prashant Madhukar Joshi",
        "addressLine1": "Plot D-88, Waluj MIDC Industrial Area",
        "addressLine2": "Opposite Bajaj Auto Main Gate",
        "city": "Aurangabad",
        "state": "Maharashtra",
        "pincode": "431005",
        "phone": "9422077890",
        "email": "tooling@kalyanindustrial.example.com",
        "gstin": "27AAACG7007G1Z7",
        "bankName": "Canara Bank",
        "bankAccount": "1234101098765",
        "bankIfsc": "CNRB0001234",
        "upiId": "kalyantools@canara",
        "prefix": "KIT",
        "pos": "27-Maharashtra",
        "themeColor": "#475569",
        "categories": [
            "CNC Carbide Inserts & End Mills", "Precision Measuring Instruments", "Pneumatic & Air Tools",
            "Heavy Duty Power Drills", "Grinding Wheels & Flap Discs", "Welding Machines & Torches",
            "Torque Wrenches & Sockets", "Industrial Safety & PPE"
        ],
        "hsn": "8207",
        "base_items": [
            ("Solid Carbide End Mill 4-Flute 10mm TiAlN", "CNC Carbide Inserts & End Mills", "pcs", 1450.0, 1100.0, 18.0),
            ("CNC Turning Insert CNMG 120408 Grade P25 (Box 10)", "CNC Carbide Inserts & End Mills", "box", 2600.0, 1980.0, 18.0),
            ("Digital Vernier Caliper 150mm Stainless Steel", "Precision Measuring Instruments", "pcs", 2850.0, 2150.0, 18.0),
            ("Outside Micrometer 0-25mm Carbide Anvil", "Precision Measuring Instruments", "pcs", 1950.0, 1450.0, 18.0),
            ("Pneumatic 1/2-inch Drive Impact Wrench 650Nm", "Pneumatic & Air Tools", "pcs", 4800.0, 3600.0, 18.0),
            ("Rotary Hammer Drill SDS-Plus 800W 26mm", "Heavy Duty Power Drills", "pcs", 5400.0, 4100.0, 18.0),
            ("Heavy Duty Angle Grinder 4-inch 850W", "Heavy Duty Power Drills", "pcs", 2450.0, 1850.0, 18.0),
            ("Inverter ARC Welding Machine 250A IGBT", "Welding Machines & Torches", "pcs", 8900.0, 6800.0, 18.0),
            ("Click-Type Adjustable Torque Wrench 40-200Nm", "Torque Wrenches & Sockets", "pcs", 3400.0, 2550.0, 18.0),
            ("Chrome Vanadium Socket Set 1/2-inch 24 Pcs", "Torque Wrenches & Sockets", "set", 3800.0, 2850.0, 18.0),
            ("Industrial Safety Gloves (Pair)", "Industrial Safety & PPE", "pcs", 85.0, 60.0, 18.0),
            ("Auto-Darkening Welding Helmet Solar Powered", "Industrial Safety & PPE", "pcs", 1650.0, 1200.0, 18.0),
            ("High Speed Drill Bit 10mm HSS-Co M35", "Heavy Duty Power Drills", "pcs", 105.0, 75.0, 18.0),
            ("Standard Mounting Bracket Heavy Metal", "Torque Wrenches & Sockets", "pcs", 150.0, 100.0, 18.0),
            ("Silicone Sealant Clear 300ml", "Pneumatic & Air Tools", "pcs", 205.0, 150.0, 18.0),
        ]
    },
    {
        "id": 8,
        "firmName": "Rainbow Paints & Surface Solutions",
        "ownerName": "Manoj Kumar Agarwal",
        "addressLine1": "Showroom 12-14, New Aatish Market",
        "addressLine2": "Mansarovar",
        "city": "Jaipur",
        "state": "Rajasthan",
        "pincode": "302006",
        "phone": "9829088901",
        "email": "sales@rainbowpaints.example.com",
        "gstin": "08AAACH8008H1Z8",
        "bankName": "Kotak Mahindra Bank",
        "bankAccount": "8412987654",
        "bankIfsc": "KKBK0000234",
        "upiId": "rainbowpaints@kotak",
        "prefix": "RP",
        "pos": "08-Rajasthan",
        "themeColor": "#db2777",
        "categories": [
            "Exterior Weatherproof Emulsions", "Interior Luxury Emulsions", "Waterproof Wall Putty & Primers",
            "Wood Finishes & Polyurethane", "Industrial Enamels & Primers", "Application Rollers & Spray Guns",
            "Texture Finishes & Plasters", "Tile Adhesives & Epoxy Grouts"
        ],
        "hsn": "3208",
        "base_items": [
            ("Exterior Weatherguard Emulsion 20L Brilliant White", "Exterior Weatherproof Emulsions", "pcs", 5800.0, 4450.0, 28.0),
            ("Luxury Sheen Interior Acrylic Emulsion 20L Base", "Interior Luxury Emulsions", "pcs", 6400.0, 4900.0, 28.0),
            ("Polymer White Cement Wall Putty 40kg Bag", "Waterproof Wall Putty & Primers", "pcs", 880.0, 690.0, 18.0),
            ("Exterior Acrylic Wall Primer 20L Bucket", "Waterproof Wall Putty & Primers", "pcs", 2400.0, 1850.0, 28.0),
            ("Polyurethane Wood Clear Finish High Gloss 4L", "Wood Finishes & Polyurethane", "pcs", 1850.0, 1400.0, 28.0),
            ("Synthetic Enamel Paint 4L Signal Red", "Industrial Enamels & Primers", "pcs", 1250.0, 950.0, 28.0),
            ("Red Oxide Metal Anti-Rust Primer 4L Can", "Industrial Enamels & Primers", "pcs", 820.0, 620.0, 18.0),
            ("Professional Paint Roller with 9-inch Cage Frame", "Application Rollers & Spray Guns", "pcs", 240.0, 170.0, 18.0),
            ("HVLP Electric Paint Spray Gun 600W with Cup", "Application Rollers & Spray Guns", "pcs", 2850.0, 2150.0, 18.0),
            ("Acrylic Texture Plaster Exterior 25kg Tub", "Texture Finishes & Plasters", "pcs", 1650.0, 1250.0, 28.0),
            ("Waterproof Tile Adhesive Polymer Modified 20kg", "Tile Adhesives & Epoxy Grouts", "pcs", 480.0, 360.0, 18.0),
            ("Silicone Sealant Clear 300ml", "Waterproof Wall Putty & Primers", "pcs", 225.0, 160.0, 18.0),
            ("Industrial Safety Gloves (Pair)", "Industrial Enamels & Primers", "pcs", 110.0, 80.0, 18.0),
            ("High Speed Drill Bit 10mm HSS-Co M35", "Application Rollers & Spray Guns", "pcs", 125.0, 90.0, 18.0),
            ("Epoxy Resin Floor Coating Kit 5kg Clear", "Industrial Enamels & Primers", "set", 3200.0, 2450.0, 18.0),
        ]
    },
    {
        "id": 9,
        "firmName": "Heritage Furnishings & Living Concepts",
        "ownerName": "Deepak S. Fernandes",
        "addressLine1": "Casa Heritage, DB Marg",
        "addressLine2": "Campal",
        "city": "Panaji",
        "state": "Goa",
        "pincode": "403001",
        "phone": "9822099012",
        "email": "decor@heritagefurniture.example.com",
        "gstin": "30AAACI9009I1Z9",
        "bankName": "Federal Bank",
        "bankAccount": "10020100987654",
        "bankIfsc": "FDRL0001002",
        "upiId": "heritagefurnish@federal",
        "prefix": "HF",
        "pos": "30-Goa",
        "themeColor": "#0d9488",
        "categories": [
            "Ergonomic Office Chairs", "Solid Teak Dining Sets", "Modular Storage & Wardrobes",
            "Living Room Sofas & Loungers", "Orthopedic Mattresses & Pillows", "Home Study Desks & Bookcases",
            "Accent Tables & Console Units", "Outdoor Wicker Patio Sets"
        ],
        "hsn": "9403",
        "base_items": [
            ("Ergonomic High-Back Mesh Office Chair with Lumbar", "Ergonomic Office Chairs", "pcs", 7800.0, 5800.0, 18.0),
            ("Mid-Back Executive Revolving Chair Black Leatherette", "Ergonomic Office Chairs", "pcs", 5400.0, 3950.0, 18.0),
            ("Solid Sheesham 6-Seater Dining Table with Chairs", "Solid Teak Dining Sets", "set", 34500.0, 26500.0, 12.0),
            ("Teak Wood Coffee Table with Glass Top", "Solid Teak Dining Sets", "pcs", 6800.0, 5100.0, 12.0),
            ("4-Door Engineered Wood Wardrobe Walnut Finish", "Modular Storage & Wardrobes", "pcs", 18500.0, 14200.0, 18.0),
            ("3-Seater Velvet Fabric Luxury Sofa Olive Green", "Living Room Sofas & Loungers", "pcs", 26800.0, 20500.0, 12.0),
            ("L-Shaped Sectional Corner Sofa Fabric Charcoal", "Living Room Sofas & Loungers", "pcs", 38900.0, 30500.0, 12.0),
            ("Orthopedic Memory Foam Mattress King 78x72x8", "Orthopedic Mattresses & Pillows", "pcs", 19500.0, 14800.0, 18.0),
            ("Cervical Memory Foam Pillow with Washable Cover", "Orthopedic Mattresses & Pillows", "pcs", 1250.0, 850.0, 12.0),
            ("Executive Wooden Study Desk with Lockable Drawers", "Home Study Desks & Bookcases", "pcs", 11200.0, 8400.0, 18.0),
            ("5-Tier Open Bookcase Wooden Display Shelf", "Home Study Desks & Bookcases", "pcs", 4800.0, 3600.0, 18.0),
            ("All-Weather Wicker Patio Outdoor Table & 4 Chairs", "Outdoor Wicker Patio Sets", "set", 16800.0, 12800.0, 18.0),
            ("Standard Mounting Bracket Heavy Metal", "Modular Storage & Wardrobes", "pcs", 150.0, 100.0, 18.0),
            ("Industrial Safety Gloves (Pair)", "Modular Storage & Wardrobes", "pcs", 95.0, 68.0, 18.0),
            ("Silicone Sealant Clear 300ml", "Accent Tables & Console Units", "pcs", 215.0, 155.0, 18.0),
        ]
    },
    {
        "id": 10,
        "firmName": "KisanSeva Agro Tech & Implements",
        "ownerName": "Pandurang B. Patil",
        "addressLine1": "Agro Bhavan, Station Road",
        "addressLine2": "Shahupuri",
        "city": "Kolhapur",
        "state": "Maharashtra",
        "pincode": "416003",
        "phone": "9422100123",
        "email": "helpdesk@kisansevagro.example.com",
        "gstin": "27AAACJ0101J1Z0",
        "bankName": "Bank of Maharashtra",
        "bankAccount": "20019283746",
        "bankIfsc": "MAHB0000456",
        "upiId": "kisanseva@mahbank",
        "prefix": "KSA",
        "pos": "27-Maharashtra",
        "themeColor": "#65a30d",
        "categories": [
            "Drip Irrigation Pipes & Emitters", "Sprinkler Systems & Nozzles", "Battery Operated Knapsack Sprayers",
            "Tractor Implements & Rotavator Blades", "Submersible Solar Agricultural Pumps",
            "Certified Hybrid Seeds & Seedlings", "Bio-Fertilizers & Soil Conditioners", "Tarpaulins & Protective Shade Nets"
        ],
        "hsn": "8424",
        "base_items": [
            ("Inline Drip Lateral Pipe 16mm 400m Roll Class-2", "Drip Irrigation Pipes & Emitters", "box", 3400.0, 2750.0, 12.0),
            ("Online Turbo Pressure Compensating Dripper 4 LPH (Pack 100)", "Drip Irrigation Pipes & Emitters", "box", 450.0, 320.0, 12.0),
            ("Agricultural Brass Impact Sprinkler 3/4-inch", "Sprinkler Systems & Nozzles", "pcs", 380.0, 270.0, 12.0),
            ("16L 12V Dual Motor Battery Knapsack Sprayer", "Battery Operated Knapsack Sprayers", "pcs", 2850.0, 2150.0, 12.0),
            ("Stainless Steel Telescopic Spray Lance with Brass Nozzle", "Battery Operated Knapsack Sprayers", "pcs", 350.0, 240.0, 12.0),
            ("Boron Steel Rotavator Blade C-Type (Pack of 10)", "Tractor Implements & Rotavator Blades", "set", 1950.0, 1480.0, 12.0),
            ("Solar Agricultural Submersible Pump Set 5HP DC", "Submersible Solar Agricultural Pumps", "set", 48500.0, 39000.0, 12.0),
            ("Certified Hybrid Tomato Seed Pack 10g Gold Grade", "Certified Hybrid Seeds & Seedlings", "box", 550.0, 420.0, 0.0),
            ("Organic Humic Acid Fertilizer Granules 25kg Bag", "Bio-Fertilizers & Soil Conditioners", "pcs", 1150.0, 880.0, 5.0),
            ("Liquid Bio-Potash Microbial Culture 1L Bottle", "Bio-Fertilizers & Soil Conditioners", "pcs", 380.0, 275.0, 5.0),
            ("Heavy Duty HDPE Waterproof Tarpaulin 250 GSM 24x18ft", "Tarpaulins & Protective Shade Nets", "pcs", 2950.0, 2250.0, 18.0),
            ("Green Agro Shade Net 75% UV Stabilized 3x50m", "Tarpaulins & Protective Shade Nets", "box", 3800.0, 2900.0, 18.0),
            ("Heavy Duty PVC Pipe 25mm", "Drip Irrigation Pipes & Emitters", "mtr", 290.0, 225.0, 18.0),
            ("Industrial Safety Gloves (Pair)", "Battery Operated Knapsack Sprayers", "pcs", 95.0, 68.0, 18.0),
            ("High Speed Drill Bit 10mm HSS-Co M35", "Tractor Implements & Rotavator Blades", "pcs", 110.0, 78.0, 18.0),
        ]
    }
]

# -------------------------------------------------------------
# 2. COMMON DELIBERATE TEST COLLISION NAMES
# -------------------------------------------------------------
SHARED_CUSTOMER_NAMES = [
    "ABC Traders",
    "Shree Ganesh Enterprises",
    "Krishna Traders",
    "National Construction Co",
    "Mahesh Sharma",
    "Sunil Deshmukh",
    "Pooja Sharma",
    "Balaji Hardware & Electricals",
    "Vijay Trading Corporation",
    "Royal Engineering Works"
]

SHARED_SUPPLIER_NAMES = [
    "Tata Steel Distribution Ltd",
    "National Polymers Corp",
    "Standard Industrial Fasteners",
    "Supreme Logistics & Supply",
    "Asian Industrial Chemicals",
    "Hinduja Manufacturing Ltd",
    "Reliance Polymer Distributors"
]

# Indian cities and states pool
CITIES_POOL = [
    ("Mumbai", "Maharashtra", "400001"),
    ("Pune", "Maharashtra", "411001"),
    ("Nagpur", "Maharashtra", "440001"),
    ("Nashik", "Maharashtra", "422001"),
    ("Ahmedabad", "Gujarat", "380001"),
    ("Surat", "Gujarat", "395001"),
    ("Vadodara", "Gujarat", "390001"),
    ("Indore", "Madhya Pradesh", "452001"),
    ("Bhopal", "Madhya Pradesh", "462001"),
    ("Ludhiana", "Punjab", "141001"),
    ("Amritsar", "Punjab", "143001"),
    ("Bengaluru", "Karnataka", "560001"),
    ("Mysuru", "Karnataka", "570001"),
    ("Jaipur", "Rajasthan", "302001"),
    ("Jodhpur", "Rajasthan", "342001"),
    ("Panaji", "Goa", "403001"),
    ("Margao", "Goa", "403601"),
    ("Kolhapur", "Maharashtra", "416001"),
    ("Sangli", "Maharashtra", "416416"),
    ("Solapur", "Maharashtra", "413001")
]

FIRST_NAMES = [
    "Aarav", "Aditi", "Amit", "Anand", "Anil", "Ananya", "Arun", "Ashok", "Chetan", "Deepak",
    "Dinesh", "Ganesh", "Gaurav", "Harish", "Jagdish", "Kiran", "Kunal", "Mahesh", "Manish", "Manoj",
    "Naveen", "Nikhil", "Nitin", "Pankaj", "Pradeep", "Prakash", "Prashant", "Pravin", "Rahul", "Rajesh",
    "Rakesh", "Ramesh", "Sachin", "Sandeep", "Sanjay", "Santosh", "Satish", "Shailesh", "Sunil", "Suresh",
    "Swapnil", "Umesh", "Varun", "Vijay", "Vikas", "Vikram", "Vinod", "Vishal", "Vivek", "Yogesh"
]

LAST_NAMES = [
    "Sharma", "Patel", "Mehta", "Joshi", "Deshmukh", "Kulkarni", "Patil", "Pawar", "Shinde", "Jadhav",
    "More", "Sawant", "Kadam", "Bhosale", "Chavan", "Gaikwad", "Jagtap", "Bedi", "Singh", "Fernandes",
    "D'Souza", "Rao", "Reddy", "Nair", "Pillai", "Menon", "Agarwal", "Gupta", "Bansal", "Verma"
]

print("Initialized reference pools.")

# -------------------------------------------------------------
# 3. BUILD COMPLETE 10-FIRM DATASET
# -------------------------------------------------------------
def build_dataset():
    print("Generating comprehensive 10-firm dataset...")

    customers = []
    products = []
    invoices = []
    invoice_payments = []
    parties = []
    purchase_orders = []
    party_payments = []
    sales_returns = []
    stock_movements = []
    employees = []
    attendance_records = []
    salary_records = []
    leave_records = []
    advances = []
    promotions = []
    goals = []
    savings = []
    goal_logs = []
    expenses = []
    reminders = []
    notes = []
    business_letters = []
    notifications = []
    notification_preferences = []

    # Entity ID counters
    cust_id_seq = 1001
    prod_id_seq = 10001
    inv_id_seq = 50001
    inv_item_id_seq = 200001
    inv_pmt_id_seq = 80001
    party_id_seq = 2001
    po_id_seq = 30001
    po_item_id_seq = 300001
    party_pmt_id_seq = 90001
    ret_id_seq = 40001
    ret_item_id_seq = 400001
    sm_id_seq = 60001
    emp_id_seq = 501
    att_id_seq = 70001
    sal_id_seq = 75001
    leave_id_seq = 76001
    adv_id_seq = 77001
    prom_id_seq = 78001
    goal_id_seq = 85001
    sav_id_seq = 86001
    goal_log_id_seq = 87001
    exp_id_seq = 95001
    rem_id_seq = 110001
    note_id_seq = 115001
    letter_id_seq = 120001
    notif_id_seq = 125001

    base_start_date = date(2025, 4, 1)

    for firm in FIRMS:
        fid = firm["id"]
        prefix = firm["prefix"]
        fname = firm["firmName"]
        fgstin = firm["gstin"]
        fphone = firm["phone"]
        faddr = firm["addressLine1"] + ", " + firm["city"]
        fpos = firm["pos"]
        print(f"Generating data for Firm {fid}: {fname}...")

        # -------------------------------------------------------------
        # 3.1 CUSTOMERS (150 per firm = 1,500 total)
        # -------------------------------------------------------------
        firm_customers = []
        for c_idx in range(1, 151):
            cid = cust_id_seq
            cust_id_seq += 1

            if c_idx <= len(SHARED_CUSTOMER_NAMES):
                cname = SHARED_CUSTOMER_NAMES[c_idx - 1]
            else:
                fn = FIRST_NAMES[(c_idx * 3 + fid * 7) % len(FIRST_NAMES)]
                ln = LAST_NAMES[(c_idx * 5 + fid * 11) % len(LAST_NAMES)]
                if c_idx % 3 == 0:
                    cname = f"{fn} {ln} & Sons"
                elif c_idx % 5 == 0:
                    cname = f"{ln} Enterprises"
                else:
                    cname = f"{fn} {ln}"

            city_info = CITIES_POOL[(c_idx + fid) % len(CITIES_POOL)]
            phone = f"98{fid % 10:01d}{c_idx:03d}{random.randint(1000, 9999)}"
            email = f"client{cid}@example.com"
            addr = f"Shop {c_idx}, Main Market, {city_info[0]}, {city_info[1]} - {city_info[2]}"
            gstin = f"{city_info[2][:2]}AAACB{cid:04d}C1Z{c_idx % 9 + 1}" if c_idx % 2 == 0 else None

            created_dt = (datetime(2025, 4, 1) + timedelta(days=c_idx % 120)).strftime("%Y-%m-%dT10:00:00")

            c_obj = {
                "id": cid,
                "version": 0,
                "firmId": fid,
                "name": cname,
                "phone": phone,
                "email": email,
                "address": addr,
                "gstin": gstin,
                "createdAt": created_dt
            }
            customers.append(c_obj)
            firm_customers.append(c_obj)

        # -------------------------------------------------------------
        # 3.2 PARTIES / SUPPLIERS (30 per firm = 300 total)
        # -------------------------------------------------------------
        firm_parties = []
        for p_idx in range(1, 31):
            pid = party_id_seq
            party_id_seq += 1

            if p_idx <= len(SHARED_SUPPLIER_NAMES):
                pname = SHARED_SUPPLIER_NAMES[p_idx - 1]
            else:
                cat = firm["categories"][(p_idx - 1) % len(firm["categories"])].split()[0]
                pname = f"{firm['city']} {cat} Supplier {p_idx}"

            city_info = CITIES_POOL[(p_idx * 2 + fid) % len(CITIES_POOL)]
            phone = f"97{fid % 10:01d}{p_idx:03d}{random.randint(1000, 9999)}"
            email = f"vendor{pid}@example.com"
            gstin = f"{city_info[2][:2]}AAACP{pid:04d}P1Z{p_idx % 9 + 1}"
            pan = f"AAACP{pid:04d}P"
            op_bal = round_curr(random.choice([0.0, 15000.0, 45000.0, 85000.0, 120000.0]))

            party_obj = {
                "id": pid,
                "version": 0,
                "firmId": fid,
                "name": pname,
                "contactPerson": f"Manager {FIRST_NAMES[p_idx % len(FIRST_NAMES)]}",
                "phone": phone,
                "email": email,
                "address": f"Industrial Area Phase-{p_idx % 4 + 1}, {city_info[0]}",
                "city": city_info[0],
                "state": city_info[1],
                "pincode": city_info[2],
                "gstin": gstin,
                "pan": pan,
                "bankName": "HDFC Bank",
                "bankAccount": f"501000{pid:06d}",
                "bankIfsc": "HDFC0000123",
                "upiId": f"vendor{pid}@hdfcbank",
                "openingBalance": op_bal,
                "openingBalanceType": "PAYABLE",
                "notes": f"Authorised wholesale distributor for Firm {fid}"
            }
            parties.append(party_obj)
            firm_parties.append(party_obj)

        # -------------------------------------------------------------
        # 3.3 PRODUCTS (300 per firm = 3,000 total)
        # -------------------------------------------------------------
        firm_products = []
        base_items = firm["base_items"]
        variations = [
            ("Standard", 1.0, 1.0),
            ("Economy Pack", 0.85, 0.82),
            ("Premium Heavy", 1.35, 1.30),
            ("Commercial Grade", 1.55, 1.50),
            ("Mini Series", 0.70, 0.68),
            ("Pro Series Gold", 1.80, 1.75),
            ("Pack of 2", 1.90, 1.85),
            ("Pack of 5", 4.50, 4.40),
            ("Pack of 10", 8.80, 8.60),
            ("Extra Length 5m", 1.60, 1.55),
            ("Compact Model", 0.90, 0.88),
            ("Heavy Duty 20mm", 1.15, 1.12),
            ("Heavy Duty 25mm", 1.30, 1.28),
            ("Heavy Duty 32mm", 1.65, 1.60),
            ("Industrial Spec 50mm", 2.20, 2.15),
            ("Reinforced Special", 1.45, 1.40),
            ("Export Edition", 2.10, 2.05),
            ("Eco Friendly", 1.05, 1.02),
            ("High Durability", 1.25, 1.22),
            ("All-Weather", 1.40, 1.35)
        ]

        p_count = 0
        for bi_name, bi_cat, bi_unit, bi_price, bi_cost, bi_gst in base_items:
            for var_label, price_mult, cost_mult in variations:
                p_count += 1
                if p_count > 300:
                    break
                pid = prod_id_seq
                prod_id_seq += 1

                pname = f"{bi_name} - {var_label}" if var_label != "Standard" else bi_name
                selling_price = round_curr(bi_price * price_mult)
                cost_price = round_curr(bi_cost * cost_mult)
                sku = f"{prefix}-{bi_cat[:3].upper()}-{p_count:03d}"
                barcode = f"890{fid:02d}{p_count:05d}{random.randint(1,9)}"
                stock_qty = round_qty(random.choice([15.0, 35.0, 60.0, 120.0, 250.0, 500.0, 800.0]))
                min_stock = round_qty(5.0 if bi_unit == "pcs" else 10.0)

                prod_obj = {
                    "id": pid,
                    "version": 0,
                    "firmId": fid,
                    "name": pname,
                    "category": bi_cat,
                    "sku": sku,
                    "barcode": barcode,
                    "price": selling_price,
                    "costPrice": cost_price,
                    "stockQuantity": stock_qty,
                    "minStockLevel": min_stock,
                    "itemType": "GOODS",
                    "unit": bi_unit,
                    "hsnCode": firm["hsn"],
                    "gstPercentage": bi_gst,
                    "inInventory": True,
                    "isArchived": False,
                    "createdAt": "2025-04-01T10:00:00",
                    "updatedAt": "2026-05-01T10:00:00"
                }
                products.append(prod_obj)
                firm_products.append(prod_obj)
            if p_count >= 300:
                break

        # -------------------------------------------------------------
        # 3.4 INVOICES (500 per firm = 5,000 total) & PAYMENTS (400 per firm)
        # -------------------------------------------------------------
        firm_invoices = []
        statuses = ["PAID"] * 325 + ["PARTIAL"] * 100 + ["UNPAID"] * 50 + ["CANCELLED"] * 25
        random.shuffle(statuses)

        for inv_idx in range(1, 501):
            invid = inv_id_seq
            inv_id_seq += 1

            inv_number = f"{prefix}-2026-{inv_idx:04d}"
            inv_status = statuses[inv_idx - 1]
            inv_date = base_start_date + timedelta(days=int((inv_idx - 1) * 0.8))
            inv_dt_str = datetime(inv_date.year, inv_date.month, inv_date.day, 9 + (inv_idx % 9), (inv_idx * 7) % 60, 0).strftime("%Y-%m-%dT%H:%M:%S")
            due_date_str = (inv_date + timedelta(days=30)).strftime("%Y-%m-%d")

            cust = firm_customers[(inv_idx - 1) % len(firm_customers)]
            cid = cust["id"]

            # 1 to 6 line items per invoice (average ~3.4 items = ~17,000 items total)
            item_count = random.choices([1, 2, 3, 4, 5, 6], weights=[5, 18, 35, 27, 10, 5])[0]
            selected_prods = random.sample(firm_products, item_count)

            inv_items = []
            subtotal_taxable = 0.0
            total_gst = 0.0
            total_cgst = 0.0
            total_sgst = 0.0

            for pr in selected_prods:
                qty = random.choice([1, 2, 3, 4, 5, 10, 15, 20])
                unit_price = pr["price"]
                unit_cost = pr["costPrice"]
                amt_without_tax = round_curr(qty * unit_price)

                disc_pct = random.choice([0.0, 0.0, 0.0, 0.0, 5.0, 10.0])
                disc_val = round_curr(amt_without_tax * disc_pct / 100.0)
                taxable = round_curr(amt_without_tax - disc_val)

                gst_pct = pr["gstPercentage"]
                gst_amt = round_curr(taxable * gst_pct / 100.0)
                cgst = round_curr(gst_amt / 2.0)
                sgst = round_curr(gst_amt - cgst)
                line_total = round_curr(taxable + gst_amt)

                subtotal_taxable += taxable
                total_gst += gst_amt
                total_cgst += cgst
                total_sgst += sgst

                item_obj = {
                    "product": {"id": pr["id"]},
                    "productName": pr["name"],
                    "qty": qty,
                    "unit": pr["unit"],
                    "hsnCode": pr["hsnCode"],
                    "pricePerUnit": unit_price,
                    "unitCostPrice": unit_cost,
                    "amountWithoutTax": amt_without_tax,
                    "taxableAmount": taxable,
                    "discountType": "PERCENT" if disc_pct > 0 else None,
                    "discountPercent": disc_pct if disc_pct > 0 else None,
                    "discountValue": disc_val if disc_pct > 0 else None,
                    "gstPercent": gst_pct,
                    "gstAmount": gst_amt,
                    "cgstAmount": cgst,
                    "sgstAmount": sgst,
                    "igstAmount": 0.0,
                    "lineTotal": line_total
                }
                inv_items.append(item_obj)

            subtotal_taxable = round_curr(subtotal_taxable)
            total_gst = round_curr(total_gst)
            total_cgst = round_curr(total_cgst)
            total_sgst = round_curr(total_sgst)
            inv_total = round_curr(subtotal_taxable + total_gst)

            is_paid = (inv_status == "PAID")
            pay_method = random.choice(["UPI", "Bank Transfer", "Cash", "Cheque"])

            inv_obj = {
                "id": invid,
                "version": 0,
                "firmId": fid,
                "invoiceNumber": inv_number,
                "status": inv_status,
                "customer": {"id": cid},
                "customerNameSnapshot": cust["name"],
                "customerPhoneSnapshot": cust["phone"],
                "customerAddressSnapshot": cust["address"],
                "customerGstinSnapshot": cust.get("gstin"),
                "firmNameSnapshot": fname,
                "firmGstinSnapshot": fgstin,
                "firmAddressSnapshot": faddr,
                "firmPhoneSnapshot": fphone,
                "firmEmailSnapshot": firm["email"],
                "placeOfSupply": fpos,
                "subtotalWithoutTax": subtotal_taxable,
                "totalTax": total_gst,
                "cgstAmount": total_cgst,
                "sgstAmount": total_sgst,
                "igstAmount": 0.0,
                "totalDiscount": 0.0,
                "roundOff": 0.0,
                "totalAmount": inv_total,
                "invoiceDate": inv_dt_str,
                "dueDate": due_date_str,
                "paid": is_paid,
                "paymentMethod": pay_method,
                "currency": "INR",
                "items": inv_items
            }
            invoices.append(inv_obj)
            firm_invoices.append(inv_obj)

            # Generate Invoice Payments for PAID and PARTIAL invoices
            if inv_status in ("PAID", "PARTIAL") and len(invoice_payments) < (fid * 400):
                pmtid = inv_pmt_id_seq
                inv_pmt_id_seq += 1

                pmt_amt = inv_total if inv_status == "PAID" else round_curr(inv_total * 0.5)
                pmt_date_str = (inv_date + timedelta(days=random.randint(1, 10))).strftime("%Y-%m-%d")

                pmt_obj = {
                    "id": pmtid,
                    "firmId": fid,
                    "invoiceId": invid,
                    "customerId": cid,
                    "amount": pmt_amt,
                    "paymentDate": pmt_date_str,
                    "paymentMode": pay_method,
                    "referenceNumber": f"TXN{fid}{pmtid:06d}",
                    "notes": f"Payment received for invoice {inv_number}"
                }
                invoice_payments.append(pmt_obj)

        # -------------------------------------------------------------
        # 3.5 PURCHASE ORDERS (100 per firm = 1,000 total) & PAYMENTS (80 per firm)
        # -------------------------------------------------------------
        firm_pos = []
        for po_idx in range(1, 101):
            poid = po_id_seq
            po_id_seq += 1

            po_number = f"PO-{prefix}-2026-{po_idx:04d}"
            po_date = base_start_date + timedelta(days=int((po_idx - 1) * 3.8))
            po_date_str = po_date.strftime("%Y-%m-%d")
            deliv_date_str = (po_date + timedelta(days=10)).strftime("%Y-%m-%d")

            party = firm_parties[(po_idx - 1) % len(firm_parties)]
            pid = party["id"]

            po_status = "RECEIVED" if po_idx <= 80 else ("COMPLETED" if po_idx <= 95 else "ISSUED")
            pay_status = "PAID" if po_idx <= 70 else ("PARTIAL" if po_idx <= 90 else "YET_TO_PAY")

            # 2 to 5 items per PO (average ~3.2 items = ~3,200 items total)
            item_count = random.choice([2, 3, 3, 4, 5])
            selected_prods = random.sample(firm_products, item_count)

            po_items = []
            subtotal = 0.0
            total_tax = 0.0

            for pr in selected_prods:
                qty = round_qty(random.choice([10.0, 25.0, 50.0, 100.0, 200.0]))
                unit_price = pr["costPrice"]
                taxable = round_curr(float(qty) * unit_price)
                gst_pct = pr["gstPercentage"]
                gst_amt = round_curr(taxable * gst_pct / 100.0)
                tot = round_curr(taxable + gst_amt)

                subtotal += taxable
                total_tax += gst_amt

                po_items.append({
                    "productId": pr["id"],
                    "productName": pr["name"],
                    "quantity": qty,
                    "unit": pr["unit"],
                    "unitPrice": unit_price,
                    "taxableAmount": taxable,
                    "gstPercent": gst_pct,
                    "gstAmount": gst_amt,
                    "totalAmount": tot
                })

            subtotal = round_curr(subtotal)
            total_tax = round_curr(total_tax)
            po_total = round_curr(subtotal + total_tax)
            paid_amount = po_total if pay_status == "PAID" else (round_curr(po_total * 0.5) if pay_status == "PARTIAL" else 0.0)

            po_obj = {
                "id": poid,
                "version": 0,
                "firmId": fid,
                "poNumber": po_number,
                "poDate": po_date_str,
                "expectedDeliveryDate": deliv_date_str,
                "party": {"id": pid},
                "partyName": party["name"],
                "partyPhone": party["phone"],
                "partyGstin": party["gstin"],
                "partyAddress": party["address"],
                "status": po_status,
                "paymentStatus": pay_status,
                "paidAmount": paid_amount,
                "paymentMethod": "Bank Transfer",
                "subtotalWithoutTax": subtotal,
                "totalGstAmount": total_tax,
                "totalAmount": po_total,
                "items": po_items
            }
            purchase_orders.append(po_obj)
            firm_pos.append(po_obj)

            # Supplier party payments (80 per firm = 800 total)
            if pay_status in ("PAID", "PARTIAL") and len(party_payments) < (fid * 80):
                pmtid = party_pmt_id_seq
                party_pmt_id_seq += 1

                party_payments.append({
                    "id": pmtid,
                    "firmId": fid,
                    "partyId": pid,
                    "purchaseOrderId": poid,
                    "amount": paid_amount,
                    "paymentDate": (po_date + timedelta(days=5)).strftime("%Y-%m-%d"),
                    "paymentMode": "BANK_TRANSFER",
                    "referenceNumber": f"UTR{fid}{pmtid:06d}",
                    "notes": f"Payment to supplier against {po_number}"
                })

        # -------------------------------------------------------------
        # 3.6 SALES RETURNS (20 per firm = 200 total)
        # -------------------------------------------------------------
        for ret_idx in range(1, 21):
            retid = ret_id_seq
            ret_id_seq += 1

            ret_number = f"RET-{prefix}-{ret_idx:04d}"
            orig_inv = firm_invoices[(ret_idx * 5) % len(firm_invoices)]
            inv_date = datetime.strptime(orig_inv["invoiceDate"][:10], "%Y-%m-%d").date()
            ret_date = inv_date + timedelta(days=15)
            ret_date_str = ret_date.strftime("%Y-%m-%d")

            ret_items = []
            subtot = 0.0
            tot_gst = 0.0
            tot_cgst = 0.0
            tot_sgst = 0.0

            # Return 1 or 2 items from invoice
            items_to_pick = orig_inv["items"][:min(2, len(orig_inv["items"]))]
            for item_to_ret in items_to_pick:
                ret_qty = max(1, item_to_ret["qty"] // 2)
                uprice = item_to_ret["pricePerUnit"]
                item_subtot = round_curr(ret_qty * uprice)
                gst_pct = item_to_ret["gstPercent"]
                gst_amt = round_curr(item_subtot * gst_pct / 100.0)
                cgst = round_curr(gst_amt / 2.0)
                sgst = round_curr(gst_amt - cgst)
                tot_ref = round_curr(item_subtot + gst_amt)

                subtot += item_subtot
                tot_gst += gst_amt
                tot_cgst += cgst
                tot_sgst += sgst

                ret_items.append({
                    "product": {"id": item_to_ret["product"]["id"]},
                    "productName": item_to_ret["productName"],
                    "unit": item_to_ret["unit"],
                    "hsnCode": item_to_ret.get("hsnCode", firm["hsn"]),
                    "returnQty": ret_qty,
                    "unitPrice": uprice,
                    "gstPercent": gst_pct,
                    "gstAmount": gst_amt,
                    "cgstAmount": cgst,
                    "sgstAmount": sgst,
                    "igstAmount": 0.0,
                    "refundTotal": tot_ref
                })

            subtot = round_curr(subtot)
            tot_gst = round_curr(tot_gst)
            tot_cgst = round_curr(tot_cgst)
            tot_sgst = round_curr(tot_sgst)
            tot_ref_all = round_curr(subtot + tot_gst)

            sales_returns.append({
                "id": retid,
                "firmId": fid,
                "returnNumber": ret_number,
                "returnDate": ret_date_str,
                "invoice": {"id": orig_inv["id"]},
                "customer": {"id": orig_inv["customer"]["id"]},
                "customerNameSnapshot": orig_inv["customerNameSnapshot"],
                "reason": random.choice(["Defective item replacement", "Excess order returned", "Customer changed specification"]),
                "refundMode": random.choice(["CUSTOMER_CREDIT", "BANK_TRANSFER", "CASH"]),
                "subtotal": subtot,
                "taxAmount": tot_gst,
                "cgstAmount": tot_cgst,
                "sgstAmount": tot_sgst,
                "igstAmount": 0.0,
                "totalRefundAmount": tot_ref_all,
                "notes": f"Authorized sales return against {orig_inv['invoiceNumber']}",
                "items": ret_items
            })

        # -------------------------------------------------------------
        # 3.7 STOCK MOVEMENTS (550 per firm = 5,500 total)
        # -------------------------------------------------------------
        # Reconcile stock: Initial stock, Purchase receipts, Invoice sales, Sales returns
        for sm_idx in range(1, 551):
            smid = sm_id_seq
            sm_id_seq += 1

            pr = firm_products[(sm_idx - 1) % len(firm_products)]
            pid = pr["id"]

            if sm_idx <= 250:
                # Initial opening stock entry
                mtype = "INITIAL_STOCK"
                qchange = round_qty(random.choice([50.0, 100.0, 200.0, 500.0]))
                prev_stk = 0.0
                new_stk = qchange
                ref_type = "MANUAL"
                ref_id = "OPENING-2025"
            elif sm_idx <= 400:
                # Purchase receipt additions
                mtype = "PURCHASE_RECEIPT"
                qchange = round_qty(random.choice([25.0, 50.0, 100.0]))
                prev_stk = round_qty(random.choice([30.0, 60.0, 120.0]))
                new_stk = round_qty(prev_stk + qchange)
                ref_type = "PURCHASE_ORDER"
                ref_id = f"PO-{prefix}-2026-{(sm_idx % 100) + 1:04d}"
            elif sm_idx <= 520:
                # Invoice sales deductions
                mtype = "INVOICE_SALE"
                qchange = round_qty(-random.choice([2.0, 5.0, 10.0]))
                prev_stk = round_qty(random.choice([80.0, 150.0, 300.0]))
                new_stk = round_qty(prev_stk + qchange)
                ref_type = "INVOICE"
                ref_id = f"{prefix}-2026-{(sm_idx % 500) + 1:04d}"
            else:
                # Sales returns and adjustments
                mtype = "SALE_RETURN"
                qchange = round_qty(random.choice([1.0, 2.0, 4.0]))
                prev_stk = round_qty(random.choice([40.0, 85.0, 120.0]))
                new_stk = round_qty(prev_stk + qchange)
                ref_type = "SALES_RETURN"
                ref_id = f"RET-{prefix}-{(sm_idx % 20) + 1:04d}"

            sm_dt = (base_start_date + timedelta(days=int(sm_idx * 0.7))).strftime("%Y-%m-%dT11:00:00")

            stock_movements.append({
                "id": smid,
                "firmId": fid,
                "productId": pid,
                "productName": pr["name"],
                "movementType": mtype,
                "quantityChange": qchange,
                "previousStock": prev_stk,
                "newStock": new_stk,
                "referenceType": ref_type,
                "referenceId": ref_id,
                "note": f"Audited inventory adjustment for {pr['name']}",
                "createdAt": sm_dt
            })

        # -------------------------------------------------------------
        # 3.8 EMPLOYEES (10 per firm = 100 total)
        # -------------------------------------------------------------
        roles_pool = [
            ("Branch General Manager", "Operations", 75000.0),
            ("Senior Accounts Officer", "Finance", 55000.0),
            ("Inventory Controller", "Warehouse", 42000.0),
            ("Senior Sales Executive", "Sales", 48000.0),
            ("Sales Executive", "Sales", 35000.0),
            ("Dispatch & Logistics Supervisor", "Warehouse", 38000.0),
            ("Billing Specialist", "Finance", 32000.0),
            ("Procurement Coordinator", "Operations", 40000.0),
            ("Store Assistant", "Warehouse", 26000.0),
            ("Customer Care Representative", "Support", 28000.0)
        ]

        firm_employees = []
        for e_idx, (r_name, r_dept, r_sal) in enumerate(roles_pool, start=1):
            empid = emp_id_seq
            emp_id_seq += 1

            fn = FIRST_NAMES[(e_idx * 4 + fid * 3) % len(FIRST_NAMES)]
            ln = LAST_NAMES[(e_idx * 6 + fid * 5) % len(LAST_NAMES)]
            ename = f"{fn} {ln}"
            phone = f"96{fid % 10:01d}{e_idx:03d}{random.randint(1000, 9999)}"

            emp_obj = {
                "id": empid,
                "firmId": fid,
                "name": ename,
                "phone": phone,
                "role": r_name,
                "department": r_dept,
                "designation": r_name,
                "dateOfJoining": "2023-01-15",
                "idProofNumber": f"AADHAAR-8{fid}{empid:04d}",
                "isActive": True,
                "monthlyBaseSalary": r_sal,
                "allowedPaidLeavesPerMonth": 2,
                "currentAdvanceBalance": 0.0,
                "email": f"emp{empid}@example.com",
                "address": f"Staff Quarters, {firm['city']}, {firm['state']}",
                "emergencyContactName": f"Relative of {fn}",
                "emergencyContactPhone": f"95{fid % 10:01d}{e_idx:03d}1234",
                "bankAccountName": ename,
                "bankAccountNumber": f"201000{empid:06d}",
                "bankIfscCode": firm["bankIfsc"],
                "bankName": firm["bankName"]
            }
            employees.append(emp_obj)
            firm_employees.append(emp_obj)

            # Attendance records (5 per employee = 50 per firm = 500 total)
            att_dates = [date(2026, 5, 1), date(2026, 5, 2), date(2026, 5, 3), date(2026, 5, 4), date(2026, 5, 5)]
            for att_d in att_dates:
                attid = att_id_seq
                att_id_seq += 1
                attendance_records.append({
                    "id": attid,
                    "employee": {"id": empid},
                    "date": att_d.strftime("%Y-%m-%d"),
                    "status": "PRESENT" if att_d.weekday() != 6 else "HOLIDAY",
                    "remarks": "On duty - Biometric verified"
                })

            # Salary records (3 per employee = 30 per firm = 300 total)
            months = [("04-2026", date(2026, 5, 1)), ("03-2026", date(2026, 4, 1)), ("02-2026", date(2026, 3, 1))]
            for m_str, p_date in months:
                salid = sal_id_seq
                sal_id_seq += 1
                salary_records.append({
                    "id": salid,
                    "employee": {"id": empid},
                    "monthYear": m_str,
                    "baseSalaryAtTime": r_sal,
                    "daysAbsent": 0,
                    "paidLeavesUsed": 1,
                    "unpaidLeaves": 0,
                    "leaveDeductionAmount": 0.0,
                    "bonusAmount": 1000.0 if m_str == "03-2026" else 0.0,
                    "advanceDeducted": 0.0,
                    "netPaid": r_sal + (1000.0 if m_str == "03-2026" else 0.0),
                    "paymentDate": p_date.strftime("%Y-%m-%d")
                })

            # Leave records (12 per firm = 120 total)
            if e_idx <= 2:
                for l_idx in range(1, 7):
                    leaveid = leave_id_seq
                    leave_id_seq += 1
                    s_date = date(2026, 3, l_idx * 3)
                    e_date = s_date + timedelta(days=1)
                    leave_records.append({
                        "id": leaveid,
                        "employee": {"id": empid},
                        "startDate": s_date.strftime("%Y-%m-%d"),
                        "endDate": e_date.strftime("%Y-%m-%d"),
                        "type": "CASUAL" if l_idx % 2 == 0 else "SICK",
                        "status": "APPROVED",
                        "totalDays": 2,
                        "reason": "Personal family engagement",
                        "approvedBy": "General Manager"
                    })

            # Advances (10 per firm = 100 total)
            if e_idx <= 10:
                advid = adv_id_seq
                adv_id_seq += 1
                advances.append({
                    "id": advid,
                    "employee": {"id": empid},
                    "date": "2026-04-10",
                    "amount": 5000.0,
                    "description": "Festival advance recoverable over 5 salary cycles"
                })

            # Promotions (5 per firm = 50 total)
            if e_idx <= 5:
                promid = prom_id_seq
                prom_id_seq += 1
                promotions.append({
                    "id": promid,
                    "employee": {"id": empid},
                    "effectiveDate": "2026-04-01",
                    "type": "INCREMENT",
                    "previousRole": r_name,
                    "newRole": r_name,
                    "previousSalary": r_sal - 4000.0,
                    "newSalary": r_sal,
                    "reason": "Annual performance evaluation appraisal",
                    "isApplied": True
                })

        # -------------------------------------------------------------
        # 3.9 GOALS (12 per firm = 120 total) & SAVINGS & LOGS
        # -------------------------------------------------------------
        goals_config = [
            ("Annual Sales Target FY26", "SAVINGS_TARGET", 5000000.0, 3200000.0, "INR", "ACTIVE"),
            ("Q1 Revenue Milestone", "SAVINGS_TARGET", 1200000.0, 1200000.0, "INR", "ACHIEVED"),
            ("Client Acquisition Drive", "LIFE_MILESTONE", 100.0, 68.0, "Clients", "ACTIVE"),
            ("New Dealer Onboarding", "LIFE_MILESTONE", 30.0, 22.0, "Dealers", "ACTIVE"),
            ("Daily Sales Review Meetings", "HABIT_STREAK", 60.0, 45.0, "Days", "ACTIVE"),
            ("Daily Inventory Audit Check", "HABIT_STREAK", 30.0, 28.0, "Days", "ACTIVE"),
            ("Zero Disputed Invoices", "QUIT_HABIT", 90.0, 90.0, "Days", "ACHIEVED"),
            ("Zero Negative Stock Instances", "QUIT_HABIT", 60.0, 42.0, "Days", "ACTIVE"),
            ("Capital Equipment Reserve", "SAVINGS_TARGET", 800000.0, 550000.0, "INR", "ACTIVE"),
            ("Tax Provision FY26", "SAVINGS_TARGET", 450000.0, 300000.0, "INR", "ACTIVE"),
            ("Emergency Working Capital", "SAVINGS_TARGET", 1000000.0, 750000.0, "INR", "ACTIVE"),
            ("Fast Moving Stock SLA", "LIFE_MILESTONE", 500.0, 480.0, "Dispatches", "ACTIVE")
        ]

        firm_goals = []
        for g_idx, (g_title, g_type, g_tgt, g_cur, g_unit, g_stat) in enumerate(goals_config, start=1):
            gid = goal_id_seq
            goal_id_seq += 1

            goal_obj = {
                "id": gid,
                "firmId": fid,
                "title": f"{g_title} - {prefix}",
                "goalType": g_type,
                "targetValue": g_tgt,
                "currentValue": g_cur,
                "unit": g_unit,
                "startDate": "2026-01-01",
                "targetDate": "2026-12-31",
                "status": g_stat,
                "currentStreak": 15 if "STREAK" in g_type else 0,
                "longestStreak": 30 if "STREAK" in g_type else 0,
                "notes": f"Strategic business objective for {fname}"
            }
            goals.append(goal_obj)
            firm_goals.append(goal_obj)

            # Goal logs (40 per firm = 400 total)
            if g_idx <= 4:
                for gl_idx in range(1, 11):
                    glid = goal_log_id_seq
                    goal_log_id_seq += 1
                    glog_dt = date(2026, 1, 1) + timedelta(days=gl_idx * 10)
                    goal_logs.append({
                        "id": glid,
                        "firmId": fid,
                        "goalId": gid,
                        "actionType": "DEPOSIT" if "SAVINGS" in g_type else "INCREMENT",
                        "deltaValue": 25000.0 if "SAVINGS" in g_type else 5.0,
                        "resultingValue": (gl_idx * 25000.0) if "SAVINGS" in g_type else float(gl_idx * 5),
                        "logDate": glog_dt.strftime("%Y-%m-%d"),
                        "notes": f"Progress update cycle {gl_idx}"
                    })

        # Savings records (20 per firm = 200 total)
        for s_idx in range(1, 21):
            savid = sav_id_seq
            sav_id_seq += 1
            s_date = date(2026, 1, 5) + timedelta(days=s_idx * 6)
            s_goal = firm_goals[s_idx % len(firm_goals)]
            savings.append({
                "id": savid,
                "firmId": fid,
                "goalId": s_goal["id"],
                "title": f"Reserve Deposit {s_idx} - {prefix}",
                "amount": round_curr(random.choice([15000.0, 25000.0, 50000.0, 75000.0])),
                "category": random.choice(["Business Reserves", "Fixed Deposit", "Mutual Funds / SIP"]),
                "savingDate": s_date.strftime("%Y-%m-%d"),
                "paymentMode": "Bank Transfer",
                "notes": f"Systematic monthly allocation for {s_goal['title']}"
            })

        # -------------------------------------------------------------
        # 3.10 EXPENSES (150 per firm = 1,500 total)
        # -------------------------------------------------------------
        exp_categories = [
            ("Rent & Facilities", 35000.0),
            ("Salaries & Wages", 45000.0),
            ("Electricity & Water Utilities", 8500.0),
            ("Transport & Outbound Freight", 12500.0),
            ("Marketing & Trade Directory", 9500.0),
            ("Machinery & Tool Repairs", 6200.0),
            ("Office Stationery & Printing", 3400.0),
            ("Software Subscriptions & Cloud", 4800.0)
        ]

        for exp_idx in range(1, 151):
            expid = exp_id_seq
            exp_id_seq += 1

            cat_item = exp_categories[(exp_idx - 1) % len(exp_categories)]
            exp_date = base_start_date + timedelta(days=int((exp_idx - 1) * 2.6))
            exp_date_str = exp_date.strftime("%Y-%m-%d")
            exp_amt = round_curr(cat_item[1] * random.uniform(0.85, 1.25))

            expenses.append({
                "id": expid,
                "firmId": fid,
                "title": f"{cat_item[0]} - Cycle {exp_idx}",
                "category": cat_item[0],
                "amount": exp_amt,
                "expenseDate": exp_date_str,
                "paymentMode": random.choice(["Bank Transfer", "UPI", "Cash"]),
                "notes": f"Operational disbursement for {fname}"
            })

        # -------------------------------------------------------------
        # 3.11 REMINDERS (20 per firm = 200 total) & NOTES (15 per firm = 150 total)
        # -------------------------------------------------------------
        for r_idx in range(1, 21):
            remid = rem_id_seq
            rem_id_seq += 1
            rcust = firm_customers[(r_idx - 1) % len(firm_customers)]
            rinv = firm_invoices[(r_idx - 1) % len(firm_invoices)]
            r_due = (date(2026, 6, 1) + timedelta(days=r_idx)).strftime("%Y-%m-%dT10:00:00")

            reminders.append({
                "id": remid,
                "firmId": fid,
                "customerId": rcust["id"],
                "invoiceId": rinv["id"],
                "title": f"Follow-up on Payment for Invoice {rinv['invoiceNumber']}",
                "note": f"Call customer {rcust['name']} regarding outstanding balance.",
                "dueDate": r_due,
                "completed": (r_idx % 2 == 0),
                "status": "DONE" if (r_idx % 2 == 0) else "TODO"
            })

        for n_idx in range(1, 16):
            noteid = note_id_seq
            note_id_seq += 1
            ncust = firm_customers[(n_idx * 3) % len(firm_customers)]

            notes.append({
                "id": noteid,
                "firmId": fid,
                "customerId": ncust["id"],
                "title": f"Credit & Delivery Terms Note - {ncust['name']}",
                "content": f"Customer agreed to 30 days payment cycle. Contact person mobile: {ncust['phone']}.",
                "tags": "credit_terms,wholesale,preferred"
            })

        # -------------------------------------------------------------
        # 3.12 BUSINESS LETTERS (5 per firm = 50 total)
        # -------------------------------------------------------------
        for l_idx in range(1, 6):
            letterid = letter_id_seq
            letter_id_seq += 1
            letter_num = f"LTR-{prefix}-2026-{l_idx:04d}"
            l_date = (date(2026, 3, 1) + timedelta(days=l_idx * 5)).strftime("%Y-%m-%d")

            business_letters.append({
                "id": letterid,
                "firmId": fid,
                "letterNumber": letter_num,
                "letterDate": l_date,
                "senderType": "FIRM",
                "senderName": firm["ownerName"],
                "senderCompany": fname,
                "senderAddress": faddr,
                "senderPhone": fphone
            })

        # -------------------------------------------------------------
        # 3.13 NOTIFICATIONS (10 per firm = 100 total) & PREFERENCE (1 per firm = 10 total)
        # -------------------------------------------------------------
        for n_idx in range(1, 11):
            notifid = notif_id_seq
            notif_id_seq += 1

            notifications.append({
                "id": notifid,
                "firmId": fid,
                "eventKey": f"NOTIF_{fid}_{n_idx:03d}",
                "category": "BILLING" if n_idx <= 4 else ("STOCK" if n_idx <= 7 else "SYSTEM"),
                "priority": "HIGH" if n_idx <= 3 else "NORMAL",
                "title": f"System Alert #{n_idx} for {fname}",
                "message": f"Verified operation #{n_idx} processed successfully for {fname}."
            })

        notification_preferences.append({
            "id": fid,
            "firmId": fid,
            "enabled": True,
            "bellEnabled": True,
            "inboxEnabled": True,
            "licensingEnabled": True,
            "billingEnabled": True
        })

    # Prepare Firms payload
    firms_payload = []
    for f in FIRMS:
        firms_payload.append({
            "id": f["id"],
            "firmName": f["firmName"],
            "ownerName": f["ownerName"],
            "addressLine1": f["addressLine1"],
            "addressLine2": f["addressLine2"],
            "city": f["city"],
            "state": f["state"],
            "pincode": f["pincode"],
            "phone": f["phone"],
            "email": f["email"],
            "gstin": f["gstin"],
            "bankName": f["bankName"],
            "bankAccount": f["bankAccount"],
            "bankIfsc": f["bankIfsc"],
            "upiId": f["upiId"],
            "footerNote": f"Thank you for choosing {f['firmName']}. Goods once sold will not be taken back without valid receipt.",
            "invoicePrintTheme": "MODERN",
            "invoicePrintThemeColor": f["themeColor"],
            "invoicePrintFormat": "A4"
        })

    # Construct complete BackupDTO payload
    dataset = {
        "metadata": {
            "version": "2.0",
            "type": "FULL_SYSTEM_BACKUP",
            "exportDate": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
            "backupSourceId": "rupeecrm_test_data_10_firms",
            "sourceFirmId": 1,
            "originalFirmName": FIRMS[0]["firmName"],
            "totalFirms": len(FIRMS),
            "generatedBy": "RupeeCRM Comprehensive Test Data Generator v1.0"
        },
        "allFirms": firms_payload,
        "firmDetails": firms_payload[0],
        "customers": customers,
        "products": products,
        "invoices": invoices,
        "invoicePayments": invoice_payments,
        "parties": parties,
        "purchaseOrders": purchase_orders,
        "partyPayments": party_payments,
        "salesReturns": sales_returns,
        "stockMovements": stock_movements,
        "employees": employees,
        "attendanceRecords": attendance_records,
        "salaryRecords": salary_records,
        "leaveRecords": leave_records,
        "advances": advances,
        "promotions": promotions,
        "goals": goals,
        "savings": savings,
        "goalLogs": goal_logs,
        "expenses": expenses,
        "reminders": reminders,
        "notes": notes,
        "businessLetters": business_letters,
        "notifications": notifications,
        "notificationPreferences": notification_preferences
    }

    # Summary report
    print("\n" + "="*60)
    print("DATASET GENERATION SUMMARY")
    print("="*60)
    print(f"Firms:                   {len(firms_payload):,}")
    print(f"Customers:               {len(customers):,}")
    print(f"Suppliers (Parties):     {len(parties):,}")
    print(f"Products:                {len(products):,}")
    print(f"Invoices:                {len(invoices):,}")
    total_inv_items = sum(len(inv["items"]) for inv in invoices)
    print(f"Invoice Line Items:      {total_inv_items:,}")
    print(f"Invoice Payments:        {len(invoice_payments):,}")
    print(f"Purchase Orders:         {len(purchase_orders):,}")
    total_po_items = sum(len(po["items"]) for po in purchase_orders)
    print(f"Purchase Line Items:     {total_po_items:,}")
    print(f"Supplier Party Payments: {len(party_payments):,}")
    print(f"Sales Returns:           {len(sales_returns):,}")
    total_ret_items = sum(len(ret["items"]) for ret in sales_returns)
    print(f"Sales Return Items:      {total_ret_items:,}")
    print(f"Stock Movements:         {len(stock_movements):,}")
    print(f"Employees:               {len(employees):,}")
    print(f"Attendance Records:      {len(attendance_records):,}")
    print(f"Salary Records:          {len(salary_records):,}")
    print(f"Leave Records:           {len(leave_records):,}")
    print(f"Employee Advances:       {len(advances):,}")
    print(f"Promotion Records:       {len(promotions):,}")
    print(f"Goals:                   {len(goals):,}")
    print(f"Savings Records:         {len(savings):,}")
    print(f"Goal Logs:               {len(goal_logs):,}")
    print(f"Expenses:                {len(expenses):,}")
    print(f"Reminders:               {len(reminders):,}")
    print(f"Notes:                   {len(notes):,}")
    print(f"Business Letters:        {len(business_letters):,}")
    print(f"Notifications:           {len(notifications):,}")
    print(f"Notification Prefs:      {len(notification_preferences):,}")
    print("="*60)

    out_file = "/Users/afk/Documents/GitHub/Simple-Billing/RupeeCRM_10_Firms_Comprehensive_Test_Data.json"
    print(f"Writing JSON dataset to: {out_file}...")
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(dataset, f, indent=2)

    file_size_mb = os.path.getsize(out_file) / (1024 * 1024)
    print(f"SUCCESS: Dataset file generated successfully ({file_size_mb:.2f} MB).")
    return out_file

if __name__ == "__main__":
    build_dataset()
