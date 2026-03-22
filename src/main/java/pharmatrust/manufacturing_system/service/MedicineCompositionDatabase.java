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

        DB.put("aspirin", new CompositionSpec(
            "Aspirin", "Acetylsalicylic acid (C9H8O4)",
            Arrays.asList("aspirin", "acetylsalicylic", "c9h8o4", "salicylate"),
            Arrays.asList("lead", "arsenic", "free salicylic acid"),
            Arrays.asList("75mg", "150mg", "325mg", "500mg"),
            Arrays.asList("ip", "bp", "usp"),
            99.0
        ));

        DB.put("ciprofloxacin", new CompositionSpec(
            "Ciprofloxacin", "Ciprofloxacin hydrochloride (C17H18FN3O3·HCl)",
            Arrays.asList("ciprofloxacin", "c17h18fn3o3", "fluoroquinolone", "hydrochloride"),
            Arrays.asList("lead", "arsenic", "endotoxin"),
            Arrays.asList("250mg", "500mg", "750mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.0
        ));

        DB.put("doxycycline", new CompositionSpec(
            "Doxycycline", "Doxycycline hyclate (C22H24N2O8·HCl)",
            Arrays.asList("doxycycline", "c22h24n2o8", "tetracycline", "hyclate"),
            Arrays.asList("lead", "arsenic", "endotoxin"),
            Arrays.asList("100mg", "200mg"),
            Arrays.asList("ip", "bp", "usp"),
            97.0
        ));

        DB.put("pantoprazole", new CompositionSpec(
            "Pantoprazole", "Pantoprazole sodium (C16H15F2N3O4S)",
            Arrays.asList("pantoprazole", "c16h15f2n3o4s", "proton pump", "benzimidazole"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("20mg", "40mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.0
        ));

        DB.put("amlodipine", new CompositionSpec(
            "Amlodipine", "Amlodipine besylate (C20H25ClN2O5)",
            Arrays.asList("amlodipine", "c20h25", "calcium channel", "besylate"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("2.5mg", "5mg", "10mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.5
        ));

        DB.put("losartan", new CompositionSpec(
            "Losartan", "Losartan potassium (C22H23ClKN6O)",
            Arrays.asList("losartan", "c22h23", "angiotensin", "potassium"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("25mg", "50mg", "100mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.0
        ));

        DB.put("metoprolol", new CompositionSpec(
            "Metoprolol", "Metoprolol succinate (C15H25NO3)",
            Arrays.asList("metoprolol", "c15h25no3", "beta blocker", "succinate"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("25mg", "50mg", "100mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.0
        ));

        DB.put("clopidogrel", new CompositionSpec(
            "Clopidogrel", "Clopidogrel bisulfate (C16H16ClNO2S)",
            Arrays.asList("clopidogrel", "c16h16", "thienopyridine", "bisulfate"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("75mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.0
        ));

        DB.put("insulin", new CompositionSpec(
            "Insulin", "Human Insulin (C257H383N65O77S6)",
            Arrays.asList("insulin", "c257h383", "polypeptide", "human insulin"),
            Arrays.asList("lead", "arsenic", "endotoxin", "pyrogen"),
            Arrays.asList("100iu", "40iu", "100 iu/ml"),
            Arrays.asList("ip", "bp", "usp", "pharmacopoeia"),
            99.0
        ));

        DB.put("ranitidine", new CompositionSpec(
            "Ranitidine", "Ranitidine hydrochloride (C13H22N4O3S·HCl)",
            Arrays.asList("ranitidine", "c13h22n4o3s", "h2 blocker", "hydrochloride"),
            Arrays.asList("lead", "arsenic", "ndma", "nitrosamine"),
            Arrays.asList("150mg", "300mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.0
        ));

        DB.put("levocetirizine", new CompositionSpec(
            "Levocetirizine", "Levocetirizine dihydrochloride (C21H25ClN2O3·2HCl)",
            Arrays.asList("levocetirizine", "c21h25", "antihistamine", "dihydrochloride"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("2.5mg", "5mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.5
        ));

        DB.put("montelukast", new CompositionSpec(
            "Montelukast", "Montelukast sodium (C35H35ClNNaO3S)",
            Arrays.asList("montelukast", "c35h35", "leukotriene", "sodium"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("4mg", "5mg", "10mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.0
        ));

        DB.put("salbutamol", new CompositionSpec(
            "Salbutamol", "Salbutamol sulfate (C13H21NO3·H2SO4)",
            Arrays.asList("salbutamol", "albuterol", "c13h21no3", "beta-2 agonist"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("2mg", "4mg", "100mcg", "200mcg"),
            Arrays.asList("ip", "bp", "usp"),
            98.0
        ));

        DB.put("prednisolone", new CompositionSpec(
            "Prednisolone", "Prednisolone (C21H28O5)",
            Arrays.asList("prednisolone", "c21h28o5", "corticosteroid", "glucocorticoid"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("5mg", "10mg", "20mg", "40mg"),
            Arrays.asList("ip", "bp", "usp"),
            97.0
        ));

        DB.put("dexamethasone", new CompositionSpec(
            "Dexamethasone", "Dexamethasone (C22H29FO5)",
            Arrays.asList("dexamethasone", "c22h29fo5", "corticosteroid", "fluorinated"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("0.5mg", "4mg", "8mg"),
            Arrays.asList("ip", "bp", "usp"),
            97.0
        ));

        DB.put("diclofenac", new CompositionSpec(
            "Diclofenac", "Diclofenac sodium (C14H11Cl2NO2·Na)",
            Arrays.asList("diclofenac", "c14h11cl2no2", "nsaid", "sodium"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("25mg", "50mg", "75mg", "100mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.5
        ));

        DB.put("tramadol", new CompositionSpec(
            "Tramadol", "Tramadol hydrochloride (C16H25NO2·HCl)",
            Arrays.asList("tramadol", "c16h25no2", "opioid", "hydrochloride"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("50mg", "100mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.0
        ));

        DB.put("ondansetron", new CompositionSpec(
            "Ondansetron", "Ondansetron hydrochloride (C18H19N3O·HCl)",
            Arrays.asList("ondansetron", "c18h19n3o", "5-ht3 antagonist", "antiemetic"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("4mg", "8mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.0
        ));

        DB.put("domperidone", new CompositionSpec(
            "Domperidone", "Domperidone (C22H24ClN5O2)",
            Arrays.asList("domperidone", "c22h24", "dopamine antagonist", "prokinetic"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("10mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.0
        ));

        DB.put("vitamin d3", new CompositionSpec(
            "Vitamin D3", "Cholecalciferol (C27H44O)",
            Arrays.asList("cholecalciferol", "vitamin d3", "c27h44o", "calciferol"),
            Arrays.asList("lead", "arsenic", "mercury"),
            Arrays.asList("1000iu", "2000iu", "60000iu", "400iu"),
            Arrays.asList("ip", "bp", "usp"),
            97.0
        ));

        DB.put("vitamin b12", new CompositionSpec(
            "Vitamin B12", "Cyanocobalamin (C63H88CoN14O14P)",
            Arrays.asList("cyanocobalamin", "vitamin b12", "c63h88", "cobalamin"),
            Arrays.asList("lead", "arsenic"),
            Arrays.asList("500mcg", "1000mcg", "1500mcg"),
            Arrays.asList("ip", "bp", "usp"),
            97.0
        ));

        DB.put("ferrous sulfate", new CompositionSpec(
            "Ferrous Sulfate", "Ferrous sulfate (FeSO4·7H2O)",
            Arrays.asList("ferrous sulfate", "feso4", "iron", "sulfate"),
            Arrays.asList("lead", "arsenic", "mercury"),
            Arrays.asList("200mg", "325mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.0
        ));

        DB.put("calcium carbonate", new CompositionSpec(
            "Calcium Carbonate", "Calcium carbonate (CaCO3)",
            Arrays.asList("calcium carbonate", "caco3", "calcium"),
            Arrays.asList("lead", "arsenic", "mercury"),
            Arrays.asList("500mg", "1000mg", "1250mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.5
        ));

        DB.put("zinc sulfate", new CompositionSpec(
            "Zinc Sulfate", "Zinc sulfate monohydrate (ZnSO4·H2O)",
            Arrays.asList("zinc sulfate", "znso4", "zinc"),
            Arrays.asList("lead", "arsenic", "cadmium"),
            Arrays.asList("20mg", "50mg"),
            Arrays.asList("ip", "bp", "usp"),
            98.0
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
