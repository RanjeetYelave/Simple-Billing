#!/usr/bin/env python3
"""
RupeeCRM Demo Mode Predefined 3-Firm Dataset Generator
------------------------------------------------------
Generates a deterministic, realistic, interconnected 3-firm demo dataset
100% compliant with BackupDTO and RupeeCRM entity schemas.
"""

import os
import sys
import json
import random
from datetime import datetime, date, timedelta
from decimal import Decimal, ROUND_HALF_UP

random.seed(10101)

def round_curr(val):
    return float(Decimal(str(val)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))

def round_qty(val):
    return float(Decimal(str(val)).quantize(Decimal("0.001"), rounding=ROUND_HALF_UP))

FIRMS = [
    {
        "id": 1,
        "firmName": "Apex Plumbing Solutions",
        "ownerName": "Rajesh Patel",
        "addressLine1": "Plot 42, GIDC Industrial Estate, Phase 2",
        "addressLine2": "Vatva",
        "city": "Ahmedabad",
        "state": "Gujarat",
        "pincode": "382445",
        "phone": "+91 98250 11223",
        "email": "sales@apexplumbing.demo",
        "gstin": "24AAACA1234A1Z5",
        "bankName": "State Bank of India",
        "bankAccount": "30192837461",
        "bankIfsc": "SBIN0001245",
        "upiId": "apexplumbing@sbi",
        "prefix": "APS",
        "pos": "24-Gujarat",
        "themeColor": "#0284c7",
        "categories": [
            "PVC Pipes & Fittings", "CPVC Pipes & Fittings", "Brass Ball Valves",
            "Sanitaryware & Faucets", "Adhesives & Sealants"
        ],
        "hsn": "3917",
        "parties": [
            ("Supreme Industries Ltd", "Ramesh Verma", "9825100001", "supreme@demo.com", "24AAACS1111S1Z1", "Mumbai, Maharashtra"),
            ("Astral Pipes Ltd", "Kirit Joshi", "9825100002", "astral@demo.com", "24AAACA2222A1Z2", "Ahmedabad, Gujarat"),
            ("Ashirvad Pipes Ltd", "Deepak Nair", "9825100003", "ashirvad@demo.com", "24AAACA3333A1Z3", "Bengaluru, Karnataka"),
            ("Finolex Industries Ltd", "Suresh Kulkarni", "9825100004", "finolex@demo.com", "24AAACF4444F1Z4", "Pune, Maharashtra"),
            ("Prince Pipes & Fittings", "Amit Shah", "9825100005", "prince@demo.com", "24AAACP5555P1Z5", "Mumbai, Maharashtra"),
        ],
        "customers": [
            ("Shreeji Sanitary & Hardware", "Mukesh Dave", "9825200001", "shreeji@demo.com", "24AABCS1111A1Z1", "Vatva, Ahmedabad", 15000.0),
            ("Maruti Plumbing Contractors", "Pravin Solanki", "9825200002", "maruti.contract@demo.com", "24AABCM2222B1Z2", "Maninagar, Ahmedabad", 28000.0),
            ("Krishna Pipe Fittings", "Girish Patel", "9825200003", "krishna.pipes@demo.com", "24AABCK3333C1Z3", "Naroda, Ahmedabad", 8500.0),
            ("Swastik Bath Concept", "Jignesh Vora", "9825200004", "swastik.bath@demo.com", "24AABCS4444D1Z4", "Satellite, Ahmedabad", 42000.0),
            ("Shivam Plumbing Store", "Bhavin Trivedi", "9825200005", "shivam.store@demo.com", "", "Bopal, Ahmedabad", 5200.0),
            ("Gayatri Hardware Mart", "Hitesh Prajapati", "9825200006", "gayatri.mart@demo.com", "", "Chandkheda, Ahmedabad", 12000.0),
            ("Jay Ambe Sanitaryware", "Mahesh Parmar", "9825200007", "jayambe@demo.com", "24AABCJ5555E1Z5", "Nikol, Ahmedabad", 0.0),
            ("Radhe Developers & Builders", "Vikram Rathod", "9825200008", "radhe.dev@demo.com", "24AABCR6666F1Z6", "S.G. Highway, Ahmedabad", 65000.0),
            ("Om Infrastructure Projects", "Ashok Desai", "9825200009", "om.infra@demo.com", "24AABCO7777G1Z7", "Sanand, Ahmedabad", 34000.0),
            ("Siddhi Vinayak Sanitary", "Chetan Raval", "9825200010", "siddhivinayak@demo.com", "", "Ghatlodia, Ahmedabad", 4500.0),
            ("Royal Plumbers Hub", "Nilesh Panchal", "9825200011", "royal.hub@demo.com", "", "Thaltej, Ahmedabad", 9800.0),
            ("Shakti Hardware & Pipes", "Kamlesh Chaudhary", "9825200012", "shakti.pipes@demo.com", "24AABCS8888H1Z8", "Odhav, Ahmedabad", 18500.0),
            ("Ambica Sanitary Agencies", "Pradeep Barot", "9825200013", "ambica.agency@demo.com", "24AABCA9999I1Z9", "Asarwa, Ahmedabad", 7600.0),
            ("Modern Home Solutions", "Sunil Makwana", "9825200014", "modern.home@demo.com", "", "Vastrapur, Ahmedabad", 11200.0)
        ],
        "products": [
            ("PVC Pipe 20mm (3m)", "PVC Pipes & Fittings", "mtr", 85.0, 62.0, 18.0, 250),
            ("PVC Pipe 25mm (3m)", "PVC Pipes & Fittings", "mtr", 120.0, 88.0, 18.0, 320),
            ("PVC Pipe 32mm (3m)", "PVC Pipes & Fittings", "mtr", 165.0, 122.0, 18.0, 180),
            ("PVC Pipe 40mm (3m)", "PVC Pipes & Fittings", "mtr", 210.0, 155.0, 18.0, 140),
            ("PVC Pipe 50mm (3m)", "PVC Pipes & Fittings", "mtr", 280.0, 210.0, 18.0, 95),
            ("PVC Pipe 63mm (6m)", "PVC Pipes & Fittings", "mtr", 420.0, 315.0, 18.0, 60),
            ("PVC Pipe 75mm (6m)", "PVC Pipes & Fittings", "mtr", 550.0, 410.0, 18.0, 45),
            ("PVC Pipe 90mm (6m)", "PVC Pipes & Fittings", "mtr", 720.0, 540.0, 18.0, 35),
            ("PVC Pipe 110mm Drainage (6m)", "PVC Pipes & Fittings", "mtr", 890.0, 670.0, 18.0, 50),
            ("PVC Pipe 160mm Heavy (6m)", "PVC Pipes & Fittings", "mtr", 1450.0, 1100.0, 18.0, 18),
            ("PVC Coupler 20mm", "PVC Pipes & Fittings", "pcs", 18.0, 12.0, 18.0, 600),
            ("PVC Coupler 25mm", "PVC Pipes & Fittings", "pcs", 24.0, 16.0, 18.0, 550),
            ("PVC Coupler 32mm", "PVC Pipes & Fittings", "pcs", 35.0, 24.0, 18.0, 300),
            ("PVC Coupler 40mm", "PVC Pipes & Fittings", "pcs", 48.0, 33.0, 18.0, 220),
            ("PVC Coupler 50mm", "PVC Pipes & Fittings", "pcs", 65.0, 46.0, 18.0, 180),
            ("PVC Elbow 90D 20mm", "PVC Pipes & Fittings", "pcs", 22.0, 15.0, 18.0, 500),
            ("PVC Elbow 90D 25mm", "PVC Pipes & Fittings", "pcs", 30.0, 20.0, 18.0, 450),
            ("PVC Elbow 90D 32mm", "PVC Pipes & Fittings", "pcs", 42.0, 29.0, 18.0, 320),
            ("PVC Elbow 90D 40mm", "PVC Pipes & Fittings", "pcs", 58.0, 41.0, 18.0, 200),
            ("PVC Elbow 90D 50mm", "PVC Pipes & Fittings", "pcs", 82.0, 58.0, 18.0, 140),
            ("PVC Tee 20mm", "PVC Pipes & Fittings", "pcs", 28.0, 19.0, 18.0, 400),
            ("PVC Tee 25mm", "PVC Pipes & Fittings", "pcs", 38.0, 26.0, 18.0, 380),
            ("PVC Tee 32mm", "PVC Pipes & Fittings", "pcs", 55.0, 38.0, 18.0, 250),
            ("PVC Tee 40mm", "PVC Pipes & Fittings", "pcs", 75.0, 52.0, 18.0, 160),
            ("PVC Tee 50mm", "PVC Pipes & Fittings", "pcs", 105.0, 75.0, 18.0, 110),
            ("CPVC Pipe SDR11 15mm (3m)", "CPVC Pipes & Fittings", "mtr", 145.0, 108.0, 18.0, 300),
            ("CPVC Pipe SDR11 20mm (3m)", "CPVC Pipes & Fittings", "mtr", 215.0, 162.0, 18.0, 350),
            ("CPVC Pipe SDR11 25mm (3m)", "CPVC Pipes & Fittings", "mtr", 310.0, 235.0, 18.0, 220),
            ("CPVC Pipe SDR11 32mm (3m)", "CPVC Pipes & Fittings", "mtr", 460.0, 350.0, 18.0, 120),
            ("CPVC Pipe SDR11 40mm (3m)", "CPVC Pipes & Fittings", "mtr", 620.0, 475.0, 18.0, 85),
            ("CPVC Elbow 90D 20mm", "CPVC Pipes & Fittings", "pcs", 32.0, 22.0, 18.0, 400),
            ("CPVC Elbow 90D 25mm", "CPVC Pipes & Fittings", "pcs", 48.0, 34.0, 18.0, 320),
            ("CPVC Tee 20mm", "CPVC Pipes & Fittings", "pcs", 42.0, 29.0, 18.0, 350),
            ("CPVC Tee 25mm", "CPVC Pipes & Fittings", "pcs", 62.0, 44.0, 18.0, 280),
            ("Brass Ball Valve 15mm Screwed", "Brass Ball Valves", "pcs", 280.0, 205.0, 18.0, 110),
            ("Brass Ball Valve 20mm Screwed", "Brass Ball Valves", "pcs", 390.0, 290.0, 18.0, 95),
            ("Brass Ball Valve 25mm Screwed", "Brass Ball Valves", "pcs", 560.0, 415.0, 18.0, 75),
            ("Brass Ball Valve 32mm Heavy", "Brass Ball Valves", "pcs", 880.0, 660.0, 18.0, 40),
            ("Brass Ball Valve 40mm Heavy", "Brass Ball Valves", "pcs", 1250.0, 940.0, 18.0, 28),
            ("Brass Ball Valve 50mm Heavy", "Brass Ball Valves", "pcs", 1850.0, 1400.0, 18.0, 15),
            ("PVC Ball Valve 25mm Compact", "Brass Ball Valves", "pcs", 95.0, 68.0, 18.0, 180),
            ("PVC Ball Valve 32mm Compact", "Brass Ball Valves", "pcs", 145.0, 105.0, 18.0, 120),
            ("Brass Angle Valve Chrome 15mm", "Sanitaryware & Faucets", "pcs", 320.0, 235.0, 18.0, 150),
            ("Brass Bib Cock Two-Way 15mm", "Sanitaryware & Faucets", "pcs", 450.0, 330.0, 18.0, 110),
            ("Stainless Steel Waste Coupling 32mm", "Sanitaryware & Faucets", "pcs", 125.0, 88.0, 18.0, 240),
            ("CPVC Solvent Cement 250ml Tin", "Adhesives & Sealants", "pcs", 180.0, 130.0, 18.0, 140),
            ("CPVC Solvent Cement 500ml Tin", "Adhesives & Sealants", "pcs", 320.0, 235.0, 18.0, 95),
            ("PVC Solvent Cement 500ml Tin", "Adhesives & Sealants", "pcs", 240.0, 175.0, 18.0, 120),
            ("PTFE Teflon Thread Tape 12mm x 10m (Box of 10)", "Adhesives & Sealants", "box", 190.0, 135.0, 18.0, 85),
            ("Silicone Sanitary Sealant Clear 300ml", "Adhesives & Sealants", "pcs", 260.0, 190.0, 18.0, 65)
        ],
        "employees": [
            ("Ketan Bhatt", "Sales Manager", "Sales", "9825300001", "ketan@apexplumbing.demo", 45000.0),
            ("Dinesh Rathva", "Warehouse Supervisor", "Inventory", "9825300002", "dinesh@apexplumbing.demo", 28000.0),
            ("Pooja Shah", "Billing Accountant", "Accounts", "9825300003", "pooja@apexplumbing.demo", 26000.0)
        ],
        "goals": [
            ("Q4 APVC & CPVC Sales Target", "SALES", 2500000.0, 1845000.0, "2026-10-01", "2026-12-31"),
            ("Collect Outstanding Builder Dues", "FINANCIAL", 800000.0, 520000.0, "2026-11-01", "2026-11-30"),
            ("Onboard 10 New Sanitary Retailers", "OPERATIONAL", 10.0, 7.0, "2026-10-15", "2026-12-15")
        ]
    },
    {
        "id": 2,
        "firmName": "Bright Electrical Traders",
        "ownerName": "Sanjay Shah",
        "addressLine1": "Shop 12-14, Laxmi Market, Station Road",
        "addressLine2": "Varachha",
        "city": "Surat",
        "state": "Gujarat",
        "pincode": "395003",
        "phone": "+91 98241 55667",
        "email": "contact@brightelectrical.demo",
        "gstin": "24AABCB5678B1Z2",
        "bankName": "HDFC Bank",
        "bankAccount": "50200039281746",
        "bankIfsc": "HDFC0000456",
        "upiId": "brightelectrical@hdfcbank",
        "prefix": "BET",
        "pos": "24-Gujarat",
        "themeColor": "#f59e0b",
        "categories": [
            "Wires & Cables", "Modular Switches & Sockets", "Switchgear & MCBs",
            "LED Lighting & Fixtures", "Conduit & Electrical Accessories"
        ],
        "hsn": "8544",
        "parties": [
            ("Polycab India Ltd", "Manish Mehta", "9824200001", "polycab@demo.com", "24AAACP6666M1Z1", "Vadodara, Gujarat"),
            ("Havells India Ltd", "Sunil Arora", "9824200002", "havells@demo.com", "24AAACH7777S1Z2", "Noida, Uttar Pradesh"),
            ("Legrand India Pvt Ltd", "Vivek Deshmukh", "9824200003", "legrand@demo.com", "24AAACL8888V1Z3", "Mumbai, Maharashtra"),
            ("Panasonic Anchor India", "Prakash Jain", "9824200004", "anchor@demo.com", "24AAACA9999P1Z4", "Daman, Daman & Diu"),
            ("Philips Lighting India", "Rohan Sen", "9824200005", "philips@demo.com", "24AAACP0000R1Z5", "Gurugram, Haryana"),
        ],
        "customers": [
            ("Shree Ram Electricals", "Vinod Kothari", "9824300001", "shreeram.elec@demo.com", "24AABCK1212A1Z1", "Varachha, Surat", 24000.0),
            ("Galaxy Light & Power", "Naveen Agrawal", "9824300002", "galaxy.light@demo.com", "24AABCA2323B1Z2", "Rander, Surat", 36000.0),
            ("Diamond City Electrician Hub", "Ghanshyam Patel", "9824300003", "diamond.hub@demo.com", "", "Katargam, Surat", 8200.0),
            ("Surat Powerline Contractors", "Haresh Gondaliya", "9824300004", "powerline@demo.com", "24AABCG3434C1Z3", "Udhna, Surat", 54000.0),
            ("Modern Switch World", "Jayanti Savani", "9824300005", "switchworld@demo.com", "24AABCS4545D1Z4", "Adajan, Surat", 18500.0),
            ("Khodiyar Electrical Works", "Mansukh Balar", "9824300006", "khodiyar.elec@demo.com", "", "Amroli, Surat", 4900.0),
            ("Laxmi Electric Store", "Raju Chovatiya", "9824300007", "laxmi.store@demo.com", "24AABCC5656E1Z5", "Gopipura, Surat", 12300.0),
            ("Bright Home Interiors", "Alpesh Vaghani", "9824300008", "brighthome@demo.com", "24AABCV6767F1Z6", "Vesu, Surat", 41000.0),
            ("Navkar Electrical Agency", "Pankaj Shah", "9824300009", "navkar.agency@demo.com", "24AABCS7878G1Z7", "Bhatar, Surat", 22000.0),
            ("Tapti Commercial Builders", "Mitesh Dudhat", "9824300010", "tapti.build@demo.com", "24AABCD8989H1Z8", "Piplod, Surat", 68000.0),
            ("Vardhman Electricals", "Shailesh Goti", "9824300011", "vardhman.elec@demo.com", "", "Nanpura, Surat", 6700.0),
            ("Krishna Power Solutions", "Tushar Kanani", "9824300012", "krishna.power@demo.com", "24AABCK9090I1Z9", "Sarthana, Surat", 15400.0),
            ("Ashirwad Light House", "Vijay Malani", "9824300013", "ashirwad.light@demo.com", "", "Kapodra, Surat", 7800.0),
            ("Cityline Electrical Hardware", "Ashwin Kakadiya", "9824300014", "cityline.elec@demo.com", "24AABCK0101J1Z0", "Dindoli, Surat", 19200.0)
        ],
        "products": [
            ("Copper Wire 1.0 sq mm Red 90m Coil", "Wires & Cables", "box", 1450.0, 1150.0, 18.0, 80),
            ("Copper Wire 1.0 sq mm Black 90m Coil", "Wires & Cables", "box", 1450.0, 1150.0, 18.0, 65),
            ("Copper Wire 1.5 sq mm Red 90m Coil", "Wires & Cables", "box", 2100.0, 1680.0, 18.0, 120),
            ("Copper Wire 1.5 sq mm Black 90m Coil", "Wires & Cables", "box", 2100.0, 1680.0, 18.0, 95),
            ("Copper Wire 1.5 sq mm Green Earth 90m", "Wires & Cables", "box", 2100.0, 1680.0, 18.0, 75),
            ("Copper Wire 2.5 sq mm Red 90m Coil", "Wires & Cables", "box", 3350.0, 2700.0, 18.0, 90),
            ("Copper Wire 2.5 sq mm Blue 90m Coil", "Wires & Cables", "box", 3350.0, 2700.0, 18.0, 70),
            ("Copper Wire 4.0 sq mm Yellow 90m Coil", "Wires & Cables", "box", 5200.0, 4200.0, 18.0, 45),
            ("Copper Wire 6.0 sq mm Red 90m Coil", "Wires & Cables", "box", 7600.0, 6150.0, 18.0, 25),
            ("3-Core Flat Submersible Cable 2.5 sq mm", "Wires & Cables", "mtr", 92.0, 74.0, 18.0, 400),
            ("3-Core Flat Submersible Cable 4.0 sq mm", "Wires & Cables", "mtr", 145.0, 116.0, 18.0, 300),
            ("Modular 1-Way Switch 6A White", "Modular Switches & Sockets", "pcs", 42.0, 28.0, 18.0, 500),
            ("Modular 2-Way Switch 6A White", "Modular Switches & Sockets", "pcs", 65.0, 45.0, 18.0, 220),
            ("Modular 1-Way Switch 16A Power", "Modular Switches & Sockets", "pcs", 88.0, 60.0, 18.0, 350),
            ("Modular Bell Push Switch 6A with Indicator", "Modular Switches & Sockets", "pcs", 95.0, 65.0, 18.0, 140),
            ("Modular 3-Pin Socket 6A White", "Modular Switches & Sockets", "pcs", 68.0, 46.0, 18.0, 380),
            ("Modular Combined Socket 6A/16A Power", "Modular Switches & Sockets", "pcs", 135.0, 92.0, 18.0, 280),
            ("Modular 4-Step Fan Regulator 100W", "Modular Switches & Sockets", "pcs", 240.0, 165.0, 18.0, 160),
            ("Modular 2-Module Cover Plate White", "Modular Switches & Sockets", "pcs", 55.0, 36.0, 18.0, 300),
            ("Modular 4-Module Cover Plate White", "Modular Switches & Sockets", "pcs", 85.0, 56.0, 18.0, 240),
            ("Modular 8-Module Cover Plate White", "Modular Switches & Sockets", "pcs", 145.0, 98.0, 18.0, 180),
            ("SP MCB 6A C-Curve 10kA", "Switchgear & MCBs", "pcs", 175.0, 122.0, 18.0, 150),
            ("SP MCB 10A C-Curve 10kA", "Switchgear & MCBs", "pcs", 175.0, 122.0, 18.0, 180),
            ("SP MCB 16A C-Curve 10kA", "Switchgear & MCBs", "pcs", 175.0, 122.0, 18.0, 220),
            ("SP MCB 20A C-Curve 10kA", "Switchgear & MCBs", "pcs", 185.0, 128.0, 18.0, 160),
            ("SP MCB 25A C-Curve 10kA", "Switchgear & MCBs", "pcs", 185.0, 128.0, 18.0, 140),
            ("SP MCB 32A C-Curve 10kA", "Switchgear & MCBs", "pcs", 195.0, 135.0, 18.0, 190),
            ("DP MCB 32A Main Switch", "Switchgear & MCBs", "pcs", 450.0, 315.0, 18.0, 75),
            ("DP MCB 40A Main Switch", "Switchgear & MCBs", "pcs", 480.0, 335.0, 18.0, 65),
            ("DP MCB 63A Main Switch", "Switchgear & MCBs", "pcs", 580.0, 410.0, 18.0, 50),
            ("RCCB 40A 30mA Double Pole", "Switchgear & MCBs", "pcs", 1850.0, 1380.0, 18.0, 28),
            ("4-Way SPN Distribution Board Enclosure", "Switchgear & MCBs", "pcs", 620.0, 440.0, 18.0, 40),
            ("8-Way SPN Distribution Board Enclosure", "Switchgear & MCBs", "pcs", 890.0, 640.0, 18.0, 35),
            ("12-Way SPN Distribution Board Enclosure", "Switchgear & MCBs", "pcs", 1250.0, 910.0, 18.0, 22),
            ("LED Batten Tube Light 20W 4ft Cool White", "LED Lighting & Fixtures", "pcs", 260.0, 180.0, 18.0, 140),
            ("LED Batten Tube Light 22W 4ft Warm White", "LED Lighting & Fixtures", "pcs", 280.0, 195.0, 18.0, 95),
            ("LED Recessed Downlight 7W Round White", "LED Lighting & Fixtures", "pcs", 195.0, 135.0, 18.0, 180),
            ("LED Recessed Downlight 12W Round White", "LED Lighting & Fixtures", "pcs", 290.0, 205.0, 18.0, 130),
            ("LED Recessed Downlight 18W Square White", "LED Lighting & Fixtures", "pcs", 420.0, 295.0, 18.0, 85),
            ("LED Surface Panel Light 12W Round", "LED Lighting & Fixtures", "pcs", 340.0, 240.0, 18.0, 70),
            ("LED Flood Light 50W Outdoor IP65", "LED Lighting & Fixtures", "pcs", 1150.0, 820.0, 18.0, 30),
            ("Ceiling Fan 1200mm 3-Blade White 50W", "LED Lighting & Fixtures", "pcs", 1950.0, 1480.0, 18.0, 35),
            ("PVC Conduit Pipe 20mm Medium (3m)", "Conduit & Electrical Accessories", "pcs", 58.0, 40.0, 18.0, 350),
            ("PVC Conduit Pipe 25mm Heavy (3m)", "Conduit & Electrical Accessories", "pcs", 82.0, 58.0, 18.0, 260),
            ("PVC Conduit Junction Box 20mm 4-Way", "Conduit & Electrical Accessories", "pcs", 22.0, 15.0, 18.0, 400),
            ("PVC Electrical Insulation Tape 18mm Black (Pack 5)", "Conduit & Electrical Accessories", "box", 95.0, 65.0, 18.0, 120),
            ("PVC Electrical Insulation Tape Assorted Colors", "Conduit & Electrical Accessories", "box", 95.0, 65.0, 18.0, 100),
            ("Ceiling Rose 6A 2-Plate Heavy", "Conduit & Electrical Accessories", "pcs", 32.0, 22.0, 18.0, 280),
            ("Batten Lamp Holder Angle 6A Brass Plunger", "Conduit & Electrical Accessories", "pcs", 36.0, 24.0, 18.0, 250),
            ("Pendant Lamp Holder 6A Brass Plunger", "Conduit & Electrical Accessories", "pcs", 34.0, 23.0, 18.0, 220)
        ],
        "employees": [
            ("Hardik Pandya", "Showroom Supervisor", "Sales", "9824400001", "hardik@brightelectrical.demo", 38000.0),
            ("Nirav Ghael", "Sales Executive", "Sales", "9824400002", "nirav@brightelectrical.demo", 25000.0),
            ("Chirag Vashi", "Field Technical Support", "Technical", "9824400003", "chirag@brightelectrical.demo", 24000.0)
        ],
        "goals": [
            ("Wires & Cable Volume Expansion", "SALES", 3000000.0, 2150000.0, "2026-10-01", "2026-12-31"),
            ("Surat Builder Lighting Packages", "OPERATIONAL", 15.0, 9.0, "2026-11-01", "2026-12-31"),
            ("Zero Payment Default Collections", "FINANCIAL", 1200000.0, 980000.0, "2026-11-01", "2026-11-30")
        ]
    },
    {
        "id": 3,
        "firmName": "Mehta Industrial Supplies",
        "ownerName": "Arvind Mehta",
        "addressLine1": "Shed No. 7, Shreenath Industrial Park",
        "addressLine2": "Changodar",
        "city": "Ahmedabad",
        "state": "Gujarat",
        "pincode": "382213",
        "phone": "+91 98252 88990",
        "email": "orders@mehtaindustrial.demo",
        "gstin": "24AACCM9012C1Z8",
        "bankName": "ICICI Bank",
        "bankAccount": "018205001928",
        "bankIfsc": "ICIC0000182",
        "upiId": "mehtaindustrial@icici",
        "prefix": "MIS",
        "pos": "24-Gujarat",
        "themeColor": "#10b981",
        "categories": [
            "Fasteners & Fixings", "Abrasives & Cutting Tools", "Safety & PPE Equipment",
            "Welding Supplies & Consumables", "Hand & Power Tools"
        ],
        "hsn": "7318",
        "parties": [
            ("Unbrako Fasteners India", "Kailash Agarwal", "9825300001", "unbrako@demo.com", "24AAACU1234K1Z1", "Ludhiana, Punjab"),
            ("Bosch Power Tools India", "Rajnish Bansal", "9825300002", "bosch.tools@demo.com", "24AAACB2345R1Z2", "Bengaluru, Karnataka"),
            ("ESAB India Welding", "Vijay Pillai", "9825300003", "esab@demo.com", "24AAACE3456V1Z3", "Chennai, Tamil Nadu"),
            ("3M India Industrial Safety", "Aditya Sen", "9825300004", "3msafety@demo.com", "24AAACT4567A1Z4", "Bengaluru, Karnataka"),
            ("Saint-Gobain Norton Abrasives", "Sridhar Iyer", "9825300005", "norton@demo.com", "24AAACS5678S1Z5", "Nagpur, Maharashtra"),
        ],
        "customers": [
            ("Gujarat Heavy Fabrication Works", "Kishore Solanki", "9825400001", "gujheavy@demo.com", "24AABCG1313A1Z1", "Changodar, Ahmedabad", 58000.0),
            ("Sardar Engineering Enterprises", "Jayesh Panchal", "9825400002", "sardareng@demo.com", "24AABCS2424B1Z2", "Sanand, Ahmedabad", 32000.0),
            ("Panchal Machine Tools", "Dhirubhai Panchal", "9825400003", "panchal.mach@demo.com", "24AABCP3535C1Z3", "Odhav, Ahmedabad", 19500.0),
            ("Apex Auto Components", "Hemant Chauhan", "9825400004", "apexauto@demo.com", "24AABCA4646D1Z4", "Kathwada, Ahmedabad", 45000.0),
            ("Maruti Press Works", "Natwarlal Prajapati", "9825400005", "marutipress@demo.com", "", "Naroda, Ahmedabad", 11400.0),
            ("Shree Ram Fabricators", "Ramesh Bharwad", "9825400006", "shreeram.fab@demo.com", "", "Bavla, Ahmedabad", 8700.0),
            ("Changodar Precision Works", "Ashwin Vaghela", "9825400007", "changodar.prec@demo.com", "24AABCC5757E1Z5", "Changodar, Ahmedabad", 26500.0),
            ("Balaji Boiler & Piping Co.", "Sunil Rajput", "9825400008", "balaji.boiler@demo.com", "24AABCB6868F1Z6", "Vatva, Ahmedabad", 74000.0),
            ("National Industrial Spares", "Mukund Pandya", "9825400009", "national.spares@demo.com", "24AABCN7979G1Z7", "Asarwa, Ahmedabad", 16800.0),
            ("Falcon Engineering Solutions", "Pratik Soni", "9825400010", "falcon.eng@demo.com", "24AABCF8080H1Z8", "Moraiya, Ahmedabad", 38000.0),
            ("Khodiyar Welding Workshop", "Jitendra Parmar", "9825400011", "khodiyar.weld@demo.com", "", "Dholka, Ahmedabad", 6200.0),
            ("Royal Metal Fabricators", "Bharat Makwana", "9825400012", "royal.metal@demo.com", "24AABCR9191I1Z9", "Sarkhej, Ahmedabad", 22500.0)
        ],
        "products": [
            ("Hex Bolt Grade 8.8 M8x25 Zinc Plated (Pack 50)", "Fasteners & Fixings", "box", 380.0, 275.0, 18.0, 95),
            ("Hex Bolt Grade 8.8 M8x40 Zinc Plated (Pack 50)", "Fasteners & Fixings", "box", 460.0, 335.0, 18.0, 80),
            ("Hex Bolt Grade 8.8 M10x35 Zinc Plated (Pack 25)", "Fasteners & Fixings", "box", 420.0, 305.0, 18.0, 110),
            ("Hex Bolt Grade 8.8 M10x50 Zinc Plated (Pack 25)", "Fasteners & Fixings", "box", 540.0, 395.0, 18.0, 85),
            ("Hex Bolt Grade 8.8 M12x45 Zinc Plated (Pack 20)", "Fasteners & Fixings", "box", 620.0, 455.0, 18.0, 70),
            ("Hex Bolt Grade 8.8 M12x60 Zinc Plated (Pack 20)", "Fasteners & Fixings", "box", 740.0, 545.0, 18.0, 60),
            ("Hex Bolt Grade 8.8 M16x50 Heavy (Pack 10)", "Fasteners & Fixings", "box", 680.0, 495.0, 18.0, 50),
            ("Hex Bolt Grade 8.8 M16x75 Heavy (Pack 10)", "Fasteners & Fixings", "box", 890.0, 655.0, 18.0, 40),
            ("Hex Nut Grade 8 M8 Zinc (Pack 100)", "Fasteners & Fixings", "box", 240.0, 170.0, 18.0, 140),
            ("Hex Nut Grade 8 M10 Zinc (Pack 100)", "Fasteners & Fixings", "box", 380.0, 275.0, 18.0, 120),
            ("Hex Nut Grade 8 M12 Zinc (Pack 50)", "Fasteners & Fixings", "box", 320.0, 230.0, 18.0, 110),
            ("Hex Nut Grade 8 M16 Zinc (Pack 25)", "Fasteners & Fixings", "box", 360.0, 260.0, 18.0, 85),
            ("Flat Plain Washer M8 (Pack 100)", "Fasteners & Fixings", "box", 120.0, 80.0, 18.0, 180),
            ("Flat Plain Washer M10 (Pack 100)", "Fasteners & Fixings", "box", 180.0, 125.0, 18.0, 150),
            ("Flat Plain Washer M12 (Pack 100)", "Fasteners & Fixings", "box", 260.0, 185.0, 18.0, 120),
            ("Spring Washer M10 High Tensile (Pack 100)", "Fasteners & Fixings", "box", 160.0, 110.0, 18.0, 140),
            ("Spring Washer M12 High Tensile (Pack 100)", "Fasteners & Fixings", "box", 220.0, 155.0, 18.0, 110),
            ("SS304 Hex Bolt M8x30 Stainless Steel (Pack 25)", "Fasteners & Fixings", "box", 490.0, 360.0, 18.0, 65),
            ("SS304 Hex Bolt M10x40 Stainless Steel (Pack 20)", "Fasteners & Fixings", "box", 650.0, 480.0, 18.0, 50),
            ("Grinding Wheel 100x6x16mm (4 inch)", "Abrasives & Cutting Tools", "pcs", 48.0, 32.0, 18.0, 350),
            ("Grinding Wheel 180x6x22mm (7 inch)", "Abrasives & Cutting Tools", "pcs", 145.0, 102.0, 18.0, 140),
            ("Cutting Off Wheel 105x1.2x16mm Thin (4 inch)", "Abrasives & Cutting Tools", "pcs", 28.0, 18.0, 18.0, 600),
            ("Cutting Off Wheel 355x3x25.4mm Chop Saw (14 inch)", "Abrasives & Cutting Tools", "pcs", 195.0, 140.0, 18.0, 95),
            ("Flap Disc Zirconia 100mm Grit 60", "Abrasives & Cutting Tools", "pcs", 65.0, 44.0, 18.0, 220),
            ("Flap Disc Zirconia 100mm Grit 80", "Abrasives & Cutting Tools", "pcs", 65.0, 44.0, 18.0, 180),
            ("Flap Disc Zirconia 100mm Grit 120", "Abrasives & Cutting Tools", "pcs", 65.0, 44.0, 18.0, 160),
            ("HSS Jobber Drill Bit Set 1mm-13mm (25 pcs)", "Abrasives & Cutting Tools", "set", 1850.0, 1350.0, 18.0, 25),
            ("HSS Drill Bit 6mm Industrial Grade", "Abrasives & Cutting Tools", "pcs", 55.0, 38.0, 18.0, 180),
            ("HSS Drill Bit 8mm Industrial Grade", "Abrasives & Cutting Tools", "pcs", 85.0, 60.0, 18.0, 140),
            ("HSS Drill Bit 10mm Industrial Grade", "Abrasives & Cutting Tools", "pcs", 135.0, 95.0, 18.0, 110),
            ("HSS Drill Bit 12mm Industrial Grade", "Abrasives & Cutting Tools", "pcs", 195.0, 140.0, 18.0, 80),
            ("Industrial Safety Helmet Yellow Ratchet Type", "Safety & PPE Equipment", "pcs", 195.0, 138.0, 18.0, 120),
            ("Industrial Safety Helmet White Ratchet Type", "Safety & PPE Equipment", "pcs", 210.0, 148.0, 18.0, 90),
            ("Safety Goggles Clear Anti-Fog Polycarbonate", "Safety & PPE Equipment", "pcs", 85.0, 58.0, 18.0, 200),
            ("Heavy Duty Split Leather Welding Gloves 14 inch", "Safety & PPE Equipment", "pair", 240.0, 170.0, 18.0, 150),
            ("Kevlar Cut-Resistant Work Gloves Level 5", "Safety & PPE Equipment", "pair", 280.0, 195.0, 18.0, 110),
            ("Steel Toe Industrial Safety Shoes Size 8", "Safety & PPE Equipment", "pair", 1150.0, 840.0, 18.0, 35),
            ("Steel Toe Industrial Safety Shoes Size 9", "Safety & PPE Equipment", "pair", 1150.0, 840.0, 18.0, 30),
            ("Welding Electrode E6013 2.5mm (2kg Packet)", "Welding Supplies & Consumables", "box", 380.0, 275.0, 18.0, 80),
            ("Welding Electrode E6013 3.15mm (5kg Box)", "Welding Supplies & Consumables", "box", 820.0, 610.0, 18.0, 95),
            ("Welding Electrode E7018 3.15mm Low Hydrogen (5kg)", "Welding Supplies & Consumables", "box", 1150.0, 860.0, 18.0, 60),
            ("Welding Cable 35 sq mm Flexible Copper (mtr)", "Welding Supplies & Consumables", "mtr", 285.0, 220.0, 18.0, 150),
            ("Electrode Holder 400A Heavy Duty Brass Jaw", "Welding Supplies & Consumables", "pcs", 420.0, 305.0, 18.0, 65),
            ("Earth Clamp 300A Heavy Duty Spring Type", "Welding Supplies & Consumables", "pcs", 260.0, 185.0, 18.0, 75),
            ("Welding Chipping Hammer with Spring Handle", "Welding Supplies & Consumables", "pcs", 145.0, 100.0, 18.0, 85),
            ("Stainless Steel Wire Scratch Brush 4-Row", "Welding Supplies & Consumables", "pcs", 65.0, 44.0, 18.0, 140),
            ("Adjustable Wrench 10 inch Chrome Vanadium", "Hand & Power Tools", "pcs", 380.0, 270.0, 18.0, 55),
            ("Adjustable Wrench 12 inch Chrome Vanadium", "Hand & Power Tools", "pcs", 520.0, 375.0, 18.0, 40),
            ("Combination Pliers 8 inch Insulated 1000V", "Hand & Power Tools", "pcs", 280.0, 195.0, 18.0, 80),
            ("Machinist Ball Peen Hammer 500g Fiberglass Handle", "Hand & Power Tools", "pcs", 340.0, 240.0, 18.0, 50)
        ],
        "employees": [
            ("Pravin Mistry", "Operations Incharge", "Operations", "9825500001", "pravin@mehtaindustrial.demo", 36000.0),
            ("Kalpesh Prajapati", "Dispatch Clerk", "Logistics", "9825500002", "kalpesh@mehtaindustrial.demo", 24000.0)
        ],
        "goals": [
            ("Changodar Fastener Volume Tie-ups", "SALES", 2800000.0, 2050000.0, "2026-10-01", "2026-12-31"),
            ("Industrial PPE Distribution Expansion", "OPERATIONAL", 12.0, 8.0, "2026-11-01", "2026-12-31"),
            ("Collection Cycle Reduction under 30 Days", "FINANCIAL", 1500000.0, 1180000.0, "2026-11-01", "2026-11-30")
        ]
    }
]

