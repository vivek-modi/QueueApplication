package com.example.queueapplication

class BloodPressureMeasurementStatus internal constructor(measurementStatus: Int) {

    val isBodyMovementDetected: Boolean
    val isCuffTooLoose: Boolean
    val isIrregularPulseDetected: Boolean
    val isPulseNotInRange: Boolean
    val isImproperMeasurementPosition: Boolean

    init {
        isBodyMovementDetected = measurementStatus and 0x0001 > 0
        isCuffTooLoose = measurementStatus and 0x0002 > 0
        isIrregularPulseDetected = measurementStatus and 0x0004 > 0
        isPulseNotInRange = measurementStatus and 0x0008 > 0
        isImproperMeasurementPosition = measurementStatus and 0x0020 > 0
    }
}