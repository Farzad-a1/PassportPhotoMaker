package com.passportphoto.worldwide.requirements

import java.util.Locale

data class Country(val code: String, val name: String) {
    val flag: String
        get() =
            code.map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }.joinToString("")
}

data class Requirement(
    val code: String,
    val document: String = "Passport",
    val widthMm: Float = 35f,
    val heightMm: Float = 45f,
    val minWidth: Int? = null,
    val minHeight: Int? = null,
    val background: String = "Not verified. Check your issuing authority.",
    val head: String = "Not verified",
    val positioning: String = "Face forward; check official guidance.",
    val print: String = "Not verified",
    val digital: String =
        "Digital application requirements are unverified. Use the original photo and consult your authority.",
    val source: String = "",
    val checked: String? = null,
    val verified: Boolean = false,
    val restriction: String = "",
    val formats: String = "Not verified",
    val originalOnly: Boolean = false,
)

object Requirements {
    val countries =
        Locale.getISOCountries()
            .map { Country(it, Locale("", it).getDisplayCountry(Locale.ENGLISH)) }
            .sortedBy { it.name }
    private val presets =
        listOf(
            Requirement(
                "US",
                widthMm = 50.8f,
                heightMm = 50.8f,
                background = "White or off-white, plain, without shadows",
                head = "Chin to top of head: 25–35 mm",
                print = "2 × 2 inches on matte or glossy photo paper; taken within 6 months",
                digital =
                    "Online renewal uses its own upload guidance. Export your original photo; do not manually crop for that route.",
                source = "https://travel.state.gov/en/passports/apply/help/photos.html",
                checked = "2026-11-03",
                verified = true,
                formats = "Printed photo; digital JPEG route must be checked",
                originalOnly = true,
            ),
            Requirement(
                "GB",
                minWidth = 600,
                minHeight = 750,
                background = "Plain light-coloured background",
                head = "Printed head measurements must be checked in printed-photo guidance",
                print =
                    "Printed route requires separate guidance; dimensions here are not a verified printed preset",
                digital =
                    "At least 600 × 750 pixels; 50 KB–10 MB. Include head, shoulders and upper body. Do not crop or alter the digital photo.",
                source = "https://www.gov.uk/photos-for-passports",
                checked = "2026-11-03",
                restriction =
                    "Only digital guidance verified. Printed dimensions are custom defaults, not an official preset.",
                formats = "Digital photograph; consult application portal",
                originalOnly = true,
            ),
            Requirement(
                "CA",
                widthMm = 50f,
                heightMm = 70f,
                background = "Plain white or light-coloured background",
                head = "Chin to crown: 31–36 mm",
                print =
                    "Two identical professional photographic prints. Commercial photographer required; no home printing. Photographer details and guarantor rules apply.",
                digital =
                    "Online renewal is a separate route; consult the official page. No edited photos.",
                source =
                    "https://www.canada.ca/en/immigration-refugees-citizenship/services/canadian-passports/photos.html",
                checked = "2026-11-03",
                verified = true,
                restriction =
                    "Use a commercial photographer. App exports do not replace the required professional service.",
                formats = "Professional printed photos; digital route separate",
                originalOnly = true,
            ),
            Requirement(
                "AU",
                background = "Plain white or light grey",
                head = "Chin to crown: 32–36 mm",
                print =
                    "Two prints, 35–40 mm wide × 45–50 mm high. Dye sublimation, glossy paper at least 200 gsm. Preset uses 35 × 45 mm.",
                source =
                    "https://www.passports.gov.au/passports-explained/how-apply/passport-photo-guidelines",
                checked = "2026-11-03",
                verified = true,
                restriction =
                    "Authority recommends professional providers and does not recommend mobile apps.",
                formats = "Printed photographs",
            ),
            Requirement(
                "FR",
                background = "Plain light-coloured background; white is prohibited",
                head = "Chin to crown: 32–36 mm",
                print = "35 × 45 mm; authorised photographer or approved booth required",
                source = "https://www.service-public.gouv.fr/particuliers/vosdroits/F10619?lang=fr",
                checked = "2026-11-03",
                verified = true,
                restriction =
                    "An authorised photographer or approved booth is required. This app is not an approved capture service.",
                formats = "Approved photographic print",
            ),
            Requirement(
                "DE",
                source = "https://stadt.muenchen.de/en-old/info/digital-passport-photos.html",
                checked = "2026-11-03",
                restriction =
                    "Domestic passports require certified digital capture and secure transmission since May 2025. App exports cannot provide this.",
                originalOnly = true,
            ),
            Requirement(
                "IN",
                source =
                    "https://portal4.passportindia.gov.in/Online/pdf/Guidelines_for_ICAO_Compliant_Photographs_for_Passport_Applications.pdf",
                restriction =
                    "Specifications vary by application route. Not verified; use custom dimensions only.",
            ),
            Requirement(
                "PK",
                source = "https://www.dgip.gov.pk/eServices/online-passport.php",
                checked = "2026-11-03",
                restriction =
                    "Online renewal and office capture have different workflows. Dimensions not verified.",
            ),
            Requirement(
                "BD",
                source = "https://www.epassport.gov.bd/landing/faqs/4",
                checked = "2026-11-03",
                restriction =
                    "Biometric enrolment rules vary by age and location. Dimensions not verified.",
            ),
            Requirement(
                "NG",
                source = "https://immigration.gov.ng/passports/",
                checked = "2026-11-03",
                restriction =
                    "Office biometric capture and online routes differ. Dimensions not verified.",
            ),
        )

    fun get(code: String, document: String): Requirement =
        if (document == "Passport") presets.find { it.code == code } ?: Requirement(code)
        else Requirement(code, document)

    fun country(code: String) =
        countries.firstOrNull { it.code == code } ?: Country("US", "United States")
}
