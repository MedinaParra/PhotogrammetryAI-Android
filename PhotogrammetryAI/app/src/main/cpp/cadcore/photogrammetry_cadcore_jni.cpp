#include <jni.h>

#include <iomanip>
#include <sstream>
#include <stdexcept>
#include <string>

#include "OcctStepFeatureExtractor.hpp"

namespace {

std::string fromJString(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char* characters = env->GetStringUTFChars(value, nullptr);
    if (characters == nullptr) {
        throw std::runtime_error("GetStringUTFChars failed");
    }
    std::string result(characters);
    env->ReleaseStringUTFChars(value, characters);
    return result;
}

bool isUnreserved(unsigned char value) {
    return (value >= 'a' && value <= 'z')
            || (value >= 'A' && value <= 'Z')
            || (value >= '0' && value <= '9')
            || value == '-' || value == '_' || value == '.' || value == '~';
}

std::string percentEncode(const std::string& value) {
    static const char* hex = "0123456789ABCDEF";
    std::string encoded;
    encoded.reserve(value.size());
    for (unsigned char byte : value) {
        if (isUnreserved(byte)) {
            encoded.push_back(static_cast<char>(byte));
        } else {
            encoded.push_back('%');
            encoded.push_back(hex[(byte >> 4U) & 0x0fU]);
            encoded.push_back(hex[byte & 0x0fU]);
        }
    }
    return encoded;
}

std::string encodeResult(const pgai::StepExtractionResult& result) {
    std::ostringstream output;
    output << "PGAI_STEP_V1\n";
    output << "STATUS\t" << (result.success ? "OK" : "ERROR") << '\t'
           << percentEncode(result.errorCode) << '\t'
           << percentEncode(result.message.empty() ? "Sin mensaje del importador STEP." : result.message) << '\t'
           << percentEncode(result.sourceFileName) << '\t'
           << percentEncode(result.renderMeshKey) << '\n';

    output << std::setprecision(17);
    for (const pgai::ExtractedFeature& feature : result.features) {
        output << "FEATURE\t"
               << percentEncode(feature.id) << '\t'
               << percentEncode(feature.name) << '\t'
               << pgai::featureKindName(feature.kind) << '\t'
               << feature.origin.x << '\t' << feature.origin.y << '\t' << feature.origin.z << '\t'
               << feature.direction.x << '\t' << feature.direction.y << '\t' << feature.direction.z << '\t'
               << feature.radiusMm << '\t' << feature.extentMm << '\t' << feature.trackingWeight << '\n';
    }
    return output.str();
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_cl_ingenieria_photogrammetryai_core_foundation_NativeStepReferenceImporter_nativeImportStepEncoded(
        JNIEnv* env,
        jclass,
        jstring localFilePath,
        jstring displayName
) {
    try {
        const pgai::StepExtractionResult result = pgai::extractStepFeatures(
                fromJString(env, localFilePath),
                fromJString(env, displayName)
        );
        const std::string encoded = encodeResult(result);
        return env->NewStringUTF(encoded.c_str());
    } catch (const std::exception& error) {
        pgai::StepExtractionResult result;
        result.errorCode = "JNI_EXCEPTION";
        result.message = error.what();
        const std::string encoded = encodeResult(result);
        return env->NewStringUTF(encoded.c_str());
    } catch (...) {
        pgai::StepExtractionResult result;
        result.errorCode = "JNI_UNKNOWN_EXCEPTION";
        result.message = "El adaptador JNI lanzó una excepción desconocida.";
        const std::string encoded = encodeResult(result);
        return env->NewStringUTF(encoded.c_str());
    }
}
