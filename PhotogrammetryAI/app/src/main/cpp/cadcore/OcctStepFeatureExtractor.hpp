#pragma once

#include <string>
#include <vector>

namespace pgai {

enum class FeatureKind {
    Cylinder,
    Plane,
    Circle,
    Axis,
    Point
};

struct Vec3d {
    double x = 0.0;
    double y = 0.0;
    double z = 0.0;
};

struct ExtractedFeature {
    std::string id;
    std::string name;
    FeatureKind kind = FeatureKind::Point;
    Vec3d origin;
    Vec3d direction{0.0, 0.0, 1.0};
    double radiusMm = 0.0;
    double extentMm = 0.0;
    double trackingWeight = 0.5;
};

struct StepExtractionResult {
    bool success = false;
    std::string errorCode;
    std::string message;
    std::string sourceFileName;
    std::string renderMeshKey;
    std::vector<ExtractedFeature> features;
};

StepExtractionResult extractStepFeatures(
        const std::string& localFilePath,
        const std::string& displayName
);

const char* featureKindName(FeatureKind kind);

}  // namespace pgai
