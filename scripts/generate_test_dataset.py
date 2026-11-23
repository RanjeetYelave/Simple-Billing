#!/usr/bin/env python3
"""
RupeeCRM 10-Firm Comprehensive Test Dataset Generator
------------------------------------------------------
Generates a realistic, relationally valid, interconnected 10-firm dataset
for RupeeCRM as an importable JSON file compatible with RupeeCRM's BackupDTO schema.
"""

import json
import random
import datetime
from decimal import Decimal, ROUND_HALF_UP

# Set deterministic seed
random.seed(42)

def round_curr(val):
    return float(Decimal(str(val)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))

def round_qty(val):
    return float(Decimal(str(val)).quantize(Decimal("0.001"), rounding=ROUND_HALF_UP))

# -------------------------------------------------------------
# 1. FIRMS CONFIGURATION (10 Diverse Fictional Indian Firms)
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
        ]
    }
]

print(f"Loaded {len(FIRMS)} firm configurations.")
