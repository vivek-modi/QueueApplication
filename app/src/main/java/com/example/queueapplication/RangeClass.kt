package com.example.queueapplication

class RangeComposition {
    var bpExplained = mutableListOf<BpRange>()

    init {
        bpExplained.addAll(
            listOf(
                BpRange(
                    "Low",
                    "Less than 90",
                    "and",
                    "Less than 90",
                    "Consult your healthcare provider",
                    (100 / 12f) * 1,
                    (100 / 6F) * 0,
                    (100 / 6F) * 1,
                ) { sys, dia ->
                    (sys < 90 && dia < 60)
                },
                BpRange(
                    "Normal",
                    "90-119",
                    "and",
                    "60-79",
                    "Normal blood pressure",
                    (100 / 12f) * 3,
                    (100 / 6F) * 1,
                    (100 / 6F) * 2,
                ) { sys, dia ->
                    ((sys in 90..119) && (dia in 60..80))
                },
                BpRange(
                    "Elevated",
                    "120-129",
                    "and",
                    "Less than 80",
                    "Share this result with your healthcare provide",
                    (100 / 12f) * 5,
                    (100 / 6F) * 2,
                    (100 / 6F) * 3,
                ) { sys, dia ->
                    ((sys in 120..129) && (dia < 80))
                },
                BpRange(
                    "High",
                    "130-139",
                    "or",
                    "80-89",
                    "Share this result with your healthcare provide",
                    (100 / 12f) * 7,
                    (100 / 6F) * 3,
                    (100 / 6F) * 4,
                ) { sys, dia ->
                    ((sys in 130..139) || (dia in 80..89))
                },
                BpRange(
                    "Very High",
                    "140 or higher",
                    "or",
                    "90 or higher",
                    "Consult your healthcare provider immediately",
                    (100 / 12f) * 9,
                    (100 / 6F) * 4,
                    (100 / 6F) * 5,
                ) { sys, dia ->
                    ((sys in 140..179) || (dia in 90..119))
                },
                BpRange(
                    "Extremely High",
                    "180 or higher",
                    "and/or",
                    "120 or higher",
                    "Seek immediate medical",
                    (100 / 12F) * 11,
                    (100 / 6F) * 5,
                    (100 / 6F) * 6,
                ) { sys, dia ->
                    ((sys >= 180) || (dia >= 120))
                },
            )
        )
    }

    fun findReadingWithPointer(systolic: Int, diastolic: Int): Pair<String, Float> {
        for (range in bpExplained) {
            if (range.condition(systolic, diastolic)) {
                return range.name to range.progressBarPointer
            }
        }

        return "Something went wrong" to 0.0F
    }
}

data class BpRange(
    val name: String,
    val systolicString: String,
    val conditionString: String,
    val diastolicString: String,
    val subTitle: String,
    val progressBarPointer: Float,
    val startPoint: Float,
    val endPoint: Float,
    val condition: (Int, Int) -> Boolean
)