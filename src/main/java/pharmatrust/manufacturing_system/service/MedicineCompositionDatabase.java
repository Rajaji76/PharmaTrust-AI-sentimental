package pharmatrust.manufacturing_system.service;

import java.util.*;

/**
 * Built-in pharmaceutical composition knowledge base.
 * AI uses this as ground truth to verify uploaded lab reports.
 *
 * Each entry defines:
 *  - requiredIngredients: must ALL be present in the report
 *  - forbiddenSubstances: must NOT appear (contaminants/adulterants)
 *  - purityMin: minimum acceptable purity % mentioned in report
 *  - dosageKeywords: expected dosage markers
 *  - regulatoryStandards: pharmacopoeia references that should appear
 */
public class MedicineCompositionDatabase {

    public static class CompositionSpec {
        public final String medicineName;
        public final List<String> requiredIngredients;   // must all appear
        public final List<String> forbiddenSubstances;   // must not appear
        public final List<String> dosageKeywords;        // at least one must appear
        public final List<String> regulatoryStandards;   // bonus score if present
        public final double purityMin;                   // minimum purity % threshold
        public final String activeIngredient;

        public CompositionSpec(String medicineName, String activeIngredient,
                               List<String> requiredIngredients,
                               List<String> forbiddenSubstances,
                               List<String> dosageKeywords,
                               List<String> regulatoryStandards,
                               double purityMin) {
            this.medicineName = medicineName;
            this.activeIngredient = activeIngredient;
            this.requiredIngredients = requiredIngredients;
            this.forbiddenSubstances = forbiddenSubstances;
            this.dosageKeywords = dosageKeywords;
            this.regulatoryStandards = regulatoryStandards;
            this.purityMin = purityMin;
        }
    }

    // Ground-truth composition database
    private static final Map<String, CompositionSpec> DB = new LinkedHashMap<>();

    static {
        DB.put("paracetamol", new CompositionSpec(
            "Paracetamol", "Acetaminophen (C8H9NO2)",
            Arrays.asList("acetaminophen", "paracetamol", "c8h9no2", "4-acetamidophenol"),
            Arrays.asList("methanol", "lead", "arsenic", "mercury", "diethylene glycol"),
            Arrays.asList("500mg", "650mg", "1000mg", "325mg"),
            Arrays.asList("ip", "bp", "usp", "pharmacopoeia", "who"),
            98.0
        ));

        DB.put("ibuprofen", new CompositionSpec(
            "Ibuprofen", "Ibuprofen (C13H18O2)",
            Arrays.asList("ibuprofen", "c13h18o2", "2-(4-isobutylphenyl)"),
            Arrays.asList("lead", "arsenic", "mercury", "cadmium"),
            Arrays.asList("200mg", "400mg", "600mg", "800mg"),
            Arrays.asList("ip", "bp", "usp", "pharmacopoeia"),
            98.5
        ));

        DB.put("amoxicillin", new CompositionSpec(
            "Amoxicillin", "Amoxicillin trihydrate (C16H19N3O5S)",
            Arrays.asList("amoxicillin", "c16h19n3o5s", "beta-lactam", "trihydrate"),
            Arrays.asList("penicillinase", "lead", "arsenic", "endotoxin"),
            Arrays.asList("250mg", "500mg", "875mg"),
            Arrays.asList("ip", "bp", "usp", "pharmacopoeia"),
            97.5
        ));

        DB.put("metformin", new CompositionSpec(
            "Metformin", "Metformin hydrochloride (C4H11N5·HCl)",
            Arrays.asList("metformin", "c4h11n5", "biguanide", "hydrochloride"),
            Arrays.asList("lead", "arsenic", "ndma", "nitrosamine"),
            Arrays.asList("500mg", "850mg", "1000mg"),
            Arrays.asList("ip", "bp", "usp"),
            99.0
        ));

        DB.put("atorvastatin", new CompositionSpec(
            "Atorvastatin", "Atorvastatin calcium (C66H68CaF2N4O10)",
            Arrays.asList("atorvastatin", "calcium", "c66h68", "statin"),
            Arrays.asList("lead", "arsenic", "mercury"),
            Arrays.asList("10mg", "20mg", "40mg", "80mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.0
        ));

        DB.put("azithromycin", new CompositionSpec(
            "Azithromycin", "Azithromycin (C38H72N2O12)",
            Arrays.asList("azithromycin", "c38h72n2o12", "macrolide"),
            Arrays.asList("lead", "arsenic", "endotoxin"),
            Arrays.asList("250mg", "500mg"),
            Arrays.asList("ip", "bp", "usp"),
            97.0
        ));

        DB.put("omeprazole", new CompositionSpec(
            "Omeprazole", "Omeprazole (C17H19N3O3S)",
            Arrays.asList("omeprazole", "c17h19n3o3s", "proton pump", "benzimidazole"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("20mg", "40mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.0
        ));

        DB.put("cetirizine", new CompositionSpec(
            "Cetirizine", "Cetirizine hydrochloride (C21H25ClN2O3·HCl)",
            Arrays.asList("cetirizine", "c21h25", "antihistamine", "hydrochloride"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("5mg", "10mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.5
        ));
    }

    /**
     * Find composition spec for a medicine name (fuzzy match).
     * Returns null if not found (new/unknown medicine).
     */
    public static CompositionSpec find(String medicineName) {
        if (medicineName == null) return null;
        String lower = medicineName.toLowerCase().trim();

        // Exact key match
        if (DB.containsKey(lower)) return DB.get(lower);

        // Partial match — medicine name contains or is contained by a key
        for (Map.Entry<String, CompositionSpec> entry : DB.entrySet()) {
            if (lower.contains(entry.getKey()) || entry.getKey().contains(lower)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static Collection<CompositionSpec> all() {
        return DB.values();
    }
}
