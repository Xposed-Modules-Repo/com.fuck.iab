#include "native_api.h"
#include "frida-gumjs.h"

#include <android/log.h>
#include <string>
#include <cstring>
#include <mutex>
#include <thread>
#include <jni.h>

#define LOG(...) __android_log_print(ANDROID_LOG_ERROR, "FKIAB", __VA_ARGS__)
#define LOG_FRIDA(...) __android_log_print(ANDROID_LOG_ERROR, "FRIDA", __VA_ARGS__)

static GumScript *script = nullptr;
static std::once_flag gum_init_flag;
static std::atomic<bool> script_started{false};

static std::string json_unescape(const std::string &input) {
    std::string out;
    out.reserve(input.size());

    for (size_t i = 0; i < input.size(); ++i) {
        char c = input[i];
        if (c == '\\' && i + 1 < input.size()) {
            char next = input[i + 1];
            switch (next) {
                case '"':  out += '"';  i++; break;
                case '\\': out += '\\'; i++; break;
                case 'n':  out += '\n'; i++; break;
                case 't':  out += '\t'; i++; break;
                case 'r':  out += '\r'; i++; break;
                default:   out += c;         break;
            }
        } else {
            out += c;
        }
    }

    return out;
}

static bool extract_json_string_field(const char *json, const char *key, std::string &out) {
    std::string pattern = std::string("\"") + key + "\":\"";
    const char *start = strstr(json, pattern.c_str());
    if (!start) return false;

    start += pattern.size();

    const char *p = start;
    while (*p) {
        if (*p == '\\' && *(p + 1) != '\0') {
            p += 2; // skip escaped char
            continue;
        }
        if (*p == '"') {
            out = json_unescape(std::string(start, p - start));
            return true;
        }
        p++;
    }

    return false;
}

static void on_message(const gchar *message, GBytes *data, gpointer user_data) {
    std::string type;
    std::string payload;

    if (!extract_json_string_field(message, "type", type)) {
        LOG_FRIDA("[raw] %s", message);
        return;
    }

    if (type == "error") {
        std::string description;
        if (extract_json_string_field(message, "description", description)) {
            LOG_FRIDA("[error] %s", description.c_str());
        } else {
            LOG_FRIDA("[error] %s", message);
        }
        return;
    }

    if (type == "log") {
        if (extract_json_string_field(message, "payload", payload)) {
            LOG_FRIDA("%s", payload.c_str());
        } else {
            LOG_FRIDA("%s", message);
        }
        return;
    }

    if (type == "send") {
        if (extract_json_string_field(message, "payload", payload)) {
            LOG_FRIDA("[send] %s", payload.c_str());
        } else {
            LOG_FRIDA("[send-raw] %s", message);
        }
        return;
    }

    LOG_FRIDA("[%s] %s", type.c_str(), message);
}

static void startFridaScript(const std::string &packageName, const std::string &jsSource) {
    bool expected = false;
    if (!script_started.compare_exchange_strong(expected, true)) {
        LOG("Script already started for this process, ignoring request for %s", packageName.c_str());
        return;
    }

    std::thread([packageName, jsSource]() {
        std::call_once(gum_init_flag, []() {
            gum_init_embedded();
        });

        GError *error = nullptr;
        GumScriptBackend *backend = gum_script_backend_obtain_v8();

        script = gum_script_backend_create_sync(
                backend,
                packageName.c_str(),
                jsSource.c_str(),
                nullptr,
                nullptr,
                &error
        );

        if (error) {
            LOG("Script compile error for %s: %s", packageName.c_str(), error->message);
            g_error_free(error);
            return;
        }

        gum_script_set_message_handler(script, on_message, nullptr, nullptr);
        gum_script_load_sync(script, nullptr);

        LOG("Script loaded for package: %s", packageName.c_str());

        GMainContext *context = g_main_context_default();
        while (true) {
            g_main_context_iteration(context, TRUE);
        }
    }).detach();
}

void onLibraryLoaded(const char *name, void *handle) {

}

extern "C"
__attribute__((visibility("default")))
__attribute__((used))
NativeOnModuleLoaded native_init(const NativeAPIEntries *entries) {
    LOG("native_init called");

    return onLibraryLoaded;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_fuck_iab_NativeBridge_startScript(
        JNIEnv *env, jclass /*clazz*/, jstring jPackageName, jstring jScriptSource) {

    const char *pkgChars = env->GetStringUTFChars(jPackageName, nullptr);
    const char *srcChars = env->GetStringUTFChars(jScriptSource, nullptr);

    std::string packageName(pkgChars);
    std::string scriptSource(srcChars);

    env->ReleaseStringUTFChars(jPackageName, pkgChars);
    env->ReleaseStringUTFChars(jScriptSource, srcChars);

    startFridaScript(packageName, scriptSource);
}