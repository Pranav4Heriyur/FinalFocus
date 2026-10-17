package com.pranav.focus.data

object StudySeed {
    val PHYSICS = listOf(
        "Units and Measurements", "Motion in a Straight Line", "Motion in a Plane", "Laws of Motion", "Work, Energy and Power",
        "Rotational Motion", "Gravitation", "Mechanical Properties of Solids", "Mechanical Properties of Fluids",
        "Thermal Properties of Matter", "Thermodynamics", "Kinetic Theory", "Oscillations", "Waves",
        "Electric Charges and Fields", "Electrostatic Potential and Capacitance", "Current Electricity",
        "Moving Charges and Magnetism", "Magnetism and Matter", "Electromagnetic Induction", "Alternating Current",
        "Electromagnetic Waves", "Ray Optics", "Wave Optics", "Dual Nature of Radiation and Matter", "Atoms", "Nuclei",
        "Semiconductor Electronics",
    )
    val CHEMISTRY = listOf(
        "Some Basic Concepts of Chemistry", "Structure of Atom", "Classification of Elements and Periodicity",
        "Chemical Bonding and Molecular Structure", "Thermodynamics", "Equilibrium", "Redox Reactions",
        "Organic Chemistry: Basic Principles", "Hydrocarbons", "Solutions", "Electrochemistry", "Chemical Kinetics",
        "p-Block Elements", "d- and f-Block Elements", "Coordination Compounds", "Haloalkanes and Haloarenes",
        "Alcohols, Phenols and Ethers", "Aldehydes, Ketones and Carboxylic Acids", "Amines", "Biomolecules",
    )
    val BIOLOGY = listOf(
        "The Living World", "Biological Classification", "Plant Kingdom", "Animal Kingdom", "Morphology of Flowering Plants",
        "Anatomy of Flowering Plants", "Structural Organisation in Animals", "Cell: The Unit of Life", "Biomolecules",
        "Cell Cycle and Cell Division", "Photosynthesis", "Respiration in Plants", "Plant Growth and Development",
        "Breathing and Exchange of Gases", "Body Fluids and Circulation", "Excretory Products", "Locomotion and Movement",
        "Neural Control and Coordination", "Chemical Coordination", "Sexual Reproduction in Flowering Plants",
        "Human Reproduction", "Reproductive Health", "Principles of Inheritance and Variation", "Molecular Basis of Inheritance",
        "Evolution", "Human Health and Disease", "Microbes in Human Welfare", "Biotechnology: Principles",
        "Biotechnology: Applications", "Organisms and Populations", "Ecosystem", "Biodiversity and Conservation",
    )

    private val ZOOLOGY = setOf("Animal Kingdom", "Structural Organisation in Animals", "Breathing and Exchange of Gases", "Body Fluids and Circulation",
        "Excretory Products", "Locomotion and Movement", "Neural Control and Coordination", "Chemical Coordination", "Human Reproduction",
        "Reproductive Health", "Evolution", "Human Health and Disease", "Principles of Inheritance and Variation", "Molecular Basis of Inheritance")
    private val CHEM_PHYSICAL = setOf("Some Basic Concepts of Chemistry", "Structure of Atom", "Thermodynamics", "Equilibrium", "Redox Reactions", "Solutions", "Electrochemistry", "Chemical Kinetics")
    private val CHEM_INORGANIC = setOf("Classification of Elements and Periodicity", "Chemical Bonding and Molecular Structure", "p-Block Elements", "d- and f-Block Elements", "Coordination Compounds")

    fun groupOf(s: Subject, name: String): String = when (s) {
        Subject.BIOLOGY -> if (name in ZOOLOGY) "Zoology" else "Botany"
        Subject.CHEMISTRY -> if (name in CHEM_PHYSICAL) "Physical" else if (name in CHEM_INORGANIC) "Inorganic" else "Organic"
        else -> ""
    }

    val BOOKS = listOf("NCERT", "PYQ", "Module", "Mock")

    fun all(): List<Pair<Subject, String>> =
        PHYSICS.map { Subject.PHYSICS to it } + CHEMISTRY.map { Subject.CHEMISTRY to it } + BIOLOGY.map { Subject.BIOLOGY to it }
}
