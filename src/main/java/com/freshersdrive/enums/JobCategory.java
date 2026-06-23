package com.freshersdrive.enums;

/**
 * All recognised job categories for a scraped or manually-added drive.
 *
 * Naming convention
 * -----------------
 *  CORE_*   → core/non-IT engineering roles (power, mechanical, civil, etc.)
 *  BPO_*    → BPO / ITES / call-centre roles, split by process type
 *  Everything else is domain-level (IT_SOFTWARE, BANKING, GOVERNMENT, …)
 *
 * Adding a new constant here is all that's needed for the RSS scraper to
 * persist it correctly — resolveCategory() in DriveIngestionService uses
 * JobCategory.valueOf() so it picks up new constants automatically.
 */
public enum JobCategory {

    // ── IT / Software ──────────────────────────────────────────────────────
    IT_SOFTWARE,

    // ── Core Engineering ───────────────────────────────────────────────────
    CORE_POWER_ELECTRICAL,   // power systems, substations, SCADA, PLC — ABB, Hitachi, BHEL, Siemens
    CORE_MECHANICAL,         // manufacturing, CAD/CAM, CNC, thermal — Godrej, L&T, Cummins
    CORE_OIL_GAS_CHEMICAL,   // refineries, process engineering — IOCL, ONGC, GAIL, Reliance
    CORE_AEROSPACE_DEFENCE,  // avionics, propulsion, defence — HAL, DRDO, BEL, ISRO, Safran
    CORE_CIVIL,              // structural, infrastructure, construction
    CORE_ELECTRONICS,        // VLSI, embedded, PCB, instrumentation
    CORE_ENGINEERING,        // catch-all for core roles that don't fit a sub-domain above

    // ── BPO / ITES ─────────────────────────────────────────────────────────
    BPO_INTERNATIONAL_VOICE, // US/UK/night shift customer support
    BPO_DOMESTIC_VOICE,      // inbound/outbound, telecalling
    BPO_NON_VOICE,           // chat, email, back-office, data entry, medical coding
    BPO_GENERAL,             // BPO company match but process type not determinable

    // ── Other domains ──────────────────────────────────────────────────────
    GOVERNMENT,              // PSU, SSC, UPSC, railways, defence recruitment
    BANKING,                 // IBPS, SBI, RBI, private banks, NBFCs
    MANAGEMENT,              // MBA, operations, consulting
    INTERNSHIP,              // any internship regardless of degree/domain
    ARTS,                    // arts / humanities / commerce / science graduates

    // ── Fallback ───────────────────────────────────────────────────────────
    OTHERS
}