package com.billing.simple.billsoft.forecast.selection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandClassification {
    private String pattern; // "SMOOTH", "INTERMITTENT", "ERRATIC", "LUMPY", "LIMITED"
    private double adi;     // Average Demand Interval
    private double cv2;     // Coefficient of Variation squared
    private int nonZeroCount;
    private int totalObservations;
    private String recommendedModel;
}
