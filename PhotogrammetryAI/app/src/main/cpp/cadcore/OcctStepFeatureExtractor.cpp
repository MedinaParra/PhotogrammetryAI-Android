#include "OcctStepFeatureExtractor.hpp"

#include <algorithm>
#include <cmath>
#include <sstream>

#ifdef PGAI_USE_OCCT
#include <Bnd_Box.hxx>
#include <BRepAdaptor_Surface.hxx>
#include <BRepBndLib.hxx>
#include <GeomAbs_SurfaceType.hxx>
#include <IFSelect_ReturnStatus.hxx>
#include <STEPControl_Reader.hxx>
#include <Standard_Failure.hxx>
#include <TopAbs_ShapeEnum.hxx>
#include <TopExp_Explorer.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Face.hxx>
#include <TopoDS_Shape.hxx>
#include <gp_Ax1.hxx>
#include <gp_Cylinder.hxx>
#include <gp_Dir.hxx>
#include <gp_Pln.hxx>
#include <gp_Pnt.hxx>
#endif

namespace pgai {
namespace {

constexpr double kDirectionTolerance = 1.0e-5;
constexpr double kRadiusMergeToleranceMm = 0.05;
constexpr double kAxisMergeToleranceMm = 0.10;

std::string baseName(const std::string& path) {
    const std::size_t separator = path.find_last_of("/\\");
    return separator == std::string::npos ? path : path.substr(separator + 1);
}

Vec3d canonicalDirection(Vec3d direction) {
    const double length = std::sqrt(direction.x * direction.x
            + direction.y * direction.y + direction.z * direction.z);
    if (length <= 1.0e-12) {
        return {0.0, 0.0, 1.0};
    }
    direction.x /= length;
    direction.y /= length;
    direction.z /= length;

    const double absoluteX = std::abs(direction.x);
    const double absoluteY = std::abs(direction.y);
    const double absoluteZ = std::abs(direction.z);
    bool reverse = false;
    if (absoluteX >= absoluteY && absoluteX >= absoluteZ) {
        reverse = direction.x < 0.0;
    } else if (absoluteY >= absoluteX && absoluteY >= absoluteZ) {
        reverse = direction.y < 0.0;
    } else {
        reverse = direction.z < 0.0;
    }
    if (reverse) {
        direction.x = -direction.x;
        direction.y = -direction.y;
        direction.z = -direction.z;
    }
    return direction;
}

double dot(const Vec3d& left, const Vec3d& right) {
    return left.x * right.x + left.y * right.y + left.z * right.z;
}

Vec3d subtract(const Vec3d& left, const Vec3d& right) {
    return {left.x - right.x, left.y - right.y, left.z - right.z};
}

Vec3d cross(const Vec3d& left, const Vec3d& right) {
    return {
            left.y * right.z - left.z * right.y,
            left.z * right.x - left.x * right.z,
            left.x * right.y - left.y * right.x
    };
}

double norm(const Vec3d& value) {
    return std::sqrt(dot(value, value));
}

double lineDistance(const ExtractedFeature& left, const ExtractedFeature& right) {
    return norm(cross(subtract(right.origin, left.origin), left.direction));
}

bool equivalentCylinder(const ExtractedFeature& left, const ExtractedFeature& right) {
    return left.kind == FeatureKind::Cylinder
            && right.kind == FeatureKind::Cylinder
            && std::abs(left.radiusMm - right.radiusMm) <= kRadiusMergeToleranceMm
            && std::abs(std::abs(dot(left.direction, right.direction)) - 1.0) <= kDirectionTolerance
            && lineDistance(left, right) <= kAxisMergeToleranceMm;
}

bool equivalentPlane(const ExtractedFeature& left, const ExtractedFeature& right) {
    if (left.kind != FeatureKind::Plane || right.kind != FeatureKind::Plane) {
        return false;
    }
    if (std::abs(std::abs(dot(left.direction, right.direction)) - 1.0) > kDirectionTolerance) {
        return false;
    }
    return std::abs(dot(subtract(right.origin, left.origin), left.direction))
            <= kAxisMergeToleranceMm;
}

void appendOrMerge(std::vector<ExtractedFeature>& features, ExtractedFeature candidate) {
    for (ExtractedFeature& existing : features) {
        if (equivalentCylinder(existing, candidate) || equivalentPlane(existing, candidate)) {
            existing.extentMm = std::max(existing.extentMm, candidate.extentMm);
            existing.trackingWeight = std::max(existing.trackingWeight, candidate.trackingWeight);
            return;
        }
    }
    features.push_back(std::move(candidate));
}

#ifdef PGAI_USE_OCCT
Vec3d fromPoint(const gp_Pnt& point) {
    return {point.X(), point.Y(), point.Z()};
}

Vec3d fromDirection(const gp_Dir& direction) {
    return canonicalDirection({direction.X(), direction.Y(), direction.Z()});
}

double projectedExtent(const TopoDS_Face& face, const Vec3d& direction) {
    Bnd_Box box;
    BRepBndLib::Add(face, box, Standard_False);
    if (box.IsVoid() || box.IsOpen()) {
        return 0.0;
    }

    Standard_Real xMin, yMin, zMin, xMax, yMax, zMax;
    box.Get(xMin, yMin, zMin, xMax, yMax, zMax);
    double minimum = 1.0e300;
    double maximum = -1.0e300;
    for (int x = 0; x < 2; ++x) {
        for (int y = 0; y < 2; ++y) {
            for (int z = 0; z < 2; ++z) {
                Vec3d corner{
                        x == 0 ? xMin : xMax,
                        y == 0 ? yMin : yMax,
                        z == 0 ? zMin : zMax
                };
                const double projection = dot(corner, direction);
                minimum = std::min(minimum, projection);
                maximum = std::max(maximum, projection);
            }
        }
    }
    return std::max(0.0, maximum - minimum);
}
#endif

}  // namespace

const char* featureKindName(FeatureKind kind) {
    switch (kind) {
        case FeatureKind::Cylinder:
            return "CYLINDER";
        case FeatureKind::Plane:
            return "PLANE";
        case FeatureKind::Circle:
            return "CIRCLE";
        case FeatureKind::Axis:
            return "AXIS";
        case FeatureKind::Point:
            return "POINT";
    }
    return "POINT";
}

StepExtractionResult extractStepFeatures(
        const std::string& localFilePath,
        const std::string& displayName
) {
    StepExtractionResult result;
    result.sourceFileName = baseName(localFilePath);
    result.renderMeshKey = "occt-step:" + result.sourceFileName;

#ifndef PGAI_USE_OCCT
    (void) displayName;
    result.errorCode = "OCCT_MISSING";
    result.message = "OpenCASCADE no fue enlazado en esta variante nativa.";
    return result;
#else
    try {
        STEPControl_Reader reader;
        const IFSelect_ReturnStatus readStatus = reader.ReadFile(localFilePath.c_str());
        if (readStatus != IFSelect_RetDone) {
            result.errorCode = "STEP_READ_FAILED";
            result.message = "OpenCASCADE no pudo leer el archivo STEP.";
            return result;
        }
        const Standard_Integer transferredRoots = reader.TransferRoots();
        if (transferredRoots <= 0) {
            result.errorCode = "STEP_TRANSFER_FAILED";
            result.message = "El STEP no contiene raíces transferibles.";
            return result;
        }

        const TopoDS_Shape shape = reader.OneShape();
        if (shape.IsNull()) {
            result.errorCode = "STEP_NULL_SHAPE";
            result.message = "El STEP produjo una forma nula.";
            return result;
        }

        int cylinderIndex = 0;
        int planeIndex = 0;
        for (TopExp_Explorer explorer(shape, TopAbs_FACE); explorer.More(); explorer.Next()) {
            const TopoDS_Face face = TopoDS::Face(explorer.Current());
            try {
                BRepAdaptor_Surface surface(face, Standard_True);
                if (surface.GetType() == GeomAbs_Cylinder) {
                    const gp_Cylinder cylinder = surface.Cylinder();
                    const gp_Ax1 axis = cylinder.Axis();
                    ExtractedFeature feature;
                    std::ostringstream id;
                    id << "cylinder_" << ++cylinderIndex;
                    feature.id = id.str();
                    feature.name = "Superficie cilíndrica " + std::to_string(cylinderIndex);
                    feature.kind = FeatureKind::Cylinder;
                    feature.origin = fromPoint(axis.Location());
                    feature.direction = fromDirection(axis.Direction());
                    feature.radiusMm = cylinder.Radius();
                    feature.extentMm = projectedExtent(face, feature.direction);
                    feature.trackingWeight = 0.95;
                    appendOrMerge(result.features, std::move(feature));
                } else if (surface.GetType() == GeomAbs_Plane) {
                    const gp_Pln plane = surface.Plane();
                    ExtractedFeature feature;
                    std::ostringstream id;
                    id << "plane_" << ++planeIndex;
                    feature.id = id.str();
                    feature.name = "Plano CAD " + std::to_string(planeIndex);
                    feature.kind = FeatureKind::Plane;
                    feature.origin = fromPoint(plane.Location());
                    feature.direction = fromDirection(plane.Axis().Direction());
                    feature.extentMm = projectedExtent(face, feature.direction);
                    feature.trackingWeight = 0.60;
                    appendOrMerge(result.features, std::move(feature));
                }
            } catch (const Standard_Failure&) {
                // A damaged or unsupported individual face must not invalidate the whole model.
            }
        }

        if (result.features.empty()) {
            result.errorCode = "NO_TRACKING_SURFACES";
            result.message = "No se encontraron cilindros ni planos exactos en el STEP.";
            return result;
        }

        result.success = true;
        result.message = "OpenCASCADE extrajo " + std::to_string(result.features.size())
                + " rasgos de " + (displayName.empty() ? result.sourceFileName : displayName) + ".";
        return result;
    } catch (const Standard_Failure& error) {
        result.errorCode = "OCCT_EXCEPTION";
        result.message = error.GetMessageString() == nullptr
                ? "OpenCASCADE lanzó una excepción sin detalle."
                : error.GetMessageString();
        return result;
    } catch (const std::exception& error) {
        result.errorCode = "NATIVE_EXCEPTION";
        result.message = error.what();
        return result;
    }
#endif
}

}  // namespace pgai