def build_demo_dataset():
    print("Generating RupeeCRM Predefined 3-Firm Demo Dataset...")

    all_customers = []
    all_parties = []
    all_products = []
    all_invoices = []
    all_invoice_payments = []
    all_purchase_orders = []
    all_party_payments = []
    all_sales_returns = []
    all_stock_movements = []
    all_employees = []
    all_attendance_records = []
    all_salary_records = []
    all_leave_records = []
    all_advances = []
    all_promotions = []
    all_goals = []
    all_savings = []
    all_goal_logs = []
    all_expenses = []
    all_reminders = []
    all_notes = []
    all_business_letters = []
    all_notifications = []
    all_notification_preferences = []

    global_cust_id = 1
    global_party_id = 1
    global_prod_id = 1
    global_inv_id = 1
    global_inv_pay_id = 1
    global_po_id = 1
    global_party_pay_id = 1
    global_sr_id = 1
    global_sm_id = 1
    global_emp_id = 1
    global_att_id = 1
    global_sal_id = 1
    global_goal_id = 1
    global_goal_log_id = 1
    global_exp_id = 1
    global_rem_id = 1
    global_note_id = 1
    global_let_id = 1
    global_notif_id = 1

    firms_payload = []

    EXPENSE_CATEGORIES = [
        ("Warehouse Electricity Bill", "Utilities", 8500.0),
        ("Monthly Godown Rent", "Rent", 25000.0),
        ("Transport & Freight Charges", "Logistics", 4200.0),
        ("Packaging Materials & Boxes", "Operations", 3100.0),
        ("Internet & Telephone Charges", "Office", 1850.0),
        ("Printing, Stationery & Paper", "Office", 1450.0),
        ("Tea, Refreshments & Water", "Pantry", 2200.0),
        ("Equipment Maintenance & Repair", "Repairs", 3800.0),
    ]

    for f_idx, firm_def in enumerate(FIRMS):
        fid = firm_def["id"]
        fname = firm_def["firmName"]
        prefix = firm_def["prefix"]
        print(f"\nProcessing Firm {fid}: {fname}...")

        firms_payload.append({
            "id": fid,
            "firmName": fname,
            "ownerName": firm_def["ownerName"],
            "addressLine1": firm_def["addressLine1"],
            "addressLine2": firm_def["addressLine2"],
            "city": firm_def["city"],
            "state": firm_def["state"],
            "pincode": firm_def["pincode"],
            "phone": firm_def["phone"],
            "email": firm_def["email"],
            "gstin": firm_def["gstin"],
            "bankName": firm_def["bankName"],
            "bankAccount": firm_def["bankAccount"],
            "bankIfsc": firm_def["bankIfsc"],
            "upiId": firm_def["upiId"],
            "footerNote": f"Thank you for choosing {fname}. All disputes are subject to local jurisdiction.",
            "invoicePrintTheme": "MODERN",
            "invoicePrintThemeColor": firm_def["themeColor"],
            "invoicePrintFormat": "A4"
        })

        # 1. Suppliers (Parties) - 5 per firm = 15 total
        firm_parties = []
        for p_name, contact, phone, email, gstin, city in firm_def["parties"]:
            party_obj = {
                "id": global_party_id,
                "firmId": fid,
                "name": p_name,
                "contactPerson": contact,
                "phone": phone,
                "email": email,
                "gstin": gstin,
                "address": city,
                "openingBalance": round_curr(random.uniform(5000.0, 25000.0)),
                "currentBalance": round_curr(random.uniform(15000.0, 60000.0)),
                "createdAt": "2026-08-01T10:00:00"
            }
            firm_parties.append(party_obj)
            all_parties.append(party_obj)
            global_party_id += 1

        # 2. Customers - 14, 14, 12 per firm = 40 total
        firm_customers = []
        for c_name, contact, phone, email, gstin, addr, op_bal in firm_def["customers"]:
            cust_obj = {
                "id": global_cust_id,
                "firmId": fid,
                "name": c_name,
                "contactPerson": contact,
                "phone": phone,
                "email": email,
                "gstin": gstin,
                "address": addr,
                "openingBalance": round_curr(op_bal),
                "currentBalance": round_curr(op_bal),
                "creditLimit": 100000.0,
                "createdAt": "2026-08-10T10:00:00"
            }
            firm_customers.append(cust_obj)
            all_customers.append(cust_obj)
            global_cust_id += 1

        # 3. Products - 50 per firm = 150 total (Matching Product.java fields!)
        firm_products = []
        for p_name, cat, unit, sell_p, buy_p, gst, st_qty in firm_def["products"]:
            prod_obj = {
                "id": global_prod_id,
                "version": 0,
                "firmId": fid,
                "name": p_name,
                "category": cat,
                "unit": unit,
                "price": round_curr(sell_p),
                "costPrice": round_curr(buy_p),
                "gstPercentage": round_curr(gst),
                "hsnCode": firm_def["hsn"],
                "stockQuantity": round_qty(st_qty),
                "minStockLevel": 20.0,
                "itemType": "GOODS",
                "inInventory": True,
                "isArchived": False,
                "createdAt": "2026-08-15T10:00:00"
            }
            firm_products.append(prod_obj)
            all_products.append(prod_obj)
            global_prod_id += 1

        # 4. Purchases (Purchase Orders) - 7, 7, 6 = 20 total
        num_purchases = 7 if fid in (1, 2) else 6
        base_date = date(2026, 9, 1)
        for po_idx in range(num_purchases):
            po_date = base_date + timedelta(days=po_idx * 11 + (fid * 3))
            supplier = firm_parties[po_idx % len(firm_parties)]
            po_num = f"{prefix}-PO-{2026001 + po_idx}"

            po_items = []
            subtotal = Decimal("0.00")
            tax_total = Decimal("0.00")
            for item_idx in range(3):
                prod = firm_products[(po_idx * 3 + item_idx) % len(firm_products)]
                qty = Decimal(str(random.randint(15, 60)))
                rate = Decimal(str(prod["costPrice"]))
                tax_rate = Decimal(str(prod["gstPercentage"]))
                line_sub = (qty * rate).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
                line_tax = (line_sub * tax_rate / Decimal("100.00")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
                line_tot = line_sub + line_tax

                po_items.append({
                    "productId": prod["id"],
                    "productName": prod["name"],
                    "quantity": float(qty),
                    "unit": prod["unit"],
                    "unitPrice": float(rate),
                    "taxableAmount": float(line_sub),
                    "gstPercent": float(tax_rate),
                    "gstAmount": float(line_tax),
                    "totalAmount": float(line_tot),
                    "hsnCode": prod["hsnCode"]
                })
                subtotal += line_sub
                tax_total += line_tax

            total_amt = subtotal + tax_total
            paid_amt = total_amt if po_idx % 2 == 0 else (total_amt * Decimal("0.6")).quantize(Decimal("0.01"))
            pay_status = "PAID" if paid_amt >= total_amt else ("PARTIAL" if paid_amt > 0 else "YET_TO_PAY")

            po_obj = {
                "id": global_po_id,
                "version": 0,
                "firmId": fid,
                "poNumber": po_num,
                "poDate": po_date.strftime("%Y-%m-%d"),
                "expectedDeliveryDate": (po_date + timedelta(days=7)).strftime("%Y-%m-%d"),
                "party": {"id": supplier["id"]},
                "partyName": supplier["name"],
                "partyPhone": supplier["phone"],
                "partyEmail": supplier["email"],
                "partyGstin": supplier["gstin"],
                "partyAddress": supplier["address"],
                "status": "RECEIVED",
                "paymentStatus": pay_status,
                "paymentMethod": "Bank Transfer",
                "subtotalWithoutTax": float(subtotal),
                "totalGstAmount": float(tax_total),
                "totalAmount": float(total_amt),
                "paidAmount": float(paid_amt),
                "items": po_items
            }
            all_purchase_orders.append(po_obj)

            # Party payment record if paid > 0 (20 total)
            if paid_amt > 0:
                all_party_payments.append({
                    "id": global_party_pay_id,
                    "firmId": fid,
                    "partyId": supplier["id"],
                    "purchaseOrderId": global_po_id,
                    "amount": float(paid_amt),
                    "paymentDate": (po_date + timedelta(days=2)).strftime("%Y-%m-%d"),
                    "paymentMode": "BANK_TRANSFER" if po_idx % 2 == 0 else "UPI",
                    "referenceNumber": f"TXN-PO-{global_po_id}",
                    "notes": f"Payment against PO {po_num}"
                })
                global_party_pay_id += 1

            global_po_id += 1

        # 5. Invoices - 15 per firm = 45 total
        inv_base_date = date(2026, 9, 15)
        firm_invoices = []
        for inv_idx in range(15):
            inv_date = inv_base_date + timedelta(days=inv_idx * 4 + (fid * 2))
            inv_dt_str = datetime(inv_date.year, inv_date.month, inv_date.day, 10, 0, 0).strftime("%Y-%m-%dT%H:%M:%S")
            customer = firm_customers[inv_idx % len(firm_customers)]
            inv_num = f"{prefix}-INV-{2026001 + inv_idx}"

            num_items = 4 if inv_idx % 2 == 0 else 3
            inv_items = []
            subtotal = Decimal("0.00")
            tax_total = Decimal("0.00")
            cgst_total = Decimal("0.00")
            sgst_total = Decimal("0.00")

            for itm_i in range(num_items):
                prod = firm_products[(inv_idx * 3 + itm_i) % len(firm_products)]
                qty = Decimal(str(random.randint(2, 18)))
                rate = Decimal(str(prod["price"]))
                tax_rate = Decimal(str(prod["gstPercentage"]))
                line_sub = (qty * rate).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
                line_tax = (line_sub * tax_rate / Decimal("100.00")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
                cgst_line = (line_tax / Decimal("2.0")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
                sgst_line = line_tax - cgst_line
                line_tot = line_sub + line_tax

                inv_items.append({
                    "product": {"id": prod["id"]},
                    "productName": prod["name"],
                    "qty": float(qty),
                    "unit": prod["unit"],
                    "hsnCode": prod["hsnCode"],
                    "pricePerUnit": float(rate),
                    "amountWithoutTax": float(line_sub),
                    "taxableAmount": float(line_sub),
                    "gstPercent": float(tax_rate),
                    "gstAmount": float(line_tax),
                    "cgstAmount": float(cgst_line),
                    "sgstAmount": float(sgst_line),
                    "igstAmount": 0.0,
                    "lineTotal": float(line_tot)
                })
                subtotal += line_sub
                tax_total += line_tax
                cgst_total += cgst_line
                sgst_total += sgst_line

                # Stock movement for invoice item
                if len(all_stock_movements) < 75:
                    prev_stk = float(prod["stockQuantity"])
                    qty_chg = -float(qty)
                    new_stk = prev_stk + qty_chg
                    all_stock_movements.append({
                        "id": global_sm_id,
                        "firmId": fid,
                        "productId": prod["id"],
                        "productName": prod["name"],
                        "movementType": "INVOICE_SALE",
                        "quantityChange": qty_chg,
                        "previousStock": prev_stk,
                        "newStock": new_stk,
                        "referenceType": "INVOICE",
                        "referenceId": inv_num,
                        "note": f"Sale via Invoice {inv_num}",
                        "createdAt": inv_dt_str
                    })
                    global_sm_id += 1

            total_amt = subtotal + tax_total

            # Status distribution
            if inv_idx < 10:
                paid_amt = total_amt
                status = "PAID"
                is_paid = True
            elif inv_idx < 13:
                paid_amt = (total_amt * Decimal("0.50")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
                status = "PARTIALLY_PAID"
                is_paid = False
            else:
                paid_amt = Decimal("0.00")
                status = "UNPAID"
                is_paid = False

            inv_obj = {
                "id": global_inv_id,
                "version": 0,
                "firmId": fid,
                "invoiceNumber": inv_num,
                "invoiceDate": inv_dt_str,
                "dueDate": (inv_date + timedelta(days=30)).strftime("%Y-%m-%d"),
                "status": status,
                "paid": is_paid,
                "customer": {"id": customer["id"]},
                "customerNameSnapshot": customer["name"],
                "customerPhoneSnapshot": customer["phone"],
                "customerAddressSnapshot": customer["address"],
                "customerGstinSnapshot": customer.get("gstin"),
                "firmNameSnapshot": fname,
                "firmGstinSnapshot": firm_def["gstin"],
                "firmAddressSnapshot": f"{firm_def['addressLine1']}, {firm_def['city']}",
                "firmPhoneSnapshot": firm_def["phone"],
                "firmEmailSnapshot": firm_def["email"],
                "placeOfSupply": firm_def["pos"],
                "subtotalWithoutTax": float(subtotal),
                "totalTax": float(tax_total),
                "cgstAmount": float(cgst_total),
                "sgstAmount": float(sgst_total),
                "igstAmount": 0.0,
                "totalDiscount": 0.0,
                "roundOff": 0.0,
                "totalAmount": float(total_amt),
                "paymentMethod": "UPI" if inv_idx % 2 == 0 else "Bank Transfer",
                "currency": "INR",
                "items": inv_items
            }
            all_invoices.append(inv_obj)
            firm_invoices.append(inv_obj)

            # Record InvoicePayment if paid > 0 (total ~39 payments)
            if paid_amt > 0:
                all_invoice_payments.append({
                    "id": global_inv_pay_id,
                    "firmId": fid,
                    "invoiceId": global_inv_id,
                    "customerId": customer["id"],
                    "amount": float(paid_amt),
                    "paymentDate": (inv_date + timedelta(days=1)).strftime("%Y-%m-%d"),
                    "paymentMode": "CASH" if inv_idx % 3 == 0 else ("UPI" if inv_idx % 3 == 1 else "BANK_TRANSFER"),
                    "referenceNumber": f"PAY-{inv_num}",
                    "notes": f"Payment against Invoice {inv_num}"
                })
                global_inv_pay_id += 1

            global_inv_id += 1

        # 6. Sales Returns (5 total across 3 firms: 2, 2, 1)
        sr_indices = (2, 7) if fid in (1, 2) else (4,)
        for sr_i in sr_indices:
            orig_inv = firm_invoices[sr_i]
            ret_item = orig_inv["items"][0]
            ret_qty = 1.0
            ret_rate = ret_item["pricePerUnit"]
            ret_sub = round_curr(ret_qty * ret_rate)
            ret_tax = round_curr(ret_sub * (ret_item["gstPercent"] / 100.0))
            ret_cgst = round_curr(ret_tax / 2.0)
            ret_sgst = round_curr(ret_tax - ret_cgst)
            ret_tot = round_curr(ret_sub + ret_tax)

            all_sales_returns.append({
                "id": global_sr_id,
                "firmId": fid,
                "returnNumber": f"{prefix}-SR-{2026001 + global_sr_id}",
                "returnDate": (datetime.strptime(orig_inv["invoiceDate"][:10], "%Y-%m-%d").date() + timedelta(days=3)).strftime("%Y-%m-%d"),
                "invoice": {"id": orig_inv["id"]},
                "customer": {"id": orig_inv["customer"]["id"]},
                "customerNameSnapshot": orig_inv["customerNameSnapshot"],
                "reason": "Excess material returned by customer after installation",
                "refundMode": "CUSTOMER_CREDIT",
                "subtotal": ret_sub,
                "taxAmount": ret_tax,
                "cgstAmount": ret_cgst,
                "sgstAmount": ret_sgst,
                "igstAmount": 0.0,
                "totalRefundAmount": ret_tot,
                "notes": f"Authorized sales return against {orig_inv['invoiceNumber']}",
                "items": [{
                    "productId": ret_item["product"]["id"],
                    "productName": ret_item["productName"],
                    "unit": ret_item["unit"],
                    "hsnCode": ret_item["hsnCode"],
                    "returnQty": ret_qty,
                    "unitPrice": ret_rate,
                    "gstPercent": ret_item["gstPercent"],
                    "gstAmount": ret_tax,
                    "cgstAmount": ret_cgst,
                    "sgstAmount": ret_sgst,
                    "igstAmount": 0.0,
                    "refundTotal": ret_tot
                }]
            })
            global_sr_id += 1

        # 7. Expenses - 7, 7, 6 = 20 total
        num_exp = 7 if fid in (1, 2) else 6
        for exp_i in range(num_exp):
            exp_title, exp_cat, exp_amt = EXPENSE_CATEGORIES[exp_i % len(EXPENSE_CATEGORIES)]
            exp_date = date(2026, 10, 1) + timedelta(days=exp_i * 8 + fid)
            all_expenses.append({
                "id": global_exp_id,
                "firmId": fid,
                "title": exp_title,
                "category": exp_cat,
                "amount": exp_amt,
                "expenseDate": exp_date.strftime("%Y-%m-%d"),
                "paymentMode": "BANK_TRANSFER" if exp_amt > 5000.0 else "CASH",
                "notes": f"Monthly recurring business expense - {exp_title}",
                "createdAt": exp_date.strftime("%Y-%m-%dT10:00:00")
            })
            global_exp_id += 1

        # 8. Employees - 3, 3, 2 = 8 total
        for emp_name, emp_role, emp_dept, emp_phone, emp_email, emp_sal in firm_def["employees"]:
            all_employees.append({
                "id": global_emp_id,
                "firmId": fid,
                "name": emp_name,
                "role": emp_role,
                "department": emp_dept,
                "phone": emp_phone,
                "email": emp_email,
                "salary": emp_sal,
                "joiningDate": "2026-06-01",
                "status": "ACTIVE",
                "createdAt": "2026-06-01T09:00:00"
            })
            all_salary_records.append({
                "id": global_sal_id,
                "firmId": fid,
                "employee": {"id": global_emp_id},
                "monthYear": "10-2026",
                "baseSalaryAtTime": float(emp_sal),
                "daysAbsent": 0,
                "paidLeavesUsed": 0,
                "unpaidLeaves": 0,
                "leaveDeductionAmount": 0.0,
                "bonusAmount": 0.0,
                "advanceDeducted": 0.0,
                "netPaid": float(emp_sal),
                "paymentDate": "2026-11-01",
                "createdAt": "2026-11-01T10:00:00"
            })
            global_sal_id += 1
            global_emp_id += 1

        # 9. Goals - 3 per firm = 9 total
        for g_title, g_type, target, cur_val, st_date, end_date in firm_def["goals"]:
            mapped_g_type = "SAVINGS_TARGET" if g_type in ["SALES", "FINANCIAL"] else "LIFE_MILESTONE"
            all_goals.append({
                "id": global_goal_id,
                "firmId": fid,
                "title": g_title,
                "goalType": mapped_g_type,
                "targetValue": float(target),
                "currentValue": float(cur_val),
                "unit": "INR" if g_type in ["SALES", "FINANCIAL"] else "UNITS",
                "startDate": st_date,
                "targetDate": end_date,
                "status": "ACTIVE",
                "notes": f"Strategic operational goal for {fname}",
                "createdAt": f"{st_date}T09:00:00"
            })
            all_goal_logs.append({
                "id": global_goal_log_id,
                "firmId": fid,
                "goalId": global_goal_id,
                "actionType": "CHECK_IN",
                "deltaValue": float(cur_val),
                "resultingValue": float(cur_val),
                "logDate": "2026-11-15",
                "notes": "Mid-quarter milestone reviewed and logged",
                "createdAt": "2026-11-15T18:00:00"
            })
            global_goal_id += 1
            global_goal_log_id += 1

        # 10. Reminders, Notes, Business Letters, Notifications
        all_reminders.append({
            "id": global_rem_id,
            "firmId": fid,
            "title": f"GST GSTR-3B Return Filing for {fname}",
            "dueDate": "2026-11-20",
            "priority": "HIGH",
            "status": "PENDING",
            "createdAt": "2026-11-01T10:00:00"
        })
        global_rem_id += 1

        all_notes.append({
            "id": global_note_id,
            "firmId": fid,
            "title": f"Firm Standard Operating Guidelines",
            "content": f"Official sales terms, minimum invoice discount policy, and supplier return protocols for {fname}.",
            "tags": "policy,billing,inventory",
            "createdAt": "2026-08-01T10:00:00"
        })
        global_note_id += 1

        all_business_letters.append({
            "id": global_let_id,
            "firmId": fid,
            "letterNumber": f"LTR-2026-{global_let_id:04d}",
            "letterDate": "2026-09-01",
            "recipientName": firm_customers[0]["name"],
            "recipientAddress": firm_customers[0]["address"],
            "subject": f"Confirmation of Commercial Credit Line - {fname}",
            "content": f"We are pleased to extend a verified 30-day revolving credit line of ₹1,00,000 for your business orders with {fname}.",
            "createdAt": "2026-09-01T11:00:00"
        })
        global_let_id += 1

        all_notifications.append({
            "id": global_notif_id,
            "firmId": fid,
            "eventKey": f"demo-welcome-{fid}-{global_notif_id}",
            "title": f"Welcome to {fname} Demo Workspace",
            "body": "Feel free to browse catalogue products, create invoices, test inventory movements, and record customer payments.",
            "category": "BILLING",
            "priority": "NORMAL",
            "status": "UNREAD",
            "createdAt": "2026-11-20T10:00:00"
        })
        global_notif_id += 1

    # Fill remaining stock movements if under 75
    while len(all_stock_movements) < 75:
        p = all_products[len(all_stock_movements) % len(all_products)]
        prev_stk = float(p["stockQuantity"])
        qty_chg = 50.0
        new_stk = prev_stk + qty_chg
        all_stock_movements.append({
            "id": global_sm_id,
            "firmId": p["firmId"],
            "productId": p["id"],
            "productName": p["name"],
            "movementType": "PURCHASE_RECEIPT",
            "quantityChange": qty_chg,
            "previousStock": prev_stk,
            "newStock": new_stk,
            "referenceType": "PURCHASE_ORDER",
            "referenceId": f"PO-{global_sm_id}",
            "note": "Initial stock replenishment movement",
            "createdAt": "2026-09-05T10:00:00"
        })
        global_sm_id += 1

    # Final dataset structure
    dataset = {
        "metadata": {
            "version": "1.0-DEMO",
            "type": "FULL_SYSTEM_BACKUP",
            "isDemoDataset": True,
            "exportDate": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
            "backupSourceId": "rupeecrm_demo_mode_seed_dataset",
            "sourceFirmId": 1,
            "originalFirmName": FIRMS[0]["firmName"],
            "totalFirms": len(FIRMS),
            "generatedBy": "RupeeCRM Demo Mode Deterministic Seed Generator v1.0"
        },
        "allFirms": firms_payload,
        "firmDetails": firms_payload[0],
        "customers": all_customers,
        "products": all_products,
        "invoices": all_invoices,
        "invoicePayments": all_invoice_payments,
        "parties": all_parties,
        "purchaseOrders": all_purchase_orders,
        "partyPayments": all_party_payments,
        "salesReturns": all_sales_returns,
        "stockMovements": all_stock_movements,
        "employees": all_employees,
        "attendanceRecords": all_attendance_records,
        "salaryRecords": all_salary_records,
        "leaveRecords": all_leave_records,
        "advances": all_advances,
        "promotions": all_promotions,
        "goals": all_goals,
        "savings": all_savings,
        "goalLogs": all_goal_logs,
        "expenses": all_expenses,
        "reminders": all_reminders,
        "notes": all_notes,
        "businessLetters": all_business_letters,
        "notifications": all_notifications,
        "notificationPreferences": all_notification_preferences
    }

    out_file = "/Users/afk/Documents/GitHub/Simple-Billing/billsoft/src/main/resources/demo/demo_seed_dataset.json"
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(dataset, f, indent=2)

    print("\n" + "=" * 60)
    print("DEMO MODE DATASET GENERATION SUMMARY")
    print("=" * 60)
    print(f"Firms:                   {len(firms_payload)}")
    print(f"Customers:               {len(all_customers)}")
    print(f"Suppliers (Parties):     {len(all_parties)}")
    print(f"Products:                {len(all_products)}")
    print(f"Invoices:                {len(all_invoices)}")
    total_inv_items = sum(len(inv["items"]) for inv in all_invoices)
    print(f"Invoice Line Items:      {total_inv_items}")
    print(f"Invoice Payments:        {len(all_invoice_payments)}")
    print(f"Purchase Orders:         {len(all_purchase_orders)}")
    total_po_items = sum(len(po["items"]) for po in all_purchase_orders)
    print(f"Purchase Line Items:     {total_po_items}")
    print(f"Supplier Party Payments: {len(all_party_payments)}")
    print(f"Sales Returns:           {len(all_sales_returns)}")
    print(f"Stock Movements:         {len(all_stock_movements)}")
    print(f"Employees:               {len(all_employees)}")
    print(f"Salary Records:          {len(all_salary_records)}")
    print(f"Goals:                   {len(all_goals)}")
    print(f"Expenses:                {len(all_expenses)}")
    print(f"File:                    {out_file} ({os.path.getsize(out_file) / 1024:.1f} KB)")
    print("=" * 60)
    return out_file

if __name__ == "__main__":
    build_demo_dataset()
