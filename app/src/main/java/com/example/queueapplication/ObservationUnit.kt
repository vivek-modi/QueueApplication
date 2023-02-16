package com.example.queueapplication

enum class ObservationUnit(val notation: String, val mdc: String) {
    KPA("kPa", "MDC_DIM_KILO_PASCAL"),
    MMHG("mmHg", "MDC_DIM_MMHG"),
}